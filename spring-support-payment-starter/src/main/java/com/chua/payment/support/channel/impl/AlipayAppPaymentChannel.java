package com.chua.payment.support.channel.impl;

import com.chua.payment.support.channel.PaymentChannel;
import com.chua.payment.support.channel.PaymentRequest;
import com.chua.payment.support.channel.PaymentResult;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.provider.PaymentProviderGatewayRegistry;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 支付宝 App 支付
 * @author CH
 * @since 4.0.0
 */
public class AlipayAppPaymentChannel extends AlipayWebPaymentChannel implements PaymentChannel {

    /**
     * 创建支付宝 App 支付通道实例
     * @param merchantChannelService merchant 通道服务
     * @param objectMapper 对象映射器
     * @param providerGatewayRegistry 提供方 gateway 注册表
     */
    public AlipayAppPaymentChannel(MerchantChannelService merchantChannelService,
                                   ObjectMapper objectMapper,
                                   PaymentProviderGatewayRegistry providerGatewayRegistry) {
        super(merchantChannelService, objectMapper, providerGatewayRegistry);
    }

    @Override
    /**
     * 是否支持该通道
     */
    public boolean supports(String channelType, String channelSubType) {
        return "ALIPAY".equalsIgnoreCase(channelType) && "APP".equalsIgnoreCase(channelSubType);
    }

    @Override
    /**
     * 发起支付
     */
    public PaymentResult pay(MerchantChannel channel, PaymentRequest request) {
        AliyunAlipayPayResponse response = gateway(providerSpi(channel, null)).appPay(
                /**
                 * 构建客户端属性。
                 * @param channel 通道配置
                 */
                buildClientProperties(channel),
                buildPayRequest(request, "QUICK_MSECURITY_PAY"));

        PaymentResult result = new PaymentResult();
        result.setSuccess(response.success());
        result.setLaunchType("APP");
        result.setBody(response.body());
        result.setSdkParams(Map.of("orderString", response.body()));
        result.setTradeNo(response.tradeNo());
        result.setStatus("PAYING");
        result.setMessage(response.message());
        result.setRawResponse(response.rawResponse());
        return result;
    }
}
