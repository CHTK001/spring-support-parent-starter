package com.chua.payment.support.controller;

import com.chua.payment.support.service.MerchantChannelService;
import com.chua.payment.support.service.PaymentNotifyProcessService;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
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

    private final MerchantChannelService merchantChannelService;
    private final PaymentNotifyProcessService paymentNotifyProcessService;
    private final ObjectMapper objectMapper;

    @PostMapping("/wechat/pay/{channelId}")
    public ResponseEntity<String> handleWechatPayNotify(@PathVariable Long channelId,
                                                        @RequestHeader(value = "Wechatpay-Serial", required = false) String serialNumber,
                                                        @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
                                                        @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
                                                        @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
                                                        @RequestHeader(value = "Wechatpay-Signature-Type", required = false) String signType,
                                                        @RequestBody(required = false) String body,
                                                        HttpServletRequest request) {
        return handleWechatPayNotify(channelId, null, serialNumber, timestamp, nonce, signature, signType, body, request);
    }

    @PostMapping("/wechat/pay/{channelId}/{orderNo}")
    public ResponseEntity<String> handleWechatPayNotify(@PathVariable Long channelId,
                                                        @PathVariable String orderNo,
                                                        @RequestHeader(value = "Wechatpay-Serial", required = false) String serialNumber,
                                                        @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
                                                        @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
                                                        @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
                                                        @RequestHeader(value = "Wechatpay-Signature-Type", required = false) String signType,
                                                        @RequestBody(required = false) String body,
                                                        HttpServletRequest request) {
        try {
            var channel = merchantChannelService.getChannel(channelId);
            var notifyLog = paymentNotifyProcessService.logNotify(
                    "WECHAT_PAY",
                    channel.getMerchantId(),
                    channelId,
                    channel.getChannelType(),
                    channel.getChannelSubType(),
                    orderNo,
                    null,
                    request,
                    body);
            paymentNotifyProcessService.processPaymentNotify(notifyLog);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("微信支付通知处理失败: channelId={}, orderNo={}", channelId, orderNo, e);
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
                                                           @RequestBody(required = false) String body,
                                                           HttpServletRequest request) {
        return handleWechatRefundNotify(channelId, null, serialNumber, timestamp, nonce, signature, signType, body, request);
    }

    @PostMapping("/wechat/refund/{channelId}/{refundNo}")
    public ResponseEntity<String> handleWechatRefundNotify(@PathVariable Long channelId,
                                                           @PathVariable String refundNo,
                                                           @RequestHeader(value = "Wechatpay-Serial", required = false) String serialNumber,
                                                           @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
                                                           @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
                                                           @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
                                                           @RequestHeader(value = "Wechatpay-Signature-Type", required = false) String signType,
                                                           @RequestBody(required = false) String body,
                                                           HttpServletRequest request) {
        try {
            var channel = merchantChannelService.getChannel(channelId);
            var notifyLog = paymentNotifyProcessService.logNotify(
                    "WECHAT_REFUND",
                    channel.getMerchantId(),
                    channelId,
                    channel.getChannelType(),
                    channel.getChannelSubType(),
                    null,
                    refundNo,
                    request,
                    body);
            paymentNotifyProcessService.processRefundNotify(notifyLog);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("微信退款通知处理失败: channelId={}, refundNo={}", channelId, refundNo, e);
            return wechatFailure(e.getMessage());
        }
    }

    @PostMapping("/wechat/payscore/{channelId}/{outOrderNo}")
    public ResponseEntity<String> handleWechatPayScoreNotify(@PathVariable Long channelId,
                                                             @PathVariable String outOrderNo,
                                                             @RequestHeader(value = "Wechatpay-Serial", required = false) String serialNumber,
                                                             @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
                                                             @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
                                                             @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
                                                             @RequestHeader(value = "Wechatpay-Signature-Type", required = false) String signType,
                                                             @RequestBody(required = false) String body,
                                                             HttpServletRequest request) {
        try {
            var channel = merchantChannelService.getChannel(channelId);
            var notifyLog = paymentNotifyProcessService.logNotify(
                    "WECHAT_PAYSCORE",
                    channel.getMerchantId(),
                    channelId,
                    channel.getChannelType(),
                    channel.getChannelSubType(),
                    outOrderNo,
                    null,
                    request,
                    body);
            paymentNotifyProcessService.processPaymentNotify(notifyLog);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("微信支付分通知处理失败: channelId={}, outOrderNo={}", channelId, outOrderNo, e);
            return wechatFailure(e.getMessage());
        }
    }

    @PostMapping(value = "/alipay/pay/{channelId}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String handleAlipayPayNotify(@PathVariable Long channelId,
                                        @RequestParam Map<String, String> params,
                                        HttpServletRequest request) {
        return handleAlipayPayNotify(channelId, null, params, request);
    }

    @PostMapping(value = "/alipay/pay/{channelId}/{orderNo}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String handleAlipayPayNotify(@PathVariable Long channelId,
                                        @PathVariable String orderNo,
                                        @RequestParam Map<String, String> params,
                                        HttpServletRequest request) {
        try {
            var channel = merchantChannelService.getChannel(channelId);
            var notifyLog = paymentNotifyProcessService.logNotify(
                    "ALIPAY_PAY",
                    channel.getMerchantId(),
                    channelId,
                    channel.getChannelType(),
                    channel.getChannelSubType(),
                    orderNo,
                    null,
                    request,
                    null);
            paymentNotifyProcessService.processPaymentNotify(notifyLog);
            return "success";
        } catch (Exception e) {
            log.error("支付宝支付通知处理失败: channelId={}, orderNo={}", channelId, orderNo, e);
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
