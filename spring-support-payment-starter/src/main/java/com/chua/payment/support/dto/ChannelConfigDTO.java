package com.chua.payment.support.dto;

import lombok.Data;

/**
 * 渠道配置DTO
 *
 * @since 2026-03-18
 * @author CH
 */
@Data
public class ChannelConfigDTO {
    /**
     * merchantid
    */
    private Long merchantId;
    /**
     * 通道类型
    */
    private String channelType;
    /**
     * 通道SUB类型
    */
    private String channelSubType;
    /**
     * 通道名称
    */
    private String channelName;
    /**
     * APPID
    */
    private String appId;
    /**
     * merchantno
    */
    private String merchantNo;
    /**
     * API密钥
    */
    private String apiKey;
    /**
     * 私有密钥
    */
    private String privateKey;
    /**
     * 公开密钥
    */
    private String publicKey;
    /**
     * 证书路径
    */
    private String certPath;
    /**
     * Sandbox模式
    */
    private Integer sandboxMode;
    /**
     * 通知url
    */
    private String notifyUrl;
    /**
     * 返回值URL
    */
    private String returnUrl;
    /**
     * 状态
    */
    private Integer status;
    /**
     * EXT配置
    */
    private String extConfig;
}
