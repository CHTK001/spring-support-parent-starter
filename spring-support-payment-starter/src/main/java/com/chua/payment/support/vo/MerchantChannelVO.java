package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商户支付方式视图对象
 * @author CH
 * @since 4.0.0
 */
@Data
public class MerchantChannelVO implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 标识
    */
    private Long id;

    /**
     * merchantid
    */
    private Long merchantId;

    /**
     * Merchant名称
    */
    private String merchantName;

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
     * API密钥configured
    */
    private Boolean apiKeyConfigured;

    /**
     * 私有密钥configured
    */
    private Boolean privateKeyConfigured;

    /**
     * 公开密钥configured
    */
    private Boolean publicKeyConfigured;

    /**
     * 证书configured
    */
    private Boolean certConfigured;

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
     * 状态desc
    */
    private String statusDesc;

    /**
     * EXT配置
    */
    private String extConfig;

    /**
     * Guide标题
    */
    private String guideTitle;

    /**
     * guideurl
    */
    private String guideUrl;

    /**
     * 通道类型desc
    */
    private String channelTypeDesc;

    /**
     * effectivenotifyurl
    */
    private String effectiveNotifyUrl;

    /**
     * Effective返回值URL
    */
    private String effectiveReturnUrl;

    /**
     * 创建时间AT
    */
    private LocalDateTime createdAt;

    /**
     * 更新时间AT
    */
    private LocalDateTime updatedAt;
}
