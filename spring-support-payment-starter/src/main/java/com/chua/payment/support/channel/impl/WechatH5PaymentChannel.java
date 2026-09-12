package com.chua.payment.support.channel.impl;

import com.chua.payment.support.channel.PaymentChannel;
import com.chua.payment.support.channel.PaymentRequest;
import com.chua.payment.support.channel.PaymentResult;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.provider.PaymentProviderGatewayRegistry;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.starter.tencent.support.payment.dto.TencentWechatOrderResponse;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
* 微信 H5 支付
* @author CH
* @since 4.0.0
 */
public class WechatH5PaymentChannel extends WechatJsapiPaymentChannel implements PaymentChannel {

    /**
    * 创建 wechath5薪酬通道 实例
    * @param merchantChannelService merchant通道服务
    * @param objectMapper 对象映射器
    * @param providerGatewayRegistry 提供者gatewayregistry
     */
    public WechatH5PaymentChannel(MerchantChannelService merchantChannelService,
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
        return ("WECHAT".equalsIgnoreCase(channelType) || "EPAY".equalsIgnoreCase(channelType))
                && "H5".equalsIgnoreCase(channelSubType);
    }

    @Override
    /** 薪酬 */
    public PaymentResult pay(MerchantChannel channel, PaymentRequest request) {
        TencentWechatPayRequest payRequest = buildPayRequest(channel, request);
        payRequest.setAppUrl(firstNonBlank(request.getReturnUrl(), extText(channel, "appUrl")));
        TencentWechatPayResponse response = gateway(providerSpi(channel, null)).h5Pay(buildProperties(channel), payRequest);

        PaymentResult result = new PaymentResult();
        result.setSuccess(response.isSuccess());
        result.setLaunchType("REDIRECT_URL");
        result.setPayUrl(response.getPayUrl());
        result.setStatus("PAYING");
        result.setMessage(response.getMessage());
        result.setRawResponse(response.getRawResponse());
        return result;
    }

    @Override
    /** 查询 */
    public PaymentResult query(MerchantChannel channel, String orderNo) {
        TencentWechatOrderResponse response = gateway(providerSpi(channel, null)).queryH5Order(buildProperties(channel), orderNo);

        PaymentResult result = new PaymentResult();
        result.setSuccess(response.isSuccess());
        result.setTradeNo(response.getTransactionId());
        result.setPaidAmount(resolvePaidAmount(response.getPayerTotalAmountFen(), response.getTotalAmountFen()));
        result.setStatus(normalizeTradeState(response.getTradeState()));
        result.setMessage(response.getTradeStateDesc());
        result.setRawResponse(response.getRawResponse());
        return result;
    }

    @Override
    /** 关闭 */
    public boolean close(MerchantChannel channel, String orderNo) {
        return gateway(providerSpi(channel, null)).closeH5Order(buildProperties(channel), orderNo);
    }
}

