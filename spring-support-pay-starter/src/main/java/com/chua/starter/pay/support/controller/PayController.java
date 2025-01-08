package com.chua.starter.pay.support.controller;

import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.pojo.*;
import com.chua.starter.pay.support.result.PayOrderResponse;
import com.chua.starter.pay.support.result.PayRefundResponse;
import com.chua.starter.pay.support.result.PaySignResponse;
import com.chua.starter.pay.support.service.PayMerchantService;
import com.chua.starter.pay.support.service.PayOrderService;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 支付接口
 * @author CH
 * @since 2024/12/30
 */
@Api(tags = "支付接口")
@Tag(name = "支付接口")
@RestController
@RequestMapping("/v3/pay/order")
@Slf4j
@RequiredArgsConstructor
public class PayController {


    final PayMerchantService payMerchantService;
    final PayOrderService payOrderService;



    /**
     * 详情
     */
    @GetMapping("/detail")
    @Operation(summary = "订单详情")
    public ReturnResult<PayMerchantOrder> detail(String payMerchantOrderCode) {
        return payOrderService.detail(payMerchantOrderCode);
    }
    /**
     * 退款
     */
    @PutMapping("/cancel")
    @Operation(summary = "关闭订单")
    @Permission({"sys:pay:cancel"})
    public ReturnResult<PayRefundResponse> cancel(@Validated(UpdateGroup.class) @RequestBody PayRefundCreateRequest request, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        PayRefundRequest refundRequest = BeanUtils.copyProperties(request, PayRefundRequest.class);
        BeanUtils.copyProperties(request, refundRequest);
        return payOrderService.cancel(refundRequest);
    }
    /**
     * 退款
     */
    @PutMapping("/refund")
    @Operation(summary = "退款")
    @Permission({"sys:pay:refund"})
    public ReturnResult<PayRefundResponse> refund(@Validated(UpdateGroup.class) @RequestBody PayRefundCreateRequest request, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        PayRefundRequest refundRequest = BeanUtils.copyProperties(request, PayRefundRequest.class);
        BeanUtils.copyProperties(request, refundRequest);
        return payOrderService.refund(refundRequest);
    }
    /**
     * 创建订单
     */
    @PutMapping("/createOrder")
    @Operation(summary = "创建订单")
    public ReturnResult<PayOrderResponse> createOrder(@Validated(AddGroup.class) @RequestBody PayOrderCreateRequest request,
                                                      BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        PayOrderRequest payOrderRequest = BeanUtils.copyProperties(request, PayOrderRequest.class);
        payOrderRequest.setUserId(StringUtils.defaultString(request.getOpenId(), null));
        return payOrderService.createOrder(payOrderRequest);
    }
    /**
     * 创建签名
     */
    @PutMapping("/createSign")
    @Operation(summary = "创建签名")
    public ReturnResult<PaySignResponse> createSign(@Validated(AddGroup.class) @RequestBody PaySignCreateRequest request,
                                                    BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return payOrderService.createSign(request);
    }
}
