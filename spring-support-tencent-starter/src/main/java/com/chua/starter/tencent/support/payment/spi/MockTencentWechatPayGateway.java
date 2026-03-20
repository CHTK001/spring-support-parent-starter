package com.chua.starter.tencent.support.payment.spi;

import com.chua.common.support.core.annotation.Spi;
import com.chua.starter.tencent.support.payment.TencentWechatPayGateway;
import com.chua.starter.tencent.support.payment.dto.TencentWechatNotifyRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatOrderResponse;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayNotifyPayload;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayResponse;
import com.chua.starter.tencent.support.payment.dto.TencentWechatRefundNotifyPayload;
import com.chua.starter.tencent.support.payment.dto.TencentWechatRefundRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatRefundResponse;
import com.chua.starter.tencent.support.properties.TencentWechatPayProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信支付 mock SPI，实现本地联调与前后端演示。
 */
@Spi({"mock", "wechat-mock"})
public class MockTencentWechatPayGateway implements TencentWechatPayGateway {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public TencentWechatPayResponse jsapiPay(TencentWechatPayProperties properties, TencentWechatPayRequest request) {
        TencentWechatPayResponse result = new TencentWechatPayResponse();
        result.setSuccess(true);
        result.setSdkParams(buildSdkParams(request.getOrderNo(), properties.getAppId()));
        result.setMessage("微信支付 mock JSAPI 下单成功");
        result.setRawResponse(raw("wechat-mock", "JSAPI", request.getOrderNo()));
        return result;
    }

    @Override
    public TencentWechatPayResponse h5Pay(TencentWechatPayProperties properties, TencentWechatPayRequest request) {
        TencentWechatPayResponse result = new TencentWechatPayResponse();
        result.setSuccess(true);
        result.setPayUrl("https://mock.wechat.local/pay/" + request.getOrderNo());
        result.setMessage("微信支付 mock H5 下单成功");
        result.setRawResponse(raw("wechat-mock", "H5", request.getOrderNo()));
        return result;
    }

    @Override
    public TencentWechatOrderResponse queryJsapiOrder(TencentWechatPayProperties properties, String orderNo) {
        return buildOrderResponse(orderNo);
    }

    @Override
    public TencentWechatOrderResponse queryH5Order(TencentWechatPayProperties properties, String orderNo) {
        return buildOrderResponse(orderNo);
    }

    @Override
    public boolean closeJsapiOrder(TencentWechatPayProperties properties, String orderNo) {
        return true;
    }

    @Override
    public boolean closeH5Order(TencentWechatPayProperties properties, String orderNo) {
        return true;
    }

    @Override
    public TencentWechatRefundResponse refund(TencentWechatPayProperties properties, TencentWechatRefundRequest request) {
        return buildRefundResponse(request, "SUCCESS", "微信支付 mock 退款成功");
    }

    @Override
    public TencentWechatRefundResponse queryRefund(TencentWechatPayProperties properties, TencentWechatRefundRequest request) {
        return buildRefundResponse(request, "SUCCESS", "微信支付 mock 退款查询成功");
    }

    @Override
    public TencentWechatPayNotifyPayload parsePayNotify(TencentWechatPayProperties properties, TencentWechatNotifyRequest request) {
        Map<String, Object> body = parseJsonBody(request.getBody());
        TencentWechatPayNotifyPayload payload = new TencentWechatPayNotifyPayload();
        String orderNo = firstNonBlank(textOf(body.get("outTradeNo")), textOf(body.get("orderNo")));
        payload.setOrderNo(orderNo);
        payload.setTransactionId(firstNonBlank(textOf(body.get("transactionId")), "MOCK-WX-TRADE-" + orderNo));
        payload.setAppId(firstNonBlank(textOf(body.get("appId")), properties.getAppId(), "mock-app-id"));
        payload.setMerchantId(firstNonBlank(textOf(body.get("merchantId")), properties.getMerchantId(), "mock-mchid"));
        payload.setTradeState(firstNonBlank(textOf(body.get("tradeState")), resolveTradeState(orderNo)));
        payload.setTradeStateDesc(firstNonBlank(textOf(body.get("tradeStateDesc")), "MOCK"));
        payload.setTotalAmountFen(longValue(body.get("totalAmountFen")));
        payload.setPayerTotalAmountFen(longValue(body.get("payerTotalAmountFen")));
        return payload;
    }

    @Override
    public TencentWechatRefundNotifyPayload parseRefundNotify(TencentWechatPayProperties properties, TencentWechatNotifyRequest request) {
        Map<String, Object> body = parseJsonBody(request.getBody());
        TencentWechatRefundNotifyPayload payload = new TencentWechatRefundNotifyPayload();
        String orderNo = firstNonBlank(textOf(body.get("outTradeNo")), textOf(body.get("orderNo")));
        String refundNo = firstNonBlank(textOf(body.get("outRefundNo")), textOf(body.get("refundNo")));
        payload.setOrderNo(orderNo);
        payload.setRefundNo(refundNo);
        payload.setRefundId(firstNonBlank(textOf(body.get("refundId")), "MOCK-WX-REFUND-" + refundNo));
        payload.setRefundStatus(firstNonBlank(textOf(body.get("refundStatus")), "SUCCESS"));
        payload.setTotalAmountFen(longValue(body.get("totalAmountFen")));
        payload.setRefundAmountFen(longValue(body.get("refundAmountFen")));
        return payload;
    }

    private Map<String, Object> buildSdkParams(String orderNo, String appId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("appId", firstNonBlank(appId, "mock-app-id"));
        params.put("timeStamp", String.valueOf(System.currentTimeMillis() / 1000));
        params.put("nonceStr", "mock-" + orderNo);
        params.put("package", "prepay_id=mock_" + orderNo);
        params.put("signType", "RSA");
        params.put("paySign", "mock-sign");
        return params;
    }

    private TencentWechatOrderResponse buildOrderResponse(String orderNo) {
        TencentWechatOrderResponse result = new TencentWechatOrderResponse();
        result.setSuccess(true);
        result.setTransactionId("MOCK-WX-TRADE-" + orderNo);
        result.setTradeState(resolveTradeState(orderNo));
        result.setTradeStateDesc("微信支付 mock 查询成功");
        result.setRawResponse(raw("wechat-mock", "QUERY", orderNo));
        return result;
    }

    private TencentWechatRefundResponse buildRefundResponse(TencentWechatRefundRequest request, String refundStatus, String message) {
        TencentWechatRefundResponse result = new TencentWechatRefundResponse();
        result.setSuccess(true);
        result.setRefundNo(request.getRefundNo());
        result.setRefundId("MOCK-WX-REFUND-" + request.getRefundNo());
        result.setRefundAmountFen(request.getRefundAmountFen());
        result.setRefundStatus(refundStatus);
        result.setMessage(message);
        result.setRawResponse(raw("wechat-mock", "REFUND", request.getRefundNo()));
        return result;
    }

    private Map<String, Object> parseJsonBody(String body) {
        if (!StringUtils.hasText(body)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("微信支付 mock 通知体必须是 JSON", e);
        }
    }

    private String raw(String provider, String scene, String value) {
        return "{\"provider\":\"" + provider + "\",\"scene\":\"" + scene + "\",\"value\":\"" + value + "\",\"time\":\"" + LocalDateTime.now() + "\"}";
    }

    private String resolveTradeState(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return "SUCCESS";
        }
        String normalized = orderNo.toUpperCase();
        if (normalized.contains("WAIT")) {
            return "USERPAYING";
        }
        if (normalized.contains("CLOSE") || normalized.contains("CANCEL")) {
            return "CLOSED";
        }
        if (normalized.contains("FAIL")) {
            return "PAYERROR";
        }
        return "SUCCESS";
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            throw new IllegalStateException("微信支付 mock 金额格式错误: " + value, e);
        }
    }

    private String textOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
