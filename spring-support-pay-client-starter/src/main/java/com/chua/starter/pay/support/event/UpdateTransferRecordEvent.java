package com.chua.starter.pay.support.event;

import com.chua.starter.pay.support.entity.PayMerchantTransferRecord;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

/**
 * 创建转账记录事件
 * @author CH
 * @since 2025/10/15 11:09
 */
@Getter
@Setter
public class UpdateTransferRecordEvent extends ApplicationEvent {
    public UpdateTransferRecordEvent(Object source) {
        super(source);
    }

    private PayMerchantTransferRecord payMerchantTransferRecord;
}
