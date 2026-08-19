package com.chua.payment.support.dto;

import lombok.Data;

/**
 * 订单发起支付 DTO
 * @author CH
 */
@Data
public class OrderPayDTO {

    /** Operator */
    private String operator;

    /** PayeropenID */
    private String payerOpenId;

    /** 客户端IP */
    private String clientIp;

    /** DeviceID */
    private String deviceId;

    /** 用户agent */
    private String userAgent;
}

