package com.hmdp.controller;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.ISeckillVoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 优惠券发放、核销等前端控制器 - 提供优惠券相关的REST API接口
 * 包含优惠券的增删改查、秒杀券发布、库存同步等功能
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class VoucherController {

    private final IVoucherService voucherService;
    private final ISeckillVoucherService seckillVoucherService;

    /**
     * 新增普通优惠券
     * @param voucher 优惠券信息
     * @return 优惠券ID
     */
    @PostMapping
    public Result addVoucher(@RequestBody Voucher voucher) {
        voucherService.save(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 查询店铺的优惠券列表
     * @param shopId 店铺ID
     * @return 优惠券列表
     */
    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
        return voucherService.queryVoucherOfShop(shopId);
    }

    /**
     * 发布秒杀券
     * @param voucherVO 秒杀券信息
     * @return 优惠券ID
     */
    @PostMapping("/seckill")
    public Result addSeckillVoucher(@RequestBody Voucher voucherVO) {
        voucherService.addSeckillVoucher(voucherVO);
        return Result.ok(voucherVO.getId());
    }
    
    /**
     * 同步数据库库存到Redis
     * 用于管理员补货或修正库存不一致的情况
     * @param voucherId 优惠券ID
     * @return Result 结果
     */
    @PutMapping("/sync_stock/{id}")
    public Result syncStockToRedis(@PathVariable("id") Long voucherId) {
        return voucherService.syncStockToRedis(voucherId);
    }
}