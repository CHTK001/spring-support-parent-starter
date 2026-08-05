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

    /** 默认调度间隔(毫秒) */
    private static final long DEFAULT_INTERVAL_MS = 10_000L;
    /** 最大并发数 */
    private static final int CONCURRENCY = 50;

    /** 任务 Service */
    private final MonitorTaskService taskService;
    /** 资产 Service */
    private final MonitorService monitorService;
    /** 指标 Service */
    private final MetricService metricService;
    /** 配置 Service */
    private final MonitorConfigService configService;
    /** Provider 注册中心 */
    private final ProviderRegistry providerRegistry;

    /**
     * 调度入口, 每 10 秒执行一次
     */
    @Scheduled(fixedRate = 10_000)
    public void tick() {
        List<MsMonitorTask> tasks = taskService.listEnabled().getData();
        if (tasks == null || tasks.isEmpty()) {
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

    /**
     * 判断任务是否到达触发时间
     *
     * @param task 任务
     * @param now  当前时间
     * @return true 表示应触发
     */
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

    /**
     * 执行采集任务
     *
     * @param task 任务
     * @return 响应式采集结果
     */
    private reactor.core.publisher.Mono<CollectResult> execute(MsMonitorTask task) {
        ServiceType serviceType = ServiceType.of(task.getServiceType());
        ProviderType providerType = ProviderType.of(task.getProvider());
        Optional<MetricProvider> providerOpt = providerRegistry.resolveMetric(serviceType, providerType);
        if (providerOpt.isEmpty()) {
            log.warn("[MonitorScheduler] 未找到 Provider service={} provider={} task={}",
                    serviceType, providerType, task.getId());
            return reactor.core.publisher.Mono.empty();
        }
        MsMonitor monitor = monitorService.findById(task.getMonitorId()).getData();
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
                    metricService.writeCollectResult(result);
                    return reactor.core.publisher.Mono.just(result);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}