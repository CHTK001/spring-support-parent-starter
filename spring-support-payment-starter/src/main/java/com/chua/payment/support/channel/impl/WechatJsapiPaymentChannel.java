package com.chua.payment.support.channel.impl;

import com.chua.payment.support.channel.PaymentChannel;
import com.chua.payment.support.channel.PaymentRequest;
import com.chua.payment.support.channel.PaymentResult;
import com.chua.payment.support.channel.RefundRequest;
import com.chua.payment.support.channel.RefundResult;
import com.chua.payment.support.channel.support.AbstractMerchantPaymentChannel;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.provider.PaymentProviderGatewayRegistry;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.starter.tencent.support.payment.TencentWechatPayGateway;
import com.chua.starter.tencent.support.payment.dto.TencentWechatOrderResponse;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayResponse;
import com.chua.starter.tencent.support.payment.dto.TencentWechatRefundRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatRefundResponse;
import com.chua.starter.tencent.support.properties.TencentWechatPayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 微信 JSAPI 支付
 * @author CH
 * @since 4.0.0
 */
public class WechatJsapiPaymentChannel extends AbstractMerchantPaymentChannel implements PaymentChannel {

    /**
     * 提供方 gateway 注册表
     */
    private final PaymentProviderGatewayRegistry providerGatewayRegistry;

    /**
     * 创建微信 JSAPI 支付通道实例
     * @param merchantChannelService merchant 通道服务
     * @param objectMapper 对象映射器
     * @param providerGatewayRegistry 提供方 gateway 注册表
     */
    public WechatJsapiPaymentChannel(MerchantChannelService merchantChannelService,
                                     ObjectMapper objectMapper,
                                     PaymentProviderGatewayRegistry providerGatewayRegistry) {
        super(merchantChannelService, objectMapper);
        this.providerGatewayRegistry = providerGatewayRegistry;
    }

    @Override
    /**
     * 是否支持该通道
     */
    public boolean supports(String channelType, String channelSubType) {
        return ("WECHAT".equalsIgnoreCase(channelType) || "EPAY".equalsIgnoreCase(channelType))
                && "JSAPI".equalsIgnoreCase(channelSubType);
    }

    @Override
    /**
     * 发起支付
     */
    public PaymentResult pay(MerchantChannel channel, PaymentRequest request) {
        if (!StringUtils.hasText(request.payerOpenId())) {
            throw new PaymentException("微信 JSAPI 支付必须提供 payerOpenId");
        }

        TencentWechatPayResponse response = gateway(providerSpi(channel, null)).jsapiPay(buildProperties(channel), buildPayRequest(channel, request));

        PaymentResult result = new PaymentResult(response.success(), null, response.message(), null,
                "JSAPI", null, null, response.sdkParams(), null, "PAYING", response.rawResponse());
        return result;
    }

    @Override
    /**
     * 查询支付结果
     */
    public PaymentResult query(MerchantChannel channel, String orderNo) {
        TencentWechatOrderResponse response = gateway(providerSpi(channel, null)).queryJsapiOrder(buildProperties(channel), orderNo);

        PaymentResult result = new PaymentResult(response.success(), response.transactionId(), response.tradeStateDesc(), null,
                null, null, null, null, resolvePaidAmount(response.payerTotalAmountFen(), response.totalAmountFen()),
                normalizeTradeState(response.tradeState()), response.rawResponse());
        return result;
    }

    @Override
    /**
     * 关闭订单
     */
    public boolean close(MerchantChannel channel, String orderNo) {
        return gateway(providerSpi(channel, null)).closeJsapiOrder(buildProperties(channel), orderNo);
    }

    @Override
    /**
     * 申请退款
     */
    public RefundResult refund(MerchantChannel channel, RefundRequest request) {
        TencentWechatRefundResponse response = gateway(providerSpi(channel, null)).refund(buildProperties(channel), buildRefundRequest(channel, request));

        RefundResult result = new RefundResult(response.success(), response.refundNo(), response.refundId(),
                response.message(), fenToYuan(response.refundAmountFen()), normalizeRefundStatus(response.refundStatus()),
                response.rawResponse());
        return result;
    }

    @Override
    /**
     * 查询退款结果
     */
    public RefundResult queryRefund(MerchantChannel channel, RefundRequest request) {
        TencentWechatRefundResponse response = gateway(providerSpi(channel, null)).queryRefund(buildProperties(channel), buildRefundRequest(channel, request));

        RefundResult result = new RefundResult(response.success(), response.refundNo(), response.refundId(),
                response.message(), fenToYuan(response.refundAmountFen()), normalizeRefundStatus(response.refundStatus()),
                response.rawResponse());
        return result;
    }

    /**
     * 获取微信网关
     *
     * @return 微信网关
     */
    protected TencentWechatPayGateway gateway() {
        return providerGatewayRegistry.tencentWechatPayGateway();
    }

    /**
     * 获取微信网关
     *
     * @param extension 扩展名
     * @return 微信网关
     */
    protected TencentWechatPayGateway gateway(String extension) {
        return providerGatewayRegistry.tencentWechatPayGateway(extension);
    }

    /**
     * 构建支付请求
     *
     * @param channel 通道配置
     * @param request 请求参数
     * @return 构建好的支付请求
     */
    protected TencentWechatPayRequest buildPayRequest(MerchantChannel channel, PaymentRequest request) {
        TencentWechatPayRequest payRequest = new TencentWechatPayRequest(
                request.orderNo(),
                yuanToFen(request.amount()),
                firstNonBlank(request.currency(), "CNY"),
                firstNonBlank(request.subject(), request.orderNo()),
                request.notifyUrl(),
                formatExpireTime(request),
                request.payerOpenId(),
                firstNonBlank(request.clientIp(), "127.0.0.1"),
                request.deviceId(),
                request.attach(),
                firstNonBlank(extText(channel, "h5Type"), "Wap"),
                firstNonBlank(extText(channel, "appName"), "payment-console"),
                firstNonBlank(request.returnUrl(), extText(channel, "appUrl"))
        );
        return payRequest;
    }

    /**
     * 构建退款请求
     *
     * @param channel 通道配置
     * @param request 请求参数
     * @return 构建好的退款请求
     */
    protected TencentWechatRefundRequest buildRefundRequest(MerchantChannel channel, RefundRequest request) {
        TencentWechatRefundRequest refundRequest = new TencentWechatRefundRequest(
                request.orderNo(),
                request.tradeNo(),
                request.refundNo(),
                yuanToFen(request.refundAmount()),
                yuanToFen(request.totalAmount()),
                request.reason(),
                firstNonBlank(extText(channel, "refundNotifyUrl"), request.notifyUrl(), channel.getNotifyUrl())
        );
        return refundRequest;
    }

    /**
     * 构建微信支付属性
     *
     * @param channel 通道配置
     * @return 构建好的微信支付属性
     */
    protected TencentWechatPayProperties buildProperties(MerchantChannel channel) {
        TencentWechatPayProperties properties = new TencentWechatPayProperties();
        properties.setAppId(requiredText(channel.getAppId(), "微信 AppId 不能为空"));
        properties.setMerchantId(requiredText(channel.getMerchantNo(), "微信商户号不能为空"));
        properties.setPrivateKey(requiredText(decryptValue(channel.getPrivateKey()), "微信商户私钥不能为空"));
        properties.setApiV3Key(requiredText(decryptValue(channel.getApiKey()), "微信 APIv3 Key 不能为空"));
        properties.setMerchantSerialNumber(requiredText(
                /**
                 * ext 文本。
                 * @param channel 通道配置
                 */
                extText(channel, "merchantSerialNumber"),
                "微信商户证书序列号不能为空，请在 extConfig 中提供 merchantSerialNumber"));
        properties.setNotifyUrl(channel.getNotifyUrl());
        return properties;
    }

    /**
     * 归一化交易状态
     *
     * @param tradeState 渠道交易状态
     * @return 归一化后的交易状态
     */
    protected String normalizeTradeState(String tradeState) {
        if (!StringUtils.hasText(tradeState)) {
            return "FAILED";
        }
        return switch (tradeState) {
            case "SUCCESS", "REFUND" -> "PAID";
            case "NOTPAY", "USERPAYING", "ACCEPT" -> "PAYING";
            case "CLOSED", "REVOKED" -> "CANCELLED";
            case "PAYERROR" -> "FAILED";
            default -> "FAILED";
        };
    }

    /**
     * 归一化退款状态
     *
     * @param refundStatus 渠道退款状态
     * @return 归一化后的退款状态
     */
    protected String normalizeRefundStatus(String refundStatus) {
        if (!StringUtils.hasText(refundStatus)) {
            return "FAILED";
        }
        return switch (refundStatus) {
            case "SUCCESS" -> "REFUNDED";
            case "PROCESSING" -> "REFUNDING";
            case "CLOSED", "ABNORMAL" -> "FAILED";
            default -> "FAILED";
        };
    }

    /**
     * 格式化过期时间
     *
     * @param request 请求参数
     * @return 格式化后的过期时间
     */
    protected String formatExpireTime(PaymentRequest request) {
        if (request.expireTime() == null) {
            return null;
        }
        return request.expireTime()
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /**
     * 解析实付金额
     *
     * @param payerTotalAmountFen 渠道付款方实付金额(分)
     * @param totalAmountFen 渠道订单总金额(分)
     * @return 解析出的实付金额
     */
    protected BigDecimal resolvePaidAmount(Long payerTotalAmountFen, Long totalAmountFen) {
        Long amountFen = payerTotalAmountFen != null ? payerTotalAmountFen : totalAmountFen;
        return amountFen == null ? null : fenToYuan(amountFen);
    }
}
