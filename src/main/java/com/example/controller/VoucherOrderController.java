package com.example.controller;


import com.example.dto.Result;
import com.example.service.IVoucherOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/voucher-order")
@Tag(name = "优惠券下单")
public class VoucherOrderController {

    @Autowired
    private IVoucherOrderService voucherOrderService;


    @PostMapping("/seckill/{id}")
    @Operation(summary = "秒杀优惠券下单")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }
}
