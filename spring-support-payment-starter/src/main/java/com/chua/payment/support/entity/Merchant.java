package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户实体类
 *
 * @since 2026-03-18
 * @author CH
 */
@Data
@TableName("pay_merchant")
public class Merchant {
    @TableId(value = "pay_merchant_id", type = IdType.AUTO)
    /** 标识 */
    private Long id;

    @TableField("pay_merchant_no")
    /** merchantno */
    private String merchantNo;

    @TableField("pay_merchant_name")
    /** Merchant名称 */
    private String merchantName;

    @TableField("pay_merchant_contact_name")
    /** Contact名称 */
    private String contactName;

    @TableField("pay_merchant_contact_phone")
    /** Contactphone */
    private String contactPhone;

    @TableField("pay_merchant_contact_email")
    /** Contactemail */
    private String contactEmail;

    @TableField("pay_merchant_business_license")
    /** Businesslicense */
    private String businessLicense;

    @TableField("pay_merchant_legal_person")
    /** Legalperson */
    private String legalPerson;

    @TableField("pay_merchant_default_notify_url")
    /** 默认通知url */
    private String defaultNotifyUrl;

    @TableField("pay_merchant_default_return_url")
    /** 默认返回值URL */
    private String defaultReturnUrl;

    @TableField("pay_merchant_payment_wallet_enabled")
    /** Paymentwallet是否启用 */
    private Boolean paymentWalletEnabled;

    @TableField("pay_merchant_payment_auto_close_enabled")
    /** Paymentautoclose是否启用 */
    private Boolean paymentAutoCloseEnabled;

    @TableField("pay_merchant_payment_auto_close_minutes")
    /** Paymentautocloseminutes */
    private Integer paymentAutoCloseMinutes;

    @TableField("pay_merchant_payment_split_table_enabled")
    /** Paymentsplit表是否启用 */
    private Boolean paymentSplitTableEnabled;

    @TableField("pay_merchant_payment_profit_sharing_enabled")
    /** Paymentprofitsharing是否启用 */
    private Boolean paymentProfitSharingEnabled;

    @TableField("pay_merchant_payment_coupon_enabled")
    /** Paymentcoupon是否启用 */
    private Boolean paymentCouponEnabled;

    @TableField("pay_merchant_remark")
    /** 评论 */
    private String remark;

    @TableField("pay_merchant_status")
    /** 状态 */
    private Integer status;

    @TableField(value = "pay_merchant_created_at", fill = FieldFill.INSERT)
    /** 创建时间AT */
    private LocalDateTime createdAt;

    @TableField(value = "pay_merchant_updated_at", fill = FieldFill.INSERT_UPDATE)
    /** 更新时间AT */
    private LocalDateTime updatedAt;
}
