package com.chua.starter.pay.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.pojo.*;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 *
 * 支付接口
 * @author CH
 * @since 2025/10/14 11:34
 */
@RestController
@RequestMapping("/v2/pay")
@Tag(name = "支付接口")
@RequiredArgsConstructor
public class PayController {

    private final PayMerchantOrderService payMerchantOrderService;

    /**
     * 创建订单
     * @param request 创建订单参数
     * @return 订单信息
     */
    @PutMapping("createOrder")
    @Operation(summary = "创建订单")
    public ReturnResult<CreateOrderV2Response> createOrder(CreateOrderV2Request request) {
       return payMerchantOrderService.createOrder(request);
    }


    /**
     * 创建签名
     */
    @PutMapping("/createSign")
    @Operation(summary = "创建签名")
    public ReturnResult<PaySignResponse> createSign(@Validated(AddGroup.class) @RequestBody CreateOrderV2Response request,
                                                 BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return payMerchantOrderService.createSign(request);
    }


    /**
     * 订单退款
     */
    @PutMapping("/refundOrder/{payMerchantOrderCode}")
    @Operation(summary = "订单退款")
    public ReturnResult<RefundOrderV2Response> refundOrder(@PathVariable String payMerchantOrderCode,
                                                           @Validated(UpdateGroup.class) @RequestBody RefundOrderV2Request request,
                                                           BindingResult bindingResult
                                                           ) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return payMerchantOrderService.refundOrder(payMerchantOrderCode, request);
    }
    /**
     * 订单退款
     */
    @PutMapping("/refundOrderToWallet/{payMerchantOrderCode}")
    @Operation(summary = "订单退款到钱包")
    public ReturnResult<RefundOrderV2Response> refundOrderToWallet(@PathVariable String payMerchantOrderCode,
                                                           @Validated(UpdateGroup.class) @RequestBody RefundOrderV2Request request,
                                                           BindingResult bindingResult
                                                           ) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return payMerchantOrderService.refundOrderToWallet(payMerchantOrderCode, request);
    }
}
