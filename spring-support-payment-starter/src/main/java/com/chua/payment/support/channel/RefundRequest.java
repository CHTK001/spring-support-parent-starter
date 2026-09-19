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
     * 排序NO
    */
    private String orderNo;
    /**
     * 用户标识
    */
    private Long userId;
    /**
     * 贸易no
    */
    private String tradeNo;
    /**
     * 退税no
    */
    private String refundNo;
    /**
     * Refundamount
    */
    private BigDecimal refundAmount;
    /**
     * 总数amount
    */
    private BigDecimal totalAmount;
    /**
     * ReasonMLmlml
    */
    private String reason;
    /**
     * 通知url
    */
    private String notifyUrl;
}
