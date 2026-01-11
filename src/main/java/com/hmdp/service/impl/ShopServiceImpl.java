package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 商铺服务实现类 - 实现商铺管理相关的具体业务逻辑
 * 提供商铺的增删改查、缓存处理、地理位置查询等核心业务功能的具体实现
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);//创建线程池
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据商铺ID查询商铺信息
     *
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        // 使用缓存穿透解决方案查询商铺信息
        /*Shop shop = queryWithPassThrough(id);
        if (shop == null) {
            return Result.fail("商铺不存在");
        }*/

        // 使用互斥锁解决缓存击穿
       /* Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("商铺不存在");
        }*/

        //使用逻辑过期解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);
        if (shop == null) {
            return Result.fail("商铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 更新店铺信息
     *
     * @param shop
     * @return
     */
    @Override
    public Result update(Shop shop) {
        //1.查询店铺id，判断是否存在
        Long id = shop.getId();
        if (id == null) {
            //2.不存在，则返回错误
            return Result.fail("店铺id不能为空");
        }
        //3.存在，则更新数据库
        updateById(shop);
        //4.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        //5.返回成功
        return Result.ok();
    }


    /**
     * 缓存穿透解决方案
     *
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        //1.从redis中查询商铺缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;//缓存key
        String shopJson = stringRedisTemplate.opsForValue().get(key);//获取缓存数据

        if (StrUtil.isNotBlank(shopJson)) {
            //2.存在，则直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        } else {
            //3.不存在，则根据id查询数据库
            Shop shop = getById(id);
            if (shop == null) {
                //4.将空值写入redis中
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                //5.不存在，则返回错误
                return null;
            }
            //6.将查询到的数据写入到redis中
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return shop;
        }
    }

    /**
     * 互斥锁解决方案
     *
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        //1.从redis中查询商铺缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;//缓存key
        String shopJson = stringRedisTemplate.opsForValue().get(key);//获取缓存数据

        if (StrUtil.isNotBlank(shopJson)) {
            //2.存在，则直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //3.判断命中是否为空值
        if (shopJson != null) {
            return null;
        }

        //4.实现缓存重建
        //4.1.获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            if (!isLock) {
                //4.2.获取锁失败，则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //4.3.获取锁成功，则根据id查询数据库
            shop = getById(id);

            //5.不存在，返回错误
            if (shop == null) {
                //将空值写入redis中
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                //返回错误信息
                return null;
            }
            //将查询到的数据写入到redis中
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //6.释放锁
            unlock(lockKey);
        }
        //7.返回
        return shop;
    }

    /**
     * 逻辑过期解决缓存击穿方案
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id){
        //1.从redis中查询商铺缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isBlank(json)) {
            //3.未命中，返回错误
            return null;
        }
        //4.命中，则需要先把json反序列化为对象
        RedisData redisDate = JSONUtil.toBean(json, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisDate.getData(), Shop.class);
        LocalDateTime expireTime = redisDate.getExpireTime();
        //5.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
             //5.1.未过期，返回店铺信息
            return shop;
        }
        //5.2.已过期，需要缓存重建
        //6.缓存重建
        //6.1.获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //6.2.判断是否获取锁成功
        if (isLock) {
            //6.3.获取锁成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                //6.4.缓存重建
                try {
                    this.saveShop2Redis(id, 20L);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
            });
        }
        //7.返回过期的商铺信息
        return shop;
    }

    //尝试获取锁
    private boolean tryLock(String key) {
        //在 Redis 中原子地创建一个 10 秒后过期的键，仅当该键不存在时才创建成功，并通过返回值判断是否获取"锁"成功
        boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);//这么返回当flag为null的时候会返回false
    }

    //释放锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id,Long expireSeconds){
        //1.查询店铺数据
        Shop shop = getById(id);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        if(shop != null) {
            redisData.setData(shop);
            redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        } else {
            // 如果商铺不存在，缓存空值，设置较短的过期时间
            redisData.setData(null);
            redisData.setExpireTime(LocalDateTime.now().plusSeconds(10)); // 空值设置较短的过期时间
        }
        //3.写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }
}