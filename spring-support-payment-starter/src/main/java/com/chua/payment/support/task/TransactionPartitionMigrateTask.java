package com.chua.payment.support.task;

import com.chua.payment.support.service.OrderPartitionConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
/**
 * 交易流水分表迁移任务
 * @author CH
 * @since 4.0.0.51
 */

@Component
@RequiredArgsConstructor
public class TransactionPartitionMigrateTask implements PaymentManagedTask {
    /**
     * 分表配置服务
     */
    private final OrderPartitionConfigService orderPartitionConfigService;
    @Override public String taskKey() { return "payment.transaction.partition.migrate"; }
    @Override public String taskName() { return "交易流水主表迁移"; }
    @Override public String defaultCron() { return "0 30 3 * * ?"; }
    @Override public String description() { return "把交易流水主表超过阈值的历史数据迁移到目标分表"; }
    @Override public void execute() { orderPartitionConfigService.executeAutoMigrate("TRANSACTION"); }
}
