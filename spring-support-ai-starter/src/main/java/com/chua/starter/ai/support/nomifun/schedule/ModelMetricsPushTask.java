package com.chua.starter.ai.support.nomifun.schedule;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.nomifun.service.ModelMetricsSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * 模型价格/指标定时推送任务。
 *
 * <p>与 {@code UsageAggregationTask} 相同的动态 {@link Trigger} 调度范式：开关与间隔
 * 从 {@code ai_global_setting} 表 {@code global} 分组读取：</p>
 * <ul>
 *   <li>开关：{@code enable_model_metrics_push}（默认打开）</li>
 *   <li>间隔：{@code model_metrics_push_interval_sec}（秒，最小 30s，默认 300s）</li>
 * </ul>
 *
 * <p>每次执行调用 {@link ModelMetricsSnapshotService#buildAndPushSnapshot()}：
 * 聚合全量配置模型的价格/速度/延迟快照 → 写 Redis → 经 WebSocket 推送
 * {@code model.metrics.push} 主题。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RequiredArgsConstructor
public class ModelMetricsPushTask implements SchedulingConfigurer {

    /**
     * 全局设置分组
     */
    private static final String GROUP = "global";

    /**
     * 未配置时的默认执行间隔（秒）
     */
    private static final long DEFAULT_INTERVAL_SEC = 300;

    /**
     * 最小执行间隔（秒），防止配置过小导致频繁聚合
     */
    private static final long MIN_INTERVAL_SEC = 30;

    /**
     * 全局设置读取器
     */
    private final GlobalSettingReader settingReader;

    /**
     * 模型价格/指标快照服务
     */
    private final ModelMetricsSnapshotService snapshotService;

    /**
     * 注册动态调度任务，每次执行后按配置间隔计算下次执行时间。
     *
     * @param taskRegistrar 调度任务注册器
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        Trigger trigger = this::nextTriggerTime;
        taskRegistrar.addTriggerTask(this::run, trigger);
    }

    /**
     * 调度入口：先校验开关，关闭时跳过。
     */
    public void run() {
        if (!isEnabled()) {
            log.debug("[ModelMetrics] 模型指标推送未启用，跳过本次执行");
            return;
        }
        try {
            snapshotService.buildAndPushSnapshot();
            log.info("[ModelMetrics] 价格/指标快照已推送完成");
        } catch (Exception e) {
            log.warn("[ModelMetrics] 快照推送失败: {}", e.getMessage());
        }
    }

    /**
     * 计算下次执行时间：读取全局配置间隔（最小 30s），未配置时使用默认值。
     *
     * @param triggerContext 触发器上下文
     * @return 下次执行时间
     */
    private Instant nextTriggerTime(TriggerContext triggerContext) {
        long intervalSec = settingReader.getInt(GROUP, "model_metrics_push_interval_sec", (int) DEFAULT_INTERVAL_SEC);
        if (intervalSec < MIN_INTERVAL_SEC) {
            intervalSec = MIN_INTERVAL_SEC;
        }
        java.util.Date last = triggerContext.lastScheduledExecutionTime();
        if (last == null) {
            return Instant.now().plusMillis(TimeUnit.SECONDS.toMillis(intervalSec));
        }
        return last.toInstant().plusMillis(TimeUnit.SECONDS.toMillis(intervalSec));
    }

    /**
     * 判断模型指标推送是否启用（global 分组 enable_model_metrics_push，默认启用）。
     *
     * @return true 表示启用
     */
    private boolean isEnabled() {
        return settingReader.getBool(GROUP, "enable_model_metrics_push", true);
    }
}