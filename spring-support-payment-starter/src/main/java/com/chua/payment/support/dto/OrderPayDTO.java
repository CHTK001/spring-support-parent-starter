package com.chua.payment.support.dto;

import lombok.Data;

/**
 * 订单发起支付 DTO
 * @author CH
 * @since 4.0.0
 */
@Data
public class OrderPayDTO {

    /**
     * 操作人
     */
    private String operator;

    /**
     * 付款方openid
     */
    private String payerOpenId;

    /**
     * 客户端IP
     */
    private String clientIp;

    /**
     * 设备标识
     */
    private String deviceId;

    /**
     * 用户Agent
     */
    private String userAgent;
}
