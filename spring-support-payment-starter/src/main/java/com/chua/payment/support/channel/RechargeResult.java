package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 充值结果
 * @author CH
 * @since 4.0.0
 */
@Data
public class RechargeResult {
    /** 成功 */
    private boolean success;
    /** rechargeno */
    private String rechargeNo;
    /** 贸易no */
    private String tradeNo;
    /** Amount */
    private BigDecimal amount;
    /** 状态 */
    private String status;
    /** 消息 */
    private String message;
    /** RAW响应 */
    private String rawResponse;
}

