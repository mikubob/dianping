package com.hmdp.mapper;

import com.hmdp.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * 用户数据访问接口 - 定义用户基础数据的持久化操作方法
 * 提供对用户表的增删改查等基本操作，继承自MyBatis-Plus的BaseMapper
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface UserMapper extends BaseMapper<User> {

}
