package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

/**
 * 微信退款响应
 * @author CH
 */
@Data
public class TencentWechatRefundResponse {
    /** Success */
    private boolean success;
    /** RefundNO */
    private String refundNo;
    /** RefundID */
    private String refundId;
    /** RefundamountFEN */
    private Long refundAmountFen;
    /** Refund状态 */
    private String refundStatus;
    /** 消息 */
    private String message;
    /** RAW响应 */
    private String rawResponse;
}

