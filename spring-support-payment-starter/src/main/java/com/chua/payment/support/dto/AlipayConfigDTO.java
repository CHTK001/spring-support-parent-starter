package com.chua.payment.support.dto;

/**
 * 支付宝配置DTO
 *
 * @param appId       APPID
 * @param privateKey  私钥字符串
 * @param publicKey   公钥字符串
 * @param notifyUrl   通知URL
 * @param sandbox     Sandbox
 * @since 2026-03-18
 * @author CH
 */
public record AlipayConfigDTO(
        String appId,
        String privateKey,
        String publicKey,
        String notifyUrl,
        Boolean sandbox) {
}
