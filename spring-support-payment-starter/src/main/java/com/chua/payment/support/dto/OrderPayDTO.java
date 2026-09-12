package com.chua.payment.support.dto;

import lombok.Data;

/**
 * 订单发起支付 DTO
 * @author CH
 * @since 4.0.0
 */
@Data
public class OrderPayDTO {

    /** 操作符 */
    private String operator;

    /** payeropenid */
    private String payerOpenId;

    /** 客户端IP */
    private String clientIp;

    /** deviceid */
    private String deviceId;

    /** 用户智能体 */
    private String userAgent;
}

