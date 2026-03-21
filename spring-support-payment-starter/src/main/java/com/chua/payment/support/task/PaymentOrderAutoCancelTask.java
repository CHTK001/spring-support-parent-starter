package com.chua.payment.support.task;

import com.chua.payment.support.service.PaymentOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付订单自动关单任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOrderAutoCancelTask {

    private final PaymentOrderService paymentOrderService;

    @Scheduled(cron = "${payment.order.auto-cancel-cron:0 0/1 * * * ?}")
    public void autoCancelTimeoutOrders() {
        try {
            paymentOrderService.autoCancelTimeoutOrders();
        } catch (Exception e) {
            log.error("支付订单自动关单任务执行失败", e);
        }
    }
}
