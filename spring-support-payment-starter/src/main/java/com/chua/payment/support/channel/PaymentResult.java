package com.chua.payment.support.channel;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付结果
 *
 * @param success     是否成功
 * @param tradeNo     交易单号
 * @param message     提示消息
 * @param payUrl      支付跳转地址
 * @param launchType  拉起方式
 * @param formHtml    表单 HTML
 * @param body        请求体
 * @param sdkParams   SDK 调起参数
 * @param paidAmount  实付金额
 * @param status      支付状态
 * @param rawResponse 渠道 RAW 响应
 * @since 2026-03-18
 * @author CH
 */
public record PaymentResult(
        boolean success,
        String tradeNo,
        String message,
        String payUrl,
        String launchType,
        String formHtml,
        String body,
        Map<String, Object> sdkParams,
        BigDecimal paidAmount,
        String status,
        String rawResponse) {
}
