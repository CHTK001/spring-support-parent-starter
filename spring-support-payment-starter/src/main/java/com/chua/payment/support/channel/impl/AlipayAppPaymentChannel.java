package com.chua.payment.support.channel.impl;

import com.chua.payment.support.channel.PaymentChannel;
import com.chua.payment.support.channel.PaymentRequest;
import com.chua.payment.support.channel.PaymentResult;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.provider.PaymentProviderGatewayRegistry;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 支付宝 App 支付
 */
@Component
public class AlipayAppPaymentChannel extends AlipayWebPaymentChannel implements PaymentChannel {

    public AlipayAppPaymentChannel(MerchantChannelService merchantChannelService,
                                   ObjectMapper objectMapper,
                                   PaymentProviderGatewayRegistry providerGatewayRegistry) {
        super(merchantChannelService, objectMapper, providerGatewayRegistry);
    }

    @Override
    public boolean supports(String channelType, String channelSubType) {
        return "ALIPAY".equalsIgnoreCase(channelType) && "APP".equalsIgnoreCase(channelSubType);
    }

    @Override
    public PaymentResult pay(MerchantChannel channel, PaymentRequest request) {
        AliyunAlipayPayResponse response = gateway(providerSpi(channel, null)).appPay(
                buildClientProperties(channel),
                buildPayRequest(request, "QUICK_MSECURITY_PAY"));

        PaymentResult result = new PaymentResult();
        result.setSuccess(response.isSuccess());
        result.setLaunchType("APP");
        result.setBody(response.getBody());
        result.setSdkParams(Map.of("orderString", response.getBody()));
        result.setTradeNo(response.getTradeNo());
        result.setStatus("PAYING");
        result.setMessage(response.getMessage());
        result.setRawResponse(response.getRawResponse());
        return result;
    }
}
