package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 关注服务实现类 - 实现用户关注相关的具体业务逻辑
 * 提供用户关注、取消关注、查询关注列表等社交功能的具体实现
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    /**
     * 关注用户
     *
     * @param followUserId
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = "follow:" + userId;
        //2.判断是否关注
        if (isFollow) {
            //3.关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess){
                //把关注的id放入redis的set集合中
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        }else {
            //4.取消关注，删除数据
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if (isSuccess){
                //把取消关注的id从redis的set集合中移除
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 取消关注
     *
     * @param followUserId
     * @return
     */
    @Override
    public Result isFollow(Long followUserId) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.查询是否关注
        Long count = query()
                .eq("user_id", userId)
                .eq("follow_user_id", followUserId)
                .count();

        //3.判断
        return Result.ok(count > 0);
    }

    /**
     * 共同关注
     * @param id
     * @return
     */
    @Override
    public Result followCommons(Long id) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key1 = "follow:" + userId;
        //2.求交集
        String key2 = "follow:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        //3.解析集合
        if (intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).toList();
        //4.根据id查询用户
        List<User> users = userService.listByIds(ids);
        return Result.ok(users);
    }
}
