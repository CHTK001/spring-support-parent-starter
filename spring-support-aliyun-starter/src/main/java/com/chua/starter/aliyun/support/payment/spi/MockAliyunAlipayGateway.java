package com.chua.starter.aliyun.support.payment.spi;

import com.chua.common.support.core.annotation.Spi;
import com.chua.starter.aliyun.support.payment.AliyunAlipayGateway;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayNotifyRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayNotifyPayload;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayResponse;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayRefundRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayRefundResponse;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayTradeQueryResponse;
import com.chua.starter.aliyun.support.properties.AliyunAlipayProperties;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 支付宝 mock SPI，实现本地联调与前后端演示。
 */
@Spi({"mock", "alipay-mock"})
public class MockAliyunAlipayGateway implements AliyunAlipayGateway {

    @Override
    public AliyunAlipayPayResponse pagePay(AliyunAlipayProperties properties, AliyunAlipayPayRequest request) {
        return buildPayResponse(request, "WEB");
    }

    @Override
    public AliyunAlipayPayResponse wapPay(AliyunAlipayProperties properties, AliyunAlipayPayRequest request) {
        return buildPayResponse(request, "WAP");
    }

    @Override
    public AliyunAlipayPayResponse appPay(AliyunAlipayProperties properties, AliyunAlipayPayRequest request) {
        AliyunAlipayPayResponse result = new AliyunAlipayPayResponse();
        result.setSuccess(true);
        // App 支付返回 SDK 调起字符串
        result.setBody("mock_app_pay_sdk_string&out_trade_no=" + request.getOrderNo() + "&total_amount=" + request.getTotalAmount());
        result.setTradeNo(null);
        result.setMessage("支付宝 mock App 支付下单成功");
        result.setRawResponse("{\"provider\":\"alipay-mock\",\"channel\":\"APP\",\"orderNo\":\"" + request.getOrderNo() + "\"}");
        return result;
    }

    @Override
    public AliyunAlipayTradeQueryResponse queryOrder(AliyunAlipayProperties properties, String orderNo) {
        AliyunAlipayTradeQueryResponse result = new AliyunAlipayTradeQueryResponse();
        result.setSuccess(true);
        result.setTradeNo("MOCK-ALI-TRADE-" + orderNo);
        result.setTotalAmount(null);
        result.setTradeStatus(resolveTradeStatus(orderNo));
        result.setMessage("支付宝 mock 查询成功");
        result.setRawResponse("{\"provider\":\"alipay-mock\",\"orderNo\":\"" + orderNo + "\"}");
        return result;
    }

    @Override
    public boolean closeOrder(AliyunAlipayProperties properties, String orderNo) {
        return true;
    }

    @Override
    public AliyunAlipayRefundResponse refund(AliyunAlipayProperties properties, AliyunAlipayRefundRequest request) {
        return buildRefundResponse(request, "SUCCESS", "支付宝 mock 退款成功");
    }

    @Override
    public AliyunAlipayRefundResponse queryRefund(AliyunAlipayProperties properties, AliyunAlipayRefundRequest request) {
        return buildRefundResponse(request, "SUCCESS", "支付宝 mock 退款查询成功");
    }

    @Override
    public AliyunAlipayPayNotifyPayload verifyAndParsePayNotify(AliyunAlipayProperties properties,
                                                                AliyunAlipayNotifyRequest request) {
        Map<String, String> params = request.getParams();
        if (params == null || params.isEmpty()) {
            throw new IllegalStateException("支付宝 mock 通知参数不能为空");
        }
        AliyunAlipayPayNotifyPayload payload = new AliyunAlipayPayNotifyPayload();
        payload.setOrderNo(firstNonBlank(params.get("out_trade_no"), params.get("orderNo")));
        payload.setTradeNo(firstNonBlank(params.get("trade_no"), "MOCK-ALI-TRADE-" + payload.getOrderNo()));
        payload.setAppId(firstNonBlank(params.get("app_id"), properties.getAppId(), "mock-app-id"));
        payload.setSellerId(firstNonBlank(params.get("seller_id"), "mock-seller-id"));
        payload.setTradeStatus(firstNonBlank(params.get("trade_status"), resolveTradeStatus(payload.getOrderNo())));
        payload.setTotalAmount(parseAmount(params.get("total_amount")));
        payload.setRawParams(params);
        return payload;
    }

    private AliyunAlipayPayResponse buildPayResponse(AliyunAlipayPayRequest request, String scene) {
        AliyunAlipayPayResponse result = new AliyunAlipayPayResponse();
        result.setSuccess(true);
        result.setTradeNo("MOCK-ALI-TRADE-" + request.getOrderNo());
        result.setBody("<form data-provider=\"alipay-mock\" data-scene=\"" + scene + "\" data-order-no=\"" + request.getOrderNo() + "\"></form>");
        result.setMessage("支付宝 mock 下单成功");
        result.setRawResponse("{\"provider\":\"alipay-mock\",\"scene\":\"" + scene + "\",\"orderNo\":\"" + request.getOrderNo() + "\",\"time\":\"" + LocalDateTime.now() + "\"}");
        return result;
    }

    private AliyunAlipayRefundResponse buildRefundResponse(AliyunAlipayRefundRequest request, String refundStatus, String message) {
        AliyunAlipayRefundResponse result = new AliyunAlipayRefundResponse();
        result.setSuccess(true);
        result.setRefundNo(request.getRefundNo());
        result.setTradeNo("MOCK-ALI-REFUND-" + request.getRefundNo());
        result.setRefundAmount(request.getRefundAmount());
        result.setRefundStatus(refundStatus);
        result.setMessage(message);
        result.setRawResponse("{\"provider\":\"alipay-mock\",\"refundNo\":\"" + request.getRefundNo() + "\"}");
        return result;
    }

    private String resolveTradeStatus(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return "TRADE_SUCCESS";
        }
        String normalized = orderNo.toUpperCase();
        if (normalized.contains("WAIT")) {
            return "WAIT_BUYER_PAY";
        }
        if (normalized.contains("CLOSE") || normalized.contains("CANCEL")) {
            return "TRADE_CLOSED";
        }
        if (normalized.contains("FAIL")) {
            return "TRADE_CLOSED";
        }
        return "TRADE_SUCCESS";
    }

    private BigDecimal parseAmount(String amountText) {
        if (!StringUtils.hasText(amountText)) {
            return null;
        }
        try {
            return new BigDecimal(amountText);
        } catch (Exception e) {
            throw new IllegalStateException("支付宝 mock 金额格式错误: " + amountText, e);
        }
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
