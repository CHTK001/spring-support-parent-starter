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
@TableName("merchant")
public class Merchant {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String merchantNo;

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

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
