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
    /**
     * 是否成功
     */
    private boolean success;
    /**
     * 提现单号
     */
    private String withdrawNo;
    /**
     * 交易单号
     */
    private String tradeNo;
    /**
     * 提现金额
     */
    private BigDecimal amount;
    /**
     * 提现状态
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
