package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Redis数据包装类 - 用于在Redis中存储数据及其过期时间
 * 将实际数据和过期时间一起存储，便于实现逻辑过期等功能
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
