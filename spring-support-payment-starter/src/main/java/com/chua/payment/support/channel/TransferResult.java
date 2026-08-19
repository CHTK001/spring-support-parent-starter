package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 转账结果
 * @author CH
 */
@Data
public class TransferResult {
    /** Success */
    private boolean success;
    /** TransferNO */
    private String transferNo;
    /** TradeNO */
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

