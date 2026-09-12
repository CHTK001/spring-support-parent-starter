package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

/**
 * 微信退款请求
 * @author CH
 * @since 4.0.0
 */
@Data
public class TencentWechatRefundRequest {
    /** 排序NO */
    private String orderNo;
    /** 贸易no */
    private String tradeNo;
    /** 退税no */
    private String refundNo;
    /** refundamountfen */
    private Long refundAmountFen;
    /** 总数amountfen */
    private Long totalAmountFen;
    /** ReasonMLmlml */
    private String reason;
    /** 通知url */
    private String notifyUrl;
}

