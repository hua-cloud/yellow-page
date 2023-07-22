package com.example.service;

import com.example.dto.Result;
import com.example.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀优惠券下单
     * @param voucherId
     * @return
     */
    Result seckillVoucher(Long voucherId);
}
