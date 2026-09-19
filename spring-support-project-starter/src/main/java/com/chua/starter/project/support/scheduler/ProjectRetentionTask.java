package com.chua.starter.project.support.scheduler;

import com.chua.starter.project.support.config.ProjectProperties;
import com.chua.starter.project.support.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 部署日志保留清理任务
 * <p>
 * 背景：{@code ms_project_deploy_log} 每次部署/启停/守护探测都会写入若干行，且带完整命令输出。
 * 项目数与主机数一多，单表会持续增长（多项目 × 多主机 × 每日多次），而删项目才级联清理 ——
 * 缺少保留策略。指标表 {@code ms_project_metric} 已有同类清理（见 ProjectMetricWheel），
 * 本类补齐部署日志侧，保持两者一致。
 * </p>
 * <p>
 * 保留天数与执行间隔分别取 {@code project.support.deploy-log-retention-days}
 * 与 {@code project.support.deploy-log-cleanup-interval-seconds}；
 * 任一项 &lt;=0 即不启用（保留天数 &lt;=0 表示永久保留，间隔 &lt;=0 表示不定时执行）。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectRetentionTask {

    /** 项目服务（提供超期部署日志清理） */
    private final ProjectService projectService;

    /** 模块配置（保留天数 / 清理间隔） */
    private final ProjectProperties projectProperties;

    /** 清理执行线程（单线程，避免并发清理与业务写入互相争锁） */
    private ScheduledExecutorService executor;

    /**
     * 应用就绪后启动定期清理
     * <p>
     * 首次执行延迟一个间隔，避免与应用启动时的其它初始化争抢数据库连接。
     * </p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        int retentionDays = projectProperties.getDeployLogRetentionDays();
        int intervalSec = projectProperties.getDeployLogCleanupIntervalSeconds();
        if (retentionDays <= 0 || intervalSec <= 0) {
            log.info("[project] 部署日志定期清理未启用（保留天数={}, 间隔={}s）", retentionDays, intervalSec);
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "project-log-retention");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(() -> {
            try {
                projectService.cleanupDeployLogs();
            } catch (Exception e) {
                // 清理失败不能影响主流程，下一轮重试
                log.warn("[project] 部署日志定期清理异常: {}", e.getMessage());
            }
        }, intervalSec, intervalSec, TimeUnit.SECONDS);
        log.info("[project] 部署日志定期清理已启动：保留 {} 天，每 {}s 执行一次", retentionDays, intervalSec);
    }
}
