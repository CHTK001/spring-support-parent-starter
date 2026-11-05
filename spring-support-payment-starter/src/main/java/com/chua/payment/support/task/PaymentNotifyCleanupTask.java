// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.payment.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentNotifyCleanupTask implements PaymentManagedTask {

    /**
     * 通知错误映射器
     */
    private final PaymentNotifyErrorMapper notifyErrorMapper;

    /**
     * 清理已解决的回调异常记录
     */
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
    /**
     * 任务标识
     * @return 返回值
    */
    public String taskKey() {
        return "payment-notify-cleanup";
    }

    @Override
    /**
     * 任务名称
     * @return 返回值
    */
    public String taskName() {
        return "回调异常清理";
    }

    @Override
    /**
     * 默认 cron 表达式
     * @return 返回值
    */
    public String defaultCron() {
        return "0 0 2 * * ?";
    }

    @Override
    /**
     * 任务说明
     * @return 返回值
    */
    public String description() {
        return "清理已解决且超过保留期的回调异常记录";
    }

    @Override
    @Job(
            value = "payment-notify-cleanup",
            cron = "0 0 2 * * ?",
            desc = "清理已解决且超过保留期的回调异常记录"
    )
    /**
     * 执行清理任务
     */
    public void execute() {
        cleanResolvedErrors();
    }

}