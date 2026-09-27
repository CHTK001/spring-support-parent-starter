package com.chua.payment.support.dto;

import lombok.Data;

/**
 * 支付宝配置DTO
 *
 * @since 2026-03-18
 * @author CH
 */
@Data
public class AlipayConfigDTO {
    /**
     * APPID
     */
    private String appId;
    /**
     * 私钥字符串
     */
    private String privateKey;
    /**
     * 公钥字符串
     */
    private String publicKey;
    /**
     * 通知URL
     */
    private String notifyUrl;
    /**
     * Sandbox
     */
    private Boolean sandbox;
}
