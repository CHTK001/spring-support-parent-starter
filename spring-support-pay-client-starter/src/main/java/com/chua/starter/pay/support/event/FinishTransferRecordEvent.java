package com.chua.starter.pay.support.event;

import com.chua.starter.pay.support.entity.PayMerchantTransferRecord;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

/**
 * 完成转账记录事件
 * @author CH
 * @since 2025/10/15 11:09
 */
@Getter
@Setter
public class FinishTransferRecordEvent extends ApplicationEvent {
    public FinishTransferRecordEvent(Object source) {
        super(source);
    }

    private PayMerchantTransferRecord payMerchantTransferRecord;
}
