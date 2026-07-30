package com.chua.payment.support.dto;

import lombok.Data;

/**
 * 微信支付配置DTO
 *
 * @author CH
 * @since 2026-03-18
 */
@Data
public class WechatConfigDTO {
    private String appId;
    private String mchId;
    private String apiKey;
    private String certPath;
    private String notifyUrl;
}
