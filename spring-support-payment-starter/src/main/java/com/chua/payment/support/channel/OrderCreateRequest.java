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
     * 订单号
     */
    private String orderNo;
    /**
     * 用户标识
     */
    private Long userId;
    /**
     * 订单金额
     */
    private BigDecimal amount;
    /**
     * 订单主题
     */
    private String subject;
    /**
     * 请求体
     */
    private String body;
    /**
     * 币种
     */
    private String currency;
    /**
     * 附加数据
     */
    private String attach;
}
