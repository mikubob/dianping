package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherService extends IService<Voucher> {

    /**
     * 查询指定店铺的优惠券列表
     * 此方法用于获取特定店铺的所有优惠券信息
     * @param shopId 目标店铺的唯一标识ID
     * @return 包含指定店铺所有优惠券列表的结果对象
     */
    Result queryVoucherOfShop(Long shopId);

    /**
     * 添加秒杀优惠券
     * 此方法用于创建秒杀类型的优惠券，同时处理普通券和秒杀券的关联信息
     * @param voucher 包含秒杀优惠券详细信息的数据对象
     */
    void addSeckillVoucher(Voucher voucher);
}
