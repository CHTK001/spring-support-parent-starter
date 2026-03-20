package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

/**
 * 微信退款响应
 */
@Data
public class TencentWechatRefundResponse {
    private boolean success;
    private String refundNo;
    private String refundId;
    private Long refundAmountFen;
    private String refundStatus;
    private String message;
    private String rawResponse;
}
