package com.chua.payment.support.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * 支付事件基类
 */
@Getter
public abstract class PaymentEvent extends ApplicationEvent {

    private final String eventType;
    private final Long tenantId;
    private final Long merchantId;
    private final LocalDateTime occurredAt;

    public PaymentEvent(Object source, String eventType, Long tenantId, Long merchantId) {
        super(source);
        this.eventType = eventType;
        this.tenantId = tenantId;
        this.merchantId = merchantId;
        this.occurredAt = LocalDateTime.now();
    }
}
