package com.chua.payment.support.dto;

/**
 * 订单发起支付 DTO
 *
 * @param operator    操作人
 * @param payerOpenId 付款方openid
 * @param clientIp    客户端IP
 * @param deviceId    设备标识
 * @param userAgent   用户Agent
 * @author CH
 * @since 4.0.0
 */
public record OrderPayDTO(
        String operator,
        String payerOpenId,
        String clientIp,
        String deviceId,
        String userAgent) {
}
