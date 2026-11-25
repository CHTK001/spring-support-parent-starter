package com.chua.payment.support.task;

import com.chua.payment.support.service.OrderPartitionConfigService;
import com.chua.starter.job.support.annotation.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
/**
 * 交易流水分表预创建任务
 * @author CH
 * @since 4.0.0.51
 */

@Slf4j
@Component
@ConditionalOnProperty(prefix = "plugin.payment.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class TransactionPartitionCreateTask implements PaymentManagedTask {
    /**
     * 分表配置服务
     */
    private final OrderPartitionConfigService orderPartitionConfigService;
    /**
     * 分表开关闸门
     */
    private final PaymentSplitTableGate paymentSplitTableGate;
    @Override public String taskKey() { return "payment.transaction.partition.create"; }
    @Override public String taskName() { return "交易流水分表预创建"; }
    @Override public String defaultCron() { return "0 0 3 * * ?"; }
    @Override public String description() { return "根据交易流水分表配置预创建未来分区表"; }
    @Override
    @Job(
            value = "payment.transaction.partition.create",
            cron = "0 0 3 * * ?",
            desc = "根据交易流水分表配置预创建未来分区表"
    )
    public void execute() {
        if (!paymentSplitTableGate.anyMerchantEnabled()) {
            log.info("[分表] 无商户启用支付分表，跳过交易流水分表预创建");
            return;
        }
        orderPartitionConfigService.executeAutoCreate("TRANSACTION");
    }
}
