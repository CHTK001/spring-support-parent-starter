package com.chua.payment.support.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 商户数据传输对象
 *
 * @author CH
 * @since 2026-03-18
 */
@Data
public class MerchantDTO implements Serializable {
    private String merchantName;

    private String contactName;

    private String contactPhone;

    private String contactEmail;

    private String businessLicense;

    private String legalPerson;

    private String defaultNotifyUrl;

    private String defaultReturnUrl;

    private Boolean paymentWalletEnabled;

    private Boolean paymentAutoCloseEnabled;

    private Integer paymentAutoCloseMinutes;

    private Boolean paymentSplitTableEnabled;

    private Boolean paymentProfitSharingEnabled;

    private Boolean paymentCouponEnabled;

    private String remark;
}
