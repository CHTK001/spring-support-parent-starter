package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户支付配置
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@TableName("pay_merchant_payment_config")
public class MerchantPaymentConfig {
    @TableId(value = "pay_merchant_payment_config_id", type = IdType.AUTO)
    /**
     * 配置标识
     */
    private Long id;

    @TableField("pay_merchant_payment_config_tenant_id")
    /**
     * 租户标识
     */
    private Long tenantId;

    @TableField("pay_merchant_payment_config_merchant_id")
    /**
     * 商户标识
     */
    private Long merchantId;

    @TableField("pay_merchant_payment_config_order_reusable")
    /**
     * 订单号是否可复用
     */
    private Boolean orderReusable;

    @TableField("pay_merchant_payment_config_order_timeout_minutes")
    /**
     * 订单超时分钟数
     */
    private Integer orderTimeoutMinutes;

    @TableField("pay_merchant_payment_config_pending_order_limit")
    /**
     * 待支付订单数量限制
     */
    private Integer pendingOrderLimit;

    @TableField("pay_merchant_payment_config_auto_cancel_timeout_order")
    /**
     * 是否自动取消超时订单
     */
    private Boolean autoCancelTimeoutOrder;

    @TableField("pay_merchant_payment_config_create_time")
    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    @TableField("pay_merchant_payment_config_update_time")
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    @TableField("pay_merchant_payment_config_create_by")
    /**
     * 创建人
     */
    private String createBy;

    @TableField("pay_merchant_payment_config_update_by")
    /**
     * 更新人
     */
    private String updateBy;

    @TableField("pay_merchant_payment_config_create_name")
    /**
     * 创建人名称
     */
    private String createName;

    @TableField("pay_merchant_payment_config_update_name")
    /**
     * 更新人名称
     */
    private String updateName;
}
