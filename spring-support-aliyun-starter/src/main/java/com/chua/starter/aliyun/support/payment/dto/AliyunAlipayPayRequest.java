package com.chua.starter.aliyun.support.payment.dto;

import java.math.BigDecimal;

/**
 * 支付宝下单请求
 *
 * @param orderNo 排序NO
 * @param totalAmount 总数amount
 * @param subject 主题
 * @param body 请求体
 * @param notifyUrl 通知url
 * @param returnUrl 返回值URL
 * @param productCode Product代码
 * @param payerOpenId payeropenid
 *
 * @author CH
 * @since 4.0.0
 */
public record AliyunAlipayPayRequest(
        String orderNo,
        BigDecimal totalAmount,
        String subject,
        String body,
        String notifyUrl,
        String returnUrl,
        String productCode,
        String payerOpenId
) {
}

