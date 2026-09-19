package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单创建请求
 * @author CH
 * @since 4.0.0
 */
@Data
public class OrderCreateRequest {
    /**
     * 排序NO
    */
    private String orderNo;
    /**
     * 用户标识
    */
    private Long userId;
    /**
     * Amount
    */
    private BigDecimal amount;
    /**
     * 主题
    */
    private String subject;
    /**
     * 请求体
    */
    private String body;
    /**
     * 货币
    */
    private String currency;
    /**
     * Attach
    */
    private String attach;
}

