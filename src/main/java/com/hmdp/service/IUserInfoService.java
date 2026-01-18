package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.UserInfo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 用户信息服务接口 - 定义用户资料管理相关的业务操作方法
 * 提供用户详细信息的查询、更新等核心功能
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserInfoService extends IService<UserInfo> {

    /**
     * 根据用户ID获取用户详细信息
     * 此方法用于获取指定用户ID的用户详细资料信息
     *
     * @param userId 目标用户的唯一标识ID
     * @return 包含用户详细信息的结果对象
     */
    Result queryUserInfoById(Long userId);
}