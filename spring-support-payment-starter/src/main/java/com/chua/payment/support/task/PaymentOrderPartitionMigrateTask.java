package com.chua.payment.support.task;

import com.chua.payment.support.service.OrderPartitionConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
/**
 * 订单分表迁移任务
 * @author CH
 */

@Component
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
    @Override public void execute() { orderPartitionConfigService.executeAutoMigrate("ORDER"); }
}
