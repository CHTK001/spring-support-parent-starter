package com.chua.payment.support.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 客户数据传输对象
 *
 * @since 2026-03-18
 * @author CH
 */
@Data
public class MerchantDTO implements Serializable {
    private static final long serialVersionUID = 1L; // 序列化版本uid
    /**
     * 商户名称
     */
    private String merchantName;

    /**
     * 联系人名称
     */
    private String contactName;

    /**
     * 联系电话
     */
    private String contactPhone;

    /**
     * 联系邮箱
     */
    private String contactEmail;

    /**
     * 营业执照
     */
    private String businessLicense;

    /**
     * 法人
     */
    private String legalPerson;

    /**
     * 默认通知URL
     */
    private String defaultNotifyUrl;

    /**
     * 默认返回值URL
     */
    private String defaultReturnUrl;

    /**
     * 支付钱包是否启用
     */
    private Boolean paymentWalletEnabled;

    /**
     * 支付自动关闭是否启用
     */
    private Boolean paymentAutoCloseEnabled;

    /**
     * 支付自动关闭分钟数
     */
    private Integer paymentAutoCloseMinutes;

    /**
     * 支付分表是否启用
     */
    private Boolean paymentSplitTableEnabled;

    /**
     * 支付分账是否启用
     */
    private Boolean paymentProfitSharingEnabled;

    /**
     * 支付优惠券是否启用
     */
    private Boolean paymentCouponEnabled;

    /**
     * 备注
     */
    private String remark;
}
