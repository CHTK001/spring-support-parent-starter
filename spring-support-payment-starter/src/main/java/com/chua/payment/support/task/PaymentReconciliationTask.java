package com.chua.payment.support.task;

import com.chua.payment.support.dto.ReconciliationTriggerDTO;
import com.chua.payment.support.service.ReconciliationService;
import com.chua.starter.job.support.annotation.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 渠道对账定时任务：每日对昨日渠道账单执行对账。
 *
 * <p>逐渠道容错：单个渠道账单下载/解析失败仅登记 FAILED 对账单，不中断其他渠道对账。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.payment.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentReconciliationTask implements PaymentManagedTask {

    /**
     * 渠道对账服务
     */
    private final ReconciliationService reconciliationService;

    /**
     * 执行渠道对账（默认对昨日账单）
     */
    public void runReconciliation() {
        log.info("开始执行渠道对账任务");
        try {
            reconciliationService.reconcile(new ReconciliationTriggerDTO(null, null, null));
            log.info("渠道对账任务执行完毕");
        } catch (Exception e) {
            log.error("渠道对账任务执行失败", e);
        }
    }

    @Override
    /**
     * 任务标识
     * @return 返回值
     */
    public String taskKey() {
        return "payment-reconciliation";
    }

    @Override
    /**
     * 任务名称
     * @return 返回值
     */
    public String taskName() {
        return "渠道对账";
    }

    @Override
    /**
     * 默认 cron 表达式
     * @return 返回值
     */
    public String defaultCron() {
        return "0 0 4 * * ?";
    }

    @Override
    /**
     * 任务说明
     * @return 返回值
     */
    public String description() {
        return "每日下载渠道账单并与本地支付流水比对，生成对账单与差异明细";
    }

    @Override
    @Job(
            value = "payment-reconciliation",
            cron = "0 0 4 * * ?",
            desc = "每日下载渠道账单并与本地支付流水比对，生成对账单与差异明细"
    )
    /**
     * 执行对账任务
     */
    public void execute() {
        runReconciliation();
    }

}
