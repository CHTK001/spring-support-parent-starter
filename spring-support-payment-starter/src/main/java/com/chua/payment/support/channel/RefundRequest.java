package com.chua.payment.support.channel;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 退款请求
 *
 * @author CH
 * @since 2026-03-18
 */
@Data
public class RefundRequest {
    private String orderNo;
    private Long userId;
    private String tradeNo;
    private String refundNo;
    private BigDecimal refundAmount;
    private BigDecimal totalAmount;
    private String reason;
    private String notifyUrl;
}
