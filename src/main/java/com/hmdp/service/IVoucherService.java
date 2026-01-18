package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务接口 - 定义优惠券相关的业务操作方法
 *  提供优惠券的增删改查、秒杀等核心功能
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherService extends IService<Voucher> {

    /**
     * 查询指定店铺的优惠券列表
     * @param shopId 目标店铺的唯一标识ID
     * @return 包含指定店铺所有优惠券列表的结果对象
     */
    Result queryVoucherOfShop(Long shopId);

    /**
     * 添加秒杀优惠券
     * @param voucher 包含秒杀优惠券详细信息的数据对象
     */
    void addSeckillVoucher(Voucher voucher);

    /**
     * 同步数据库中的秒杀券库存到Redis
     * 用于管理员补货或修正库存不一致的情况
     * @param voucherId 优惠券ID
     * @return Result 结果
     */
    Result syncStockToRedis(Long voucherId);

    /**
     * 新增普通优惠券
     * @param voucher 优惠券信息
     * @return 优惠券ID
     */
    Result addVoucher(Voucher voucher);
}