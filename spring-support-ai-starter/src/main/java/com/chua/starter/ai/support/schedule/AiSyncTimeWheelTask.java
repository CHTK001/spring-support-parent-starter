// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.schedule;

import com.chua.common.support.task.timer.Timer;
import com.chua.common.support.task.timer.TimerTask;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.timewheel.TimeWheelGlobalSettingProvider;
import com.chua.starter.ai.support.manage.service.SysAiModelMetricsService;
import com.chua.starter.ai.support.manage.service.SysAiNewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 定时同步时间轮任务：模型指标同步 + AIHOT 资讯同步。
 *
 * <p>按 {@code timewheel} 分组配置驱动：总开关
 * {@code timewheel_enabled} 开启时，用时间轮
 * （{@code com.chua.common.support.task.timer.Timer}，512 槽 × 1s tick）
 * 按各自间隔循环执行两类同步：</p>
 * <ul>
 *   <li>模型指标同步：{@link SysAiModelMetricsService#syncFromConfiguredProviders(Set, Set)}，
 *       范围受 provider id / model id 白名单约束；</li>
 *   <li>AIHOT 资讯同步：{@link SysAiNewsService#syncFromAiHot()}（按 link 去重 upsert）。</li>
 * </ul>
 *
 * <p>调度方式与 {@link ChannelProbeWheelTask} 一致：每次触发后都重新读取间隔与白名单
 * 再排下一次，配置改动下一轮即生效，无需重启；各链路上一次执行未结束时本次触发直接
 * 跳过（同步含外部网络调用，防重入避免堆积分轮）。总开关关闭后不再重排，时间轮自然
 * 熄灭；重新开启需重启进程。</p>
 *
 * @author CH
 * @since 4.0.0.70
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiSyncTimeWheelTask implements ApplicationRunner, DisposableBean {

    /**
     * 最小间隔（秒）：避免误配置把上游打爆
     */
    private static final long MIN_INTERVAL_SEC = 60L;

    /**
     * 指标同步默认间隔（秒）：30 分钟
     */
    private static final long DEFAULT_METRICS_INTERVAL_SEC = 1800L;

    /**
     * 资讯同步默认间隔（秒）：1 小时
     */
    private static final long DEFAULT_NEWS_INTERVAL_SEC = 3600L;

    /**
     * 全局设置读取器（timewheel 分组）
     */
    private final GlobalSettingReader settingReader;

    /**
     * 模型指标同步服务
     */
    private final SysAiModelMetricsService metricsService;

    /**
     * AIHOT 资讯同步服务
     */
    private final SysAiNewsService newsService;

    /**
     * 时间轮（总开关开启时懒建，512 槽 × 1s tick 足以覆盖分钟级间隔）
     */
    private volatile Timer wheel;

    /**
     * 指标同步防重入标志
     */
    private final AtomicBoolean metricsRunning = new AtomicBoolean(false);

    /**
     * 资讯同步防重入标志
     */
    private final AtomicBoolean newsRunning = new AtomicBoolean(false);

    /**
     * 启动入口：总开关开启时为指标链与资讯链各排第一条时间轮任务。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!totalEnabled()) {
            log.info("[AiTimeWheel] 时间轮未启用，指标/资讯保持原有节奏（手动 / 懒同步）");
            return;
        }
        scheduleMetrics();
        scheduleNews();
        log.info("[AiTimeWheel] 时间轮已启动：指标间隔 {}s、资讯间隔 {}s",
                metricsIntervalSec(), newsIntervalSec());
    }

    /**
     * 为指标同步链安排下一次执行（执行前重读开关与间隔）。
     */
    private void scheduleMetrics() {
        if (!totalEnabled() || !metricsEnabled()) {
            return;
        }
        Timer current = wheel();
        if (current == null || !current.isRunning()) {
            return;
        }
        TimerTask task = current.schedule(this::metricsTick, metricsIntervalSec(), TimeUnit.SECONDS);
        if (task == null) {
            log.debug("[AiTimeWheel] 指标链重排被拒绝（时间轮已关闭）");
        }
    }

    /**
     * 指标同步 tick：执行一轮按白名单过滤的指标同步，再按最新配置重排。
     */
    private void metricsTick() {
        if (!totalEnabled() || !metricsEnabled()) {
            return;
        }
        if (!metricsRunning.compareAndSet(false, true)) {
            log.debug("[AiTimeWheel] 上一轮指标同步尚未结束，跳过本次触发");
        } else {
            try {
                Set<String> providers = whitelist(TimeWheelGlobalSettingProvider.FIELD_METRICS_PROVIDERS);
                Set<String> modelIds = whitelist(TimeWheelGlobalSettingProvider.FIELD_METRICS_MODEL_IDS);
                int ok = metricsService.syncFromConfiguredProviders(providers, modelIds);
                log.info("[AiTimeWheel] 指标定时同步完成：成功 {} 家（providers={}，modelIds={}）",
                        ok, providers.isEmpty() ? "*" : providers, modelIds.isEmpty() ? "*" : modelIds);
            } catch (Throwable e) {
                log.warn("[AiTimeWheel] 指标定时同步失败: {}", e.getMessage());
            } finally {
                metricsRunning.set(false);
            }
        }
        scheduleMetrics();
    }

    /**
     * 为资讯同步链安排下一次执行（执行前重读开关与间隔）。
     */
    private void scheduleNews() {
        if (!totalEnabled() || !newsEnabled()) {
            return;
        }
        Timer current = wheel();
        if (current == null || !current.isRunning()) {
            return;
        }
        TimerTask task = current.schedule(this::newsTick, newsIntervalSec(), TimeUnit.SECONDS);
        if (task == null) {
            log.debug("[AiTimeWheel] 资讯链重排被拒绝（时间轮已关闭）");
        }
    }

    /**
     * 资讯同步 tick：执行一轮 AIHOT 资讯同步，再按最新配置重排。
     */
    private void newsTick() {
        if (!totalEnabled() || !newsEnabled()) {
            return;
        }
        if (!newsRunning.compareAndSet(false, true)) {
            log.debug("[AiTimeWheel] 上一轮资讯同步尚未结束，跳过本次触发");
        } else {
            try {
                java.util.Map<String, Integer> result = newsService.syncFromAiHot();
                log.info("[AiTimeWheel] 资讯定时同步完成：来源 {} 条，新增 {}，更新 {}",
                        result.get("total"), result.get("inserted"), result.get("updated"));
            } catch (Throwable e) {
                log.warn("[AiTimeWheel] 资讯定时同步失败: {}", e.getMessage());
            } finally {
                newsRunning.set(false);
            }
        }
        scheduleNews();
    }

    /**
     * 时间轮总开关。
     *
     * @return true 表示启用
     */
    private boolean totalEnabled() {
        return settingReader.getBool(TimeWheelGlobalSettingProvider.GROUP,
                TimeWheelGlobalSettingProvider.FIELD_ENABLED, false);
    }

    /**
     * 指标同步链开关。
     *
     * @return true 表示启用
     */
    private boolean metricsEnabled() {
        return settingReader.getBool(TimeWheelGlobalSettingProvider.GROUP,
                TimeWheelGlobalSettingProvider.FIELD_METRICS_ENABLED, true);
    }

    /**
     * 资讯同步链开关。
     *
     * @return true 表示启用
     */
    private boolean newsEnabled() {
        return settingReader.getBool(TimeWheelGlobalSettingProvider.GROUP,
                TimeWheelGlobalSettingProvider.FIELD_NEWS_ENABLED, true);
    }

    /**
     * 指标同步间隔（秒），已钳到最小间隔。
     *
     * @return 秒数
     */
    private long metricsIntervalSec() {
        int configured = settingReader.getInt(TimeWheelGlobalSettingProvider.GROUP,
                TimeWheelGlobalSettingProvider.FIELD_METRICS_INTERVAL, (int) DEFAULT_METRICS_INTERVAL_SEC);
        return Math.max(configured > 0 ? configured : DEFAULT_METRICS_INTERVAL_SEC, MIN_INTERVAL_SEC);
    }

    /**
     * 资讯同步间隔（秒），已钳到最小间隔。
     *
     * @return 秒数
     */
    private long newsIntervalSec() {
        int configured = settingReader.getInt(TimeWheelGlobalSettingProvider.GROUP,
                TimeWheelGlobalSettingProvider.FIELD_NEWS_INTERVAL, (int) DEFAULT_NEWS_INTERVAL_SEC);
        return Math.max(configured > 0 ? configured : DEFAULT_NEWS_INTERVAL_SEC, MIN_INTERVAL_SEC);
    }

    /**
     * 解析逗号分隔白名单（小写去空格）。
     *
     * @param field 设置字段名
     * @return 白名单集合；未配置返回空集合（= 不过滤）
     */
    private Set<String> whitelist(String field) {
        String raw = settingReader.getStr(TimeWheelGlobalSettingProvider.GROUP, field);
        Set<String> result = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String part : raw.split("[,，\\s]+")) {
            String name = part.trim().toLowerCase(Locale.ROOT);
            if (!name.isEmpty()) {
                result.add(name);
            }
        }
        return result;
    }

    /**
     * 懒建时间轮（512 槽 × 1 秒 tick，长间隔由重排机制覆盖）。
     *
     * @return 时间轮；已关闭时返回 {@code null}
     */
    private Timer wheel() {
        Timer local = wheel;
        if (local != null) {
            return local.isRunning() ? local : null;
        }
        synchronized (this) {
            local = wheel;
            if (local == null) {
                local = Timer.newTimer(512, 1, TimeUnit.SECONDS);
                wheel = local;
                log.debug("[AiTimeWheel] 时间轮已创建 slots=512 tick=1s");
            }
            return local;
        }
    }

    /**
     * 关闭时间轮（Spring 容器销毁时）。
     */
    @Override
    public void destroy() {
        Timer local = wheel;
        wheel = null;
        if (local == null) {
            return;
        }
        try {
            local.shutdown();
        } catch (Exception e) {
            log.debug("[AiTimeWheel] 时间轮关闭异常: {}", e.getMessage());
        }
    }
}
