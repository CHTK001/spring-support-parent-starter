package com.chua.starter.aliyun.support.payment.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付宝退款请求
 * @author CH
 */
@Data
public class AliyunAlipayRefundRequest {
    /** 排序NO */
    private String orderNo;
    /** RefundNO */
    private String refundNo;
    /** Refundamount */
    private BigDecimal refundAmount;
    /** Reason */
    private String reason;
}

