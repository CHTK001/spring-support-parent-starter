package com.chua.starter.monitor.support.scheduler;

import com.chua.common.support.task.scheduler.CronTrigger;
import com.chua.common.support.task.scheduler.Trigger;
import com.chua.starter.monitor.support.entity.MsMonitor;
import com.chua.starter.monitor.support.entity.MsMonitorTask;
import com.chua.starter.monitor.support.enums.ProviderType;
import com.chua.starter.monitor.support.enums.ServiceType;
import com.chua.starter.monitor.support.registry.ProviderRegistry;
import com.chua.starter.monitor.support.service.MetricService;
import com.chua.starter.monitor.support.service.MonitorConfigService;
import com.chua.starter.monitor.support.service.MonitorService;
import com.chua.starter.monitor.support.service.MonitorTaskService;
import com.chua.starter.monitor.support.spi.CollectContext;
import com.chua.starter.monitor.support.spi.CollectResult;
import com.chua.starter.monitor.support.spi.MetricProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 监控采集调度器
 * <p>
 * 每 10 秒扫描启用的任务, 通过 Trigger 判断任务是否到达触发时间,
 * 使用 Reactor 背压控制采集并发与入库压力。
 * </p>
 *
 * @author CH
 * @since 2026/8/4
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorScheduler {

    private static final long DEFAULT_INTERVAL_MS = 10_000L;
    private static final int CONCURRENCY = 50;

    private final MonitorTaskService taskService;
    private final MonitorService monitorService;
    private final MetricService metricService;
    private final MonitorConfigService configService;
    private final ProviderRegistry providerRegistry;

    @Scheduled(fixedRate = 10_000)
    public void tick() {
        List<MsMonitorTask> tasks = taskService.listEnabled();
        if (tasks.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Flux.fromIterable(tasks)
                .filter(task -> isTriggered(task, now))
                .flatMap(task -> execute(task), CONCURRENCY)
                .onBackpressureBuffer(10_000)
                .subscribe(
                        result -> log.debug("[MonitorScheduler] 采集完成 task={} points={}",
                                result.getTask().getId(), result.getPoints().size()),
                        error -> log.error("[MonitorScheduler] 采集异常", error)
                );
    }

    private boolean isTriggered(MsMonitorTask task, LocalDateTime now) {
        String cron = task.getMonitorTaskCron();
        if (cron == null || cron.isEmpty()) {
            return false;
        }
        try {
            Trigger trigger = new CronTrigger(cron);
            LocalDateTime lastRun = task.getLastRunTime();
            if (lastRun == null) {
                return true;
            }
            LocalDateTime next = trigger.nextExecutionTime(lastRun);
            return next != null && !next.isAfter(now);
        } catch (Exception e) {
            log.warn("[MonitorScheduler] cron 解析失败 task={} cron={}", task.getId(), cron);
            return false;
        }
    }

    private reactor.core.publisher.Mono<CollectResult> execute(MsMonitorTask task) {
        ServiceType serviceType = ServiceType.of(task.getServiceType());
        ProviderType providerType = ProviderType.of(task.getProvider());
        Optional<MetricProvider> providerOpt = providerRegistry.resolveMetric(serviceType, providerType);
        if (providerOpt.isEmpty()) {
            log.warn("[MonitorScheduler] 未找到 Provider service={} provider={} task={}",
                    serviceType, providerType, task.getId());
            return reactor.core.publisher.Mono.empty();
        }
        MsMonitor monitor = monitorService.findById(task.getMonitorId());
        if (monitor == null) {
            log.warn("[MonitorScheduler] 主机不存在 task={} monitorId={}", task.getId(), task.getMonitorId());
            return reactor.core.publisher.Mono.empty();
        }
        CollectContext context = CollectContext.builder()
                .task(task)
                .monitor(monitor)
                .build();
        return providerOpt.get().collect(context)
                .flatMap(result -> {
                    taskService.markLastRunTime(task.getId());
                    return metricService.writeCollectResult(result).thenReturn(result);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}