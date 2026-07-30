package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户支付配置
 */
@Data
@TableName("pay_merchant_payment_config")
public class MerchantPaymentConfig {
    @TableId(value = "pay_merchant_payment_config_id", type = IdType.AUTO)
    private Long id;

    @TableField("pay_merchant_payment_config_tenant_id")
    private Long tenantId;

    @TableField("pay_merchant_payment_config_merchant_id")
    private Long merchantId;

    @TableField("pay_merchant_payment_config_order_reusable")
    private Boolean orderReusable;

    @TableField("pay_merchant_payment_config_order_timeout_minutes")
    private Integer orderTimeoutMinutes;

    @TableField("pay_merchant_payment_config_pending_order_limit")
    private Integer pendingOrderLimit;

    @TableField("pay_merchant_payment_config_auto_cancel_timeout_order")
    private Boolean autoCancelTimeoutOrder;

    @TableField("pay_merchant_payment_config_create_time")
    private LocalDateTime createTime;

    @TableField("pay_merchant_payment_config_update_time")
    private LocalDateTime updateTime;

    @TableField("pay_merchant_payment_config_create_by")
    private String createBy;

    @TableField("pay_merchant_payment_config_update_by")
    private String updateBy;

    @TableField("pay_merchant_payment_config_create_name")
    private String createName;

    @TableField("pay_merchant_payment_config_update_name")
    private String updateName;
}
