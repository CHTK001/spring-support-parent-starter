package com.chua.payment.support.channel;

import lombok.Data;

/**
* 订单创建结果
* @author CH
* @since 4.0.0
 */
@Data
public class OrderCreateResult {
    /** 成功 */
    private boolean success;
    /** 排序NO */
    private String orderNo;
    /** 消息 */
    private String message;
    /** RAW响应 */
    private String rawResponse;
}

