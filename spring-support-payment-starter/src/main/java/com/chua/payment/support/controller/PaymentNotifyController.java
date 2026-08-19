package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.entity.PaymentRefundOrder;
import com.chua.payment.support.mapper.PaymentOrderMapper;
import com.chua.payment.support.mapper.PaymentRefundOrderMapper;
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
 * @author CH
 */
@Slf4j
@Hidden
@RestController
@ApiReturnFormatIgnore
@RequestMapping("/notify")
@RequiredArgsConstructor
public class PaymentNotifyController {

    /** Merchant通道服务 */
    private final MerchantChannelService merchantChannelService;
    /** Paymentnotifyprocess服务 */
    private final PaymentNotifyProcessService paymentNotifyProcessService;
    /** Payment排序mapper */
    private final PaymentOrderMapper paymentOrderMapper;
    /** Paymentrefund排序mapper */
    private final PaymentRefundOrderMapper paymentRefundOrderMapper;
    /** Objectmapper */
    private final ObjectMapper objectMapper;

    @PostMapping("/wechat/pay/{channelId}")
    /**
     * 处理WechatPay通知
     * @param channelId channelId
     * @param serialNumber serialNumber
     * @param timestamp timestamp
     * @param nonce nonce
     * @param signature signature
     * @param signType signType
     * @param body body
     * @param request request
     */
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
    /**
     * 处理WechatPay通知
     * @param channelId channelId
     * @param orderNo orderNo
     * @param serialNumber serialNumber
     * @param timestamp timestamp
     * @param nonce nonce
     * @param signature signature
     * @param signType signType
     * @param body body
     * @param request request
     */
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

    @PostMapping("/wechat/pay/{orderNo}/{merchantId}")
    /**
     * 处理WechatPay通知ByMerchant
     * @param orderNo orderNo
     * @param merchantId merchantId
     * @param serialNumber serialNumber
     * @param timestamp timestamp
     * @param nonce nonce
     * @param signature signature
     * @param signType signType
     * @param body body
     * @param request request
     */
    public ResponseEntity<String> handleWechatPayNotifyByMerchant(@PathVariable String orderNo,
                                                                  @PathVariable Long merchantId,
                                                                  @RequestHeader(value = "Wechatpay-Serial", required = false) String serialNumber,
                                                                  @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
                                                                  @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
                                                                  @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
                                                                  @RequestHeader(value = "Wechatpay-Signature-Type", required = false) String signType,
                                                                  @RequestBody(required = false) String body,
                                                                  HttpServletRequest request) {
        try {
            PaymentOrder order = requireOrder(orderNo, merchantId);
            return handleWechatPayNotify(order.getChannelId(), orderNo, serialNumber, timestamp, nonce, signature, signType, body, request);
        } catch (Exception e) {
            log.error("微信支付通知处理失败: merchantId={}, orderNo={}", merchantId, orderNo, e);
            return wechatFailure(e.getMessage());
        }
    }

    @PostMapping("/wechat/refund/{channelId}")
    /**
     * 处理WechatRefund通知
     * @param channelId channelId
     * @param serialNumber serialNumber
     * @param timestamp timestamp
     * @param nonce nonce
     * @param signature signature
     * @param signType signType
     * @param body body
     * @param request request
     */
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
    /**
     * 处理WechatRefund通知
     * @param channelId channelId
     * @param refundNo refundNo
     * @param serialNumber serialNumber
     * @param timestamp timestamp
     * @param nonce nonce
     * @param signature signature
     * @param signType signType
     * @param body body
     * @param request request
     */
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

    @PostMapping("/wechat/refund/{refundNo}/{merchantId}")
    /**
     * 处理WechatRefund通知ByMerchant
     * @param refundNo refundNo
     * @param merchantId merchantId
     * @param serialNumber serialNumber
     * @param timestamp timestamp
     * @param nonce nonce
     * @param signature signature
     * @param signType signType
     * @param body body
     * @param request request
     */
    public ResponseEntity<String> handleWechatRefundNotifyByMerchant(@PathVariable String refundNo,
                                                                     @PathVariable Long merchantId,
                                                                     @RequestHeader(value = "Wechatpay-Serial", required = false) String serialNumber,
                                                                     @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
                                                                     @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
                                                                     @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
                                                                     @RequestHeader(value = "Wechatpay-Signature-Type", required = false) String signType,
                                                                     @RequestBody(required = false) String body,
                                                                     HttpServletRequest request) {
        try {
            PaymentRefundOrder refundOrder = requireRefundOrder(refundNo, merchantId);
            return handleWechatRefundNotify(refundOrder.getChannelId(), refundNo, serialNumber, timestamp, nonce, signature, signType, body, request);
        } catch (Exception e) {
            log.error("微信退款通知处理失败: merchantId={}, refundNo={}", merchantId, refundNo, e);
            return wechatFailure(e.getMessage());
        }
    }

    @PostMapping("/wechat/payscore/{channelId}/{outOrderNo}")
    /**
     * 处理WechatPayScore通知
     * @param channelId channelId
     * @param outOrderNo outOrderNo
     * @param serialNumber serialNumber
     * @param timestamp timestamp
     * @param nonce nonce
     * @param signature signature
     * @param signType signType
     * @param body body
     * @param request request
     */
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
    /**
     * 处理AlipayPay通知
     * @param channelId channelId
     * @param params params
     * @param request request
     */
    public String handleAlipayPayNotify(@PathVariable Long channelId,
                                        @RequestParam Map<String, String> params,
                                        HttpServletRequest request) {
        return handleAlipayPayNotify(channelId, null, params, request);
    }

    @PostMapping(value = "/alipay/pay/{channelId}/{orderNo}", produces = MediaType.TEXT_PLAIN_VALUE)
    /**
     * 处理AlipayPay通知
     * @param channelId channelId
     * @param orderNo orderNo
     * @param params params
     * @param request request
     */
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

    @PostMapping(value = "/alipay/pay/{orderNo}/{merchantId}", produces = MediaType.TEXT_PLAIN_VALUE)
    /**
     * 处理AlipayPay通知ByMerchant
     * @param orderNo orderNo
     * @param merchantId merchantId
     * @param params params
     * @param request request
     */
    public String handleAlipayPayNotifyByMerchant(@PathVariable String orderNo,
                                                  @PathVariable Long merchantId,
                                                  @RequestParam Map<String, String> params,
                                                  HttpServletRequest request) {
        try {
            PaymentOrder order = requireOrder(orderNo, merchantId);
            return handleAlipayPayNotify(order.getChannelId(), orderNo, params, request);
        } catch (Exception e) {
            log.error("支付宝支付通知处理失败: merchantId={}, orderNo={}", merchantId, orderNo, e);
            return "failure";
        }
    }

    /** RequireOrder */
    private PaymentOrder requireOrder(String orderNo, Long merchantId) {
        PaymentOrder order = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOrderNo, orderNo)
                .eq(PaymentOrder::getMerchantId, merchantId)
                .last("limit 1"));
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        return order;
    }

    /** RequireRefundOrder */
    private PaymentRefundOrder requireRefundOrder(String refundNo, Long merchantId) {
        PaymentRefundOrder refundOrder = paymentRefundOrderMapper.selectOne(new LambdaQueryWrapper<PaymentRefundOrder>()
                .eq(PaymentRefundOrder::getRefundNo, refundNo)
                .eq(PaymentRefundOrder::getMerchantId, merchantId)
                .last("limit 1"));
        if (refundOrder == null) {
            throw new IllegalArgumentException("退款单不存在");
        }
        return refundOrder;
    }

    /** WechatFailure */
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

