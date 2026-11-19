package com.chua.payment.support.task;

import com.chua.payment.support.service.OrderPartitionConfigService;
import com.chua.starter.job.support.annotation.Job;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
/**
 * 订单分表迁移任务
 * @author CH
 * @since 4.0.0.51
 */

@Component
@ConditionalOnProperty(prefix = "plugin.payment.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PaymentOrderPartitionMigrateTask implements PaymentManagedTask {
    /**
     * 分表配置服务
     */
    private final OrderPartitionConfigService orderPartitionConfigService;
    @Override public String taskKey() { return "payment.order.partition.migrate"; }
    @Override public String taskName() { return "订单主表迁移"; }
    @Override public String defaultCron() { return "0 30 2 * * ?"; }
    @Override public String description() { return "把订单主表超过阈值的历史数据迁移到目标分表"; }
    @Override
    @Job(
            value = "payment.order.partition.migrate",
            cron = "0 30 2 * * ?",
            desc = "把订单主表超过阈值的历史数据迁移到目标分表"
    )
    public void execute() { orderPartitionConfigService.executeAutoMigrate("ORDER"); }
}
