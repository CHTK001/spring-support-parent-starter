package com.chua.starter.pay.support.postprocessor;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.starter.pay.support.event.CreateOrderEvent;
import com.chua.starter.pay.support.pojo.CreateOrderV2Response;
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
    public void publish(CreateOrderV2Response data) {
        CreateOrderEvent event = new CreateOrderEvent(data.getPayMerchantOrderCode());
        event.setPayMerchantOrderCode(data.getPayMerchantOrderCode());
        applicationContext.publishEvent(event);
    }
}
