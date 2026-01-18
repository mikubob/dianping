package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 商铺服务接口 - 定义商铺管理相关的业务操作方法
 * 包括商铺的增删改查、缓存处理、地理位置查询等核心业务功能
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据商铺ID查询商铺信息
     * @param id
     * @return
     */
    Result queryById(Long id);

    /**
     * 更新商铺信息
     * @param shop
     * @return
     */
    Result update(Shop shop);

    /**
     * 保存商铺信息
     * @param shop 包含商铺详细信息的数据对象
     * @return 包含新增商铺ID的成功响应结果
     */
    Result saveShop(Shop shop);

    /**
     * 根据商铺类型分页查询商铺信息
     * @param typeId 商铺类型ID，用于筛选特定类型的商铺
     * @param current 当前页码，用于分页查询
     * @return 包含指定类型商铺列表的结果对象
     */
    Result queryShopByType(Integer typeId, Integer current);

    /**
     * 根据商铺名称关键字分页查询商铺信息
     * @param name 商铺名称关键字，支持模糊匹配
     * @param current 当前页码，用于分页查询
     * @return 包含匹配商铺列表的结果对象
     */
    Result queryShopByName(String name, Integer current);
}
