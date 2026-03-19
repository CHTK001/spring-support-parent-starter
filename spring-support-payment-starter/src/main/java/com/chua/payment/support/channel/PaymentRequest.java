package com.chua.payment.support.channel;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 支付请求
 *
 * @author CH
 * @since 2026-03-18
 */
@Data
public class PaymentRequest {
    private String orderNo;
    private BigDecimal amount;
    private String subject;
    private String body;
    private String notifyUrl;
    private String returnUrl;
}
