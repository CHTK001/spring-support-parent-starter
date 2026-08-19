package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.channel.support.AbstractMerchantPaymentChannel;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.entity.PaymentRefundOrder;
import com.chua.payment.support.enums.ChannelStatus;
import com.chua.payment.support.enums.OrderState;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.mapper.PaymentOrderMapper;
import com.chua.payment.support.provider.PaymentProviderGatewayRegistry;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.payment.support.service.PaymentNotifyService;
import com.chua.payment.support.service.PaymentOrderService;
import com.chua.payment.support.service.PaymentRefundOrderService;
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
 * @author CH
 */
@Slf4j
@Service
public class PaymentNotifyServiceImpl extends AbstractMerchantPaymentChannel implements PaymentNotifyService {

    /** Merchant通道mapper */
    private final MerchantChannelMapper merchantChannelMapper;
    /** Payment排序mapper */
    private final PaymentOrderMapper paymentOrderMapper;
    /** Payment排序服务 */
    private final PaymentOrderService paymentOrderService;
    /** Paymentrefund排序服务 */
    private final PaymentRefundOrderService paymentRefundOrderService;
    /** 提供者gateway注册表 */
    private final PaymentProviderGatewayRegistry providerGatewayRegistry;

    /**
     * 创建 PaymentNotifyServiceImpl 实例
     * @param merchantChannelService merchantChannelService
     * @param objectMapper objectMapper
     * @param merchantChannelMapper merchantChannelMapper
     * @param paymentOrderMapper paymentOrderMapper
     * @param paymentOrderService paymentOrderService
     * @param paymentRefundOrderService paymentRefundOrderService
     * @param providerGatewayRegistry providerGatewayRegistry
     */
    public PaymentNotifyServiceImpl(MerchantChannelService merchantChannelService,
                                    ObjectMapper objectMapper,
                                    MerchantChannelMapper merchantChannelMapper,
                                    PaymentOrderMapper paymentOrderMapper,
                                    PaymentOrderService paymentOrderService,
                                    PaymentRefundOrderService paymentRefundOrderService,
                                    PaymentProviderGatewayRegistry providerGatewayRegistry) {
        super(merchantChannelService, objectMapper);
        this.merchantChannelMapper = merchantChannelMapper;
        this.paymentOrderMapper = paymentOrderMapper;
        this.paymentOrderService = paymentOrderService;
        this.paymentRefundOrderService = paymentRefundOrderService;
        this.providerGatewayRegistry = providerGatewayRegistry;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
     */
    public void handleWechatPayNotify(Long channelId,
                                      String orderNo,
                                      String serialNumber,
                                      String timestamp,
                                      String nonce,
                                      String signature,
                                      String signType,
                                      String body) {
        MerchantChannel channel = requireEnabledChannel(channelId, "WECHAT");
        TencentWechatPayNotifyPayload payload = providerGatewayRegistry.tencentWechatPayGateway(providerSpi(channel, null))
                .parsePayNotify(buildWechatProperties(channel), buildWechatNotifyRequest(serialNumber, timestamp, nonce, signature, signType, body));

        validateNotifyOrderNo(orderNo, payload.getOrderNo());
        PaymentOrder order = requireOrder(firstNonBlank(orderNo, payload.getOrderNo()));
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
     */
    public void handleWechatRefundNotify(Long channelId,
                                         String refundNo,
                                         String serialNumber,
                                         String timestamp,
                                         String nonce,
                                         String signature,
                                         String signType,
                                         String body) {
        MerchantChannel channel = requireEnabledChannel(channelId, "WECHAT");
        TencentWechatRefundNotifyPayload payload = providerGatewayRegistry.tencentWechatPayGateway(providerSpi(channel, null))
                .parseRefundNotify(buildWechatProperties(channel), buildWechatNotifyRequest(serialNumber, timestamp, nonce, signature, signType, body));

        validateNotifyRefundNo(refundNo, payload.getRefundNo());
        PaymentRefundOrder refundOrder = resolveRefundOrder(firstNonBlank(refundNo, payload.getRefundNo()), payload.getRefundId(), payload.getOrderNo());
        PaymentOrder order = requireOrder(refundOrder.getOrderNo());
        validateRefundChannel(refundOrder, channel);
        validateOrderChannel(order, channel);
        validateRefundOrderMapping(refundOrder, payload.getOrderNo());
        validateAmount(order.getOrderAmount(), fenToYuan(payload.getTotalAmountFen()), "微信退款通知原订单金额不匹配");

        BigDecimal refundAmount = fenToYuan(payload.getRefundAmountFen());
        if (!StringUtils.hasText(payload.getRefundStatus())) {
            throw new PaymentException("微信退款通知缺少退款状态");
        }

        switch (payload.getRefundStatus()) {
            case "SUCCESS" -> paymentOrderService.refundSuccess(
                    refundOrder.getRefundNo(),
                    refundAmount,
                    firstNonBlank(payload.getRefundId(), refundOrder.getThirdPartyRefundNo()),
                    "wechat-refund-notify",
                    toJson(payload),
                    "微信退款回调成功");
            case "CLOSED", "ABNORMAL" -> paymentOrderService.refundFail(
                    refundOrder.getRefundNo(),
                    toJson(payload),
                    "wechat-refund-notify",
                    "微信退款失败:" + payload.getRefundStatus());
            case "PROCESSING" -> log.info("微信退款通知保持处理中: orderNo={}, refundNo={}, outRefundNo={}", order.getOrderNo(), refundOrder.getRefundNo(), payload.getRefundNo());
            default -> log.info("微信退款通知未触发状态更新: orderNo={}, refundNo={}, refundStatus={}", order.getOrderNo(), refundOrder.getRefundNo(), payload.getRefundStatus());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /** 处理AlipayPay通知 */
    public void handleAlipayPayNotify(Long channelId, String orderNo, Map<String, String> params) {
        MerchantChannel channel = requireEnabledChannel(channelId, "ALIPAY");
        AliyunAlipayPayNotifyPayload payload = providerGatewayRegistry.aliyunAlipayGateway(providerSpi(channel, null))
                .verifyAndParsePayNotify(buildAlipayProperties(channel), buildAlipayNotifyRequest(params));

        validateNotifyOrderNo(orderNo, payload.getOrderNo());
        PaymentOrder order = requireOrder(firstNonBlank(orderNo, payload.getOrderNo()));
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

    /** RequireEnabledChannel */
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

    /** RequireOrder */
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

    /** 校验OrderChannel */
    private void validateOrderChannel(PaymentOrder order, MerchantChannel channel) {
        if (order.getChannelId() == null || !order.getChannelId().equals(channel.getId())) {
            throw new PaymentException("订单和回调支付方式不匹配");
        }
    }

    /** 校验RefundChannel */
    private void validateRefundChannel(PaymentRefundOrder refundOrder, MerchantChannel channel) {
        if (refundOrder.getChannelId() == null || !refundOrder.getChannelId().equals(channel.getId())) {
            throw new PaymentException("退款单和回调支付方式不匹配");
        }
    }

    /** 校验通知OrderNo */
    private void validateNotifyOrderNo(String pathOrderNo, String payloadOrderNo) {
        if (StringUtils.hasText(pathOrderNo) && StringUtils.hasText(payloadOrderNo) && !pathOrderNo.equals(payloadOrderNo)) {
            throw new PaymentException("回调路径订单号和报文订单号不一致");
        }
    }

    /** 校验通知RefundNo */
    private void validateNotifyRefundNo(String pathRefundNo, String payloadRefundNo) {
        if (StringUtils.hasText(pathRefundNo) && StringUtils.hasText(payloadRefundNo) && !pathRefundNo.equals(payloadRefundNo)) {
            throw new PaymentException("回调路径退款单号和报文退款单号不一致");
        }
    }

    /** 校验RefundOrderMapping */
    private void validateRefundOrderMapping(PaymentRefundOrder refundOrder, String payloadOrderNo) {
        if (refundOrder == null || !StringUtils.hasText(payloadOrderNo)) {
            return;
        }
        if (!payloadOrderNo.equals(refundOrder.getOrderNo())) {
            throw new PaymentException("退款回调订单号和本地退款单不一致");
        }
    }

    /** 解析RefundOrder */
    private PaymentRefundOrder resolveRefundOrder(String refundNo, String thirdPartyRefundNo, String orderNo) {
        PaymentRefundOrder refundOrder = paymentRefundOrderService.getByRefundNo(refundNo);
        if (refundOrder == null) {
            refundOrder = paymentRefundOrderService.getByThirdPartyRefundNo(thirdPartyRefundNo);
        }
        if (refundOrder == null && StringUtils.hasText(orderNo)) {
            PaymentOrder order = requireOrder(orderNo);
            refundOrder = firstNonNull(
                    paymentRefundOrderService.getProcessingByOrderId(order.getId()),
                    paymentRefundOrderService.getLatestByOrderId(order.getId()));
        }
        if (refundOrder == null) {
            throw new PaymentException("退款单不存在: " + firstNonBlank(refundNo, thirdPartyRefundNo, orderNo));
        }
        return refundOrder;
    }

    /** 校验WechatIdentity */
    private void validateWechatIdentity(MerchantChannel channel, String appId, String merchantNo) {
        if (StringUtils.hasText(channel.getAppId()) && StringUtils.hasText(appId) && !channel.getAppId().equals(appId)) {
            throw new PaymentException("微信支付通知 AppId 不匹配");
        }
        if (StringUtils.hasText(channel.getMerchantNo()) && StringUtils.hasText(merchantNo) && !channel.getMerchantNo().equals(merchantNo)) {
            throw new PaymentException("微信支付通知商户号不匹配");
        }
    }

    /** 校验AlipayIdentity */
    private void validateAlipayIdentity(MerchantChannel channel, String appId, String sellerId) {
        if (StringUtils.hasText(channel.getAppId()) && StringUtils.hasText(appId) && !channel.getAppId().equals(appId)) {
            throw new PaymentException("支付宝通知 app_id 不匹配");
        }
        if (StringUtils.hasText(channel.getMerchantNo()) && StringUtils.hasText(sellerId) && !channel.getMerchantNo().equals(sellerId)) {
            throw new PaymentException("支付宝通知 seller_id 不匹配");
        }
    }

    /** 校验Amount */
    private void validateAmount(BigDecimal expected, BigDecimal actual, String message) {
        if (expected == null || actual == null) {
            return;
        }
        if (expected.compareTo(actual) != 0) {
            throw new PaymentException(message);
        }
    }

    /** 解析WechatPaidAmount */
    private BigDecimal resolveWechatPaidAmount(TencentWechatPayNotifyPayload payload) {
        if (payload == null) {
            return null;
        }
        Long paidAmountFen = payload.getPayerTotalAmountFen() != null ? payload.getPayerTotalAmountFen() : payload.getTotalAmountFen();
        return paidAmountFen == null ? null : fenToYuan(paidAmountFen);
    }

    /** 是否可以Cancel */
    private boolean canCancel(String status) {
        return OrderState.PENDING.name().equals(status) || OrderState.PAYING.name().equals(status);
    }

    @SafeVarargs
    /** FirstNonNull */
    private <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 构建Wechat通知Request
     * @param serialNumber serialNumber
     * @param timestamp timestamp
     * @param nonce nonce
     * @param signature signature
     * @param signType signType
     * @param body body
     */
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

    /** 构建Alipay通知Request */
    private AliyunAlipayNotifyRequest buildAlipayNotifyRequest(Map<String, String> params) {
        AliyunAlipayNotifyRequest request = new AliyunAlipayNotifyRequest();
        request.setParams(params);
        return request;
    }

    /** 构建WechatProperties */
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

    /** 构建AlipayProperties */
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

