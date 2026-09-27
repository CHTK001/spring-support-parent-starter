package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 转账结果
 * @author CH
 * @since 4.0.0
 */
@Data
public class TransferResult {
    /**
     * 是否成功
     */
    private boolean success;
    /**
     * 转账单号
     */
    private String transferNo;
    /**
     * 交易单号
     */
    private String tradeNo;
    /**
     * 转账金额
     */
    private BigDecimal amount;
    /**
     * 转账状态
     */
    private String status;
    /**
     * 提示消息
     */
    private String message;
    /**
     * 渠道 RAW 响应
     */
    private String rawResponse;
}
