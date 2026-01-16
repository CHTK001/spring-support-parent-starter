package com.chua.starter.pay.support.callback;

import com.alibaba.fastjson.JSONObject;
import com.chua.common.support.annotations.Ignore;
import com.chua.starter.oauth.client.support.annotation.TokenForIgnore;
import com.chua.starter.pay.support.callback.parser.CallbackNotificationParser;
import com.chua.starter.pay.support.callback.parser.WebchatCallbackPaymentPointsNotificationParser;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.pojo.PayMerchantConfigWechatWrapper;
import com.chua.starter.pay.support.service.PayMerchantConfigWechatService;
import com.chua.starter.pay.support.service.PayMerchantFailureRecordService;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 转账回调
 *
 * @author CH
 * @since 2024/12/30
 */
@Api(tags = "信用分回调")
@Tag(name = "信用分回调")
@RestController
@RequestMapping("/v2/pay/callback/wechat/payment")
@Slf4j
@Ignore
@TokenForIgnore
@RequiredArgsConstructor
public class WechatPayPaymentPointsCallbackController {



    final PayMerchantOrderService payMerchantOrderService;
    final PayMerchantConfigWechatService payMerchantConfigWechatService;
    final PayMerchantFailureRecordService payMerchantFailureRecordService;
    final ApplicationContext applicationContext;

    /**
     * 微信支付订单结果通知
     *
     * @param requestBody
     * @param payMerchantCode
     * @param wechatSignature
     * @param wechatpayNonce
     * @param wechatPaySerial
     * @param wechatTimestamp
     * @param wechatpaySignatureType
     * @return
     */
    @PostMapping(value = "/{orderCode}", produces = MediaType.APPLICATION_XML_VALUE)
    @ApiOperation("订单结果通知")
    public ResponseEntity<?> notifyOrder(
            @RequestBody String requestBody,
            @PathVariable("orderCode") String payMerchantCode,
            @RequestHeader("Wechatpay-Signature") String wechatSignature,
            @RequestHeader("Wechatpay-Nonce") String wechatpayNonce,
            @RequestHeader("Wechatpay-Serial") String wechatPaySerial,
            @RequestHeader("Wechatpay-Timestamp") String wechatTimestamp,
            @RequestHeader("Wechatpay-Signature-Type") String wechatpaySignatureType
    ) {

        log.info("微信支付分回调");
        log.info("当前订单{}", payMerchantCode);
        PayMerchantOrder merchantOrder = payMerchantOrderService.getByCode(payMerchantCode);
        PayMerchantConfigWechatWrapper byCodeForPayMerchantConfigWechat = payMerchantConfigWechatService.getByCodeForPayMerchantConfigWechat(merchantOrder.getPayMerchantId(), merchantOrder.getPayMerchantTradeType().getName());
        CallbackNotificationParser parser = new WebchatCallbackPaymentPointsNotificationParser(
                merchantOrder,
                byCodeForPayMerchantConfigWechat,
                requestBody,
                wechatSignature,
                wechatpayNonce,
                wechatPaySerial,
                wechatTimestamp,
                wechatpaySignatureType,
                payMerchantOrderService,
                payMerchantFailureRecordService,
                applicationContext);
        WechatOrderCallbackResponse response = null;
        try {
            response = parser.parse();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new JSONObject().fluentPut("code", "FAIL").fluentPut("message", "失败").toJSONString());
        }
        if ("SUCCESS".equals(response.getReturnCode())) {
            return ResponseEntity.status(HttpStatus.OK).build();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new JSONObject().fluentPut("code", "FAIL").fluentPut("message", "失败").toJSONString());
    }
}
