package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.dto.LogNotifyDTO;
import com.chua.payment.support.dto.WechatPayNotifyDTO;
import com.chua.payment.support.dto.WechatRefundNotifyDTO;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 第三方支付异步通知控制器，统一处理微信支付和支付宝的支付、回调通知。
 *
 * @since 2025-01-01
 * @author CH
 */
@Slf4j
@Hidden
@RestController
@ApiReturnFormatIgnore
@RequestMapping("/notify")
@RequiredArgsConstructor
public class PaymentNotifyController {

    /**
     * 商户渠道服务
     */
    private final MerchantChannelService merchantChannelService;
    /**
     * 支付通知处理服务
     */
    private final PaymentNotifyProcessService paymentNotifyProcessService;
    /**
     * 支付订单数据访问
     */
    private final PaymentOrderMapper paymentOrderMapper;
    /**
     * 退款单数据访问
     */
    private final PaymentRefundOrderMapper paymentRefundOrderMapper;
    /**
     * JSON 序列化工具
     */
    private final ObjectMapper objectMapper;

    /**
     * 处理微信支付支付通知（按 通道标识 路由，无 订单号 时由通知体决定订单）。
     *
     * @param channelId      支付通道标识
     * @param serialNumber   Wechatpay-序列号请求头
     * @param timestamp      Wechatpay-时间戳请求头
     * @param nonce          Wechatpay-Nonce 请求头
     * @param signature      Wechatpay-签名请求头
     * @param signType       Wechatpay-签名类型请求头
     * @param body           微信通知报文
     * @param request        原始 HTTP 请求
     * @return 微信支付要求的不重复响应体（通常为空）
     */
    @PostMapping("/wechat/pay/{channelId}")
    public ResponseEntity<String> handleWechatPayNotify(@PathVariable Long channelId,
                                                         @RequestHeader(value = "Wechatpay-Serial", required = false) String serialNumber,
                                                         @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
                                                         @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
                                                         @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
                                                         @RequestHeader(value = "Wechatpay-Signature-Type", required = false) String signType,
                                                         @RequestBody(required = false) String body,
                                                         HttpServletRequest request) {
        WechatPayNotifyDTO notifyDto = new WechatPayNotifyDTO(channelId, null, serialNumber, timestamp, nonce, signature, signType, body);
        paymentNotifyProcessService.processPaymentNotify(
                paymentNotifyProcessService.logNotify(new LogNotifyDTO(
                        "WECHAT_PAY", null, channelId, null, null, null, null, request, body)));
        return ResponseEntity.noContent().build();
    }

    /**
     * 处理微信支付支付通知（带 订单号，可直接路由到对应订单）。
     *
     * @param channelId      支付通道标识
     * @param orderNo        商户订单号
     * @param serialNumber   Wechatpay-序列号请求头
     * @param timestamp      Wechatpay-时间戳请求头
     * @param nonce          Wechatpay-Nonce 请求头
     * @param signature      Wechatpay-签名请求头
     * @param signType       Wechatpay-签名类型请求头
     * @param body           微信通知报文
     * @param request        原始 HTTP 请求
     * @return 微信支付要求的不重复响应体
     */
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
                    new LogNotifyDTO(
                            "WECHAT_PAY",
                            channel.merchantId(),
                            channelId,
                            channel.channelType(),
                            channel.channelSubType(),
                            orderNo,
                            null,
                            request,
                            body));
            paymentNotifyProcessService.processPaymentNotify(notifyLog);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("微信支付通知处理失败: channelId={}, orderNo={}", channelId, orderNo, e);
            return wechatFailure(e.getMessage());
        }
    }

    /**
     * 按商户维度处理微信支付支付通知（通过订单号/商户标识查找通道后委托给 通道标识 版本处理）。
     *
     * @param orderNo        商户订单号
     * @param merchantId     商户标识
     * @param serialNumber   Wechatpay-序列号请求头
     * @param timestamp      Wechatpay-时间戳请求头
     * @param nonce          Wechatpay-Nonce 请求头
     * @param signature      Wechatpay-签名请求头
     * @param signType       Wechatpay-签名类型请求头
     * @param body           微信通知报文
     * @param request        原始 HTTP 请求
     * @return 微信支付要求的不重复响应体
     */
    @PostMapping("/wechat/pay/{orderNo}/{merchantId}")
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

    /**
     * 处理微信支付退款通知（按 通道标识 路由，无 退款单号 时由通知体决定退款单）。
     *
     * @param channelId      支付通道标识
     * @param serialNumber   Wechatpay-序列号请求头
     * @param timestamp      Wechatpay-时间戳请求头
     * @param nonce          Wechatpay-Nonce 请求头
     * @param signature      Wechatpay-签名请求头
     * @param signType       Wechatpay-签名类型请求头
     * @param body           微信通知报文
     * @param request        原始 HTTP 请求
     * @return 微信支付要求的不重复响应体
     */
    @PostMapping("/wechat/refund/{channelId}")
    public ResponseEntity<String> handleWechatRefundNotify(@PathVariable Long channelId,
                                                           @RequestHeader(value = "Wechatpay-Serial", required = false) String serialNumber,
                                                           @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
                                                           @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
                                                           @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
                                                           @RequestHeader(value = "Wechatpay-Signature-Type", required = false) String signType,
                                                           @RequestBody(required = false) String body,
                                                           HttpServletRequest request) {
        WechatRefundNotifyDTO refundDto = new WechatRefundNotifyDTO(channelId, null, serialNumber, timestamp, nonce, signature, signType, body);
        paymentNotifyProcessService.processRefundNotify(
                paymentNotifyProcessService.logNotify(new LogNotifyDTO(
                        "WECHAT_REFUND", null, channelId, null, null, null, null, request, body)));
        return ResponseEntity.noContent().build();
    }

    /**
     * 处理微信支付退款通知（带 退款单号，可直接路由到对应退款单）。
     *
     * @param channelId      支付通道标识
     * @param refundNo       商户退款单号
     * @param serialNumber   Wechatpay-序列号请求头
     * @param timestamp      Wechatpay-时间戳请求头
     * @param nonce          Wechatpay-Nonce 请求头
     * @param signature      Wechatpay-签名请求头
     * @param signType       Wechatpay-签名类型请求头
     * @param body           微信通知报文
     * @param request        原始 HTTP 请求
     * @return 微信支付要求的不重复响应体
     */
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
                    new LogNotifyDTO(
                            "WECHAT_REFUND",
                            channel.merchantId(),
                            channelId,
                            channel.channelType(),
                            channel.channelSubType(),
                            null,
                            refundNo,
                            request,
                            body));
            paymentNotifyProcessService.processRefundNotify(notifyLog);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("微信退款通知处理失败: channelId={}, refundNo={}", channelId, refundNo, e);
            return wechatFailure(e.getMessage());
        }
    }

    /**
     * 按商户维度处理微信支付退款通知（通过退款单号/商户标识查找通道后委托处理）。
     *
     * @param refundNo       商户退款单号
     * @param merchantId     商户标识
     * @param serialNumber   Wechatpay-序列号请求头
     * @param timestamp      Wechatpay-时间戳请求头
     * @param nonce          Wechatpay-Nonce 请求头
     * @param signature      Wechatpay-签名请求头
     * @param signType       Wechatpay-签名类型请求头
     * @param body           微信通知报文
     * @param request        原始 HTTP 请求
     * @return 微信支付要求的不重复响应体
     */
    @PostMapping("/wechat/refund/{refundNo}/{merchantId}")
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

    /**
     * 处理微信支付分通知（用于支付分模式的支付回调）。
     *
     * @param channelId      支付通道标识
     * @param outOrderNo     商户订单号
     * @param serialNumber   Wechatpay-序列号请求头
     * @param timestamp      Wechatpay-时间戳请求头
     * @param nonce          Wechatpay-Nonce 请求头
     * @param signature      Wechatpay-签名请求头
     * @param signType       Wechatpay-签名类型请求头
     * @param body           微信通知报文
     * @param request        原始 HTTP 请求
     * @return 微信支付要求的不重复响应体
     */
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
                    new LogNotifyDTO(
                            "WECHAT_PAYSCORE",
                            channel.merchantId(),
                            channelId,
                            channel.channelType(),
                            channel.channelSubType(),
                            outOrderNo,
                            null,
                            request,
                            body));
            paymentNotifyProcessService.processPaymentNotify(notifyLog);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("微信支付分通知处理失败: channelId={}, outOrderNo={}", channelId, outOrderNo, e);
            return wechatFailure(e.getMessage());
        }
    }

    /**
     * 处理支付宝支付通知（按 通道标识 路由，无 订单号 时由通知体决定订单）。
     * 支付宝使用 form 表单提交，返回 "成功" 或 "失败" 字符串。
     *
     * @param channelId 支付通道标识
     * @param params    支付宝回调参数集合
     * @param request   原始 HTTP 请求
     * @return "success" 或 "failure"
     */
    @PostMapping(value = "/alipay/pay/{channelId}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String handleAlipayPayNotify(@PathVariable Long channelId,
                                        @RequestParam Map<String, String> params,
                                        HttpServletRequest request) {
        return handleAlipayPayNotify(channelId, null, params, request);
    }

    /**
     * 处理支付宝支付通知（带 订单号，可直接路由到对应订单）。
     *
     * @param channelId 支付通道标识
     * @param orderNo   商户订单号
     * @param params    支付宝回调参数集合
     * @param request   原始 HTTP 请求
     * @return "success" 或 "failure"
     */
    @PostMapping(value = "/alipay/pay/{channelId}/{orderNo}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String handleAlipayPayNotify(@PathVariable Long channelId,
                                        @PathVariable String orderNo,
                                        @RequestParam Map<String, String> params,
                                        HttpServletRequest request) {
        try {
            var channel = merchantChannelService.getChannel(channelId);
            var notifyLog = paymentNotifyProcessService.logNotify(new LogNotifyDTO(
                    "ALIPAY_PAY",
                    channel.merchantId(),
                    channelId,
                    channel.channelType(),
                    channel.channelSubType(),
                    orderNo,
                    null,
                    request,
                    null));
            paymentNotifyProcessService.processPaymentNotify(notifyLog);
            return "success";
        } catch (Exception e) {
            log.error("支付宝支付通知处理失败: channelId={}, orderNo={}", channelId, orderNo, e);
            return "failure";
        }
    }

    /**
     * 按商户维度处理支付宝支付通知（通过订单号/商户标识查找通道后委托处理）。
     *
     * @param orderNo    商户订单号
     * @param merchantId 商户标识
     * @param params     支付宝回调参数集合
     * @param request    原始 HTTP 请求
     * @return "success" 或 "failure"
     */
    @PostMapping(value = "/alipay/pay/{orderNo}/{merchantId}", produces = MediaType.TEXT_PLAIN_VALUE)
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

    /**
     * 根据订单号和商户标识查询支付订单，若不存在则抛出异常
     *
     * @param orderNo    订单号
     * @param merchantId 商户标识
     * @return 支付订单实体
     * @throws IllegalArgumentException 订单不存在时抛出
     */
    private PaymentOrder requireOrder(String orderNo, Long merchantId) {
        PaymentOrder order = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOrderNo, orderNo)
                .eq(PaymentOrder::getMerchantId, merchantId)
                .last("limit 1"));
        if (order == null) {
            /**
     * 订单不存在，抛出 IllegalArgumentException
             */
            throw new IllegalArgumentException("订单不存在");
        }
        return order;
    }

    /**
     * 根据退款单号和商户标识查询退款订单，若不存在则抛出异常
     *
     * @param refundNo   退款单号
     * @param merchantId 商户标识
     * @return 退款订单实体
     * @throws IllegalArgumentException 退款单不存在时抛出
     */
    private PaymentRefundOrder requireRefundOrder(String refundNo, Long merchantId) {
        PaymentRefundOrder refundOrder = paymentRefundOrderMapper.selectOne(new LambdaQueryWrapper<PaymentRefundOrder>()
                .eq(PaymentRefundOrder::getRefundNo, refundNo)
                .eq(PaymentRefundOrder::getMerchantId, merchantId)
                .last("limit 1"));
        if (refundOrder == null) {
            /**
     * 退款单不存在，抛出 IllegalArgumentException
             */
            throw new IllegalArgumentException("退款单不存在");
        }
        return refundOrder;
    }

    /**
     * 构造微信支付通知失败响应体（JSON 格式，编码为失败，提示系统处理失败）。
     *
     * @param message 失败原因提示语
     * @return HTTP 500 响应，包含错误编码与消息
     */
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
