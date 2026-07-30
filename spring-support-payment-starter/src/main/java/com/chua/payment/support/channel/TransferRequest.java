package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 转账请求
 */
@Data
public class TransferRequest {
    private String transferNo;
    private String notifyUrl;
    private Long merchantId;
    private Long fromUserId;
    private Long toUserId;
    private BigDecimal amount;
    private String operator;
    private String remark;
}
