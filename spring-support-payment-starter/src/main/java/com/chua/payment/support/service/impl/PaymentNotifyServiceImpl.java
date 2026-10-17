package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.dto.WechatPayNotifyDTO;
import com.chua.payment.support.dto.WechatRefundNotifyDTO;
import com.chua.payment.support.dto.RefundSuccessDTO;
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
 * 第三方支付异步通知处理，负责微信、支付宝等渠道的支付与退款回调解析和状态更新。
 *
 * @author CH
 * @since 4.0.0.50
 */
@Slf4j
@Service
public class PaymentNotifyServiceImpl extends AbstractMerchantPaymentChannel implements PaymentNotifyService {

    /**
     * 商户渠道映射器
     */
    private final MerchantChannelMapper merchantChannelMapper;
    /**
     * 支付订单映射器
     */
    private final PaymentOrderMapper paymentOrderMapper;
    /**
     * 支付订单服务
     */
    private final PaymentOrderService paymentOrderService;
    /**
     * 支付退款单服务
     */
    private final PaymentRefundOrderService paymentRefundOrderService;
    /**
     * 支付渠道网关注册表
     */
    private final PaymentProviderGatewayRegistry providerGatewayRegistry;

    /**
     * 构造支付异步通知处理服务。
     * @param merchantChannelService 商户渠道服务
     * @param objectMapper 对象映射器
     * @param merchantChannelMapper 商户渠道映射器
     * @param paymentOrderMapper 支付订单映射器
     * @param paymentOrderService 支付订单服务
     * @param paymentRefundOrderService 支付退款单服务
     * @param providerGatewayRegistry 支付渠道网关注册表
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
     * 处理微信支付通知
     * @param channelId 渠道标识
     * @param orderNo 订单号
     * @param serialNumber 平台证书序列号
     * @param timestamp 时间戳
     * @param nonce 随机字符串
     * @param signature 签名值
     * @param signType 签名类型
     * @param body 请求主体
     * @param dto dto
     */
    public void handleWechatPayNotify(WechatPayNotifyDTO dto) {
        MerchantChannel channel = requireEnabledChannel(dto.channelId(), "WECHAT");
        TencentWechatPayNotifyPayload payload = providerGatewayRegistry.tencentWechatPayGateway(providerSpi(channel, null))
                .parsePayNotify(buildWechatProperties(channel), buildWechatNotifyRequest(dto.serialNumber(), dto.timestamp(), dto.nonce(), dto.signature(), dto.signType(), dto.body()));

        /**
         * 校验回调路径订单号与报文订单号一致
         * @param dto.orderNo() 路径中的订单号
         */
        validateNotifyOrderNo(dto.orderNo(), payload.orderNo());
        PaymentOrder order = requireOrder(firstNonBlank(dto.orderNo(), payload.orderNo()));
        /**
         * 校验订单所属渠道
         * @param order 订单
         * @param channel 渠道
         */
        validateOrderChannel(order, channel);
        /**
         * 校验微信应用与商户身份
         * @param channel 渠道
         * @param payload.getAppId() 报文中的 AppID
         */
        validateWechatIdentity(channel, payload.appId(), payload.merchantId());
        /**
         * 校验金额一致
         * @param order.getOrderAmount() 订单应付金额
         */
        validateAmount(order.getOrderAmount(), fenToYuan(payload.totalAmountFen()), "微信支付通知金额不匹配");

        if (!StringUtils.hasText(payload.tradeState())) {
            throw new PaymentException("微信支付通知缺少交易状态");
        }

        switch (payload.tradeState()) {
            case "SUCCESS", "REFUND" -> paymentOrderService.paySuccess(
                    order.getId(),
                    /**
                     * 解析微信实付金额
                     * @param payload 通知报文
                     */
                    resolveWechatPaidAmount(payload),
                    payload.transactionId(),
                    "wechat-notify");
            case "CLOSED", "REVOKED" -> {
                if (canCancel(order.getStatus())) {
                    paymentOrderService.cancelOrder(order.getId(), "wechat-notify", firstNonBlank(payload.tradeStateDesc(), "微信订单已关闭"));
                } else {
                    log.info("微信支付关闭通知已忽略 orderNo={}, status={}", order.getOrderNo(), order.getStatus());
                }
            }
            case "PAYERROR" -> paymentOrderService.payFail(order.getId(), firstNonBlank(payload.tradeStateDesc(), "微信支付失败"), "wechat-notify");
            case "NOTPAY", "USERPAYING", "ACCEPT" -> log.info("微信支付通知保持处理中 orderNo={}, tradeState={}", order.getOrderNo(), payload.tradeState());
            default -> log.info("微信支付通知未触发状态更新 orderNo={}, tradeState={}", order.getOrderNo(), payload.tradeState());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 处理微信退款通知
     * @param channelId 渠道标识
     * @param refundNo 退款单号
     * @param serialNumber 平台证书序列号
     * @param timestamp 时间戳
     * @param nonce 随机字符串
     * @param signature 签名值
     * @param signType 签名类型
     * @param body 请求主体
     * @param dto dto
     */
    public void handleWechatRefundNotify(WechatRefundNotifyDTO dto) {
        MerchantChannel channel = requireEnabledChannel(dto.channelId(), "WECHAT");
        TencentWechatRefundNotifyPayload payload = providerGatewayRegistry.tencentWechatPayGateway(providerSpi(channel, null))
                .parseRefundNotify(buildWechatProperties(channel), buildWechatNotifyRequest(dto.serialNumber(), dto.timestamp(), dto.nonce(), dto.signature(), dto.signType(), dto.body()));

        /**
         * 校验回调路径退款单号与报文退款单号一致
         * @param dto.refundNo() 路径中的退款单号
         */
        validateNotifyRefundNo(dto.refundNo(), payload.refundNo());
        PaymentRefundOrder refundOrder = resolveRefundOrder(firstNonBlank(dto.refundNo(), payload.refundNo()), payload.refundId(), payload.orderNo());
        PaymentOrder order = requireOrder(refundOrder.getOrderNo());
        /**
         * 校验退款单所属渠道
         * @param refundOrder 退款订单
         * @param channel 渠道
         */
        validateRefundChannel(refundOrder, channel);
        /**
         * 校验订单所属渠道
         * @param order 订单
         * @param channel 渠道
         */
        validateOrderChannel(order, channel);
        /**
         * 校验退款单与原订单的映射关系
         * @param refundOrder 退款订单
         * @param payload.getOrderNo() 报文中的订单号
         */
        validateRefundOrderMapping(refundOrder, payload.orderNo());
        /**
         * 校验原订单金额一致
         * @param order.getOrderAmount() 订单应付金额
         */
        validateAmount(order.getOrderAmount(), fenToYuan(payload.totalAmountFen()), "微信退款通知原订单金额不匹配");

        BigDecimal refundAmount = fenToYuan(payload.refundAmountFen());
        if (!StringUtils.hasText(payload.refundStatus())) {
            throw new PaymentException("微信退款通知缺少退款状态");
        }

        switch (payload.refundStatus()) {
            case "SUCCESS" -> paymentOrderService.refundSuccess(new RefundSuccessDTO(
                    refundOrder.getRefundNo(),
                    refundAmount,
                    /**
                     * 依次取报文退款号与本地第三方退款号
                     * @param payload.getRefundId() 报文中的退款号
                     */
                    firstNonBlank(payload.refundId(), refundOrder.getThirdPartyRefundNo()),
                    "wechat-refund-notify",
                    /**
                     * 序列化为 JSON 文本
                     * @param payload 通知报文
                     */
                    toJson(payload),
                    "微信退款回调成功"));
            case "CLOSED", "ABNORMAL" -> paymentOrderService.refundFail(
                    refundOrder.getRefundNo(),
                    /**
                     * 序列化为 JSON 文本
                     * @param payload 通知报文
                     */
                    toJson(payload),
                    "wechat-refund-notify",
                    "微信退款失败:" + payload.refundStatus());
            case "PROCESSING" -> log.info("微信退款通知保持处理中 orderNo={}, refundNo={}, outRefundNo={}", order.getOrderNo(), refundOrder.getRefundNo(), payload.refundNo());
            default -> log.info("微信退款通知未触发状态更新 orderNo={}, refundNo={}, refundStatus={}", order.getOrderNo(), refundOrder.getRefundNo(), payload.refundStatus());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 处理支付宝支付通知
     *
     * @param channelId 渠道标识
     * @param orderNo 订单号
     * @param params 参数
     */
    public void handleAlipayPayNotify(Long channelId, String orderNo, Map<String, String> params) {
        MerchantChannel channel = requireEnabledChannel(channelId, "ALIPAY");
        AliyunAlipayPayNotifyPayload payload = providerGatewayRegistry.aliyunAlipayGateway(providerSpi(channel, null))
                .verifyAndParsePayNotify(buildAlipayProperties(channel), buildAlipayNotifyRequest(params));

        /**
         * 校验回调路径订单号与报文订单号一致
         * @param orderNo 路径中的订单号
         * @param payload.getOrderNo() 报文中的订单号
         */
        validateNotifyOrderNo(orderNo, payload.getOrderNo());
        PaymentOrder order = requireOrder(firstNonBlank(orderNo, payload.getOrderNo()));
        /**
         * 校验订单所属渠道
         * @param order 订单
         * @param channel 渠道
         */
        validateOrderChannel(order, channel);
        /**
         * 校验支付宝应用与收款方身份
         * @param channel 渠道
         * @param payload.getAppId() 报文中的应用 ID
         */
        validateAlipayIdentity(channel, payload.getAppId(), payload.getSellerId());
        /**
         * 校验金额一致
         * @param order.getOrderAmount() 订单应付金额
         */
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
                    log.info("支付宝关闭通知已忽略 orderNo={}, status={}", order.getOrderNo(), order.getStatus());
                }
            }
            case "WAIT_BUYER_PAY" -> log.info("支付宝支付通知保持处理中 orderNo={}", order.getOrderNo());
            default -> log.info("支付宝通知未触发状态更新 orderNo={}, tradeStatus={}", order.getOrderNo(), tradeStatus);
        }
    }

    /**
     * 获取已启用的渠道
     *
     * @param channelId 渠道标识
     * @param channelType 渠道类型
     * @return 获取到的已启用渠道
     */
    private MerchantChannel requireEnabledChannel(Long channelId, String channelType) {
        MerchantChannel channel = merchantChannelMapper.selectById(channelId);
        if (channel == null) {
            throw new PaymentException("支付方式不存在");
        }
        if (!channelType.equalsIgnoreCase(channel.getChannelType())) {
            throw new PaymentException("支付方式类型不匹配");
        }
        if (!ChannelStatus.ENABLED.getCode().equals(channel.getStatus())) {
            throw new PaymentException("支付方式未启用");
        }
        return channel;
    }

    /**
     * 获取订单，不存在时抛出异常
     *
     * @param orderNo 订单号
     * @return 获取到的订单
     */
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

    /**
     * 校验订单所属渠道
     *
     * @param order 订单
     * @param channel 渠道
     */
    private void validateOrderChannel(PaymentOrder order, MerchantChannel channel) {
        if (order.getChannelId() == null || !order.getChannelId().equals(channel.getId())) {
            throw new PaymentException("订单和回调支付方式不匹配");
        }
    }

    /**
     * 校验退款单所属渠道
     *
     * @param refundOrder 退款订单
     * @param channel 渠道
     */
    private void validateRefundChannel(PaymentRefundOrder refundOrder, MerchantChannel channel) {
        if (refundOrder.getChannelId() == null || !refundOrder.getChannelId().equals(channel.getId())) {
            throw new PaymentException("退款单和回调支付方式不匹配");
        }
    }

    /**
     * 校验回调路径订单号与报文订单号一致
     *
     * @param pathOrderNo 路径中的订单号
     * @param payloadOrderNo 报文中的订单号
     */
    private void validateNotifyOrderNo(String pathOrderNo, String payloadOrderNo) {
        if (StringUtils.hasText(pathOrderNo) && StringUtils.hasText(payloadOrderNo) && !pathOrderNo.equals(payloadOrderNo)) {
            throw new PaymentException("回调路径订单号和报文订单号不一致");
        }
    }

    /**
     * 校验回调路径退款单号与报文退款单号一致
     *
     * @param pathRefundNo 路径中的退款单号
     * @param payloadRefundNo 报文中的退款单号
     */
    private void validateNotifyRefundNo(String pathRefundNo, String payloadRefundNo) {
        if (StringUtils.hasText(pathRefundNo) && StringUtils.hasText(payloadRefundNo) && !pathRefundNo.equals(payloadRefundNo)) {
            throw new PaymentException("回调路径退款单号和报文退款单号不一致");
        }
    }

    /**
     * 校验退款单与原订单的映射关系
     *
     * @param refundOrder 退款订单
     * @param payloadOrderNo 报文中的订单号
     */
    private void validateRefundOrderMapping(PaymentRefundOrder refundOrder, String payloadOrderNo) {
        if (refundOrder == null || !StringUtils.hasText(payloadOrderNo)) {
            return;
        }
        if (!payloadOrderNo.equals(refundOrder.getOrderNo())) {
            throw new PaymentException("退款回调订单号和本地退款单不一致");
        }
    }

    /**
     * 解析退款单
     *
     * @param refundNo 退款单号
     * @param thirdPartyRefundNo 第三方退款单号
     * @param orderNo 原订单号
     * @return 解析出的退款订单
     */
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

    /**
     * 校验微信应用与商户身份
     *
     * @param channel 渠道
     * @param appId 报文中的 AppID
     * @param merchantNo 报文中的商户号
     */
    private void validateWechatIdentity(MerchantChannel channel, String appId, String merchantNo) {
        if (StringUtils.hasText(channel.getAppId()) && StringUtils.hasText(appId) && !channel.getAppId().equals(appId)) {
            throw new PaymentException("微信支付通知 AppId 不匹配");
        }
        if (StringUtils.hasText(channel.getMerchantNo()) && StringUtils.hasText(merchantNo) && !channel.getMerchantNo().equals(merchantNo)) {
            throw new PaymentException("微信支付通知商户号不匹配");
        }
    }

    /**
     * 校验支付宝应用与收款方身份
     *
     * @param channel 渠道
     * @param appId 报文中的应用 ID
     * @param sellerId 报文中的收款方 ID
     */
    private void validateAlipayIdentity(MerchantChannel channel, String appId, String sellerId) {
        if (StringUtils.hasText(channel.getAppId()) && StringUtils.hasText(appId) && !channel.getAppId().equals(appId)) {
            throw new PaymentException("支付宝通知 app_id 不匹配");
        }
        if (StringUtils.hasText(channel.getMerchantNo()) && StringUtils.hasText(sellerId) && !channel.getMerchantNo().equals(sellerId)) {
            throw new PaymentException("支付宝通知 seller_id 不匹配");
        }
    }

    /**
     * 校验金额一致
     *
     * @param expected 期望金额
     * @param actual 实际金额
     * @param message 错误消息
     */
    private void validateAmount(BigDecimal expected, BigDecimal actual, String message) {
        if (expected == null || actual == null) {
            return;
        }
        if (expected.compareTo(actual) != 0) {
            throw new PaymentException(message);
        }
    }

    /**
     * 解析微信实付金额
     *
     * @param payload 通知报文
     */
    private BigDecimal resolveWechatPaidAmount(TencentWechatPayNotifyPayload payload) {
        if (payload == null) {
            return null;
        }
        Long paidAmountFen = payload.payerTotalAmountFen() != null ? payload.payerTotalAmountFen() : payload.totalAmountFen();
        return paidAmountFen == null ? null : fenToYuan(paidAmountFen);
    }

    /**
     * 判断当前状态是否允许取消
     *
     * @param status 订单状态
     * @return 是否允许取消
     */
    private boolean canCancel(String status) {
        return OrderState.PENDING.name().equals(status) || OrderState.PAYING.name().equals(status);
    }

    @SafeVarargs
    /**
     * 返回第一个非空值
     *
     * @param values 候选值
     * @return 第一个非空值
     */
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
     * 构建微信通知请求对象
     * @param serialNumber 平台证书序列号
     * @param timestamp 时间戳
     * @param nonce 随机字符串
     * @param signature 签名值
     * @param signType 签名类型
     * @param body 请求主体
     */
    private TencentWechatNotifyRequest buildWechatNotifyRequest(String serialNumber,
                                                                String timestamp,
                                                                String nonce,
                                                                String signature,
                                                                String signType,
                                                                String body) {
        TencentWechatNotifyRequest request = new TencentWechatNotifyRequest(
                serialNumber,
                timestamp,
                nonce,
                signature,
                signType,
                body
        );
        return request;
    }

    /**
     * 构建支付宝通知请求对象
     *
     * @param params 参数
     * @return 构建出的支付宝通知请求对象
     */
    private AliyunAlipayNotifyRequest buildAlipayNotifyRequest(Map<String, String> params) {
        AliyunAlipayNotifyRequest request = new AliyunAlipayNotifyRequest(params);
        return request;
    }

    /**
     * 构建微信支付配置属性
     *
     * @param channel 渠道
     * @return 构建出的微信支付配置属性
     */
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

    /**
     * 构建支付宝配置属性
     *
     * @param channel 渠道
     * @return 构建出的支付宝配置属性
     */
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
