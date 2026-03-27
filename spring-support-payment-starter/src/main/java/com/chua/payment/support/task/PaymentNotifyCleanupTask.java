package com.chua.payment.support.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.entity.PaymentNotifyError;
import com.chua.payment.support.mapper.PaymentNotifyErrorMapper;
import com.chua.starter.job.support.annotation.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 回调异常清理任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.payment.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentNotifyCleanupTask implements PaymentManagedTask {

    private final PaymentNotifyErrorMapper notifyErrorMapper;

    public void cleanResolvedErrors() {
        log.info("开始清理已解决的回调异常记录");

        try {
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            int deleted = notifyErrorMapper.delete(new LambdaQueryWrapper<PaymentNotifyError>()
                    .eq(PaymentNotifyError::getStatus, "RESOLVED")
                    .lt(PaymentNotifyError::getResolvedAt, thirtyDaysAgo));

            log.info("清理了 {} 条已解决的回调异常记录", deleted);
        } catch (Exception e) {
            log.error("清理回调异常记录失败", e);
        }
    }

    @Override
    public String taskKey() {
        return "payment-notify-cleanup";
    }

    @Override
    public String taskName() {
        return "回调异常清理";
    }

    @Override
    public String defaultCron() {
        return "0 0 2 * * ?";
    }

    @Override
    public String description() {
        return "清理已解决且超过保留期的回调异常记录。";
    }

    @Override
    @Job(
            value = "payment-notify-cleanup",
            scheduleType = "cron",
            scheduleTime = "0 0 2 * * ?",
            desc = "清理已解决且超过保留期的回调异常记录。",
            autoStart = true
    )
    public void execute() {
        cleanResolvedErrors();
    }
}
