package com.chua.starter.aliyun.support.payment.spi;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.request.AlipayTradeAppPayRequest;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.chua.common.support.core.annotation.Spi;
import com.chua.starter.aliyun.support.factory.AliyunAlipayClientFactory;
import com.chua.starter.aliyun.support.payment.AliyunAlipayGateway;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayNotifyRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayNotifyPayload;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayResponse;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayRefundRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayRefundResponse;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayTradeQueryResponse;
import com.chua.starter.aliyun.support.properties.AliyunAlipayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 默认支付宝网关实现
 */
@Spi({"default", "alipay"})
public class DefaultAliyunAlipayGateway implements AliyunAlipayGateway {

    private final AliyunAlipayClientFactory clientFactory = new AliyunAlipayClientFactory();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AliyunAlipayPayResponse pagePay(AliyunAlipayProperties properties, AliyunAlipayPayRequest request) {
        try {
            AlipayTradePagePayRequest payRequest = new AlipayTradePagePayRequest();
            payRequest.setNotifyUrl(request.getNotifyUrl());
            payRequest.setReturnUrl(request.getReturnUrl());
            payRequest.setBizContent(toJson(buildPayBizContent(request)));
            AlipayTradePagePayResponse response = client(properties).pageExecute(payRequest);

            AliyunAlipayPayResponse result = new AliyunAlipayPayResponse();
            result.setSuccess(StringUtils.hasText(response.getBody()));
            result.setBody(response.getBody());
            result.setTradeNo(response.getTradeNo());
            result.setMessage(firstNonBlank(response.getSubMsg(), response.getMsg()));
            result.setRawResponse(response.getBody());
            return result;
        } catch (AlipayApiException e) {
            throw new IllegalStateException("发起支付宝电脑网站支付失败", e);
        }
    }

    @Override
    public AliyunAlipayPayResponse wapPay(AliyunAlipayProperties properties, AliyunAlipayPayRequest request) {
        try {
            AlipayTradeWapPayRequest payRequest = new AlipayTradeWapPayRequest();
            payRequest.setNotifyUrl(request.getNotifyUrl());
            payRequest.setReturnUrl(request.getReturnUrl());
            payRequest.setBizContent(toJson(buildPayBizContent(request)));
            AlipayTradeWapPayResponse response = client(properties).pageExecute(payRequest);

            AliyunAlipayPayResponse result = new AliyunAlipayPayResponse();
            result.setSuccess(StringUtils.hasText(response.getBody()));
            result.setBody(response.getBody());
            result.setTradeNo(response.getTradeNo());
            result.setMessage(firstNonBlank(response.getSubMsg(), response.getMsg()));
            result.setRawResponse(response.getBody());
            return result;
        } catch (AlipayApiException e) {
            throw new IllegalStateException("发起支付宝手机网站支付失败", e);
        }
    }

    @Override
    public AliyunAlipayPayResponse appPay(AliyunAlipayProperties properties, AliyunAlipayPayRequest request) {
        try {
            AlipayTradeAppPayRequest payRequest = new AlipayTradeAppPayRequest();
            payRequest.setNotifyUrl(request.getNotifyUrl());
            payRequest.setBizContent(toJson(buildPayBizContent(request)));
            com.alipay.api.response.AlipayTradeAppPayResponse response = client(properties).sdkExecute(payRequest);

            AliyunAlipayPayResponse result = new AliyunAlipayPayResponse();
            result.setSuccess(StringUtils.hasText(response.getBody()));
            result.setBody(response.getBody());
            result.setTradeNo(response.getTradeNo());
            result.setMessage(firstNonBlank(response.getSubMsg(), response.getMsg()));
            result.setRawResponse(response.getBody());
            return result;
        } catch (AlipayApiException e) {
            throw new IllegalStateException("发起支付宝 App 支付失败", e);
        }
    }

    @Override
    public AliyunAlipayTradeQueryResponse queryOrder(AliyunAlipayProperties properties, String orderNo) {
        try {
            AlipayTradeQueryRequest queryRequest = new AlipayTradeQueryRequest();
            queryRequest.setBizContent(toJson(Map.of("out_trade_no", orderNo)));
            AlipayTradeQueryResponse response = client(properties).execute(queryRequest);

            AliyunAlipayTradeQueryResponse result = new AliyunAlipayTradeQueryResponse();
            result.setSuccess(response.isSuccess());
            result.setTradeNo(response.getTradeNo());
            result.setTotalAmount(StringUtils.hasText(response.getTotalAmount()) ? new BigDecimal(response.getTotalAmount()) : null);
            result.setTradeStatus(response.getTradeStatus());
            result.setMessage(firstNonBlank(response.getSubMsg(), response.getMsg(), response.getTradeStatus()));
            result.setRawResponse(response.getBody());
            return result;
        } catch (AlipayApiException e) {
            throw new IllegalStateException("查询支付宝订单失败", e);
        }
    }

    @Override
    public boolean closeOrder(AliyunAlipayProperties properties, String orderNo) {
        try {
            AlipayTradeCloseRequest closeRequest = new AlipayTradeCloseRequest();
            closeRequest.setBizContent(toJson(Map.of("out_trade_no", orderNo)));
            return client(properties).execute(closeRequest).isSuccess();
        } catch (AlipayApiException e) {
            throw new IllegalStateException("关闭支付宝订单失败", e);
        }
    }

    @Override
    public AliyunAlipayRefundResponse refund(AliyunAlipayProperties properties, AliyunAlipayRefundRequest request) {
        try {
            AlipayTradeRefundRequest refundRequest = new AlipayTradeRefundRequest();
            Map<String, Object> bizContent = new LinkedHashMap<>();
            bizContent.put("out_trade_no", request.getOrderNo());
            bizContent.put("refund_amount", request.getRefundAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
            bizContent.put("refund_reason", request.getReason());
            bizContent.put("out_request_no", request.getRefundNo());
            refundRequest.setBizContent(toJson(bizContent));
            AlipayTradeRefundResponse response = client(properties).execute(refundRequest);

            AliyunAlipayRefundResponse result = new AliyunAlipayRefundResponse();
            result.setSuccess(response.isSuccess());
            result.setRefundNo(request.getRefundNo());
            result.setTradeNo(response.getTradeNo());
            result.setRefundAmount(StringUtils.hasText(response.getRefundFee()) ? new BigDecimal(response.getRefundFee()) : request.getRefundAmount());
            result.setRefundStatus(response.isSuccess() ? "SUCCESS" : "FAILED");
            result.setMessage(firstNonBlank(response.getSubMsg(), response.getMsg()));
            result.setRawResponse(response.getBody());
            return result;
        } catch (AlipayApiException e) {
            throw new IllegalStateException("支付宝退款失败", e);
        }
    }

    @Override
    public AliyunAlipayRefundResponse queryRefund(AliyunAlipayProperties properties, AliyunAlipayRefundRequest request) {
        try {
            AlipayTradeFastpayRefundQueryRequest queryRequest = new AlipayTradeFastpayRefundQueryRequest();
            Map<String, Object> bizContent = new LinkedHashMap<>();
            bizContent.put("out_trade_no", request.getOrderNo());
            bizContent.put("out_request_no", request.getRefundNo());
            queryRequest.setBizContent(toJson(bizContent));
            AlipayTradeFastpayRefundQueryResponse response = client(properties).execute(queryRequest);

            AliyunAlipayRefundResponse result = new AliyunAlipayRefundResponse();
            result.setSuccess(response.isSuccess());
            result.setRefundNo(response.getOutRequestNo());
            result.setTradeNo(response.getTradeNo());
            result.setRefundAmount(StringUtils.hasText(response.getRefundAmount()) ? new BigDecimal(response.getRefundAmount()) : request.getRefundAmount());
            result.setRefundStatus(response.getRefundStatus());
            result.setMessage(firstNonBlank(response.getErrorCode(), response.getSubMsg(), response.getMsg(), response.getRefundStatus()));
            result.setRawResponse(response.getBody());
            return result;
        } catch (AlipayApiException e) {
            throw new IllegalStateException("查询支付宝退款失败", e);
        }
    }

    @Override
    public AliyunAlipayPayNotifyPayload verifyAndParsePayNotify(AliyunAlipayProperties properties,
                                                                AliyunAlipayNotifyRequest request) {
        Map<String, String> params = request.getParams();
        if (params == null || params.isEmpty()) {
            throw new IllegalStateException("支付宝通知参数不能为空");
        }
        try {
            boolean verified = AlipaySignature.rsaCheckV1(
                    params,
                    properties.getAlipayPublicKey(),
                    firstNonBlank(params.get("charset"), properties.getCharset(), "UTF-8"),
                    firstNonBlank(params.get("sign_type"), properties.getSignType(), "RSA2"));
            if (!verified) {
                throw new IllegalStateException("支付宝通知验签失败");
            }
        } catch (AlipayApiException e) {
            throw new IllegalStateException("支付宝通知验签失败", e);
        }

        AliyunAlipayPayNotifyPayload payload = new AliyunAlipayPayNotifyPayload();
        payload.setOrderNo(params.get("out_trade_no"));
        payload.setTradeNo(params.get("trade_no"));
        payload.setAppId(params.get("app_id"));
        payload.setSellerId(params.get("seller_id"));
        payload.setTradeStatus(params.get("trade_status"));
        payload.setTotalAmount(StringUtils.hasText(params.get("total_amount")) ? new BigDecimal(params.get("total_amount")) : null);
        payload.setRawParams(params);
        return payload;
    }

    private AlipayClient client(AliyunAlipayProperties properties) {
        return clientFactory.createClient(properties);
    }

    private Map<String, Object> buildPayBizContent(AliyunAlipayPayRequest request) {
        Map<String, Object> bizContent = new LinkedHashMap<>();
        bizContent.put("out_trade_no", request.getOrderNo());
        bizContent.put("total_amount", request.getTotalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        bizContent.put("subject", firstNonBlank(request.getSubject(), request.getOrderNo()));
        bizContent.put("body", request.getBody());
        bizContent.put("product_code", request.getProductCode());
        return bizContent;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("支付宝请求序列化失败", e);
        }
    }
}
