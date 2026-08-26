package com.chua.starter.server.support.scheduler;

import com.chua.starter.server.support.service.ExecService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 批量计划任务调度器
 * <p>
 * 每 30 秒扫描一次到期任务（ms_exec_job.next_run_time），
 * 到期即触发执行并回写 last/next 运行时间。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecJobScheduler {

    /** 批量执行服务 */
    private final ExecService execService;

    /**
     * 定时扫描到期任务
     */
    @Scheduled(fixedDelay = 30_000L, initialDelay = 60_000L)
    public void tick() {
        try {
            int fired = execService.tickDueJobs();
            if (fired > 0) {
                log.info("[exec] 本轮触发计划任务 {} 个", fired);
            }
        } catch (Exception e) {
            log.warn("[exec] 计划任务扫描异常: {}", e.getMessage());
        }
    }
}
