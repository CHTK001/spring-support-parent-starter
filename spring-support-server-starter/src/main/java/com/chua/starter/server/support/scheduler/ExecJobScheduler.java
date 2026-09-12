package com.chua.starter.server.support.scheduler;

import com.chua.starter.server.support.service.ExecService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 批量计划任务调度器
 * <p>
 * 由 {@link ServerTaskScheduler} 时间轮驱动，每 30 秒扫描一次到期任务
   * （ms_执行_作业.下一个_运行_时间），到期即触发执行并回写 最后一个/下一个 运行时间。
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
     * 扫描到期任务
     */
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
