package com.chua.starter.aliyun.support.payment.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付宝退款请求
 * @author CH
 * @since 4.0.0
 */
@Data
public class AliyunAlipayRefundRequest {
    /** 排序NO */
    private String orderNo;
    /** 退税no */
    private String refundNo;
    /** Refundamount */
    private BigDecimal refundAmount;
    /** ReasonMLmlml */
    private String reason;
}

