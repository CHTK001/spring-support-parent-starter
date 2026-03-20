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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 支付宝电脑网站支付
 */
@Component
public class AlipayWebPaymentChannel extends AbstractMerchantPaymentChannel implements PaymentChannel {

    private final PaymentProviderGatewayRegistry providerGatewayRegistry;

    public AlipayWebPaymentChannel(MerchantChannelService merchantChannelService,
                                   ObjectMapper objectMapper,
                                   PaymentProviderGatewayRegistry providerGatewayRegistry) {
        super(merchantChannelService, objectMapper);
        this.providerGatewayRegistry = providerGatewayRegistry;
    }

    @Override
    public boolean supports(String channelType, String channelSubType) {
        return "ALIPAY".equalsIgnoreCase(channelType) && "WEB".equalsIgnoreCase(channelSubType);
    }

    @Override
    public PaymentResult pay(MerchantChannel channel, PaymentRequest request) {
        AliyunAlipayPayResponse response = gateway(providerSpi(channel, null)).pagePay(buildClientProperties(channel),
                buildPayRequest(request, "FAST_INSTANT_TRADE_PAY"));

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

    @Override
    public PaymentResult query(MerchantChannel channel, String orderNo) {
        AliyunAlipayTradeQueryResponse response = gateway(providerSpi(channel, null)).queryOrder(buildClientProperties(channel), orderNo);

        PaymentResult result = new PaymentResult();
        result.setSuccess(response.isSuccess());
        result.setTradeNo(response.getTradeNo());
        result.setPaidAmount(response.getTotalAmount());
        result.setStatus(normalizeTradeStatus(response.getTradeStatus()));
        result.setMessage(response.getMessage());
        result.setRawResponse(response.getRawResponse());
        return result;
    }

    @Override
    public boolean close(MerchantChannel channel, String orderNo) {
        return gateway(providerSpi(channel, null)).closeOrder(buildClientProperties(channel), orderNo);
    }

    @Override
    public RefundResult refund(MerchantChannel channel, RefundRequest request) {
        AliyunAlipayRefundResponse response = gateway(providerSpi(channel, null)).refund(buildClientProperties(channel), buildRefundRequest(request));

        RefundResult result = new RefundResult();
        result.setSuccess(response.isSuccess());
        result.setRefundNo(response.getRefundNo());
        result.setTradeNo(response.getTradeNo());
        result.setRefundAmount(response.getRefundAmount());
        result.setStatus(normalizeRefundStatus(response.getRefundStatus()));
        result.setMessage(response.getMessage());
        result.setRawResponse(response.getRawResponse());
        return result;
    }

    @Override
    public RefundResult queryRefund(MerchantChannel channel, RefundRequest request) {
        AliyunAlipayRefundResponse response = gateway(providerSpi(channel, null)).queryRefund(buildClientProperties(channel), buildRefundRequest(request));

        RefundResult result = new RefundResult();
        result.setSuccess(response.isSuccess());
        result.setRefundNo(response.getRefundNo());
        result.setTradeNo(response.getTradeNo());
        result.setRefundAmount(response.getRefundAmount());
        result.setStatus(normalizeRefundStatus(response.getRefundStatus()));
        result.setMessage(response.getMessage());
        result.setRawResponse(response.getRawResponse());
        return result;
    }

    protected AliyunAlipayProperties buildClientProperties(MerchantChannel channel) {
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

    protected AliyunAlipayGateway gateway() {
        return providerGatewayRegistry.aliyunAlipayGateway();
    }

    protected AliyunAlipayGateway gateway(String extension) {
        return providerGatewayRegistry.aliyunAlipayGateway(extension);
    }

    protected AliyunAlipayPayRequest buildPayRequest(PaymentRequest request, String productCode) {
        AliyunAlipayPayRequest payRequest = new AliyunAlipayPayRequest();
        payRequest.setOrderNo(request.getOrderNo());
        payRequest.setTotalAmount(request.getAmount());
        payRequest.setSubject(firstNonBlank(request.getSubject(), request.getOrderNo()));
        payRequest.setBody(request.getBody());
        payRequest.setNotifyUrl(request.getNotifyUrl());
        payRequest.setReturnUrl(request.getReturnUrl());
        payRequest.setProductCode(productCode);
        return payRequest;
    }

    protected AliyunAlipayRefundRequest buildRefundRequest(RefundRequest request) {
        AliyunAlipayRefundRequest refundRequest = new AliyunAlipayRefundRequest();
        refundRequest.setOrderNo(request.getOrderNo());
        refundRequest.setRefundNo(request.getRefundNo());
        refundRequest.setRefundAmount(request.getRefundAmount());
        refundRequest.setReason(request.getReason());
        return refundRequest;
    }

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
