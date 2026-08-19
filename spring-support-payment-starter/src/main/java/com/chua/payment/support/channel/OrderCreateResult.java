package com.chua.payment.support.channel;

import lombok.Data;

/**
 * 订单创建结果
 * @author CH
 */
@Data
public class OrderCreateResult {
    /** Success */
    private boolean success;
    /** 排序NO */
    private String orderNo;
    /** 消息 */
    private String message;
    /** RAW响应 */
    private String rawResponse;
}

