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
@TableName("pay_merchant_channel")
public class MerchantChannel {

    @TableId(value = "pay_merchant_channel_id", type = IdType.AUTO)
    private Long id;

    @TableField("pay_merchant_id")
    private Long merchantId;

    @TableField("pay_merchant_channel_channel_type")
    private String channelType;

    @TableField("pay_merchant_channel_channel_sub_type")
    private String channelSubType;

    @TableField("pay_merchant_channel_channel_name")
    private String channelName;

    @TableField("pay_merchant_channel_app_id")
    private String appId;

    @TableField("pay_merchant_channel_merchant_no")
    private String merchantNo;

    @TableField("pay_merchant_channel_api_key")
    private String apiKey;

    @TableField("pay_merchant_channel_private_key")
    private String privateKey;

    @TableField("pay_merchant_channel_public_key")
    private String publicKey;

    @TableField("pay_merchant_channel_cert_path")
    private String certPath;

    @TableField("pay_merchant_channel_sandbox_mode")
    private Integer sandboxMode;

    @TableField("pay_merchant_channel_notify_url")
    private String notifyUrl;

    @TableField("pay_merchant_channel_return_url")
    private String returnUrl;

    @TableField("pay_merchant_channel_status")
    private Integer status;

    @TableField("pay_merchant_channel_ext_config")
    private String extConfig;

    @TableField(value = "pay_merchant_channel_created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "pay_merchant_channel_updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
