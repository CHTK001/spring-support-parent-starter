package com.chua.payment.support.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.entity.PaymentNotifyError;
import com.chua.payment.support.mapper.PaymentNotifyErrorMapper;
import com.chua.payment.support.service.PaymentNotifyProcessService;
import com.chua.starter.job.support.annotation.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
* 回调异常处理定时任务
* @author CH
* @since 4.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.payment.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentNotifyRetryTask implements PaymentManagedTask {

    /** 通知错误映射器 */
    private final PaymentNotifyErrorMapper notifyErrorMapper;
    /** Notifyprocess服务 */
    private final PaymentNotifyProcessService notifyProcessService;

    /** 重试失败通知 */
    public void retryFailedNotifies() {
        log.info("开始执行回调异常重试任务");

        try {
            List<PaymentNotifyError> errors = notifyErrorMapper.selectList(new LambdaQueryWrapper<PaymentNotifyError>()
                    .eq(PaymentNotifyError::getStatus, "PENDING")
                    .le(PaymentNotifyError::getNextRetryTime, LocalDateTime.now())
                    .lt(PaymentNotifyError::getRetryCount, 5)
                    .last("limit 100"));

            log.info("找到 {} 条待重试的回调异常记录", errors.size());

            for (PaymentNotifyError error : errors) {
                try {
                    notifyProcessService.retryFailedNotify(error.getId());
                } catch (Exception e) {
                    log.error("重试回调异常失败, errorId={}", error.getId(), e);
                }
            }

            log.info("回调异常重试任务执行完成");
        } catch (Exception e) {
            log.error("回调异常重试任务执行失败", e);
        }
    }

    @Override
    /** 任务钥匙 */
    public String taskKey() {
        return "payment-notify-retry";
    }

    @Override
    /** 任务名称 */
    public String taskName() {
        return "回调异常重试";
    }

    @Override
    /** 默认cron */
    public String defaultCron() {
        return "0 */5 * * * ?";
    }

    @Override
    /** Description */
    public String description() {
        return "扫描待重试的支付回调异常并自动回放。";
    }

    @Override
    @Job(
            value = "payment-notify-retry",
            cron = "0 */5 * * * ?",
            desc = "扫描待重试的支付回调异常并自动回放。"
    )
    /** 执行 */
    public void execute() {
        /**
        * 重试失败通知。
         */
        retryFailedNotifies();
    }
}

