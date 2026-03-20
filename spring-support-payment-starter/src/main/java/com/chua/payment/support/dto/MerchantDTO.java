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

    private Boolean walletEnabled;

    private Boolean compositeEnabled;

    private Boolean autoCloseEnabled;

    private Integer autoCloseMinutes;

    private String remark;
}
