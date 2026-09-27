package com.chua.payment.support.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * 支付事件基类
 *
 * @author CH
 * @since 4.0.0
 */
@Getter
public abstract class PaymentEvent extends ApplicationEvent {

    /**
     * 事件类型
     */
    private final String eventType;
    /**
     * 租户标识
     */
    private final Long tenantId;
    /**
     * 商户标识
     */
    private final Long merchantId;
    /**
     * 事件发生时间
     */
    private final LocalDateTime occurredAt;

    /**
     * 创建支付事件实例
     *
     * @param source 来源
     * @param eventType 事件类型
     * @param tenantId 租户标识
     * @param merchantId 商户标识
     */
    public PaymentEvent(Object source, String eventType, Long tenantId, Long merchantId) {
        super(source);
        this.eventType = eventType;
        this.tenantId = tenantId;
        this.merchantId = merchantId;
        this.occurredAt = LocalDateTime.now();
    }
}
