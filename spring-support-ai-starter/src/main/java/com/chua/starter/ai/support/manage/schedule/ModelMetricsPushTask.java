package com.chua.starter.ai.support.manage.schedule;

import com.chua.common.support.datasearch.pricing.PricingSyncer;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.manage.service.ModelMetricsSnapshotService;
import com.chua.starter.ai.support.manage.service.SysAiModelMetricsService;
import com.chua.starter.ai.support.manage.service.SysAiProviderMetricService;
import com.chua.starter.ai.support.service.SysAiPricingService;
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
 * <p>每次执行：</p>
 * <ol>
 *   <li>调用 {@link ModelMetricsSnapshotService#buildAndPushSnapshot()}：
 *       聚合全量配置模型的价格/速度/延迟快照 → 写 Redis</li>
 *   <li>调用 {@link SysAiPricingService#syncOfficialPricing()}：
 *       从 datasearch-starter 加载全量官方定价 → 清空旧数据 → 全量插入 sys_ai_pricing 表，
 *       供用量统计在无金额时回退使用官方金额</li>
 * </ol>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
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
     * datasearch 数据源在线刷新间隔设置键（分钟，0 = 不刷新）
     */
    private static final String SETTING_SOURCE_REFRESH_INTERVAL_MIN =
            "model_metrics_source_refresh_interval_min";

    /**
     * 数据源在线刷新间隔默认值（分钟）
     */
    private static final int DEFAULT_SOURCE_REFRESH_INTERVAL_MIN = 360;

    /**
     * 上次数据源在线刷新时间戳（毫秒，0 表示还没刷过）
     */
    private final java.util.concurrent.atomic.AtomicLong lastSourceRefreshAt =
            new java.util.concurrent.atomic.AtomicLong(0L);

    /**
     * 全局设置读取器
     */
    private final GlobalSettingReader settingReader;

    /**
     * 模型价格/指标快照服务
     */
    private final ModelMetricsSnapshotService snapshotService;

    /**
     * 全量指标库同步服务
     */
    private final SysAiModelMetricsService metricsService;

    /**
     * 官方定价同步服务（datasearch-starter 模型指标采集）
     */
    private final SysAiPricingService pricingService;

    /**
     * 厂商指标系数同步服务（一厂商一系数）
     */
    private final SysAiProviderMetricService providerMetricService;

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
     * 执行顺序：1) 指标快照推送 Redis  2) 官方定价同步到表（每次清空）
     */
    public void run() {
        if (!isEnabled()) {
            log.debug("[ModelMetrics] 模型指标推送未启用，跳过本次执行");
            return;
        }
        refreshOnlineSourcesIfDue();
        try {
            // 1. 全量指标同步到指标库（锁定字段跳过）
            int synced = metricsService.syncAll();
            log.info("[ModelMetrics] 全量指标库同步完成，共 {} 条", synced);
        } catch (Exception e) {
            log.warn("[ModelMetrics] 全量指标库同步失败: {}", e.getMessage());
        }
        try {
            // 2. 指标快照推送 Redis（兼容）
            snapshotService.buildAndPushSnapshot();
            log.info("[ModelMetrics] 价格/指标快照已推送完成");
        } catch (Exception e) {
            log.warn("[ModelMetrics] 快照推送失败: {}", e.getMessage());
        }
        try {
            // 3. 官方定价同步到表（datasearch-starter 模型指标采集，每次清空旧数据）
            pricingService.syncOfficialPricing();
            log.info("[ModelMetrics] 官方定价同步完成（datasearch-starter）");
        } catch (Exception e) {
            log.warn("[ModelMetrics] 官方定价同步失败: {}", e.getMessage());
        }
        try {
            // 4. 厂商指标系数同步（一厂商一系数，取价链最末一层兜底）
            int providerCount = providerMetricService.syncProviderMetrics();
            log.info("[ModelMetrics] 厂商指标系数同步完成，共 {} 家", providerCount);
        } catch (Exception e) {
            log.warn("[ModelMetrics] 厂商指标系数同步失败: {}", e.getMessage());
        }
    }

    /**
     * 按独立节奏刷新 datasearch 各数据源（Artificial Analysis / OpenRouter / Vercel 等）的在线数据到本地缓存。
     *
     * <p>各 {@code ModelMetricsProvider} 的 {@code getMetrics()} 只读本地文件缓存、不会自行联网，
     * 必须显式调用 {@code PricingSyncer.syncAllFromOnline()}（内部逐源触发 {@code syncFromOnline()}）
     * 才会更新。若从不刷新，新接入的数据源（如只补模型能力的 Vercel）会永远停留在首次种子的快照上。
     * 这里用一个较长的独立间隔（默认 6 小时，可用 {@code model_metrics_source_refresh_interval_min}
     * 调整，0 表示关闭）避免每次推送都压外网；刷新失败时保留旧缓存并回退时间戳，下个周期重试。</p>
     */
    private void refreshOnlineSourcesIfDue() {
        int intervalMin = settingReader.getInt(GROUP, SETTING_SOURCE_REFRESH_INTERVAL_MIN,
                DEFAULT_SOURCE_REFRESH_INTERVAL_MIN);
        if (intervalMin <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastSourceRefreshAt.get();
        if (last > 0 && now - last < TimeUnit.MINUTES.toMillis(intervalMin)) {
            return;
        }
        // 先占位再执行，避免上一次刷新尚未结束时被并发进入
        if (!lastSourceRefreshAt.compareAndSet(last, now)) {
            return;
        }
        try {
            int count = PricingSyncer.syncAllFromOnline(null);
            log.info("[ModelMetrics] datasearch 数据源在线刷新完成，共 {} 个", count);
        } catch (Exception e) {
            // 失败时回退时间戳让下个周期重试，不阻断本轮指标与定价同步
            lastSourceRefreshAt.compareAndSet(now, last);
            log.warn("[ModelMetrics] datasearch 数据源在线刷新失败: {}", e.getMessage());
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
     * 判断模型指标推送是否启用（全局 分组 enable_model_metrics_push，默认启用）。
     *
     * @return true 表示启用
     */
    private boolean isEnabled() {
        return settingReader.getBool(GROUP, "enable_model_metrics_push", true);
    }
}
