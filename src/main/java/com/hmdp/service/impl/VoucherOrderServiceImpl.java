package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 * 优惠券订单服务实现类 - 实现优惠券订单管理相关的具体业务逻辑
 * 提供优惠券订单的创建、查询、支付等核心功能的具体实现
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;

    /**
     * 秒杀优惠券
     * @param voucherId 优惠券ID
     * @return 订单ID
      * @throws RuntimeException 秒杀失败
     */
    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        //1. 查询秒杀券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //2. 判断秒杀券是否合法
        if(seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
            //3.秒杀券抢购尚未开始
            return Result.fail("秒杀尚未开始");
        }

        if(seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
            //4.秒杀券抢购已经结束
            return Result.fail("秒杀已经结束");
        }

        if(seckillVoucher.getStock() < 1){
            //5.秒杀券已经售完
            return Result.fail("秒杀券已经抢空");
        }

        //6.秒杀券合法，则秒杀券抢购成功，秒杀券库存减一
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .ge("stock", 1)
                .update();
        if(!success){
            //秒杀券抢购失败
            return Result.fail("秒杀券抢购失败");
        }
        //7.秒杀成功，创建对应的订单，并保存到数据库
        VoucherOrder voucherOrder = new VoucherOrder();
        //7.1.生成订单ID
        long orderId = redisIdWorker.nextId(RedisConstants.SECKILL_VOUCHER_ORDER);
        voucherOrder.setId(orderId);
        //7.2.用户ID
        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        //7.3.秒杀券ID
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        //8.返回订单ID
        return Result.ok(orderId);
    }
}
