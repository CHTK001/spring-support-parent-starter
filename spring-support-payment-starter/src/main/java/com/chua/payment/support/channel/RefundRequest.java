package com.chua.payment.support.channel;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 退款请求
 *
 * @since 2026-03-18
 * @author CH
 */
@Data
public class RefundRequest {
    /** 排序NO */
    private String orderNo;
    /** 用户ID */
    private Long userId;
    /** TradeNO */
    private String tradeNo;
    /** RefundNO */
    private String refundNo;
    /** Refundamount */
    private BigDecimal refundAmount;
    /** 总数amount */
    private BigDecimal totalAmount;
    /** Reason */
    private String reason;
    /** NotifyURL */
    private String notifyUrl;
}
