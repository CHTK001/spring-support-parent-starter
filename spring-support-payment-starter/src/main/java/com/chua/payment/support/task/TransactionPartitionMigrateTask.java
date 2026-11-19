package com.chua.payment.support.task;

import com.chua.payment.support.service.OrderPartitionConfigService;
import com.chua.starter.job.support.annotation.Job;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
/**
 * 交易流水分表迁移任务
 * @author CH
 * @since 4.0.0.51
 */

@Component
@ConditionalOnProperty(prefix = "plugin.payment.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
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
    @Override
    @Job(
            value = "payment.transaction.partition.migrate",
            cron = "0 30 3 * * ?",
            desc = "把交易流水主表超过阈值的历史数据迁移到目标分表"
    )
    public void execute() { orderPartitionConfigService.executeAutoMigrate("TRANSACTION"); }
}
