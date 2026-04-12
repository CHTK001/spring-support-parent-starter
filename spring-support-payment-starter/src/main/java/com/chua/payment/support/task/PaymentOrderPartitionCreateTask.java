package com.chua.payment.support.task;

import com.chua.payment.support.service.OrderPartitionConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentOrderPartitionCreateTask implements PaymentManagedTask {
    private final OrderPartitionConfigService orderPartitionConfigService;
    @Override public String taskKey() { return "payment.order.partition.create"; }
    @Override public String taskName() { return "订单分表预创建"; }
    @Override public String defaultCron() { return "0 0 2 * * ?"; }
    @Override public String description() { return "根据订单分表配置预创建未来分区表"; }
    @Override public void execute() { orderPartitionConfigService.executeAutoCreate("ORDER"); }
}
