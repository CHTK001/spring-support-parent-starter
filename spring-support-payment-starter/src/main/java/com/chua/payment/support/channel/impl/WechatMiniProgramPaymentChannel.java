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
 * 微信小程序支付
 * @author CH
 * @since 4.0.0
 */
public class WechatMiniProgramPaymentChannel extends WechatJsapiPaymentChannel implements PaymentChannel {

    /**
     * 创建 wechatminiprogram薪酬通道 实例
     * @param merchantChannelService merchant通道服务
     * @param objectMapper 对象映射器
     * @param providerGatewayRegistry 提供者gatewayregistry
     */
    public WechatMiniProgramPaymentChannel(MerchantChannelService merchantChannelService,
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
                && ("MINI_PROGRAM".equalsIgnoreCase(channelSubType)
                || "MINIPROGRAM".equalsIgnoreCase(channelSubType));
    }

    @Override
    /** 薪酬 */
    public PaymentResult pay(MerchantChannel channel, PaymentRequest request) {
        TencentWechatPayRequest payRequest = buildPayRequest(channel, request);
        TencentWechatPayResponse response = gateway(providerSpi(channel, null)).miniProgramPay(buildProperties(channel), payRequest);

        PaymentResult result = new PaymentResult();
        result.setSuccess(response.isSuccess());
        result.setLaunchType("MINI_PROGRAM");
        result.setSdkParams(response.getSdkParams());
        result.setStatus("PAYING");
        result.setMessage(response.getMessage());
        result.setRawResponse(response.getRawResponse());
        return result;
    }

    @Override
    /** 查询 */
    public PaymentResult query(MerchantChannel channel, String orderNo) {
        TencentWechatOrderResponse response = gateway(providerSpi(channel, null)).queryMiniProgramOrder(buildProperties(channel), orderNo);

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
        return gateway(providerSpi(channel, null)).closeMiniProgramOrder(buildProperties(channel), orderNo);
    }
}

