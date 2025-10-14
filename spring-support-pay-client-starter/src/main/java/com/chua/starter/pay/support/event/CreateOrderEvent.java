package com.chua.starter.pay.support.event;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

/**
 * 创建订单事件
 * @author CH
 * @since 2025/10/14 14:39
 */
@Getter
@Setter
public class CreateOrderEvent extends ApplicationEvent {
    public CreateOrderEvent(Object source) {
        super(source);
    }

    private String payMerchantOrderCode;
}
