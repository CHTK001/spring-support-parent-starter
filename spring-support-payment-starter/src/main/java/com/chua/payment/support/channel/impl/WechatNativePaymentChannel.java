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
 * 微信 Native 扫码支付
 * @author CH
 * @since 4.0.0
 */
public class WechatNativePaymentChannel extends WechatJsapiPaymentChannel implements PaymentChannel {

    /**
     * 创建微信 Native 扫码支付通道实例
     * @param merchantChannelService merchant 通道服务
     * @param objectMapper 对象映射器
     * @param providerGatewayRegistry 提供方 gateway 注册表
     */
    public WechatNativePaymentChannel(MerchantChannelService merchantChannelService,
                                      ObjectMapper objectMapper,
                                      PaymentProviderGatewayRegistry providerGatewayRegistry) {
        super(merchantChannelService, objectMapper, providerGatewayRegistry);
    }

    @Override
    /**
     * 是否支持该通道
     */
    public boolean supports(String channelType, String channelSubType) {
        return ("WECHAT".equalsIgnoreCase(channelType) || "EPAY".equalsIgnoreCase(channelType))
                && "NATIVE".equalsIgnoreCase(channelSubType);
    }

    @Override
    /**
     * 发起支付
     */
    public PaymentResult pay(MerchantChannel channel, PaymentRequest request) {
        TencentWechatPayRequest payRequest = buildPayRequest(channel, request);
        TencentWechatPayResponse response = gateway(providerSpi(channel, null)).nativePay(buildProperties(channel), payRequest);

        PaymentResult result = new PaymentResult();
        result.setSuccess(response.success());
        result.setLaunchType("NATIVE");
        result.setPayUrl(response.payUrl());
        result.setStatus("PAYING");
        result.setMessage(response.message());
        result.setRawResponse(response.rawResponse());
        return result;
    }

    @Override
    /**
     * 查询支付结果
     */
    public PaymentResult query(MerchantChannel channel, String orderNo) {
        TencentWechatOrderResponse response = gateway(providerSpi(channel, null)).queryNativeOrder(buildProperties(channel), orderNo);

        PaymentResult result = new PaymentResult();
        result.setSuccess(response.success());
        result.setTradeNo(response.transactionId());
        result.setPaidAmount(resolvePaidAmount(response.payerTotalAmountFen(), response.totalAmountFen()));
        result.setStatus(normalizeTradeState(response.tradeState()));
        result.setMessage(response.tradeStateDesc());
        result.setRawResponse(response.rawResponse());
        return result;
    }

    @Override
    /**
     * 关闭订单
     */
    public boolean close(MerchantChannel channel, String orderNo) {
        return gateway(providerSpi(channel, null)).closeNativeOrder(buildProperties(channel), orderNo);
    }
}
