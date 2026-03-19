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

    /**
     * 商户名称
     */
    private String merchantName;

    /**
     * 联系人
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
}
