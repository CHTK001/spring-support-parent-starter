package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商户视图对象
 *
 * @author CH
 * @since 2026-03-18
 */
@Data
public class MerchantVO implements Serializable {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 商户号
     */
    private String merchantNo;

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

    /**
     * 状态：0待审核 1已激活 2已停用 3已注销
     */
    private Integer status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
