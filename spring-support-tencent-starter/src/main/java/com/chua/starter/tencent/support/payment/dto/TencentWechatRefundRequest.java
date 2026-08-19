package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

/**
 * 微信退款请求
 * @author CH
 */
@Data
public class TencentWechatRefundRequest {
    /** 排序NO */
    private String orderNo;
    /** TradeNO */
    private String tradeNo;
    /** RefundNO */
    private String refundNo;
    /** RefundamountFEN */
    private Long refundAmountFen;
    /** 总数amountFEN */
    private Long totalAmountFen;
    /** Reason */
    private String reason;
    /** NotifyURL */
    private String notifyUrl;
}

