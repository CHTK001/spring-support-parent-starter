package com.chua.payment.support.channel;

import lombok.Data;

/**
 * 支付结果
 *
 * @author CH
 * @since 2026-03-18
 */
@Data
public class PaymentResult {
    private boolean success;
    private String tradeNo;
    private String message;
    private String payUrl;
}
