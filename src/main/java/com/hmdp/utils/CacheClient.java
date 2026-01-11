package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 将数据缓存进redis，并设置有效期
     *
     * @param key
     * @param value
     * @param timeout
     * @param unit
     */
    public void set(String key, Object value, Long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), timeout, unit);
    }

    /**
     * 将数据缓存进redis，并设置逻辑有效期
     *
     * @param key     缓存的key
     * @param value   缓存的数据
     * @param timeout 缓存有效期
     * @param unit    时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long timeout, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        // unit.toSeconds()是为了确保缓存时间是秒的
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(timeout)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 根据id查询数据（处理缓存穿透）
     *
     * @param keyPrefix  前缀
     * @param id         id
     * @param type       数据类型
     * @param dbFallback 数据库查询方法
     * @param timeout    有效期
     * @param unit       时间单位
     * @param <T>        泛型
     * @param <ID>       id类型
     * @return 数据
     */
    public <T, ID> T handleCachePenetration(String keyPrefix, ID id, Class<T> type,
                                            Function<ID, T> dbFallback, Long timeout, TimeUnit unit) {
        String key = keyPrefix + id;
        //1.从redis中查询店铺的数据
        String jsonStr = stringRedisTemplate.opsForValue().get(key);

        T t = null;

        //2.判断缓存是否命中
        if (StrUtil.isNotBlank(jsonStr)) {
            //2.1 缓存命中，返回数据
            t = JSONUtil.toBean(jsonStr, type);
            return t;
        } else {
            //2.2 缓存未命中,判断缓存中查询的数据是否为空字符串（isNotBlank把null和空字符串都排除了）
            if (Objects.nonNull(jsonStr)) {
                //2.2.1 当前缓存数据为空字符串（说明该数据是之前缓存的空对象），返回错误信息
                return null;
            }
            //2.2.2 当前缓存数据为null，从数据库查询数据
            t = dbFallback.apply(id);
            //3.判断数据库是否存在该店铺数据
            if (Objects.isNull(t)) {
                //3.1 数据库中不存在该店铺数据，将空对象缓存进redis，并返回错误信息
                this.set(key, "", timeout, unit);
                return null;
            } else {
                //3.2 数据库中存在该店铺数据，将数据缓存进redis，并返回数据
                this.set(key, t, timeout, unit);
                return t;
            }
        }
    }

    /**
     * 缓存重建线程池
     */
    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    public <T, ID> T handleCacheBreakdown(String keyPrefix, ID id, Class<T> type,
                                          Function<ID, T> dbFallback, Long timeout, TimeUnit unit) {
        String key = keyPrefix + id;
        
        // 1. 从redis中查询数据
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        
        // 2. 判断缓存是否命中
        if (StrUtil.isBlank(jsonStr)) {
            // 缓存未命中，直接返回null
            return null;
        }
        
        // 3. 解析缓存数据
        RedisData redisData = JSONUtil.toBean(jsonStr, RedisData.class);
        T t = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        
        // 4. 判断缓存是否未过期
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            // 缓存未过期，直接返回数据
            return t;
        }
        
        // 5. 缓存已过期，尝试获取互斥锁进行缓存重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        if (tryLock(lockKey)) {
            // 获取锁成功，开启独立线程进行缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    T newT = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, newT, timeout, unit);
                } catch (Exception e) {
                    log.error("缓存重建异常", e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        } else {
            // 获取锁失败，执行双检机制
            return doubleCheck(key, type, dbFallback, timeout, unit);
        }
        
        // 返回过期数据，但允许后台线程异步更新
        return t;
    }
    
    /**
     * 双重检查机制，获取锁失败后再次检查缓存状态
     */
    private <T, ID> T doubleCheck(String key, Class<T> type, Function<ID, T> dbFallback, Long timeout, TimeUnit unit) {
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(jsonStr)) {
            return null; // 缓存未命中
        }
        
        RedisData redisData = JSONUtil.toBean(jsonStr, RedisData.class);
        T t = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            // 缓存未过期，直接返回数据
            return t;
        }
        
        // 即使缓存仍然过期，也返回当前数据，避免长时间阻塞
        return t;
    }

    //获取锁
    private boolean tryLock(String key) {
        //在 Redis 中原子地创建一个 10 秒后过期的键，仅当该键不存在时才创建成功，并通过返回值判断是否获取"锁"成功
        boolean flag = Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS));
        return BooleanUtil.isTrue(flag);//这么返回当flag为null的时候会返回false
    }

    //释放锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
