package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户支付配置
 * @author CH
 * @since 4.0.0
 */
@Data
@TableName("pay_merchant_payment_config")
public class MerchantPaymentConfig {
    @TableId(value = "pay_merchant_payment_config_id", type = IdType.AUTO)
    /** 标识 */
    private Long id;

    @TableField("pay_merchant_payment_config_tenant_id")
    /** tenantid */
    private Long tenantId;

    @TableField("pay_merchant_payment_config_merchant_id")
    /** merchantid */
    private Long merchantId;

    @TableField("pay_merchant_payment_config_order_reusable")
    /** 排序reusable */
    private Boolean orderReusable;

    @TableField("pay_merchant_payment_config_order_timeout_minutes")
    /** 排序超时minutes */
    private Integer orderTimeoutMinutes;

    @TableField("pay_merchant_payment_config_pending_order_limit")
    /** Pending排序限制 */
    private Integer pendingOrderLimit;

    @TableField("pay_merchant_payment_config_auto_cancel_timeout_order")
    /** Autocancel超时排序 */
    private Boolean autoCancelTimeoutOrder;

    @TableField("pay_merchant_payment_config_create_time")
    /** 创建时间 */
    private LocalDateTime createTime;

    @TableField("pay_merchant_payment_config_update_time")
    /** 更新时间 */
    private LocalDateTime updateTime;

    @TableField("pay_merchant_payment_config_create_by")
    /** 创建by */
    private String createBy;

    @TableField("pay_merchant_payment_config_update_by")
    /** 更新by */
    private String updateBy;

    @TableField("pay_merchant_payment_config_create_name")
    /** 创建名称 */
    private String createName;

    @TableField("pay_merchant_payment_config_update_name")
    /** 更新名称 */
    private String updateName;
}

