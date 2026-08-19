package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 充值请求
 * @author CH
 */
@Data
public class RechargeRequest {
    /** RechargeNO */
    private String rechargeNo;
    /** NotifyURL */
    private String notifyUrl;
    /** MerchantID */
    private Long merchantId;
    /** 用户ID */
    private Long userId;
    /** Amount */
    private BigDecimal amount;
    /** Operator */
    private String operator;
    /** Remark */
    private String remark;
}

