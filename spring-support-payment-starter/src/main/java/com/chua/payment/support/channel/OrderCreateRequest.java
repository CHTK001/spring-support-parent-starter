package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单创建请求
 * @author CH
 */
@Data
public class OrderCreateRequest {
    /** 排序NO */
    private String orderNo;
    /** 用户ID */
    private Long userId;
    /** Amount */
    private BigDecimal amount;
    /** Subject */
    private String subject;
    /** 请求体 */
    private String body;
    /** Currency */
    private String currency;
    /** Attach */
    private String attach;
}

