package com.chua.starter.spider.support.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * lay-task 进度上报集成（可选依赖）。
 *
 * <p>若 lay-task 模块可用，则通过其 API 上报爬虫任务进度；
 * 否则静默跳过，不影响爬虫执行。</p>
 *
 * <p>使用方式：在 SpiderExecutionEngine 中通过
 * {@code @Autowired(required = false)} 注入此 Bean，
 * 调用前先判断是否为 null。</p>
 *
 * @author CH
 */
@Slf4j
@Component
public class SpiderTaskProgressReporter {

    /**
     * lay-task 进度服务（可选，若未引入 lay-task 则为 null）。
     * 使用 Object 类型避免编译期依赖 lay-task 模块。
     */
    @Autowired(required = false)
    private Object layTaskProgressService;

    /**
     * 上报任务进度。
     *
     * @param taskId   爬虫任务 ID
     * @param current  当前完成数
     * @param total    总数（-1 表示未知）
     * @param message  进度描述
     */
    public void report(Long taskId, long current, long total, String message) {
        if (layTaskProgressService == null) {
            log.debug("[Spider][Progress] lay-task 未集成，跳过进度上报 taskId={} {}/{} {}",
                    taskId, current, total, message);
            return;
        }
        try {
            // 通过反射调用 lay-task 进度 API，避免硬依赖
            layTaskProgressService.getClass()
                    .getMethod("report", Long.class, long.class, long.class, String.class)
                    .invoke(layTaskProgressService, taskId, current, total, message);
        } catch (Exception e) {
            log.debug("[Spider][Progress] lay-task 进度上报失败（可忽略）: {}", e.getMessage());
        }
    }

    /** 是否已集成 lay-task */
    public boolean isAvailable() {
        return layTaskProgressService != null;
    }
}
