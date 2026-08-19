package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

/**
 * 微信退款通知解析结果
 * @author CH
 */
@Data
public class TencentWechatRefundNotifyPayload {
    /** 排序NO */
    private String orderNo;
    /** RefundNO */
    private String refundNo;
    /** RefundID */
    private String refundId;
    /** 总数amountFEN */
    private Long totalAmountFen;
    /** RefundamountFEN */
    private Long refundAmountFen;
    /** Refund状态 */
    private String refundStatus;
}

