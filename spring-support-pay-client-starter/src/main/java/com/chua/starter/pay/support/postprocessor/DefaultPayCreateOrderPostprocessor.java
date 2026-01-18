package com.chua.starter.pay.support.postprocessor;

import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.core.annotation.SpiDefault;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.event.CreateOrderEvent;
import org.springframework.context.ApplicationContext;

/**
 * @author CH
 * @since 2025/10/14 14:38
 */
@Spi("default")
@SpiDefault
public class DefaultPayCreateOrderPostprocessor implements PayCreateOrderPostprocessor{

    @AutoInject
    private ApplicationContext applicationContext;

    @Override
    public void publish(PayMerchantOrder data) {
        CreateOrderEvent event = new CreateOrderEvent(data);
        event.setPayMerchantOrder(data);
        applicationContext.publishEvent(event);
    }
}
