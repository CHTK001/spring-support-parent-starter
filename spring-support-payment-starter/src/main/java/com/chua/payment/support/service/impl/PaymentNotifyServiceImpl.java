package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.channel.support.AbstractMerchantPaymentChannel;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.enums.ChannelStatus;
import com.chua.payment.support.enums.OrderState;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.mapper.PaymentOrderMapper;
import com.chua.payment.support.provider.PaymentProviderGatewayRegistry;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.payment.support.service.PaymentNotifyService;
import com.chua.payment.support.service.PaymentOrderService;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayNotifyRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayNotifyPayload;
import com.chua.starter.aliyun.support.properties.AliyunAlipayProperties;
import com.chua.starter.tencent.support.payment.dto.TencentWechatNotifyRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayNotifyPayload;
import com.chua.starter.tencent.support.payment.dto.TencentWechatRefundNotifyPayload;
import com.chua.starter.tencent.support.properties.TencentWechatPayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 第三方支付异步通知处理
 */
@Slf4j
@Service
public class PaymentNotifyServiceImpl extends AbstractMerchantPaymentChannel implements PaymentNotifyService {

    private final MerchantChannelMapper merchantChannelMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentOrderService paymentOrderService;
    private final PaymentProviderGatewayRegistry providerGatewayRegistry;

    public PaymentNotifyServiceImpl(MerchantChannelService merchantChannelService,
                                    ObjectMapper objectMapper,
                                    MerchantChannelMapper merchantChannelMapper,
                                    PaymentOrderMapper paymentOrderMapper,
                                    PaymentOrderService paymentOrderService,
                                    PaymentProviderGatewayRegistry providerGatewayRegistry) {
        super(merchantChannelService, objectMapper);
        this.merchantChannelMapper = merchantChannelMapper;
        this.paymentOrderMapper = paymentOrderMapper;
        this.paymentOrderService = paymentOrderService;
        this.providerGatewayRegistry = providerGatewayRegistry;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleWechatPayNotify(Long channelId,
                                      String serialNumber,
                                      String timestamp,
                                      String nonce,
                                      String signature,
                                      String signType,
                                      String body) {
        MerchantChannel channel = requireEnabledChannel(channelId, "WECHAT");
        TencentWechatPayNotifyPayload payload = providerGatewayRegistry.tencentWechatPayGateway(providerSpi(channel, null))
                .parsePayNotify(buildWechatProperties(channel), buildWechatNotifyRequest(serialNumber, timestamp, nonce, signature, signType, body));

        PaymentOrder order = requireOrder(payload.getOrderNo());
        validateOrderChannel(order, channel);
        validateWechatIdentity(channel, payload.getAppId(), payload.getMerchantId());
        validateAmount(order.getOrderAmount(), fenToYuan(payload.getTotalAmountFen()), "微信支付通知金额不匹配");

        if (!StringUtils.hasText(payload.getTradeState())) {
            throw new PaymentException("微信支付通知缺少交易状态");
        }

        switch (payload.getTradeState()) {
            case "SUCCESS", "REFUND" -> paymentOrderService.paySuccess(
                    order.getId(),
                    resolveWechatPaidAmount(payload),
                    payload.getTransactionId(),
                    "wechat-notify");
            case "CLOSED", "REVOKED" -> {
                if (canCancel(order.getStatus())) {
                    paymentOrderService.cancelOrder(order.getId(), "wechat-notify", firstNonBlank(payload.getTradeStateDesc(), "微信订单已关闭"));
                } else {
                    log.info("微信支付关闭通知已忽略: orderNo={}, status={}", order.getOrderNo(), order.getStatus());
                }
            }
            case "PAYERROR" -> paymentOrderService.payFail(order.getId(), firstNonBlank(payload.getTradeStateDesc(), "微信支付失败"), "wechat-notify");
            case "NOTPAY", "USERPAYING", "ACCEPT" -> log.info("微信支付通知保持处理中: orderNo={}, tradeState={}", order.getOrderNo(), payload.getTradeState());
            default -> log.info("微信支付通知未触发状态更新: orderNo={}, tradeState={}", order.getOrderNo(), payload.getTradeState());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleWechatRefundNotify(Long channelId,
                                         String serialNumber,
                                         String timestamp,
                                         String nonce,
                                         String signature,
                                         String signType,
                                         String body) {
        MerchantChannel channel = requireEnabledChannel(channelId, "WECHAT");
        TencentWechatRefundNotifyPayload payload = providerGatewayRegistry.tencentWechatPayGateway(providerSpi(channel, null))
                .parseRefundNotify(buildWechatProperties(channel), buildWechatNotifyRequest(serialNumber, timestamp, nonce, signature, signType, body));

        PaymentOrder order = requireOrder(payload.getOrderNo());
        validateOrderChannel(order, channel);
        validateAmount(order.getOrderAmount(), fenToYuan(payload.getTotalAmountFen()), "微信退款通知原订单金额不匹配");

        BigDecimal refundAmount = fenToYuan(payload.getRefundAmountFen());
        if (!StringUtils.hasText(payload.getRefundStatus())) {
            throw new PaymentException("微信退款通知缺少退款状态");
        }

        switch (payload.getRefundStatus()) {
            case "SUCCESS" -> paymentOrderService.refundSuccess(order.getId(), refundAmount, "wechat-refund-notify");
            case "CLOSED", "ABNORMAL" -> paymentOrderService.refundFail(order.getId(), "微信退款失败:" + payload.getRefundStatus(), "wechat-refund-notify");
            case "PROCESSING" -> log.info("微信退款通知保持处理中: orderNo={}, outRefundNo={}", order.getOrderNo(), payload.getRefundNo());
            default -> log.info("微信退款通知未触发状态更新: orderNo={}, refundStatus={}", order.getOrderNo(), payload.getRefundStatus());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleAlipayPayNotify(Long channelId, Map<String, String> params) {
        MerchantChannel channel = requireEnabledChannel(channelId, "ALIPAY");
        AliyunAlipayPayNotifyPayload payload = providerGatewayRegistry.aliyunAlipayGateway(providerSpi(channel, null))
                .verifyAndParsePayNotify(buildAlipayProperties(channel), buildAlipayNotifyRequest(params));

        PaymentOrder order = requireOrder(payload.getOrderNo());
        validateOrderChannel(order, channel);
        validateAlipayIdentity(channel, payload.getAppId(), payload.getSellerId());
        validateAmount(order.getOrderAmount(), payload.getTotalAmount(), "支付宝支付通知金额不匹配");

        String tradeStatus = payload.getTradeStatus();
        if (!StringUtils.hasText(tradeStatus)) {
            throw new PaymentException("支付宝支付通知缺少 trade_status");
        }

        switch (tradeStatus) {
            case "TRADE_SUCCESS", "TRADE_FINISHED" -> paymentOrderService.paySuccess(order.getId(), payload.getTotalAmount(), payload.getTradeNo(), "alipay-notify");
            case "TRADE_CLOSED" -> {
                if (canCancel(order.getStatus())) {
                    paymentOrderService.cancelOrder(order.getId(), "alipay-notify", "支付宝交易已关闭");
                } else {
                    log.info("支付宝关闭通知已忽略: orderNo={}, status={}", order.getOrderNo(), order.getStatus());
                }
            }
            case "WAIT_BUYER_PAY" -> log.info("支付宝支付通知保持处理中: orderNo={}", order.getOrderNo());
            default -> log.info("支付宝通知未触发状态更新: orderNo={}, tradeStatus={}", order.getOrderNo(), tradeStatus);
        }
    }

    private MerchantChannel requireEnabledChannel(Long channelId, String channelType) {
        MerchantChannel channel = merchantChannelMapper.selectById(channelId);
        if (channel == null) {
            throw new PaymentException("支付方式不存在");
        }
        if (!channelType.equalsIgnoreCase(channel.getChannelType())) {
            throw new PaymentException("支付方式类型不匹配");
        }
        if (!Integer.valueOf(ChannelStatus.ENABLED.getCode()).equals(channel.getStatus())) {
            throw new PaymentException("支付方式未启用");
        }
        return channel;
    }

    private PaymentOrder requireOrder(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new PaymentException("通知中缺少订单号");
        }
        PaymentOrder order = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOrderNo, orderNo)
                .and(wrapper -> wrapper.isNull(PaymentOrder::getDeleted).or().eq(PaymentOrder::getDeleted, 0))
                .last("limit 1"));
        if (order == null) {
            throw new PaymentException("订单不存在: " + orderNo);
        }
        return order;
    }

    private void validateOrderChannel(PaymentOrder order, MerchantChannel channel) {
        if (order.getChannelId() == null || !order.getChannelId().equals(channel.getId())) {
            throw new PaymentException("订单和回调支付方式不匹配");
        }
    }

    private void validateWechatIdentity(MerchantChannel channel, String appId, String merchantNo) {
        if (StringUtils.hasText(channel.getAppId()) && StringUtils.hasText(appId) && !channel.getAppId().equals(appId)) {
            throw new PaymentException("微信支付通知 AppId 不匹配");
        }
        if (StringUtils.hasText(channel.getMerchantNo()) && StringUtils.hasText(merchantNo) && !channel.getMerchantNo().equals(merchantNo)) {
            throw new PaymentException("微信支付通知商户号不匹配");
        }
    }

    private void validateAlipayIdentity(MerchantChannel channel, String appId, String sellerId) {
        if (StringUtils.hasText(channel.getAppId()) && StringUtils.hasText(appId) && !channel.getAppId().equals(appId)) {
            throw new PaymentException("支付宝通知 app_id 不匹配");
        }
        if (StringUtils.hasText(channel.getMerchantNo()) && StringUtils.hasText(sellerId) && !channel.getMerchantNo().equals(sellerId)) {
            throw new PaymentException("支付宝通知 seller_id 不匹配");
        }
    }

    private void validateAmount(BigDecimal expected, BigDecimal actual, String message) {
        if (expected == null || actual == null) {
            return;
        }
        if (expected.compareTo(actual) != 0) {
            throw new PaymentException(message);
        }
    }

    private BigDecimal resolveWechatPaidAmount(TencentWechatPayNotifyPayload payload) {
        if (payload == null) {
            return null;
        }
        Long paidAmountFen = payload.getPayerTotalAmountFen() != null ? payload.getPayerTotalAmountFen() : payload.getTotalAmountFen();
        return paidAmountFen == null ? null : fenToYuan(paidAmountFen);
    }

    private boolean canCancel(String status) {
        return OrderState.PENDING.name().equals(status) || OrderState.PAYING.name().equals(status);
    }

    private TencentWechatNotifyRequest buildWechatNotifyRequest(String serialNumber,
                                                                String timestamp,
                                                                String nonce,
                                                                String signature,
                                                                String signType,
                                                                String body) {
        TencentWechatNotifyRequest request = new TencentWechatNotifyRequest();
        request.setSerialNumber(serialNumber);
        request.setTimestamp(timestamp);
        request.setNonce(nonce);
        request.setSignature(signature);
        request.setSignType(signType);
        request.setBody(body);
        return request;
    }

    private AliyunAlipayNotifyRequest buildAlipayNotifyRequest(Map<String, String> params) {
        AliyunAlipayNotifyRequest request = new AliyunAlipayNotifyRequest();
        request.setParams(params);
        return request;
    }

    private TencentWechatPayProperties buildWechatProperties(MerchantChannel channel) {
        TencentWechatPayProperties properties = new TencentWechatPayProperties();
        properties.setAppId(requiredText(channel.getAppId(), "微信 AppId 不能为空"));
        properties.setMerchantId(requiredText(channel.getMerchantNo(), "微信商户号不能为空"));
        properties.setPrivateKey(requiredText(decryptValue(channel.getPrivateKey()), "微信商户私钥不能为空"));
        properties.setApiV3Key(requiredText(decryptValue(channel.getApiKey()), "微信 APIv3 Key 不能为空"));
        properties.setMerchantSerialNumber(requiredText(extText(channel, "merchantSerialNumber"), "微信商户证书序列号不能为空"));
        properties.setNotifyUrl(channel.getNotifyUrl());
        return properties;
    }

    private AliyunAlipayProperties buildAlipayProperties(MerchantChannel channel) {
        AliyunAlipayProperties properties = new AliyunAlipayProperties();
        properties.setAppId(requiredText(channel.getAppId(), "支付宝应用ID不能为空"));
        properties.setPrivateKey(requiredText(decryptValue(channel.getPrivateKey()), "支付宝私钥不能为空"));
        properties.setAlipayPublicKey(requiredText(channel.getPublicKey(), "支付宝公钥不能为空"));
        properties.setSandbox(channel.getSandboxMode() != null && channel.getSandboxMode() == 1);
        properties.setServerUrl(extText(channel, "serverUrl"));
        properties.setCharset(firstNonBlank(extText(channel, "charset"), "UTF-8"));
        properties.setFormat(firstNonBlank(extText(channel, "format"), "json"));
        properties.setSignType(firstNonBlank(extText(channel, "signType"), "RSA2"));
        return properties;
    }
}
