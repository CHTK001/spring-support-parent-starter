package com.chua.starter.server.support.scheduler;

import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsExecJob;
import com.chua.starter.server.support.event.ExecJobChangedEvent;
import com.chua.starter.server.support.service.ExecService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 批量计划任务调度器（TimeScheduler 逐任务 CronTrigger）
 * <p>
 * 采用 Spring {@link ThreadPoolTaskScheduler} 为每个「定时(CRON)」任务
 * 注册独立的 {@link CronTrigger}，cron 到点即触发，精度不受扫描周期限制；
 * 任务增删改 / 启停通过 {@link ExecJobChangedEvent} 事件同步（就地重排或取消），
 * 避免与 {@link ExecService} 直接依赖造成循环引用。
 * </p>
 * <p>
 * 「脚本(NONE)」类型与已停用任务不注册；固定周期任务（在离线检测/采集/会话清理等）
 * 仍由 {@link ServerTaskScheduler} 的哈希时间轮负责。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecJobScheduler {

    /**
     * 批量执行服务
     */
    private final ExecService execService;

    /**
     * 逐任务 Cron 调度线程池
     */
    private ThreadPoolTaskScheduler taskScheduler;

    /**
     * jobId → 调度句柄
     */
    private final Map<Long, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    /**
     * 启动：初始化调度线程池并加载全部定时任务
     */
    @PostConstruct
    public void start() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(2);
        taskScheduler.setThreadNamePrefix("exec-cron-");
        taskScheduler.setRemoveOnCancelPolicy(true);
        taskScheduler.setWaitForTasksToCompleteOnShutdown(false);
        taskScheduler.initialize();
        rescheduleAll();
        log.info("[exec-cron] 计划任务调度器已启动(CronTrigger 逐任务)");
    }

    /**
     * 关闭：停止调度线程池
     */
    @PreDestroy
    public void stop() {
        if (taskScheduler != null) {
            taskScheduler.shutdown();
            taskScheduler = null;
        }
        futures.clear();
    }

    /**
     * 任务变更事件：新增/更新重排，删除取消
     *
     * @param event 变更事件
     */
    @EventListener
    public void onJobChanged(ExecJobChangedEvent event) {
        if (event.deleted()) {
            cancel(event.jobId());
            return;
        }
        var result = execService.getJob(event.jobId());
        if (result.isOk() && result.getData() != null) {
            schedule(result.getData());
        } else {
            cancel(event.jobId());
        }
    }

    /**
     * 加载全部任务并按需注册
     */
    private void rescheduleAll() {
        var result = execService.listJobs();
        List<MsExecJob> jobs = result.isOk() ? result.getData() : null;
        if (jobs == null) {
            return;
        }
        for (MsExecJob job : jobs) {
            schedule(job);
        }
    }

    /**
     * 注册/重排单个任务（非定时、未启用或 cron 非法则跳过/取消）
     *
     * @param job 任务
     */
    public void schedule(MsExecJob job) {
        if (job == null || job.getId() == null) {
            return;
        }
        cancel(job.getId());
        if (job.getMsExecJobEnabled() == null || job.getMsExecJobEnabled() != 1) {
            return;
        }
        String type = job.getMsExecJobType();
        boolean cron = type == null || type.isBlank() || ServerConstants.EXEC_JOB_TYPE_CRON.equalsIgnoreCase(type.trim());
        if (!cron || job.getMsExecJobCron() == null || job.getMsExecJobCron().isBlank()) {
            return;
        }
        try {
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> execService.runScheduledJob(job.getId()),
                    new CronTrigger(job.getMsExecJobCron()));
            if (future != null) {
                futures.put(job.getId(), future);
            }
        } catch (IllegalArgumentException e) {
            log.warn("[exec-cron] cron 非法，跳过任务 jobId={} cron={}: {}",
                    job.getId(), job.getMsExecJobCron(), e.getMessage());
        }
    }

    /**
     * 取消任务调度
     *
     * @param jobId 任务主键
     */
    public void cancel(Long jobId) {
        if (jobId == null) {
            return;
        }
        ScheduledFuture<?> future = futures.remove(jobId);
        if (future != null) {
            future.cancel(false);
        }
    }
}
