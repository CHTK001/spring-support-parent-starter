package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付请求
 *
 * @since 2026-03-18
 * @author CH
 */
@Data
public class PaymentRequest {
    /** 排序NO */
    private String orderNo;
    /** 用户ID */
    private Long userId;
    /** Amount */
    private BigDecimal amount;
    /** Subject */
    private String subject;
    /** 请求体 */
    private String body;
    /** NotifyURL */
    private String notifyUrl;
    /** 返回值URL */
    private String returnUrl;
    /** Currency */
    private String currency;
    /** Expire时间 */
    private LocalDateTime expireTime;
    /** PayeropenID */
    private String payerOpenId;
    /** 客户端IP */
    private String clientIp;
    /** DeviceID */
    private String deviceId;
    /** 用户agent */
    private String userAgent;
    /** Attach */
    private String attach;
}
