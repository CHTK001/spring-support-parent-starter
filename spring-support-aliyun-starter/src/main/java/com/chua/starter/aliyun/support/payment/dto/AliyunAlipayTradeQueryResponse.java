package com.chua.starter.aliyun.support.payment.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付宝交易查询响应
 * @author CH
 */
@Data
public class AliyunAlipayTradeQueryResponse {
    /** Success */
    private boolean success;
    /** TradeNO */
    private String tradeNo;
    /** 总数amount */
    private BigDecimal totalAmount;
    /** Trade状态 */
    private String tradeStatus;
    /** 消息 */
    private String message;
    /** RAW响应 */
    private String rawResponse;
}

