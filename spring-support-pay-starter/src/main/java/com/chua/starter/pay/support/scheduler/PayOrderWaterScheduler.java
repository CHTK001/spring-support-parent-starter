package com.chua.starter.pay.support.scheduler;

import com.chua.starter.pay.support.service.PayMerchantOrderWaterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 流水表生成器
 * @author CH
 * @since 2024/12/31
 */
@Slf4j
@SpringBootConfiguration
@RequiredArgsConstructor
public class PayOrderWaterScheduler {

    private final PayMerchantOrderWaterService payMerchantOrderWaterService;

    @Scheduled(cron = "0 1 * * * ?")
    public void execute() {
        log.info("流水表检测开始扫描");
        try {
            scan();
        } catch (Exception ignored) {
        }
        log.info("流水表检测机制结束扫描");
    }

    private void scan() {
        Thread.ofVirtual()
                .name("流水表检测机制")
                .start(() -> {
                    payMerchantOrderWaterService.createNewTable();
                });
    }

}
