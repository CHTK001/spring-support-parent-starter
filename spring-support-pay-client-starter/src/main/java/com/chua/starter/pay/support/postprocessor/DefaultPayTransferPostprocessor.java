package com.chua.starter.pay.support.postprocessor;

import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.constant.Action;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.starter.pay.support.entity.PayMerchantTransferRecord;
import com.chua.starter.pay.support.event.CreateTransferRecordEvent;
import com.chua.starter.pay.support.event.UpdateTransferRecordEvent;
import org.springframework.context.ApplicationContext;

/**
 * @author CH
 * @since 2025/10/15 11:08
 */
@Spi("default")
@SpiDefault
public class DefaultPayTransferPostprocessor implements PayTransferPostprocessor{
    @AutoInject
    private ApplicationContext applicationContext;
    @Override
    public void publish(PayMerchantTransferRecord data, Action action) {
        if(action == Action.CREATE) {
            CreateTransferRecordEvent createTransferRecordEvent = new CreateTransferRecordEvent(data);
            createTransferRecordEvent.setPayMerchantTransferRecord(data);
            applicationContext.publishEvent(createTransferRecordEvent);
            return;
        }

        UpdateTransferRecordEvent event = new UpdateTransferRecordEvent(data);
        event.setPayMerchantTransferRecord(data);
        applicationContext.publishEvent(event);
    }
}
