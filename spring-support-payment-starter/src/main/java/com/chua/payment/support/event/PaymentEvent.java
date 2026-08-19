package com.chua.payment.support.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * 支付事件基类
 */
@Getter
public abstract class PaymentEvent extends ApplicationEvent {

    /** 事件类型 */
    private final String eventType;
    /** TenantID */
    private final Long tenantId;
    /** MerchantID */
    private final Long merchantId;
    /** OccurredAT */
    private final LocalDateTime occurredAt;

    /**
     * 创建 PaymentEvent 实例
     * @param source source
     * @param String String
     * @param Long Long
     * @param Long Long
     */
    public PaymentEvent(Object source, String eventType, Long tenantId, Long merchantId) {
        super(source);
        this.eventType = eventType;
        this.tenantId = tenantId;
        this.merchantId = merchantId;
        this.occurredAt = LocalDateTime.now();
    }
}

