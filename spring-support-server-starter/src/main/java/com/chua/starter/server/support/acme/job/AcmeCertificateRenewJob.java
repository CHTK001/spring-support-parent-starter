package com.chua.starter.server.support.acme.job;

import com.chua.starter.server.support.acme.config.AcmeProperties;
import com.chua.starter.server.support.acme.service.AcmeCertificateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ACME 证书自动续签定时任务
 * 每天凌晨 2 点检查即将到期的证书并自动续签
 *
 * @since 2025-12-06
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "monitor.acme", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AcmeCertificateRenewJob {

    /**
     * Acme证书服务
    */
    private final AcmeCertificateService acmeCertificateService;
    /**
     * Acme属性
    */
    private final AcmeProperties acmeProperties;

    /**
     * 定时检查并续签即将到期的证书
     * 默认每天凌晨 2 点执行
     */
    @Scheduled(cron = "${monitor.acme.renew-cron:0 0 2 * * ?}")
    public void renewExpiringSoonCertificates() {
        if (!acmeProperties.isEnabled()) {
            log.debug("[ACME] 证书自动续签功能已禁用");
            return;
        }

        log.info("[ACME] 开始执行证书自动续签检查任务，续签阈值: {}天", acmeProperties.getRenewDaysBefore());
        try {
            acmeCertificateService.checkAndRenewSoonExpired();
            log.info("[ACME] 证书自动续签检查任务执行完成");
        } catch (Exception e) {
            log.error("[ACME] 证书自动续签检查任务执行失败", e);
        }
    }
}
