package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    /**
     * 发布用户博客/探店笔记
     * 此接口用于让用户发布新的博客内容，自动设置发布用户为当前登录用户
     *
     * @param blog 包含博客详细内容的数据对象
     * @return 包含新增博客ID的成功响应结果
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /**
     * 点赞博客
     *
     * @param id
     * @return
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        // 修改点赞数量
        return blogService.likeBlog(id);
    }

    /**
     * 查询当前登录用户的所有博客
     * 该接口用于查询当前登录用户发布的所有博客内容
     *
     * @param current 当前页码，用于分页查询
     * @return 包含当前登录用户博客列表的成功响应结果
     */
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryMyBlog(current);
    }

    /**
     * 查询最热博客
     *
     * @param current
     * @return
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    /**
     * 查询博客详情
     *
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        return blogService.queryBlogById(id);
    }

    /**
     * 查询博客点赞数
     *
     * @param id
     * @return
     */
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        return blogService.queryBlogLikes(id);
    }

    /**
     * 根据id查询博主的探店笔记
     * 该接口用于查询指定用户发布的所有博客内容
     *
     * @param current 当前页码，用于分页查询
     * @param id      用户ID，指定要查询的博主
     * @return 包含指定用户博客列表的成功响应结果
     */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(@RequestParam(
            value = "current", defaultValue = "1") Integer current, @RequestParam("id") Long id){
        return blogService.queryBlogByUserId(current, id);
    }
}