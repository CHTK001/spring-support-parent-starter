package com.chua.payment.support.channel;

import java.math.BigDecimal;

/**
 * 订单创建请求
 *
 * @param orderNo  订单号
 * @param userId   用户标识
 * @param amount   订单金额
 * @param subject  订单主题
 * @param body     请求体
 * @param currency 币种
 * @param attach   附加数据
 * @author CH
 * @since 4.0.0
 */
public record OrderCreateRequest(
        String orderNo,
        Long userId,
        BigDecimal amount,
        String subject,
        String body,
        String currency,
        String attach) {
}
