package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 用户博客服务接口 - 定义博客管理相关的业务操作方法
 * 提供博客的增删改查、点赞等核心功能
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 查询博客详情
     * @param id
     * @return
     */
    Result queryBlogById(Long id);

    /**
     * 查询最热博客
     * @param current
     * @return
     */
    Result queryHotBlog(Integer current);

    /**
     * 点赞博客
     */
    Result likeBlog(Long id);

    /**
     * 查询博客点赞
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);

    /**
     * 保存博客
     * @param blog 博客实体
     * @return 包含新增博客ID的成功响应结果
     */
    Result saveBlog(Blog blog);

    /**
     * 查询当前登录用户的所有博客
     * @param current 当前页码
     * @return 用户的博客列表
     */
    Result queryMyBlog(Integer current);

    /**
     * 根据用户ID查询其发布的博客
     * @param current 当前页码
     * @param id 用户ID
     * @return 指定用户的博客列表
     */
    Result queryBlogByUserId(Integer current, Long id);
}
