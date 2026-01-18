package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 关注服务接口 - 定义用户关注相关的业务操作方法
 * 提供用户关注、取消关注、查询关注列表等社交功能
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注用户
     * @param followUserId
     * @param isFollow
     * @return
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 取消关注
     * @param followUserId
     * @return
     */
    Result isFollow(Long followUserId);
}
