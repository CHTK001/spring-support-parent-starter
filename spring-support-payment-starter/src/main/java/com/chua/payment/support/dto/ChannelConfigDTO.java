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
     * 商户ID
     */
    private Long merchantId;
    /**
     * 渠道类型
     */
    private String channelType;
    /**
     * 渠道SUB类型
     */
    private String channelSubType;
    /**
     * 渠道名称
     */
    private String channelName;
    /**
     * APPID
     */
    private String appId;
    /**
     * 商户号
     */
    private String merchantNo;
    /**
     * API密钥
     */
    private String apiKey;
    /**
     * 私钥字符串
     */
    private String privateKey;
    /**
     * 公钥字符串
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
     * 通知URL
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
