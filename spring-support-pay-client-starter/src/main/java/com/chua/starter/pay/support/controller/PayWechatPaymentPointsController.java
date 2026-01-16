package com.chua.starter.pay.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.starter.pay.support.enums.PayTradeType;
import com.chua.starter.pay.support.pojo.CreateOrderV2Request;
import com.chua.starter.pay.support.pojo.CreateOrderV2Response;
import com.chua.starter.pay.support.pojo.CreatePayPrepaymentOrderV2Request;
import com.chua.starter.pay.support.pojo.CreatePaymentPointsOrderV2Request;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信信用分
 * @author CH
 * @since 2025/10/15 11:00
 */
@RestController
@RequestMapping("/v2/pay/payment-points")
@Tag(name = "通用支付接口")
@RequiredArgsConstructor
public class PayWechatPaymentPointsController {

    final PayMerchantOrderService payMerchantOrderService;
    /**
     * 创建订单
     * @param request 创建订单参数
     * @return 订单信息
     */
    @PutMapping("createOrder")
    @Operation(summary = "创建订单")
    public ReturnResult<CreateOrderV2Response> createOrder(@Validated(AddGroup.class)  @RequestBody CreatePaymentPointsOrderV2Request request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return payMerchantOrderService.createOrder(request);
    }

}
