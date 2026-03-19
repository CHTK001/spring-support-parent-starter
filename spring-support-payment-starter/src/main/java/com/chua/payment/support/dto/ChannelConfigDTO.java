package com.chua.payment.support.dto;

import lombok.Data;

/**
 * 渠道配置DTO
 *
 * @author CH
 * @since 2026-03-18
 */
@Data
public class ChannelConfigDTO {
    private Long merchantId;
    private String channelType;
    private String channelName;
    private String appId;
    private String apiKey;
    private String privateKey;
    private String publicKey;
    private String certPath;
    private Boolean sandbox;
    private String extConfig;
}
