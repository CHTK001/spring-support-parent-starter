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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 微信 JSAPI 支付
 */
@Component
public class WechatJsapiPaymentChannel extends AbstractMerchantPaymentChannel implements PaymentChannel {

    private final PaymentProviderGatewayRegistry providerGatewayRegistry;

    public WechatJsapiPaymentChannel(MerchantChannelService merchantChannelService,
                                     ObjectMapper objectMapper,
                                     PaymentProviderGatewayRegistry providerGatewayRegistry) {
        super(merchantChannelService, objectMapper);
        this.providerGatewayRegistry = providerGatewayRegistry;
    }

    @Override
    public boolean supports(String channelType, String channelSubType) {
        return ("WECHAT".equalsIgnoreCase(channelType) || "EPAY".equalsIgnoreCase(channelType))
                && "JSAPI".equalsIgnoreCase(channelSubType);
    }

    @Override
    public PaymentResult pay(MerchantChannel channel, PaymentRequest request) {
        if (!StringUtils.hasText(request.getPayerOpenId())) {
            throw new PaymentException("微信 JSAPI 支付必须提供 payerOpenId");
        }

        TencentWechatPayResponse response = gateway(providerSpi(channel, null)).jsapiPay(buildProperties(channel), buildPayRequest(channel, request));

        PaymentResult result = new PaymentResult();
        result.setSuccess(response.isSuccess());
        result.setLaunchType("JSAPI");
        result.setSdkParams(response.getSdkParams());
        result.setStatus("PAYING");
        result.setMessage(response.getMessage());
        result.setRawResponse(response.getRawResponse());
        return result;
    }

    @Override
    public PaymentResult query(MerchantChannel channel, String orderNo) {
        TencentWechatOrderResponse response = gateway(providerSpi(channel, null)).queryJsapiOrder(buildProperties(channel), orderNo);

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
    public boolean close(MerchantChannel channel, String orderNo) {
        return gateway(providerSpi(channel, null)).closeJsapiOrder(buildProperties(channel), orderNo);
    }

    @Override
    public RefundResult refund(MerchantChannel channel, RefundRequest request) {
        TencentWechatRefundResponse response = gateway(providerSpi(channel, null)).refund(buildProperties(channel), buildRefundRequest(channel, request));

        RefundResult result = new RefundResult();
        result.setSuccess(response.isSuccess());
        result.setRefundNo(response.getRefundNo());
        result.setTradeNo(response.getRefundId());
        result.setRefundAmount(fenToYuan(response.getRefundAmountFen()));
        result.setStatus(normalizeRefundStatus(response.getRefundStatus()));
        result.setMessage(response.getMessage());
        result.setRawResponse(response.getRawResponse());
        return result;
    }

    @Override
    public RefundResult queryRefund(MerchantChannel channel, RefundRequest request) {
        TencentWechatRefundResponse response = gateway(providerSpi(channel, null)).queryRefund(buildProperties(channel), buildRefundRequest(channel, request));

        RefundResult result = new RefundResult();
        result.setSuccess(response.isSuccess());
        result.setRefundNo(response.getRefundNo());
        result.setTradeNo(response.getRefundId());
        result.setRefundAmount(fenToYuan(response.getRefundAmountFen()));
        result.setStatus(normalizeRefundStatus(response.getRefundStatus()));
        result.setMessage(response.getMessage());
        result.setRawResponse(response.getRawResponse());
        return result;
    }

    protected TencentWechatPayGateway gateway() {
        return providerGatewayRegistry.tencentWechatPayGateway();
    }

    protected TencentWechatPayGateway gateway(String extension) {
        return providerGatewayRegistry.tencentWechatPayGateway(extension);
    }

    protected TencentWechatPayRequest buildPayRequest(MerchantChannel channel, PaymentRequest request) {
        TencentWechatPayRequest payRequest = new TencentWechatPayRequest();
        payRequest.setOrderNo(request.getOrderNo());
        payRequest.setAmountFen(yuanToFen(request.getAmount()));
        payRequest.setCurrency(firstNonBlank(request.getCurrency(), "CNY"));
        payRequest.setDescription(firstNonBlank(request.getSubject(), request.getOrderNo()));
        payRequest.setNotifyUrl(request.getNotifyUrl());
        payRequest.setExpireTime(formatExpireTime(request));
        payRequest.setPayerOpenId(request.getPayerOpenId());
        payRequest.setClientIp(firstNonBlank(request.getClientIp(), "127.0.0.1"));
        payRequest.setDeviceId(request.getDeviceId());
        payRequest.setAttach(request.getAttach());
        payRequest.setH5Type(firstNonBlank(extText(channel, "h5Type"), "Wap"));
        payRequest.setAppName(firstNonBlank(extText(channel, "appName"), "payment-console"));
        payRequest.setAppUrl(firstNonBlank(request.getReturnUrl(), extText(channel, "appUrl")));
        return payRequest;
    }

    protected TencentWechatRefundRequest buildRefundRequest(MerchantChannel channel, RefundRequest request) {
        TencentWechatRefundRequest refundRequest = new TencentWechatRefundRequest();
        refundRequest.setOrderNo(request.getOrderNo());
        refundRequest.setTradeNo(request.getTradeNo());
        refundRequest.setRefundNo(request.getRefundNo());
        refundRequest.setRefundAmountFen(yuanToFen(request.getRefundAmount()));
        refundRequest.setTotalAmountFen(yuanToFen(request.getTotalAmount()));
        refundRequest.setReason(request.getReason());
        refundRequest.setNotifyUrl(firstNonBlank(extText(channel, "refundNotifyUrl"), request.getNotifyUrl(), channel.getNotifyUrl()));
        return refundRequest;
    }

    protected TencentWechatPayProperties buildProperties(MerchantChannel channel) {
        TencentWechatPayProperties properties = new TencentWechatPayProperties();
        properties.setAppId(requiredText(channel.getAppId(), "微信 AppId 不能为空"));
        properties.setMerchantId(requiredText(channel.getMerchantNo(), "微信商户号不能为空"));
        properties.setPrivateKey(requiredText(decryptValue(channel.getPrivateKey()), "微信商户私钥不能为空"));
        properties.setApiV3Key(requiredText(decryptValue(channel.getApiKey()), "微信 APIv3 Key 不能为空"));
        properties.setMerchantSerialNumber(requiredText(
                extText(channel, "merchantSerialNumber"),
                "微信商户证书序列号不能为空，请在 extConfig 中提供 merchantSerialNumber"));
        properties.setNotifyUrl(channel.getNotifyUrl());
        return properties;
    }

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

    protected String formatExpireTime(PaymentRequest request) {
        if (request.getExpireTime() == null) {
            return null;
        }
        return request.getExpireTime()
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    protected BigDecimal resolvePaidAmount(Long payerTotalAmountFen, Long totalAmountFen) {
        Long amountFen = payerTotalAmountFen != null ? payerTotalAmountFen : totalAmountFen;
        return amountFen == null ? null : fenToYuan(amountFen);
    }
}
