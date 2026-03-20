package com.chua.payment.support.dto;

import lombok.Data;

/**
 * 订单发起支付 DTO
 */
@Data
public class OrderPayDTO {

    private String operator;

    private String payerOpenId;

    private String clientIp;

    private String deviceId;

    private String userAgent;
}
