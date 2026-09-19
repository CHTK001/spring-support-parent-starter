package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商户视图对象
 *
 * @since 2026-03-18
 * @author CH
 */
@Data
public class MerchantVO implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid
    /**
     * 标识
    */
    private Long id;

    /**
     * merchantno
    */
    private String merchantNo;

    /**
     * Merchant名称
    */
    private String merchantName;

    /**
     * Contact名称
    */
    private String contactName;

    /**
     * Contactphone
    */
    private String contactPhone;

    /**
     * Contactemail
    */
    private String contactEmail;

    /**
     * Businesslicense
    */
    private String businessLicense;

    /**
     * Legalperson
    */
    private String legalPerson;

    /**
     * 默认通知url
    */
    private String defaultNotifyUrl;

    /**
     * 默认返回值URL
    */
    private String defaultReturnUrl;

    /**
     * Paymentwallet是否启用
    */
    private Boolean paymentWalletEnabled;

    /**
     * Paymentautoclose是否启用
    */
    private Boolean paymentAutoCloseEnabled;

    /**
     * Paymentautocloseminutes
    */
    private Integer paymentAutoCloseMinutes;

    /**
     * Paymentsplit表是否启用
    */
    private Boolean paymentSplitTableEnabled;

    /**
     * Paymentprofitsharing是否启用
    */
    private Boolean paymentProfitSharingEnabled;

    /**
     * Paymentcoupon是否启用
    */
    private Boolean paymentCouponEnabled;

    /**
     * 评论
    */
    private String remark;

    /**
     * 状态
    */
    private Integer status;

    /**
     * 状态desc
    */
    private String statusDesc;

    /**
     * 通道数量
    */
    private Integer channelCount;

    /**
     * 创建时间AT
    */
    private LocalDateTime createdAt;

    /**
     * 更新时间AT
    */
    private LocalDateTime updatedAt;
}