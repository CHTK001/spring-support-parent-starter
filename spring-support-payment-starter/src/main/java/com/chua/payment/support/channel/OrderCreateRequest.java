package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单创建请求
 */
@Data
public class OrderCreateRequest {
    private String orderNo;
    private Long userId;
    private BigDecimal amount;
    private String subject;
    private String body;
    private String currency;
    private String attach;
}
