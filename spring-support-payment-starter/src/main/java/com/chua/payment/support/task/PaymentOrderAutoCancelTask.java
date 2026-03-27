package com.chua.payment.support.task;

import com.chua.payment.support.service.PaymentOrderService;
import com.chua.starter.job.support.annotation.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 支付订单自动关单任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.payment.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentOrderAutoCancelTask implements PaymentManagedTask {

    private final PaymentOrderService paymentOrderService;

    public void autoCancelTimeoutOrders() {
        try {
            paymentOrderService.autoCancelTimeoutOrders();
        } catch (Exception e) {
            log.error("支付订单自动关单任务执行失败", e);
        }
    }

    @Override
    public String taskKey() {
        return "payment-order-auto-cancel";
    }

    @Override
    public String taskName() {
        return "订单自动关单";
    }

    @Override
    public String defaultCron() {
        return "0 0/1 * * * ?";
    }

    @Override
    public String description() {
        return "根据订单 expireTime 自动关闭仍未支付的订单。";
    }

    @Override
    @Job(
            value = "payment-order-auto-cancel",
            scheduleType = "cron",
            scheduleTime = "0 0/1 * * * ?",
            desc = "根据订单 expireTime 自动关闭仍未支付的订单。",
            autoStart = true
    )
    public void execute() {
        autoCancelTimeoutOrders();
    }
}
