package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 转账请求
 * @author CH
 */
@Data
public class TransferRequest {
    /** TransferNO */
    private String transferNo;
    /** NotifyURL */
    private String notifyUrl;
    /** MerchantID */
    private Long merchantId;
    /** From用户ID */
    private Long fromUserId;
    /** TO用户ID */
    private Long toUserId;
    /** Amount */
    private BigDecimal amount;
    /** Operator */
    private String operator;
    /** Remark */
    private String remark;
}

