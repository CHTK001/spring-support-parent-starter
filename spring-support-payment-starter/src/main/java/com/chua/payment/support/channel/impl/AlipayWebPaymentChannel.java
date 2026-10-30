package com.chua.payment.support.channel.impl;

import com.chua.payment.support.channel.PaymentChannel;
import com.chua.payment.support.channel.PaymentRequest;
import com.chua.payment.support.channel.PaymentResult;
import com.chua.payment.support.channel.RefundRequest;
import com.chua.payment.support.channel.RefundResult;
import com.chua.payment.support.channel.support.AbstractMerchantPaymentChannel;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.provider.PaymentProviderGatewayRegistry;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.starter.aliyun.support.payment.AliyunAlipayGateway;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayResponse;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayRefundRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayRefundResponse;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayTradeQueryResponse;
import com.chua.starter.aliyun.support.properties.AliyunAlipayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

/**
 * 支付宝电脑网站支付
 * @author CH
 * @since 4.0.0
 */
public class AlipayWebPaymentChannel extends AbstractMerchantPaymentChannel implements PaymentChannel {

    /**
     * 提供方 gateway 注册表
     */
    private final PaymentProviderGatewayRegistry providerGatewayRegistry;

    /**
     * 创建支付宝电脑网站支付通道实例
     * @param merchantChannelService merchant 通道服务
     * @param objectMapper 对象映射器
     * @param providerGatewayRegistry 提供方 gateway 注册表
     */
    public AlipayWebPaymentChannel(MerchantChannelService merchantChannelService,
                                   ObjectMapper objectMapper,
                                   PaymentProviderGatewayRegistry providerGatewayRegistry) {
        super(merchantChannelService, objectMapper);
        this.providerGatewayRegistry = providerGatewayRegistry;
    }

    @Override
    /**
     * 是否支持该通道
     * @param channelSubType 字符串
     * @param channelType 字符串
     * @return 返回值
    */
    public boolean supports(String channelType, String channelSubType) {
        return "ALIPAY".equalsIgnoreCase(channelType) && "WEB".equalsIgnoreCase(channelSubType);
    }

    @Override
    /**
     * 发起支付
     * @param channel 参数
     * @param request 参数
     * @return 返回值
    */
    public PaymentResult pay(MerchantChannel channel, PaymentRequest request) {
        AliyunAlipayPayResponse response = gateway(providerSpi(channel, null)).pagePay(buildClientProperties(channel),
                buildPayRequest(request, "FAST_INSTANT_TRADE_PAY"));

        PaymentResult result = new PaymentResult(response.success(), response.tradeNo(), response.message(), null,
                "HTML_FORM", response.body(), null, null, null, "PAYING", response.rawResponse());
        return result;
    }

    @Override
    /**
     * 查询支付结果
     * @param channel 参数
     * @param orderNo 字符串
     * @return 返回值
    */
    public PaymentResult query(MerchantChannel channel, String orderNo) {
        AliyunAlipayTradeQueryResponse response = gateway(providerSpi(channel, null)).queryOrder(buildClientProperties(channel), orderNo);

        PaymentResult result = new PaymentResult(response.success(), response.tradeNo(), response.message(), null,
                null, null, null, null, response.totalAmount(), normalizeTradeStatus(response.tradeStatus()), response.rawResponse());
        return result;
    }

    @Override
    /**
     * 关闭订单
     * @param channel 参数
     * @param orderNo 字符串
     * @return 返回值
    */
    public boolean close(MerchantChannel channel, String orderNo) {
        return gateway(providerSpi(channel, null)).closeOrder(buildClientProperties(channel), orderNo);
    }

    @Override
    /**
     * 申请退款
     * @param channel 参数
     * @param request 参数
     * @return 返回值
    */
    public RefundResult refund(MerchantChannel channel, RefundRequest request) {
        AliyunAlipayRefundResponse response = gateway(providerSpi(channel, null)).refund(buildClientProperties(channel), buildRefundRequest(request));

        RefundResult result = new RefundResult(response.success(), response.refundNo(), response.tradeNo(),
                response.message(), response.refundAmount(), normalizeRefundStatus(response.refundStatus()), response.rawResponse());
        return result;
    }

    @Override
    /**
     * 查询退款结果
     * @param channel 参数
     * @param request 参数
     * @return 返回值
    */
    public RefundResult queryRefund(MerchantChannel channel, RefundRequest request) {
        AliyunAlipayRefundResponse response = gateway(providerSpi(channel, null)).queryRefund(buildClientProperties(channel), buildRefundRequest(request));

        RefundResult result = new RefundResult(response.success(), response.refundNo(), response.tradeNo(),
                response.message(), response.refundAmount(), normalizeRefundStatus(response.refundStatus()), response.rawResponse());
        return result;
    }

    /**
     * 构建客户端属性
     *
     * @param channel 通道配置
     * @return 构建好的客户端属性
     */
    protected AliyunAlipayProperties buildClientProperties(MerchantChannel channel) {
        AliyunAlipayProperties properties = new AliyunAlipayProperties();
        properties.setAppId(requiredText(channel.getAppId(), "支付宝应用ID不能为空"));
        properties.setPrivateKey(requiredText(decryptValue(channel.getPrivateKey()), "支付宝应用私钥不能为空"));
        properties.setAlipayPublicKey(requiredText(channel.getPublicKey(), "支付宝应用公钥不能为空"));
        properties.setSandbox(channel.getSandboxMode() != null && channel.getSandboxMode() == 1);
        properties.setServerUrl(extText(channel, "serverUrl"));
        properties.setCharset(firstNonBlank(extText(channel, "charset"), "UTF-8"));
        properties.setFormat(firstNonBlank(extText(channel, "format"), "json"));
        properties.setSignType(firstNonBlank(extText(channel, "signType"), "RSA2"));
        return properties;
    }

    /**
     * 获取支付宝网关
     *
     * @return 支付宝网关
     */
    protected AliyunAlipayGateway gateway() {
        return providerGatewayRegistry.aliyunAlipayGateway();
    }

    /**
     * 获取支付宝网关
     *
     * @param extension 扩展名
     * @return 支付宝网关
     */
    protected AliyunAlipayGateway gateway(String extension) {
        return providerGatewayRegistry.aliyunAlipayGateway(extension);
    }

    /**
     * 构建支付请求
     *
     * @param request 请求参数
     * @param productCode 产品编码
     * @return 构建好的支付请求
     */
    protected AliyunAlipayPayRequest buildPayRequest(PaymentRequest request, String productCode) {
        AliyunAlipayPayRequest payRequest = new AliyunAlipayPayRequest(
                request.orderNo(),
                request.amount(),
                firstNonBlank(request.subject(), request.orderNo()),
                request.body(),
                request.notifyUrl(),
                request.returnUrl(),
                productCode,
                null
        );
        return payRequest;
    }

    /**
     * 构建退款请求
     *
     * @param request 请求参数
     * @return 构建好的退款请求
     */
    protected AliyunAlipayRefundRequest buildRefundRequest(RefundRequest request) {
        AliyunAlipayRefundRequest refundRequest = new AliyunAlipayRefundRequest(
                request.orderNo(),
                request.refundNo(),
                request.refundAmount(),
                request.reason()
        );
        return refundRequest;
    }

    /**
     * 归一化交易状态
     *
     * @param tradeStatus 渠道交易状态
     * @return 归一化后的交易状态
     */
    protected String normalizeTradeStatus(String tradeStatus) {
        if (!StringUtils.hasText(tradeStatus)) {
            return "FAILED";
        }
        return switch (tradeStatus) {
            case "TRADE_SUCCESS", "TRADE_FINISHED" -> "PAID";
            case "WAIT_BUYER_PAY" -> "PAYING";
            case "TRADE_CLOSED" -> "CANCELLED";
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
        if (refundStatus.contains("SUCCESS")) {
            return "REFUNDED";
        }
        if (refundStatus.contains("PROCESS")) {
            return "REFUNDING";
        }
        return "FAILED";
    }
}
