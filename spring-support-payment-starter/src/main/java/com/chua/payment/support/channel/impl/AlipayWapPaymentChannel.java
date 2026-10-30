package com.chua.payment.support.channel.impl;

import com.chua.payment.support.channel.PaymentChannel;
import com.chua.payment.support.channel.PaymentRequest;
import com.chua.payment.support.channel.PaymentResult;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.provider.PaymentProviderGatewayRegistry;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 支付宝手机网站支付
 * @author CH
 * @since 4.0.0
 */
public class AlipayWapPaymentChannel extends AlipayWebPaymentChannel implements PaymentChannel {

    /**
     * 创建支付宝手机网站支付通道实例
     * @param merchantChannelService merchant 通道服务
     * @param objectMapper 对象映射器
     * @param providerGatewayRegistry 提供方 gateway 注册表
     */
    public AlipayWapPaymentChannel(MerchantChannelService merchantChannelService,
                                   ObjectMapper objectMapper,
                                   PaymentProviderGatewayRegistry providerGatewayRegistry) {
        super(merchantChannelService, objectMapper, providerGatewayRegistry);
    }

    @Override
    /**
     * 是否支持该通道
     * @param channelSubType 字符串
     * @param channelType 字符串
     * @return 返回值
    */
    public boolean supports(String channelType, String channelSubType) {
        return "ALIPAY".equalsIgnoreCase(channelType) && "WAP".equalsIgnoreCase(channelSubType);
    }

    @Override
    /**
     * 发起支付
     * @param channel 参数
     * @param request 参数
     * @return 返回值
    */
    public PaymentResult pay(MerchantChannel channel, PaymentRequest request) {
        AliyunAlipayPayResponse response = gateway(providerSpi(channel, null)).wapPay(buildClientProperties(channel),
                buildPayRequest(request, "QUICK_WAP_WAY"));

        PaymentResult result = new PaymentResult(response.success(), response.tradeNo(), response.message(), null,
                "HTML_FORM", response.body(), null, null, null, "PAYING", response.rawResponse());
        return result;
    }
}
