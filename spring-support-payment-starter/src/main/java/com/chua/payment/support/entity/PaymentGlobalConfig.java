package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付全局配置
 */
@Data
@TableName("payment_global_config")
public class PaymentGlobalConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String configKey;

    private String paymentNotifyBaseUrl;

    private String paymentReturnUrl;

    private String paymentCallbackPathTemplate;

    private Integer paymentAutoRefreshSeconds;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
