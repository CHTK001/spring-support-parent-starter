package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户支付渠道实体类
 *
 * @since 2026-03-18
 * @author CH
 */
@Data
@TableName("pay_merchant_channel")
public class MerchantChannel {

    @TableId(value = "pay_merchant_channel_id", type = IdType.AUTO)
    /**
     * 渠道标识
     */
    private Long id;

    @TableField("pay_merchant_id")
    /**
     * 商户标识
     */
    private Long merchantId;

    @TableField("pay_merchant_channel_channel_type")
    /**
     * 渠道类型
     */
    private String channelType;

    @TableField("pay_merchant_channel_channel_sub_type")
    /**
     * 渠道子类型
     */
    private String channelSubType;

    @TableField("pay_merchant_channel_channel_name")
    /**
     * 渠道名称
     */
    private String channelName;

    @TableField("pay_merchant_channel_app_id")
    /**
     * 应用ID
     */
    private String appId;

    @TableField("pay_merchant_channel_merchant_no")
    /**
     * 商户号
     */
    private String merchantNo;

    @TableField("pay_merchant_channel_api_key")
    /**
     * API密钥
     */
    private String apiKey;

    @TableField("pay_merchant_channel_private_key")
    /**
     * 私有密钥
     */
    private String privateKey;

    @TableField("pay_merchant_channel_public_key")
    /**
     * 公开密钥
     */
    private String publicKey;

    @TableField("pay_merchant_channel_cert_path")
    /**
     * 证书路径
     */
    private String certPath;

    @TableField("pay_merchant_channel_sandbox_mode")
    /**
     * 沙箱模式
     */
    private Integer sandboxMode;

    @TableField("pay_merchant_channel_notify_url")
    /**
     * 通知URL
     */
    private String notifyUrl;

    @TableField("pay_merchant_channel_return_url")
    /**
     * 返回值URL
     */
    private String returnUrl;

    @TableField("pay_merchant_channel_status")
    /**
     * 渠道状态
     */
    private Integer status;

    @TableField("pay_merchant_channel_ext_config")
    /**
     * 扩展配置
     */
    private String extConfig;

    @TableField(value = "pay_merchant_channel_created_at", fill = FieldFill.INSERT)
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    @TableField(value = "pay_merchant_channel_updated_at", fill = FieldFill.INSERT_UPDATE)
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
