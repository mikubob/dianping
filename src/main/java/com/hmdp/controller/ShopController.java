package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * <p>
 * 商铺前端控制器 - 提供商铺管理相关的REST API接口
 * 包含商铺的增删改查、按类型查询、按名称查询等功能
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    /**
     * 根据商铺ID查询商铺详细信息
     * 此接口用于获取特定商铺的完整信息，包括名称、地址、经纬度等
     *
     * @param id 商铺唯一标识ID
     * @return 包含商铺详细信息的结果对象
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    /**
     * 新增商铺信息
     * 此接口用于向系统中添加新的商铺记录，包括商铺的基本信息
     *
     * @param shop 包含商铺详细信息的数据对象
     * @return 包含新增商铺ID的结果对象
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        // 写入数据库
        shopService.save(shop);
        // 返回店铺id
        return Result.ok(shop.getId());
    }

    /**
     * 更新商铺信息
     * 此接口用于修改已存在的商铺信息，如名称、地址、图片等
     *
     * @param shop 包含更新后商铺信息的数据对象
     * @return 成功响应结果
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        // 写入数据库
        return shopService.update(shop);
    }

    /**
     * 根据商铺类型分页查询商铺信息
     * 此接口用于按商铺类型筛选商铺，并支持分页展示，便于前端按分类浏览
     *
     * @param typeId  商铺类型ID，用于筛选特定类型的商铺
     * @param current 当前页码，用于分页查询
     * @return 包含指定类型商铺列表的结果对象
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     * 此接口提供模糊搜索功能，根据商铺名称中的关键词进行匹配查询
     *
     * @param name    商铺名称关键字，支持模糊匹配
     * @param current 当前页码，用于分页查询
     * @return 包含匹配商铺列表的结果对象
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}
