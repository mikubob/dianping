package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;//Redis模板类
    @Resource
    private RedissonClient redissonClient;//Redisson客户端

    private IVoucherOrderService proxy;//代理对象

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();//创建默认的RedisScript对象
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));//设置脚本文件的位置
        SECKILL_SCRIPT.setResultType(Long.class);//设置返回结果类型为Long
    }

    private final BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);//创建阻塞队列
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();//创建线程池

    /**
     * 初始化创建线程池，将订单信息保存到数据库中
     */
    @PostConstruct
    public void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());//创建线程
    }
    private class VoucherOrderHandler implements Runnable {
        /**
         * 线程任务
         */
        @Override
        public void run() {
            while (true) {
                try {
                    //1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    /**
     * 处理订单信息
     * @param voucherOrder
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //1.获取用户id
        Long userId = voucherOrder.getUserId();
        //2.创建锁对象
        RLock lock = redissonClient.getLock(RedisConstants.LOCK_ORDER_KEY + userId);
        //3.获取锁
        boolean isLock = lock.tryLock();
        //4.判断是否获取锁成功
        if (!isLock) {
            //4.获取锁失败，返回错误信息
            log.error("获取锁失败");
            return;
        }
        try {
            //5.创建订单
            proxy.createVoucherOrder(voucherOrder);
        }finally {
            //6.释放锁
            lock.unlock();
        }
    }

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
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.获取订单id
        Long orderId = redisIdWorker.nextId(RedisConstants.SECKILL_VOUCHER_ORDER);
        //3.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),//不传递任何键名，提供一个空集合
                voucherId.toString(), userId.toString(), String.valueOf(orderId)//作为ARGV的参数：ARGV[1]=voucherId, ARGV[2]=userId, ARGV[3]=orderId
        );
        //4.判断结果是否为0
        int r= result.intValue();
        if (r != 0) {
            //4.1.不为0，说明没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //保留阻塞队列
        //4.2.为0，有购买资格，将下单信息保存到阻塞队列中
        VoucherOrder voucherOrder = new VoucherOrder();
        //4.2.1.用户ID
        voucherOrder.setUserId(userId);
        //4.2.2.优惠券ID
        voucherOrder.setVoucherId(voucherId);
        //4.2.3.订单ID
        voucherOrder.setId(orderId);
        //4.2.4.将下单信息放入阻塞队列中去
        orderTasks.add(voucherOrder);
        //5.获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        //5.返回订单id
        return Result.ok(orderId);
    }

    /**
     * 创建秒杀券订单
     * @param voucherOrder
     */

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        /*//1.判断当前用户是否是第一单
        Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();//对应的sql语句为：select count(*) from voucher_order where user_id = ? and voucher_id = ?
        if (count >= 1) {
            //2.当前用户已经使用过该抢购券，无法重复抢购
            return Result.fail("当前用户已经使用过该抢购券，无法重复抢购");
        }
        //3.用户是首次抢购，可以继续抢购，秒杀券库存数量减一
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")//对应的sql语句：stock = stock - 1
                .eq("voucher_id", voucherId)//对应的sql语句：voucher_id = ?
                *//*.ge("stock", 1)//对应的sql语句：stock >= 1*//*
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
        return Result.ok(orderId);*/

        //获取用户id
        Long userId = voucherOrder.getUserId();
        //1.查询订单
        Long count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        //2.判断是否存在
         if (count > 0) {
             //3.存在，返回错误信息
              log.error("用户已经抢购过一次！");
              return;
         }

         //4.不存在，扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
         //5.扣减成功，创建订单
         save(voucherOrder);
    }
}
