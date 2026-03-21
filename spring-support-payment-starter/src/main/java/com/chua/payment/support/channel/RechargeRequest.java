package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 充值请求
 */
@Data
public class RechargeRequest {
    private String rechargeNo;
    private Long merchantId;
    private Long userId;
    private BigDecimal amount;
    private String operator;
    private String remark;
}
