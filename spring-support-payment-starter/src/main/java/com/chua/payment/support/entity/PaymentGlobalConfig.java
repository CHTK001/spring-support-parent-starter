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
@TableName("pay_global_config")
public class PaymentGlobalConfig {

    @TableId(value = "pay_global_config_id", type = IdType.AUTO)
    private Long id;

    @TableField("pay_global_config_config_key")
    private String configKey;

    @TableField("pay_global_config_notify_base_url")
    private String paymentNotifyBaseUrl;

    @TableField("pay_global_config_return_url")
    private String paymentReturnUrl;

    @TableField("pay_global_config_callback_path_template")
    private String paymentCallbackPathTemplate;

    @TableField("pay_global_config_auto_refresh_seconds")
    private Integer paymentAutoRefreshSeconds;

    @TableField("pay_global_config_remark")
    private String remark;

    @TableField(value = "pay_global_config_created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "pay_global_config_updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 优惠券抵扣部分是否可提现: true-可提现, false-不可提现(冻结) */
    @TableField("pay_global_config_coupon_deduct_withdrawable")
    private Boolean couponDeductWithdrawable;
}
