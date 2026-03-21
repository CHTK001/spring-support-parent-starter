package com.chua.payment.support.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.entity.PaymentNotifyError;
import com.chua.payment.support.mapper.PaymentNotifyErrorMapper;
import com.chua.payment.support.service.PaymentNotifyProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回调异常处理定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentNotifyRetryTask {

    private final PaymentNotifyErrorMapper notifyErrorMapper;
    private final PaymentNotifyProcessService notifyProcessService;

    /**
     * 每5分钟执行一次，重试失败的回调
     */
    @Scheduled(cron = "0 */5 * * * ?")
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

    /**
     * 每天凌晨2点执行一次，清理已解决的异常记录（保留30天）
     */
    @Scheduled(cron = "0 0 2 * * ?")
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
}
