package com.hmdp.service.impl;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 秒杀优惠券服务实现类 - 实现秒杀优惠券管理相关的具体业务逻辑
 * 提供秒杀券的增删改查、库存管理、时间控制等核心功能的具体实现
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

}
