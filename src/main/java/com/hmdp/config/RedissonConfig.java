package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private String port;

    @Value("${spring.data.redis.password}")
    private String password;

    @Value("${spring.data.redis.database:2}")
    private String database;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = "redis://" + host + ":" + port;// redis://127.0.0.1:6379
        config.useSingleServer()// 单机模式
                .setAddress(address)// 设置redis地址
                .setPassword(password)// 设置密码
                .setDatabase(Integer.parseInt(database));// 设置数据库
        return Redisson.create(config);
    }
}