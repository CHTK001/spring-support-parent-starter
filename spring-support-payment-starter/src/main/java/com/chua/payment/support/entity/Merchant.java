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
    /**
     * 商户标识
     */
    private Long id;

    @TableField("pay_merchant_no")
    /**
     * 商户号
     */
    private String merchantNo;

    @TableField("pay_merchant_name")
    /**
     * 商户名称
     */
    private String merchantName;

    @TableField("pay_merchant_contact_name")
    /**
     * 联系人姓名
     */
    private String contactName;

    @TableField("pay_merchant_contact_phone")
    /**
     * 联系人电话
     */
    private String contactPhone;

    @TableField("pay_merchant_contact_email")
    /**
     * 联系人邮箱
     */
    private String contactEmail;

    @TableField("pay_merchant_business_license")
    /**
     * 营业执照
     */
    private String businessLicense;

    @TableField("pay_merchant_legal_person")
    /**
     * 法人
     */
    private String legalPerson;

    @TableField("pay_merchant_default_notify_url")
    /**
     * 默认通知URL
     */
    private String defaultNotifyUrl;

    @TableField("pay_merchant_default_return_url")
    /**
     * 默认返回值URL
     */
    private String defaultReturnUrl;

    @TableField("pay_merchant_payment_wallet_enabled")
    /**
     * 支付钱包是否启用
     */
    private Boolean paymentWalletEnabled;

    @TableField("pay_merchant_payment_auto_close_enabled")
    /**
     * 支付自动关单是否启用
     */
    private Boolean paymentAutoCloseEnabled;

    @TableField("pay_merchant_payment_auto_close_minutes")
    /**
     * 支付自动关单分钟数
     */
    private Integer paymentAutoCloseMinutes;

    @TableField("pay_merchant_payment_split_table_enabled")
    /**
     * 支付分账表是否启用
     */
    private Boolean paymentSplitTableEnabled;

    @TableField("pay_merchant_payment_profit_sharing_enabled")
    /**
     * 支付分润是否启用
     */
    private Boolean paymentProfitSharingEnabled;

    @TableField("pay_merchant_payment_coupon_enabled")
    /**
     * 支付优惠券是否启用
     */
    private Boolean paymentCouponEnabled;

    @TableField("pay_merchant_remark")
    /**
     * 备注
     */
    private String remark;

    @TableField("pay_merchant_status")
    /**
     * 商户状态
     */
    private Integer status;

    @TableField(value = "pay_merchant_created_at", fill = FieldFill.INSERT)
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    @TableField(value = "pay_merchant_updated_at", fill = FieldFill.INSERT_UPDATE)
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
