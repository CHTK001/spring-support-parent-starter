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

    private Long merchantId;

    private String channelType;

    private String channelSubType;

    private String channelName;

    private String appId;

    private String merchantNo;

    private String apiKey;

    private String privateKey;

    private String publicKey;

    private String certPath;

    private Integer sandboxMode;

    private String notifyUrl;

    private String returnUrl;

    private Integer status;

    private String extConfig;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
