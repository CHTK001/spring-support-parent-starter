package com.chua.payment.support.dto;

import lombok.Data;

/**
 * 支付宝配置DTO
 *
 * @author CH
 * @since 2026-03-18
 */
@Data
public class AlipayConfigDTO {
    private String appId;
    private String privateKey;
    private String publicKey;
    private String notifyUrl;
    private Boolean sandbox;
}
