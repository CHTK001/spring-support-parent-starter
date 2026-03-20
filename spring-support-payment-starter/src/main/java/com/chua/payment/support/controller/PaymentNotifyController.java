package com.chua.payment.support.controller;

import com.chua.payment.support.service.PaymentNotifyService;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 第三方支付异步通知控制器
 */
@Slf4j
@Hidden
@RestController
@ApiReturnFormatIgnore
@RequestMapping("/api/notify")
@RequiredArgsConstructor
public class PaymentNotifyController {

    private final PaymentNotifyService paymentNotifyService;
    private final ObjectMapper objectMapper;

    @PostMapping("/wechat/pay/{channelId}")
    public ResponseEntity<String> handleWechatPayNotify(@PathVariable Long channelId,
                                                        @RequestHeader(value = "Wechatpay-Serial", required = false) String serialNumber,
                                                        @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
                                                        @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
                                                        @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
                                                        @RequestHeader(value = "Wechatpay-Signature-Type", required = false) String signType,
                                                        @RequestBody(required = false) String body) {
        try {
            paymentNotifyService.handleWechatPayNotify(channelId, serialNumber, timestamp, nonce, signature, signType, body);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("微信支付通知处理失败: channelId={}", channelId, e);
            return wechatFailure(e.getMessage());
        }
    }

    @PostMapping("/wechat/refund/{channelId}")
    public ResponseEntity<String> handleWechatRefundNotify(@PathVariable Long channelId,
                                                           @RequestHeader(value = "Wechatpay-Serial", required = false) String serialNumber,
                                                           @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
                                                           @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
                                                           @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
                                                           @RequestHeader(value = "Wechatpay-Signature-Type", required = false) String signType,
                                                           @RequestBody(required = false) String body) {
        try {
            paymentNotifyService.handleWechatRefundNotify(channelId, serialNumber, timestamp, nonce, signature, signType, body);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("微信退款通知处理失败: channelId={}", channelId, e);
            return wechatFailure(e.getMessage());
        }
    }

    @PostMapping(value = "/alipay/pay/{channelId}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String handleAlipayPayNotify(@PathVariable Long channelId,
                                        @RequestParam Map<String, String> params) {
        try {
            paymentNotifyService.handleAlipayPayNotify(channelId, params);
            return "success";
        } catch (Exception e) {
            log.error("支付宝支付通知处理失败: channelId={}", channelId, e);
            return "failure";
        }
    }

    private ResponseEntity<String> wechatFailure(String message) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "code", "FAIL",
                    "message", message == null ? "系统处理失败" : message));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"code\":\"FAIL\",\"message\":\"系统处理失败\"}");
        }
    }
}
