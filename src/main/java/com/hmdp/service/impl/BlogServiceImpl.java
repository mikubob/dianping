package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.ScrollResult;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
    @Resource
    private IFollowService followService;

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
        //3. 查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }

    /**
     * 检查博客是否被当前用户点赞
     *
     * @param blog 博客实体
     */
    private void isBlogLiked(Blog blog) {
        // 1. 获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录，无需检查点赞状态
            return;
        }
        Long userId = user.getId();

        // 2. 获取博客ID
        Long blogId = blog.getId();

        // 3. 查询Redis中该博客的点赞集合
        String key = RedisConstants.BLOG_LIKED_KEY + blogId;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        // 4. 判断是否已点赞
        blog.setIsLike(score != null);
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

    /**
     * 保存博客
     * 此方法用于让用户发布新的博客内容，自动设置发布用户为当前登录用户
     *
     * @param blog 博客实体对象，包含博客的详细内容
     * @return 包含新增博客ID的成功响应结果
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("新增笔记失败！");
        }
        // 查询笔记作者的所有粉丝 select * from tb_follow where follow_id = ?
        List<Follow> follows = followService.query().eq("follow_id", user.getId()).list();
        for (Follow follow : follows) {
            Long userId = follow.getUserId();
            // 判断是否为空
            if (userId == null) {
                continue;
            }
            String key = RedisConstants.FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    /**
     * 查询当前登录用户的所有博客
     * 该方法用于查询当前登录用户发布的所有博客内容
     *
     * @param current 当前页码，用于分页查询
     * @return 包含当前登录用户博客列表的成功响应结果
     */
    @Override
    public Result queryMyBlog(Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 根据用户ID查询其发布的博客
     * 该方法用于查询指定用户发布的所有博客内容
     *
     * @param current 当前页码，用于分页查询
     * @param id      用户ID，指定要查询的博主
     * @return 包含指定用户博客列表的成功响应结果
     */
    @Override
    public Result queryBlogByUserId(Integer current, Long id) {
        // 1.根据用户查询
        Page<Blog> page = query()
                .eq("user_id", id)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 2.获取当前页数据
        List<Blog> records = page.getRecords();
        // 3.查询用户信息
        records.forEach(this::queryBlogUser);
        return Result.ok(records);
    }

    /**
     * 查询当前用户所关注的用户所发布的博客
     *
     * @param max    最大时间
     * @param offset 游标
     * @return 博客列表
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.查询收件箱 ZREVRANGEBYSCORE key max min LIMIT offset count
        String key = RedisConstants.FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);//获取当前用户所关注的用户所发布的博客
        //3.非空判断
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        //4.解析数据 blogId，score(时间戳), offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;//2
        int os = 1;//2
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //4.1.获取id
            ids.add(Long.valueOf(Objects.requireNonNull(typedTuple.getValue())));
            //4.2.获取分数(时间戳)
            long time = Objects.requireNonNull(typedTuple.getScore()).longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        os = minTime == max ? os : os + offset;//获取当前页码
        //5.根据id查询blog
        String idStr = StrUtil.join(",", ids);//ids转成字符串
        List<Blog> blogs = query().in("id", ids)//查询指定id的博客
                .last("ORDER BY FIELD(id," + idStr + ")")//按顺序返回
                .list();//转成List
        for (Blog blog : blogs) {
            //5.1.查询blog有关的用户
            queryBlogUser(blog);//查询blog有关的用户
            //5.2.查询blog是否被点赞
            isBlogLiked(blog);
        }

        //6.封装并返回
        return Result.ok(new ScrollResult(blogs, minTime, os));
    }
}
