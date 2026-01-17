package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher")
public class VoucherController {

    @Resource
    private IVoucherService voucherService;

    /**
     * 新增普通优惠券
     * 此接口用于创建普通类型的优惠券，不包含秒杀相关信息
     * @param voucher 包含普通优惠券详细信息的数据对象
     * @return 包含新增优惠券ID的成功响应结果
     */
    @PostMapping
    public Result addVoucher(@RequestBody Voucher voucher) {
        voucherService.save(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 新增秒杀优惠券
     * 此接口用于创建秒杀类型的优惠券，包含特殊的秒杀时间、库存等信息
     * @param voucher 包含秒杀优惠券详细信息的数据对象
     * @return 包含新增优惠券ID的成功响应结果
     */
    @PostMapping("seckill")
    public Result addSeckillVoucher(@RequestBody Voucher voucher) {
        voucherService.addSeckillVoucher(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 查询指定店铺的优惠券列表
     * 此接口用于获取特定店铺提供的所有优惠券信息，便于用户查看该店铺的优惠活动
     * @param shopId 目标店铺的唯一标识ID
     * @return 包含指定店铺所有优惠券列表的结果对象
     */
    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
       return voucherService.queryVoucherOfShop(shopId);
    }
    
    /**
     * 删除指定的优惠券
     * 此接口用于删除指定ID的优惠券及其相关数据
     * @param id 要删除的优惠券ID
     * @return 删除操作的结果
     */
    @DeleteMapping("/{id}")
    public Result deleteVoucher(@PathVariable("id") Long id) {
        voucherService.deleteVoucher(id);
        return Result.ok();
    }
}