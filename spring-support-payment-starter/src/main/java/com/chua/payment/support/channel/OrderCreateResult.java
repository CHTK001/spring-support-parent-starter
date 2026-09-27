package com.chua.payment.support.channel;

import lombok.Data;

/**
 * 订单创建结果
 * @author CH
 * @since 4.0.0
 */
@Data
public class OrderCreateResult {
    /**
     * 是否成功
     */
    private boolean success;
    /**
     * 订单号
     */
    private String orderNo;
    /**
     * 提示消息
     */
    private String message;
    /**
     * 渠道 RAW 响应
     */
    private String rawResponse;
}
