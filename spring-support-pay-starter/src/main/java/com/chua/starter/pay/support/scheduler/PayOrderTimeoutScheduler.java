package com.chua.starter.pay.support.scheduler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单超时检测机制
 * @author CH
 * @since 2024/12/31
 */
@Slf4j
@SpringBootConfiguration
@RequiredArgsConstructor
public class PayOrderTimeoutScheduler {

    private final PayMerchantOrderService payMerchantOrderService;

    @Scheduled(cron = "0 1 * * * ?")
    public void execute() {
        log.info("超时检测机制开始扫描");
        try {
            scan();
        } catch (Exception ignored) {
        }
        log.info("超时检测机制结束扫描");
    }

    private void scan() {
        Thread.ofVirtual()
                .name("订单超时检测机制")
                .start(() -> {
                    try {
                        payMerchantOrderService.update(Wrappers.<PayMerchantOrder>lambdaUpdate()
                                .set(PayMerchantOrder::getPayMerchantOrderStatus, "3000")
                                .eq(PayMerchantOrder::getPayMerchantOrderStatus, "1000")
                                .le(PayMerchantOrder::getCreateTime, LocalDateTime.now())
                                .ge(PayMerchantOrder::getCreateTime, LocalDateTime.now().minusHours(1).minusMinutes(3)));
                    } catch (Exception ignored) {
                    }
                });
    }

}
