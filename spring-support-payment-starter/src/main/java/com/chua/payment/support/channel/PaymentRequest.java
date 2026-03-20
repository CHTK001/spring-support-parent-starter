package com.chua.payment.support.channel;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付请求
 *
 * @author CH
 * @since 2026-03-18
 */
@Data
public class PaymentRequest {
    private String orderNo;
    private Long userId;
    private BigDecimal amount;
    private String subject;
    private String body;
    private String notifyUrl;
    private String returnUrl;
    private String currency;
    private LocalDateTime expireTime;
    private String payerOpenId;
    private String clientIp;
    private String deviceId;
    private String userAgent;
    private String attach;
}
