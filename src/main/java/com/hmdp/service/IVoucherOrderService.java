package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 优惠券订单服务接口 - 定义优惠券订单管理相关的业务操作方法
 * 提供优惠券订单的创建、查询、支付等核心功能
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀优惠券
     * @param voucherId
     * @return
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 创建秒杀券订单
     * @param userId
     * @param voucherId
     * @return
     */
    Result createVoucherOrder(Long userId,Long voucherId);
}
