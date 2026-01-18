package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    private static final Logger log = LoggerFactory.getLogger(VoucherServiceImpl.class);
    
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    /**
     * 同步数据库中的秒杀券库存到Redis
     * 用于管理员补货或修正库存不一致的情况
     * @param voucherId 优惠券ID
     * @return Result 结果
     */
    @Override
    @Transactional
    public Result syncStockToRedis(Long voucherId) {
        // 查询数据库中的秒杀券信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher == null) {
            return Result.fail("秒杀券不存在");
        }
        
        // 获取当前数据库中的库存
        Integer dbStock = seckillVoucher.getStock();
        
        // 更新Redis中的库存
        stringRedisTemplate.opsForValue().set(
            RedisConstants.SECKILL_STOCK_KEY + voucherId, 
            dbStock.toString()
        );
        
        log.info("已同步数据库库存到Redis，券ID: {}, 库存: {}", voucherId, dbStock);
        return Result.ok();
    }

    /**
     * 查询指定店铺的优惠券列表
     * 通过Mapper查询指定店铺的所有优惠券信息
     * @param shopId 目标店铺的唯一标识ID
     * @return 包含指定店铺所有优惠券列表的结果对象
     */
    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 尝试从缓存获取数据
        String cacheKey = RedisConstants.VOUCHER_OF_SHOP_KEY + shopId;
        
        // 从缓存中获取数据
        String jsonStr = stringRedisTemplate.opsForValue().get(cacheKey);
        List<Voucher> vouchers = null;
        
        if (jsonStr != null) {
            // 缓存命中，将JSON字符串反序列化为对象
            try {
                vouchers = JSONUtil.toList(jsonStr, Voucher.class);
                log.debug("从缓存获取优惠券列表: {}", cacheKey);
            } catch (Exception e) {
                // 如果解析失败，从数据库查询
                log.warn("缓存数据解析失败，从数据库查询: {}", e.getMessage());
                // 删除有问题的缓存
                stringRedisTemplate.delete(cacheKey);
            }
        }
        
        if (vouchers == null) {
            // 缓存未命中或解析失败，查询数据库
            vouchers = getBaseMapper().queryVoucherOfShop(shopId);
            
            // 将查询结果存入缓存
            if (vouchers != null && !vouchers.isEmpty()) {
                try {
                    String jsonString = JSONUtil.toJsonStr(vouchers);
                    stringRedisTemplate.opsForValue().set(cacheKey, jsonString, 
                            RedisConstants.CACHE_SHOP_TTL, java.util.concurrent.TimeUnit.MINUTES);
                    log.debug("将优惠券列表存入缓存: {}", cacheKey);
                } catch (Exception e) {
                    log.warn("缓存写入失败: {}", e.getMessage());
                }
            }
        }
        
        // 返回结果
        return Result.ok(vouchers);
    }

    /**
     * 添加秒杀优惠券
     * 此方法在一个事务中同时创建普通券和秒杀券的关联信息，确保数据一致性
     * @param voucher 包含秒杀优惠券详细信息的数据对象
     */
    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);

        //保存秒杀库存到Redis中
        stringRedisTemplate.opsForValue().set(RedisConstants.SECKILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString());
        
        // 清理相关缓存，确保数据一致性
        stringRedisTemplate.delete(RedisConstants.VOUCHER_OF_SHOP_KEY + voucher.getShopId());
    }
    
    /**
     * 删除优惠券
     * 删除优惠券并清理相关缓存，防止删除后仍显示乱码
     * @param voucherId 优惠券ID
     */
    @Transactional
    public void deleteVoucher(Long voucherId) {
        log.info("开始删除优惠券，ID: {}", voucherId);
        
        // 1. 根据voucherId查询要删除的优惠券信息
        Voucher voucher = getById(voucherId);
        if (voucher == null) {
            log.warn("要删除的优惠券不存在，ID: {}", voucherId);
            return;
        }
        
        Long shopId = voucher.getShopId();
        
        // 2. 删除普通优惠券
        boolean removed = removeById(voucherId);
        log.info("删除普通优惠券结果: {}, ID: {}", removed, voucherId);
        
        // 3. 如果是秒杀券，删除对应的秒杀信息
        boolean seckillRemoved = seckillVoucherService.removeById(voucherId);
        log.info("删除秒杀券结果: {}, ID: {}", seckillRemoved, voucherId);
        
        // 4. 清理相关的Redis缓存，确保删除后不再显示
        // 清理秒杀库存缓存
        String seckillStockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
        stringRedisTemplate.delete(seckillStockKey);
        log.info("清理秒杀库存缓存: {}", seckillStockKey);
        
        // 5. 清理相关的店铺优惠券列表缓存，确保删除后列表中不再显示
        String shopVoucherKey = RedisConstants.VOUCHER_OF_SHOP_KEY + shopId;
        stringRedisTemplate.delete(shopVoucherKey);
        log.info("清理店铺优惠券列表缓存: {}", shopVoucherKey);
        
        log.info("优惠券删除成功，ID: {}，店铺ID: {}", voucherId, shopId);
    }

    @Override
    public Result addVoucher(Voucher voucher) {
        save(voucher);
        return Result.ok(voucher.getId());
    }
}