package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private StringRedisTemplate stringRedisTemplate;//Redis操作模板
    private String name;//锁的名称
    private static final String KEY_PREFIX = "lock:";//锁的key的前缀
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    /**
     * 分布式锁构造函数
     *
     * @param stringRedisTemplate
     * @param name
     */
    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    /**
     * 尝试获取锁
     *
     * @param timeoutSec
     * @return
     */
    @Override
    public boolean tryLock(Long timeoutSec) {
        // 获取当前线程的线程ID
        String threadId = ID_PREFIX + Thread.currentThread().threadId();
        // 获取锁
        String key = KEY_PREFIX + name;
        // 设置锁
        Boolean success = stringRedisTemplate.opsForValue().
                setIfAbsent(key, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放锁
     */
    @Override
    public void unlock() {
        // 判断锁的线程标识是否与当前线程的线程标识一致
        String currentThreadFlag = ID_PREFIX + Thread.currentThread().threadId();
        String redisThreadFlag = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if (currentThreadFlag.equals(redisThreadFlag) || currentThreadFlag != null) {
            // 一致，说明当前线程是锁的拥有者，可以释放锁
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
        // 不一致，说明当前线程不是锁的拥有者，不能释放锁
    }
}