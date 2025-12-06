package com.chua.tenant.support.server.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.tenant.support.entity.SysTenant;
import com.chua.tenant.support.entity.SysTenantService;
import com.chua.tenant.support.server.mapper.SysTenantMapper;
import com.chua.tenant.support.server.mapper.SysTenantServiceMapper;
import com.chua.tenant.support.server.notify.TenantNotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 租户服务到期检测定时任�?
 * <p>
 * 定时检测租户订阅的服务是否即将到期或已到期，并发送通知
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.tenant.task", name = "expire-check-enabled", havingValue = "true", matchIfMissing = true)
public class TenantServiceExpireTask {

    /**
     * 到期前提醒天�?
     */
    private static final int REMIND_DAYS_BEFORE = 7;

    /**
     * 到期后宽限天�?
     */
    private static final int GRACE_PERIOD_DAYS = 3;

    private final SysTenantMapper sysTenantMapper;
    private final SysTenantServiceMapper sysTenantServiceMapper;
    private final TenantNotifyService tenantNotifyService;

    /**
     * 每天凌晨2点执行服务到期检�?
     */
    @Scheduled(cron = "${plugin.tenant.task.expire-check-cron:0 0 2 * * ?}")
    public void checkServiceExpire() {
        log.info("[租户服务到期检测] 开始执行服务到期检测任�?);

        try {
            LocalDate today = LocalDate.now();
            LocalDate remindDate = today.plusDays(REMIND_DAYS_BEFORE);

            // 查询即将到期的服务（7天内�?
            List<SysTenantService> expiringSoon = sysTenantServiceMapper.selectList(
                    Wrappers.<SysTenantService>lambdaQuery()
                            .le(SysTenantService::getSysTenantServiceValidTime, remindDate)
                            .gt(SysTenantService::getSysTenantServiceValidTime, today)
            );
            log.info("[租户服务到期检测] 即将到期服务数量: {}", expiringSoon.size());

            // 查询已到期的服务
            List<SysTenantService> expired = sysTenantServiceMapper.selectList(
                    Wrappers.<SysTenantService>lambdaQuery()
                            .le(SysTenantService::getSysTenantServiceValidTime, today)
            );
            log.info("[租户服务到期检测] 已到期服务数�? {}", expired.size());

            // 处理即将到期的服�?
            processExpiringSoon(expiringSoon, today);

            // 处理已到期的服务
            processExpired(expired, today);

            log.info("[租户服务到期检测] 服务到期检测任务执行完�?);
        } catch (Exception e) {
            log.error("[租户服务到期检测] 服务到期检测任务执行失�?, e);
        }
    }

    /**
     * 处理即将到期的服�?
     *
     * @param expiringSoon 即将到期的服务列�?
     * @param today        当前日期
     */
    private void processExpiringSoon(List<SysTenantService> expiringSoon, LocalDate today) {
        if (expiringSoon.isEmpty()) {
            return;
        }

        // 按租户分�?
        Map<Integer, List<SysTenantService>> byTenant = expiringSoon.stream()
                .collect(Collectors.groupingBy(SysTenantService::getSysTenantId));

        for (Map.Entry<Integer, List<SysTenantService>> entry : byTenant.entrySet()) {
            Integer tenantId = entry.getKey();
            List<SysTenantService> services = entry.getValue();

            SysTenant tenant = sysTenantMapper.selectById(tenantId);
            if (tenant == null) {
                continue;
            }

            // 计算最早到期日�?
            LocalDate earliestExpire = services.stream()
                    .map(SysTenantService::getSysTenantServiceValidTime)
                    .min(LocalDate::compareTo)
                    .orElse(today);

            long daysUntilExpire = today.until(earliestExpire).getDays();

            log.info("[租户服务到期检测] 租户 {} �?{} 个服务将�?{} 天后到期",
                    tenant.getSysTenantName(), services.size(), daysUntilExpire);

            // 发送即将到期通知
            tenantNotifyService.notifyExpiringSoon(tenant, services, (int) daysUntilExpire);
        }
    }

    /**
     * 处理已到期的服务
     *
     * @param expired 已到期的服务列表
     * @param today   当前日期
     */
    private void processExpired(List<SysTenantService> expired, LocalDate today) {
        if (expired.isEmpty()) {
            return;
        }

        // 按租户分�?
        Map<Integer, List<SysTenantService>> byTenant = expired.stream()
                .collect(Collectors.groupingBy(SysTenantService::getSysTenantId));

        for (Map.Entry<Integer, List<SysTenantService>> entry : byTenant.entrySet()) {
            Integer tenantId = entry.getKey();
            List<SysTenantService> services = entry.getValue();

            SysTenant tenant = sysTenantMapper.selectById(tenantId);
            if (tenant == null) {
                continue;
            }

            // 计算最早到期日�?
            LocalDate earliestExpire = services.stream()
                    .map(SysTenantService::getSysTenantServiceValidTime)
                    .min(LocalDate::compareTo)
                    .orElse(today);

            long daysExpired = earliestExpire.until(today).getDays();

            log.warn("[租户服务到期检测] 租户 {} �?{} 个服务已到期 {} �?,
                    tenant.getSysTenantName(), services.size(), daysExpired);

            // 发送已到期通知
            tenantNotifyService.notifyExpired(tenant, services, (int) daysExpired);

            // 如果超过宽限期，禁用服务
            if (daysExpired > GRACE_PERIOD_DAYS) {
                log.warn("[租户服务到期检测] 租户 {} 服务已超过宽限期，执行禁用操�?,
                        tenant.getSysTenantName());
                tenantNotifyService.notifyServiceDisabled(tenant, services);
            }
        }
    }
}
