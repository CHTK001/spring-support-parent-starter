package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

/**
 * 微信退款请求
 */
@Data
public class TencentWechatRefundRequest {
    private String orderNo;
    private String tradeNo;
    private String refundNo;
    private Long refundAmountFen;
    private Long totalAmountFen;
    private String reason;
    private String notifyUrl;
}
