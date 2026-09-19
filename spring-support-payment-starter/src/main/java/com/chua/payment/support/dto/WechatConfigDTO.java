package com.chua.payment.support.dto;

import lombok.Data;

/**
 * 微信支付配置DTO
 *
 * @since 2026-03-18
 * @author CH
 */
@Data
public class WechatConfigDTO {
    /**
     * APPID
    */
    private String appId;
    /**
     * MCHID
    */
    private String mchId;
    /**
     * API密钥
    */
    private String apiKey;
    /**
     * 证书路径
    */
    private String certPath;
    /**
     * 通知url
    */
    private String notifyUrl;
}
