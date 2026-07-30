package com.chua.payment.support.channel;

import lombok.Data;

/**
 * 订单创建结果
 */
@Data
public class OrderCreateResult {
    private boolean success;
    private String orderNo;
    private String message;
    private String rawResponse;
}
