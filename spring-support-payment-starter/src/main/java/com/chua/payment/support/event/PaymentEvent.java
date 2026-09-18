package com.chua.payment.support.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
* 支付事件基类
* @author CH
* @since 4.0.0
 */
@Getter
public abstract class PaymentEvent extends ApplicationEvent {

    /** 事件类型 */
    private final String eventType;
    /** tenantid */
    private final Long tenantId;
    /** merchantid */
    private final Long merchantId;
    /** occurredat */
    private final LocalDateTime occurredAt;

    /**
    * 创建 薪酬事件 实例
    * @param source 源
    * @param eventType 字符串
    * @param tenantId Long
    * @param tenantId Long
    * @param eventType 事件类型
    * @param tenantId tenantid
    * @param merchantId merchantid
    */
    public PaymentEvent(Object source, String eventType, Long tenantId, Long merchantId) {
        /**
        * 父。
        * @param source 源
        */
        super(source);
        this.eventType = eventType;
        this.tenantId = tenantId;
        this.merchantId = merchantId;
        this.occurredAt = LocalDateTime.now();
    }
}

