package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 充值请求
 * @author CH
 * @since 4.0.0
 */
@Data
public class RechargeRequest {
    /** rechargeno */
    private String rechargeNo;
    /** 通知url */
    private String notifyUrl;
    /** merchantid */
    private Long merchantId;
    /** 用户标识 */
    private Long userId;
    /** Amount */
    private BigDecimal amount;
    /** 操作符 */
    private String operator;
    /** 评论 */
    private String remark;
}

