package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
* 提现结果
* @author CH
* @since 4.0.0
 */
@Data
public class WithdrawResult {
    /** 成功 */
    private boolean success;
    /** withdrawno */
    private String withdrawNo;
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

