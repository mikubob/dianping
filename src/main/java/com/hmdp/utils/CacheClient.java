package com.hmdp.utils;

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
     * 将数据缓存进Redis，并且设置超时时间
     *
     * @param key     缓存的键名
     * @param value   缓存的数据
     * @param timeout 超时时间
     * @param unit    时间单位
     */
    public void set(String key, Object value, Long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), timeout, unit);
    }

    /**
     * 将数据加入缓存，并且设置逻辑过期时间
     *
     * @param key     缓存的键名
     * @param value   缓存的数据
     * @param timeout 逻辑过期时间
     * @param unit    时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long timeout, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        //unit.toSeconds()是为了确保计时单位是秒
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(timeout)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 缓存穿透处理(根据id查询数据)
     *
     * @param keyPrefix  缓存的键名前缀
     * @param id         查询的id
     * @param type       查询的数据类型
     * @param dbFallback 数据查询的回调函数
     * @param timeout    超时时间
     * @param unit       时间单位
     * @param <T>        查询的数据类型
     * @param <ID>       查询的id的类型
     * @return 查询到的数据
     */
    public <T, ID> T handCachePenetration(String keyPrefix, ID id, Class<T> type,
                                          Function<ID, T> dbFallback, Long timeout, TimeUnit unit) {
        String key = keyPrefix + id;
        //1.从Redis中查询数据
        String jsonStr = stringRedisTemplate.opsForValue().get(key);

        T t = null;
        //2.判断缓存是否命中
        if (StrUtil.isNotBlank(jsonStr)) {
            //3.缓存命中，将数据转为对象并返回
            t = JSONUtil.toBean(jsonStr, type);
            return t;
        }

        //4.缓存未命中，判断缓存中查询的数据是否为空字符串（isNotBlank()把null和空字串都判断为false，所以排除了）
        if (Objects.nonNull(jsonStr)) {
            //5.缓存命中，但数据为空字符串，返回null
            return null;
        }
        //6.缓存未命中(jsonStr为null)，查询数据库
        t = dbFallback.apply(id);

        //7.判断查询到的数据是否存在店铺数据
        if (Objects.isNull(t)) {
            //7.1数据库未查询到数据，将空字符串写入Redis并返回null
            stringRedisTemplate.opsForValue().set(key, "", timeout, unit);
            return null;
        }

        //7.2数据库中查询到数据，将数据写入Redis并返回
        this.set(key, t, timeout, unit);
        //8.返回查询到的数据
        return t;
    }

    /**
     * 缓存重建线程池
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(RedisConstants.CACHE_REBUILD_THREAD_POOL_SIZE);

    /**
     * 缓存击穿处理(根据id查询数据)
     *
     * @param keyPrefix  缓存的键名前缀
     * @param id         查询的id
     * @param type       查询的数据类型
     * @param dbFallback 数据查询的回调函数
     * @param timeout    逻辑过期时间
     * @param unit       时间单位
     * @param <T>        查询的数据类型
     * @param <ID>       查询的id的类型
     * @return 查询到的数据
     */
    public <T, ID> T handleCacheBreakdown(String keyPrefix, ID id, Class<T> type,
                                          Function<ID, T> dbFallback, Long timeout, TimeUnit unit) {
        String key = keyPrefix + id;
        //1.从Redis中查询数据
        String jsonStr = stringRedisTemplate.opsForValue().get(key);

        //2.判断缓存是否命中
        if (StrUtil.isBlank(jsonStr)) {
            //3.缓存未命中，查询数据库并设置带逻辑过期的缓存
            T t = dbFallback.apply(id);
            if (t == null) {
                return null;
            }
            // 将数据写入Redis并设置逻辑过期时间
            this.setWithLogicalExpire(key, t, timeout, unit);
            return t;
        }

        //4.缓存命中，先将JSON字符串反序列化为对象
        RedisData redisData = JSONUtil.toBean(jsonStr, RedisData.class);
        T t = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //5.获取逻辑过期时间，判断是否过期
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            //6.未过期，返回数据
            return t;
        }

        //7.已过期，获取互斥锁，并且重建缓存
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //8.判断是否获取锁成功
        if (isLock) {
            //9.获取锁成功，创建线程，并开始重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //9.1重建缓存
                    T newT = dbFallback.apply(id);
                    //9.2写入Redis
                    this.setWithLogicalExpire(key, newT, timeout, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //9.3释放锁
                    unlock(lockKey);
                }
            });
        }

        //10.获取锁失败，再次查询缓存并重建缓存（双检操作）
        jsonStr = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(jsonStr)) {
            //11.缓存未命中，返回null
            return null;
        }
        //12.缓存命中，先将JSON字符串反序列化为对象
        redisData = JSONUtil.toBean(jsonStr, RedisData.class);
        t = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //13.判断逻辑过期时间，判断是否过期
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            //14.未过期，返回数据
            return t;
        }

        //15.已过期，返回过期数据
        return t;
    }


    /**
     * 释放锁
     *
     * @param key 锁的键名
     * @return 是否释放成功
     */
    private boolean tryLock(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS));//当flag为null时，说明没有获取锁，返回false
    }

    /**
     * 释放锁
     *
     * @param key 锁的键名
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}