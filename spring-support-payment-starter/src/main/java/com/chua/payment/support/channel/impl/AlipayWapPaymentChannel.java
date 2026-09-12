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
    * 创建 alipaywap薪酬通道 实例
    * @param merchantChannelService merchant通道服务
    * @param objectMapper 对象映射器
    * @param providerGatewayRegistry 提供者gatewayregistry
     */
    public AlipayWapPaymentChannel(MerchantChannelService merchantChannelService,
                                   ObjectMapper objectMapper,
                                   PaymentProviderGatewayRegistry providerGatewayRegistry) {
        /**
        * 父。
        * @param merchantChannelService merchant通道服务
        * @param objectMapper 对象映射器
        * @param providerGatewayRegistry 提供者gatewayregistry
         */
        super(merchantChannelService, objectMapper, providerGatewayRegistry);
    }

    @Override
    /** 支持 */
    public boolean supports(String channelType, String channelSubType) {
        return "ALIPAY".equalsIgnoreCase(channelType) && "WAP".equalsIgnoreCase(channelSubType);
    }

    @Override
    /** 薪酬 */
    public PaymentResult pay(MerchantChannel channel, PaymentRequest request) {
        AliyunAlipayPayResponse response = gateway(providerSpi(channel, null)).wapPay(buildClientProperties(channel),
                buildPayRequest(request, "QUICK_WAP_WAY"));

        PaymentResult result = new PaymentResult();
        result.setSuccess(response.isSuccess());
        result.setLaunchType("HTML_FORM");
        result.setFormHtml(response.getBody());
        result.setTradeNo(response.getTradeNo());
        result.setStatus("PAYING");
        result.setMessage(response.getMessage());
        result.setRawResponse(response.getRawResponse());
        return result;
    }
}

