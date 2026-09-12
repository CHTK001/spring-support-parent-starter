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
    /** APPID */
    private String appId;
    /** 私有密钥 */
    private String privateKey;
    /** 公开密钥 */
    private String publicKey;
    /** 通知url */
    private String notifyUrl;
    /** Sandbox */
    private Boolean sandbox;
}
