package com.chua.starter.aliyun.support.payment.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付宝退款响应
 * @author CH
 * @since 4.0.0
 */
@Data
public class AliyunAlipayRefundResponse {
    /** 成功 */
    private boolean success;
    /** 退税no */
    private String refundNo;
    /** 贸易no */
    private String tradeNo;
    /** Refundamount */
    private BigDecimal refundAmount;
    /** 退税状态 */
    private String refundStatus;
    /** 消息 */
    private String message;
    /** RAW响应 */
    private String rawResponse;
}

