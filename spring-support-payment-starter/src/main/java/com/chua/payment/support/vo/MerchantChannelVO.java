package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户支付渠道视图对象
 * @author CH
 * @since 4.0.0
 */
@Data
public class MerchantChannelVO implements Serializable {
    private static final long serialVersionUID = 1L; // 序列化版本uid

    /**
     * 标识
     */
    private Long id;

    /**
     * 商户ID
     */
    private Long merchantId;

    /**
     * 商户名称
     */
    private String merchantName;

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
     * API密钥是否已配置
     */
    private Boolean apiKeyConfigured;

    /**
     * 私钥是否已配置
     */
    private Boolean privateKeyConfigured;

    /**
     * 公钥是否已配置
     */
    private Boolean publicKeyConfigured;

    /**
     * 证书是否已配置
     */
    private Boolean certConfigured;

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
     * 状态描述
     */
    private String statusDesc;

    /**
     * EXT配置
     */
    private String extConfig;

    /**
     * 引导标题
     */
    private String guideTitle;

    /**
     * 引导链接
     */
    private String guideUrl;

    /**
     * 渠道类型描述
     */
    private String channelTypeDesc;

    /**
     * 生效通知URL
     */
    private String effectiveNotifyUrl;

    /**
     * 生效返回值URL
     */
    private String effectiveReturnUrl;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
