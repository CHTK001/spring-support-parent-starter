package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

/**
 * 微信退款通知解析结果
 */
@Data
public class TencentWechatRefundNotifyPayload {
    private String orderNo;
    private String refundNo;
    private String refundId;
    private Long totalAmountFen;
    private Long refundAmountFen;
    private String refundStatus;
}
