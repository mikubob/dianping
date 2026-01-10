package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * 商铺类型服务实现类 - 实现商铺分类管理相关的具体业务逻辑
 * 提供商铺类型的增删改查等基础功能的具体实现，用于管理系统中的商铺分类信息
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;//"cache:shopType:"

        // 1. 尝试从 Redis 获取
        List<String> shopTypeJsonList = stringRedisTemplate.opsForList().range(key, 0, -1);

        if (CollectionUtil.isNotEmpty(shopTypeJsonList)) {
            // 2. 缓存命中：解析 JSON 字符串列表为对象列表
            List<ShopType> shopTypes = shopTypeJsonList.stream()
                    .map(json -> JSONUtil.toBean(json, ShopType.class))
                    .collect(Collectors.toList());
            return Result.ok(shopTypes); // 已有序，无需再排序
        }

        // 3. 缓存未命中：查数据库
        List<ShopType> shopTypes = lambdaQuery().orderByAsc(ShopType::getSort).list();

        // 4.如果不存在，则返回错误信息
        if (CollectionUtil.isEmpty(shopTypes)) {
            return Result.fail("商铺类型不存在");
        }
        // 5. 存入 Redis 缓存
        List<String> shopTypesJson = shopTypes.stream()
                .map(JSONUtil::toJsonStr)//转为JSON字符串
                .toList();//转为列表
        //因为从数据库中取出时已经为按顺序拿出来的，这里要确保有序，所以这里要倒序（类似栈）
        stringRedisTemplate.opsForList().rightPushAll(key, shopTypesJson);
        // 6. 返回结果
        return Result.ok(shopTypes);
    }
}
