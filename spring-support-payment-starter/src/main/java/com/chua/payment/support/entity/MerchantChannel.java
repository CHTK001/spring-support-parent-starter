package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户支付渠道实体类
 *
 * @author CH
 * @since 2026-03-18
 */
@Data
@TableName("merchant_channel")
public class MerchantChannel {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 商户ID
     */
    private Long merchantId;

    /**
     * 渠道类型：wechat/alipay/union/wallet
     */
    private String channelType;

    /**
     * 渠道名称
     */
    private String channelName;

    /**
     * 应用ID/商户号
     */
    private String appId;

    /**
     * API密钥(加密存储)
     */
    private String apiKey;

    /**
     * 私钥(加密存储)
     */
    private String privateKey;

    /**
     * 公钥
     */
    private String publicKey;

    /**
     * 证书路径
     */
    private String certPath;

    /**
     * 是否沙盒环境
     */
    private Boolean sandbox;

    /**
     * 状态：0禁用 1启用
     */
    private Integer status;

    /**
     * 扩展配置(JSON)
     */
    private String extConfig;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
