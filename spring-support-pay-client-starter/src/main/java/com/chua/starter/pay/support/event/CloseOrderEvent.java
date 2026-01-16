package com.chua.starter.pay.support.event;

import com.chua.starter.pay.support.entity.PayMerchantOrder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

/**
 * 关闭订单事件
 * @author CH
 * @since 2025/10/14 14:39
 */
@Getter
@Setter
public class CloseOrderEvent extends ApplicationEvent {
    public CloseOrderEvent(Object source) {
        super(source);
    }

    private PayMerchantOrder payMerchantOrder;
}
