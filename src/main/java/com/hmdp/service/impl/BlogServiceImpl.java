package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户博客服务实现类 - 实现博客管理相关的具体业务逻辑
 * 提供博客的增删改查、点赞等核心功能的具体实现
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询博客详情
     *
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        //1. 查询blog
        Blog blog = getById(id);
        if (blog == null) {
            // 404
            return Result.fail("笔记不存在！");
        }
        //2. 查询blog有关的用户
        queryBlogUser(blog);
        return Result.ok(blog);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }

    /**
     * 查询最热博客
     *
     * @param current
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")//按点赞量排序(降序)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));//分页查询
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(this::queryBlogUser);
        return Result.ok(records);
    }

    /**
     * 点赞博客
     *
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        //1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        //2.判断当前用户是否已经点赞
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());//获取当前用户点赞的分数
        if (score != null) {
            //3.如果已经点赞，取消点赞
            //3.1.数据库点赞数量-1 对应的sql语句：update blog set liked = liked - 1 where id = ?
            boolean isSuccess = update()
                    .setSql("liked = liked - 1")
                    .eq("id", id)
                    .update();
            //3.2.取消点赞，把用户从redis的set集合中移除
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        } else {
            //4.如果未点赞，点赞
            //4.1.数据库点赞数量+1 对应的sql语句：update blog set liked = liked + 1 where id = ?
            boolean isSuccess = update()
                    .setSql("liked = liked + 1")
                    .eq("id", id)
                    .update();
            //4.2.保存用户到redis的set集合中
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }
        return Result.ok();
    }

    /**
     * 查询博客点赞数据
     *
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        //1.查询top5的点赞用户，按点赞时间排序
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);//获取前5个用户
        if (top5 == null || top5.isEmpty()) {
            //如果为空，直接返回一个空集合
            return Result.ok(Collections.emptyList());
        }

        //2.解析出其中的用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //3.根据用户id查询用户
        String idStr = StrUtil.join(",", ids);//ids转成字符串
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids)//查询指定id的用户
                .last("ORDER BY FIELD(id," + idStr + ")")//按顺序返回
                .list()//转成List
                .stream()//转成流
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))//转成UserDTO
                .toList();//转成List
        //4.返回
        return Result.ok(userDTOS);
    }
}
