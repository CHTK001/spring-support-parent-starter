package com.chua.starter.ai.support.schedule;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.memory.MemoryGlobalSettingProvider;
import com.chua.starter.ai.support.model.AiMemoryMaintenanceDTO;
import com.chua.starter.ai.support.service.SysAiMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 记忆体维护任务 —— 让「记录—使用—衰减—清理」形成闭环的后台一环。
 *
 * <p>调度参数从设置分组 {@code memory} 现读，改完下一轮生效：</p>
 * <ul>
 *   <li>开关：{@code memory_maintenance_enabled}，关闭时本轮直接跳过</li>
 *   <li>间隔：{@code memory_maintenance_interval_minutes}，默认 60 分钟</li>
 * </ul>
 *
 * <p>按 {@code Trigger} 而非 {@code @Scheduled} 固定注册：间隔需要跟随设置变化，且必须以上一次
 * <b>完成</b>时间为基准，避免维护耗时超过间隔时出现重叠执行。</p>
 *
 * <p>每次执行逐用户完成过期落库、强度衰减落库、同义合并、低分归档与容量淘汰，
 * 并记录各动作条数供 {@code /api/memories/maintenance/latest} 回显取证。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryMaintenanceTask implements SchedulingConfigurer {

    /**
     * 维护开关键
     */
    private static final String SETTING_ENABLED = "memory_maintenance_enabled";

    /**
     * 维护间隔键（分钟）
     */
    private static final String SETTING_INTERVAL = "memory_maintenance_interval_minutes";

    /**
     * 默认维护间隔（分钟）
     */
    private static final int DEFAULT_INTERVAL_MINUTES = 60;

    /**
     * 全局设置读取器
     */
    private final GlobalSettingReader settingReader;

    /**
     * 记忆体服务
     */
    private final SysAiMemoryService memoryService;

    /**
     * 最近一次维护报告（供接口回显维护是否真的在跑）
     */
    private static final AtomicReference<AiMemoryMaintenanceDTO> LAST_REPORT = new AtomicReference<>();

    @Override
    /**
     * 注册维护调度。
     *
     * @param taskRegistrar 调度任务注册器
     */
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        Trigger trigger = context -> {
            Instant base = context.lastCompletionTime() != null
                    ? context.lastCompletionTime().toInstant() : Instant.now();
            return base.plusMillis(intervalMillis());
        };
        taskRegistrar.addTriggerTask(this::run, trigger);
    }

    /**
     * 执行一次全量维护：开关关闭时跳过，异常一律吃掉以免影响调度线程。
     */
    public void run() {
        if (!settingReader.getBool(MemoryGlobalSettingProvider.GROUP, SETTING_ENABLED, true)) {
            log.debug("[MemoryMaintenance] 维护开关关闭，跳过本次执行");
            return;
        }
        try {
            AiMemoryMaintenanceDTO report = memoryService.maintainAll();
            LAST_REPORT.set(report);
        } catch (Throwable e) {
            log.warn("[MemoryMaintenance] 维护执行异常（已捕获）: {}", e.getMessage());
        }
    }

    /**
     * 最近一次维护报告。
     *
     * @return 维护报告，尚未执行过时返回 {@code null}
     */
    public static AiMemoryMaintenanceDTO lastReport() {
        return LAST_REPORT.get();
    }

    /**
     * 当前配置的维护间隔（毫秒），最小 1 分钟。
     *
     * @return 间隔毫秒数
     */
    private long intervalMillis() {
        int minutes = settingReader.getInt(MemoryGlobalSettingProvider.GROUP, SETTING_INTERVAL, DEFAULT_INTERVAL_MINUTES);
        // [P3C A20 豁免]
        return Math.max(minutes, 1) * 60L * 1000L;
    }
}
