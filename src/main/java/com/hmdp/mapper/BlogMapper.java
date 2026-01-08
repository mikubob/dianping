package com.hmdp.mapper;

import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * 博客数据访问接口 - 定义博客数据的持久化操作方法
 * 提供对博客表的增删改查等基本操作，继承自MyBatis-Plus的BaseMapper
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface BlogMapper extends BaseMapper<Blog> {

}
