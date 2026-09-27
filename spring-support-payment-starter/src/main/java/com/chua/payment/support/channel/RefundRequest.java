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
    /**
     * 订单号
     */
    private String orderNo;
    /**
     * 用户标识
     */
    private Long userId;
    /**
     * 交易单号
     */
    private String tradeNo;
    /**
     * 退款单号
     */
    private String refundNo;
    /**
     * 退款金额
     */
    private BigDecimal refundAmount;
    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;
    /**
     * 退款原因
     */
    private String reason;
    /**
     * 通知回调 URL
     */
    private String notifyUrl;
}
