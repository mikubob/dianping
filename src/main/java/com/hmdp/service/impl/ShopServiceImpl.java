package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 商铺服务实现类 - 实现商铺管理相关的具体业务逻辑
 * 提供商铺的增删改查、缓存处理、地理位置查询等核心业务功能的具体实现
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据商铺ID查询商铺信息
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //1.从redis中查询商铺缓存
        String key= RedisConstants.CACHE_SHOP_KEY+id;//缓存key
        String shopJson= stringRedisTemplate.opsForValue().get(key);//获取缓存数据

        if (StrUtil.isNotBlank(shopJson)) {
            //2.存在，则直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }

        //3.不存在，则根据id查询数据库
        Shop shop = getById(id);
        if (shop == null) {
            //4.不存在，则返回错误
            return Result.fail("店铺不存在");
        }
        //5.将查询到的数据写入到redis中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));
        return Result.ok(shop);
    }
}
