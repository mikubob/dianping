package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    /**
     * 秒杀优惠券
     *
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
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //3.秒杀券抢购尚未开始
            return Result.fail("秒杀尚未开始");
        }

        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            //4.秒杀券抢购已经结束
            return Result.fail("秒杀已经结束");
        }

        if (seckillVoucher.getStock() < 1) {
            //5.秒杀券已经售完
            return Result.fail("秒杀券已经抢空");
        }

        //6.创建订单
        Long userId = UserHolder.getUser().getId();
        //6.1.创建锁对象
        /*SimpleRedisLock simpleRedisLock = new SimpleRedisLock(stringRedisTemplate, "order:" + voucherId);*/
        RLock lock = redissonClient.getLock(RedisConstants.LOCK_ORDER_KEY + voucherId);

        //6.2.获取锁
        boolean isLock = lock.tryLock();
        if (!isLock) {
            //6.3.获取锁失败，返回失败结果
            return Result.fail("不允许重复下单");
        }

        try {
            // 6.4.获取锁成功，创建代理对象，使用代理对象调用第三方事务方法，防止事务失效
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(userId, voucherId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 创建秒杀券订单
     *
     * @param userId
     * @param voucherId
     * @return
     */

    @Transactional
    public Result createVoucherOrder(Long userId, Long voucherId) {
        //1.判断当前用户是否是第一单
        Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count >= 1) {
            //2.当前用户已经使用过该抢购券，无法重复抢购
            return Result.fail("当前用户已经使用过该抢购券，无法重复抢购");
        }
        //3.用户是首次抢购，可以继续抢购，秒杀券库存数量减一
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")//对应的sql语句：stock = stock - 1
                .eq("voucher_id", voucherId)//对应的sql语句：voucher_id = ?
                /*.ge("stock", 1)//对应的sql语句：stock >= 1*/
                .gt("stock", 0)
                .update();
        if (!success) {
            //秒杀券抢购失败
            return Result.fail("秒杀券抢购失败，库存不足！");
        }
        //4.秒杀成功，创建对应的订单，并保存到数据库
        VoucherOrder voucherOrder = new VoucherOrder();
        //4.1.生成订单ID
        long orderId = redisIdWorker.nextId(RedisConstants.SECKILL_VOUCHER_ORDER);
        voucherOrder.setId(orderId);
        //4.2.用户ID
        voucherOrder.setUserId(userId);
        //4.3.秒杀券ID
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        //5.返回订单ID
        return Result.ok(orderId);
    }
}
