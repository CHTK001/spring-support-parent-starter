package com.chua.starter.aliyun.support.payment.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付宝交易查询响应
 * @author CH
 * @since 4.0.0
 */
@Data
public class AliyunAlipayTradeQueryResponse {
    /** 成功 */
    private boolean success;
    /** 贸易no */
    private String tradeNo;
    /** 总数amount */
    private BigDecimal totalAmount;
    /** 贸易状态 */
    private String tradeStatus;
    /** 消息 */
    private String message;
    /** RAW响应 */
    private String rawResponse;
}

