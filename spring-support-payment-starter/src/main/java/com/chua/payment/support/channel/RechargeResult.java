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
    /**
     * 是否成功
     */
    private boolean success;
    /**
     * 充值单号
     */
    private String rechargeNo;
    /**
     * 交易单号
     */
    private String tradeNo;
    /**
     * 充值金额
     */
    private BigDecimal amount;
    /**
     * 充值状态
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
