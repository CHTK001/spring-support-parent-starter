package com.chua.payment.support.task;

import com.chua.payment.support.service.OrderPartitionConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionPartitionCreateTask implements PaymentManagedTask {
    private final OrderPartitionConfigService orderPartitionConfigService;
    @Override public String taskKey() { return "payment.transaction.partition.create"; }
    @Override public String taskName() { return "交易流水分表预创建"; }
    @Override public String defaultCron() { return "0 0 3 * * ?"; }
    @Override public String description() { return "根据交易流水分表配置预创建未来分区表"; }
    @Override public void execute() { orderPartitionConfigService.executeAutoCreate("TRANSACTION"); }
}
