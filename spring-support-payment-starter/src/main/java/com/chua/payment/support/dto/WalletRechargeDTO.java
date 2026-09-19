package com.chua.payment.support.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 钱包充值 DTO
 * @author CH
 * @since 4.0.0
 */
@Data
public class WalletRechargeDTO {
    /** merchantid */
    private Long merchantId;
    /** 用户标识 */
    private Long userId;
    /** rechargeno */
    private String rechargeNo;
    /** 通知url */
    private String notifyUrl;
    /** Amount */
    private BigDecimal amount;
    /** 操作符 */
    private String operator;
    /** 评论 */
    private String remark;
}

