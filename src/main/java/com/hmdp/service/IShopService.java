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
}
