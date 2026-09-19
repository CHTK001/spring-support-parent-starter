package com.chua.starter.tencent.support.payment.dto;

import lombok.Data;

/**
 * 微信退款响应
 * @author CH
 * @since 4.0.0
 */
@Data
public class TencentWechatRefundResponse {
    /**
     * 成功
    */
    private boolean success;
    /**
     * 退税no
    */
    private String refundNo;
    /**
     * 退税标识
    */
    private String refundId;
    /**
     * refundamountfen
    */
    private Long refundAmountFen;
    /**
     * 退税状态
    */
    private String refundStatus;
    /**
     * 消息
    */
    private String message;
    /**
     * RAW响应
    */
    private String rawResponse;
}

