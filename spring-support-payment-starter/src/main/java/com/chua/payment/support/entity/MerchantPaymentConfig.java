package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户支付配置
 */
@Data
@TableName("merchant_payment_config")
public class MerchantPaymentConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long merchantId;
    private Boolean orderReusable;
    private Integer orderTimeoutMinutes;
    private Integer pendingOrderLimit;
    private Boolean autoCancelTimeoutOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
    private String createName;
    private String updateName;
}
