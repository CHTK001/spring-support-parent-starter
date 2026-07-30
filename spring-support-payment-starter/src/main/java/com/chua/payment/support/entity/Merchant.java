package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户实体类
 *
 * @author CH
 * @since 2026-03-18
 */
@Data
@TableName("pay_merchant")
public class Merchant {
    @TableId(value = "pay_merchant_id", type = IdType.AUTO)
    private Long id;

    @TableField("pay_merchant_no")
    private String merchantNo;

    @TableField("pay_merchant_name")
    private String merchantName;

    @TableField("pay_merchant_contact_name")
    private String contactName;

    @TableField("pay_merchant_contact_phone")
    private String contactPhone;

    @TableField("pay_merchant_contact_email")
    private String contactEmail;

    @TableField("pay_merchant_business_license")
    private String businessLicense;

    @TableField("pay_merchant_legal_person")
    private String legalPerson;

    @TableField("pay_merchant_default_notify_url")
    private String defaultNotifyUrl;

    @TableField("pay_merchant_default_return_url")
    private String defaultReturnUrl;

    @TableField("pay_merchant_payment_wallet_enabled")
    private Boolean paymentWalletEnabled;

    @TableField("pay_merchant_payment_auto_close_enabled")
    private Boolean paymentAutoCloseEnabled;

    @TableField("pay_merchant_payment_auto_close_minutes")
    private Integer paymentAutoCloseMinutes;

    @TableField("pay_merchant_payment_split_table_enabled")
    private Boolean paymentSplitTableEnabled;

    @TableField("pay_merchant_payment_profit_sharing_enabled")
    private Boolean paymentProfitSharingEnabled;

    @TableField("pay_merchant_payment_coupon_enabled")
    private Boolean paymentCouponEnabled;

    @TableField("pay_merchant_remark")
    private String remark;

    @TableField("pay_merchant_status")
    private Integer status;

    @TableField(value = "pay_merchant_created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "pay_merchant_updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
