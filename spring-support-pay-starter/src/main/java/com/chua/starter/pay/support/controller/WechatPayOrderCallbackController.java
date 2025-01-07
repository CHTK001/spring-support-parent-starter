package com.chua.starter.pay.support.controller;

import com.chua.common.support.annotations.Ignore;
import com.chua.starter.pay.support.handler.CallbackNotificationParser;
import com.chua.starter.pay.support.handler.wechat.WebchatCallbackNotificationParser;
import com.chua.starter.pay.support.mapper.PayMerchantConfigWechatMapper;
import com.chua.starter.pay.support.pojo.WechatOrderCallbackResponse;
import com.chua.starter.pay.support.service.PayOrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kong.unirest.HttpStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 支付回调
 * @author CH
 * @since 2024/12/30
 */
@Api(tags = "支付回调")
@Tag(name ="支付回调")
@RestController
@RequestMapping("/v3/pay/callback/wechat/order")
@Slf4j
@Ignore
@RequiredArgsConstructor
public class WechatPayOrderCallbackController {

    final PayOrderService payOrderService;

    final PayMerchantConfigWechatMapper payMerchantConfigWechatMapper;


    @PostMapping(value = "/notify/{payMerchantCode}/{payMerchantOrderCode}", produces = MediaType.APPLICATION_XML_VALUE)
    @ApiOperation("订单结果通知")
    @Operation(summary = "订单结果通知")
    @Ignore
    public ResponseEntity<?> notifyOrder(
            @RequestBody String requestBody,
            @PathVariable("payMerchantCode") String payMerchantCode,
            @PathVariable("payMerchantOrderCode") String payMerchantOrderCode,

            @RequestHeader("Wechatpay-Signature") String wechatSignature,
            @RequestHeader("Wechatpay-Nonce") String wechatpayNonce,
            @RequestHeader("Wechatpay-Serial") String wechatPaySerial,
            @RequestHeader("Wechatpay-Timestamp") String wechatTimestamp,
            @RequestHeader("Wechatpay-Signature-Type") String wechatpaySignatureType
    ) {
        CallbackNotificationParser parser = new WebchatCallbackNotificationParser(payMerchantConfigWechatMapper,
                requestBody,
                wechatSignature,
                wechatpayNonce,
                wechatPaySerial,
                wechatTimestamp,
                wechatpaySignatureType,
                payMerchantCode,
                payMerchantOrderCode);
        WechatOrderCallbackResponse response = payOrderService.notifyOrder(parser);
        if("SUCCESS".equals(response.getReturnCode())) {
            return ResponseEntity.status(HttpStatus.OK).build();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
