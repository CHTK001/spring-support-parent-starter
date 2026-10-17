package com.chua.payment.support.dto;

/**
 * 微信支付配置DTO
 *
 * @param appId     APPID
 * @param mchId     MCHID
 * @param apiKey    API密钥
 * @param certPath  证书路径
 * @param notifyUrl 通知URL
 * @since 2026-03-18
 * @author CH
 */
public record WechatConfigDTO(
        String appId,
        String mchId,
        String apiKey,
        String certPath,
        String notifyUrl) {
}
