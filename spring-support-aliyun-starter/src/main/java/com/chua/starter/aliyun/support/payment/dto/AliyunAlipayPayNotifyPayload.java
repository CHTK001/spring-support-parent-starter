package com.chua.starter.aliyun.support.payment.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付宝支付通知解析结果
 *
 * @param orderNo      商户订单号
 * @param tradeNo      支付宝交易号
 * @param appId        支付宝应用 ID
 * @param sellerId     收款方 PID
 * @param totalAmount  订单金额
 * @param tradeStatus  交易状态
 * @param rawParams    通知原始参数
 * @author CH
 * @since 4.0.0
 */
public record AliyunAlipayPayNotifyPayload(
        String orderNo,
        String tradeNo,
        String appId,
        String sellerId,
        BigDecimal totalAmount,
        String tradeStatus,
        Map<String, String> rawParams) {
}
