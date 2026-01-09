package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

/**
 * 用户持有者工具类 - 用于在当前线程中存储和获取用户信息
 * 使用ThreadLocal机制确保用户信息在线程内的可见性和隔离性
 */
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO userDTO){
        tl.set(userDTO);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
