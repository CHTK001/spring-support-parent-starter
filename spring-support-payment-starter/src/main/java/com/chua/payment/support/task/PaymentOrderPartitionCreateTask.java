package com.chua.payment.support.task;

import com.chua.payment.support.service.OrderPartitionConfigService;
import com.chua.starter.job.support.annotation.Job;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
/**
 * 订单分表预创建任务
 * @author CH
 * @since 4.0.0.51
 */

@Component
@ConditionalOnProperty(prefix = "plugin.payment.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PaymentOrderPartitionCreateTask implements PaymentManagedTask {
    /**
     * 分表配置服务
     */
    private final OrderPartitionConfigService orderPartitionConfigService;
    @Override public String taskKey() { return "payment.order.partition.create"; }
    @Override public String taskName() { return "订单分表预创建"; }
    @Override public String defaultCron() { return "0 0 2 * * ?"; }
    @Override public String description() { return "根据订单分表配置预创建未来分区表"; }
    @Override
    @Job(
            value = "payment.order.partition.create",
            cron = "0 0 2 * * ?",
            desc = "根据订单分表配置预创建未来分区表"
    )
    public void execute() { orderPartitionConfigService.executeAutoCreate("ORDER"); }
}
