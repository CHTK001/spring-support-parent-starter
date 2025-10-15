package com.chua.starter.pay.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.enums.PayTradeType;
import com.chua.starter.pay.support.pojo.CreateOrderV2Request;
import com.chua.starter.pay.support.pojo.CreateOrderV2Response;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预支付接口
 *
 * @author CH
 */
@RestController
@RequestMapping("/v2/pay/prepayment")
@Tag(name = "预支付接口")
@RequiredArgsConstructor
public class PayPrepaymentController {

    final PayMerchantOrderService payMerchantOrderService;
    /**
     * 创建订单
     * @param request 创建订单参数
     * @return 订单信息
     */
    @PutMapping("createOrder")
    @Operation(summary = "创建订单")
    public ReturnResult<CreateOrderV2Response> createOrder(CreateOrderV2Request request) {
        request.setPayTradeType(PayTradeType.PAY_PREPAYMENT);
        return payMerchantOrderService.createOrder(request);
    }

}
