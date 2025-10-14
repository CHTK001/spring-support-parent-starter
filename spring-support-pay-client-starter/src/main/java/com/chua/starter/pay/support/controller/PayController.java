package com.chua.starter.pay.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.pojo.CreateOrderV2Request;
import com.chua.starter.pay.support.pojo.CreateOrderV2Response;
import com.chua.starter.pay.support.pojo.PaySignResponse;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import io.swagger.annotations.ApiOperation;
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

    private PayMerchantOrderService payMerchantOrderService;

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
}
