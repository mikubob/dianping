package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(RedisConstants.CACHE_REBUILD_THREAD_POOL_SIZE);//创建线程池
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheClient cacheClient;

    /**
     * 根据商铺ID查询商铺信息
     *
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //使用逻辑过期解决缓存击穿
        Shop shop = cacheClient.handleCacheBreakdown(RedisConstants.CACHE_SHOP_KEY, id, Shop.class,
                this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("商铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 更新店铺信息
     *
     * @param shop
     * @return
     */
    @Override
    public Result update(Shop shop) {
        //1.查询店铺id，判断是否存在
        Long id = shop.getId();
        if (id == null) {
            //2.不存在，则返回错误
            return Result.fail("店铺id不能为空");
        }
        //3.存在，则更新数据库
        updateById(shop);
        //4.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        //5.返回成功
        return Result.ok();
    }

    /**
     * 保存商铺信息
     * 此方法用于向系统中添加新的商铺记录，包括商铺的基本信息
     *
     * @param shop 包含商铺详细信息的数据对象
     * @return 包含新增商铺ID的成功响应结果
     */
    @Override
    public Result saveShop(Shop shop) {
        // 写入数据库
        save(shop);
        // 返回店铺id
        return Result.ok(shop.getId());
    }

    /**
     * 根据商铺类型分页查询商铺信息
     * 此方法用于按商铺类型筛选商铺，并支持分页展示，便于前端按分类浏览
     *
     * @param typeId  商铺类型ID，用于筛选特定类型的商铺
     * @param current 当前页码，用于分页查询
     * @return 包含指定类型商铺列表的结果对象
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current) {
        // 根据类型分页查询
        Page<Shop> page = query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     * 此方法提供模糊搜索功能，根据商铺名称中的关键词进行匹配查询
     *
     * @param name    商铺名称关键字，支持模糊匹配
     * @param current 当前页码，用于分页查询
     * @return 包含匹配商铺列表的结果对象
     */
    @Override
    public Result queryShopByName(String name, Integer current) {
        // 根据类型分页查询
        Page<Shop> page = query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}