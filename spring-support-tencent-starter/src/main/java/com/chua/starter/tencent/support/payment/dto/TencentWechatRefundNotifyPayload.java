package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

/**
 * 微信退款通知解析结果
 * @author CH
 * @since 4.0.0
 */
@Data
public class TencentWechatRefundNotifyPayload {
    /** 排序NO */
    private String orderNo;
    /** 退税no */
    private String refundNo;
    /** 退税标识 */
    private String refundId;
    /** 总数amountfen */
    private Long totalAmountFen;
    /** refundamountfen */
    private Long refundAmountFen;
    /** 退税状态 */
    private String refundStatus;
}

