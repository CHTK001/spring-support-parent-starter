package com.chua.starter.pay.support.event;

import com.chua.starter.pay.support.entity.PayMerchantOrder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

/**
 * 确认支付订单事件
 * @author CH
 * @since 2025/10/14 16:06
 */
@Getter
@Setter
public class FinishPayOrderEvent extends ApplicationEvent {
    public FinishPayOrderEvent(Object source) {
        super(source);
    }

    private PayMerchantOrder payMerchantOrder;
}
