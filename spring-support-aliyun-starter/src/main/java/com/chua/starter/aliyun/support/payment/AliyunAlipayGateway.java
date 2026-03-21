package com.chua.starter.aliyun.support.payment;

import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayNotifyRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayNotifyPayload;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayResponse;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayRefundRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayRefundResponse;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayTradeQueryResponse;
import com.chua.starter.aliyun.support.properties.AliyunAlipayProperties;

/**
 * 支付宝网关 SPI
 */
public interface AliyunAlipayGateway {

    AliyunAlipayPayResponse pagePay(AliyunAlipayProperties properties, AliyunAlipayPayRequest request);

    AliyunAlipayPayResponse wapPay(AliyunAlipayProperties properties, AliyunAlipayPayRequest request);

    AliyunAlipayTradeQueryResponse queryOrder(AliyunAlipayProperties properties, String orderNo);

    boolean closeOrder(AliyunAlipayProperties properties, String orderNo);

    AliyunAlipayRefundResponse refund(AliyunAlipayProperties properties, AliyunAlipayRefundRequest request);

    AliyunAlipayRefundResponse queryRefund(AliyunAlipayProperties properties, AliyunAlipayRefundRequest request);

    AliyunAlipayPayNotifyPayload verifyAndParsePayNotify(AliyunAlipayProperties properties,
                                                         AliyunAlipayNotifyRequest request);

    AliyunAlipayPayResponse appPay(AliyunAlipayProperties properties, AliyunAlipayPayRequest request);
}
