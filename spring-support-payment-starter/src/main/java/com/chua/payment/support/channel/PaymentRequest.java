package com.chua.payment.support.channel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付请求
 *
 * @param orderNo     订单号
 * @param userId      用户标识
 * @param amount      支付金额
 * @param subject     支付主题
 * @param body        请求体
 * @param notifyUrl   通知回调 URL
 * @param returnUrl   支付完成后返回 URL
 * @param currency    币种
 * @param expireTime  过期时间
 * @param payerOpenId 付款人 openId
 * @param clientIp    客户端 IP
 * @param deviceId    设备标识
 * @param userAgent   用户 Agent
 * @param attach      附加数据
 * @since 2026-03-18
 * @author CH
 */
public record PaymentRequest(
        String orderNo,
        Long userId,
        BigDecimal amount,
        String subject,
        String body,
        String notifyUrl,
        String returnUrl,
        String currency,
        LocalDateTime expireTime,
        String payerOpenId,
        String clientIp,
        String deviceId,
        String userAgent,
        String attach) {
}
