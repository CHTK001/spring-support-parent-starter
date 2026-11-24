package com.chua.starter.osgi.support.stats;

import com.chua.common.support.osgi.OsgiLauncher;
import com.chua.starter.logger.event.OsgiLogEvent;
import com.chua.starter.osgi.properties.OsgiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * OSGi 框架体征采样器与插件操作统计聚合器。
 *
 * <p><b>体征趋势</b>：后台守护线程按固定间隔调用 {@link OsgiLauncher#getFrameworkStats()}，
 * 把 bundle 总数 / 活跃数 / 服务类型数 / 自动启动失败数等读数写入内存环形缓冲
 * （默认保留最近 240 个点）。只采样、不落库：宿主重启后历史清零，趋势图从新开始积累。</p>
 *
 * <p><b>操作统计</b>：监听本模块发布的 {@link OsgiLogEvent}（success / failure 终态），
 * 按操作类型聚合总次数、成功 / 失败次数与平均耗时。start 阶段事件不计数，
 * 避免同一次操作被重复统计；读操作默认静默（仅失败时发事件），不会因轮询虚增次数。</p>
 *
 * <p><b>旁路原则</b>：采样与聚合失败只降级为 debug 日志，绝不影响框架运行与插件操作。
 * 采样线程为守护线程，{@link #stop()} 随 Bean 销毁关闭。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
public class OsgiStatsCollector {

    /**
     * 最小采样间隔（秒）：更密的采样对框架读数没有额外意义，只会增加噪声。
     */
    private static final long MIN_INTERVAL_SECONDS = 5L;

    /**
     * 最小历史保留点数。
     */
    private static final int MIN_HISTORY_SIZE = 10;

    /**
     * 最大历史保留点数（按分钟级采样封顶一天）。
     */
    private static final int MAX_HISTORY_SIZE = 1440;

    /**
     * 操作统计的最大条目数：操作类型是有限集合，超限视为异常调用方，直接丢弃。
     */
    private static final int MAX_ACTION_ENTRIES = 32;

    /**
     * 采样间隔（秒）。
     */
    private final long intervalSeconds;

    /**
     * 历史保留点数。
     */
    private final int historySize;

    /**
     * 是否开启周期采样（操作统计不受此开关影响）。
     */
    private final boolean enabled;

    /**
     * osgi 启动器（读数来源）。
     */
    private final OsgiLauncher launcher;

    /**
     * 体征历史环形缓冲，访问须持 {@code history} 锁。
     */
    private final Deque<OsgiStatSample> history = new ArrayDeque<>();

    /**
     * 操作聚合表：action → 聚合值。
     */
    private final ConcurrentHashMap<String, ActionAgg> actions = new ConcurrentHashMap<>();

    /**
     * 采样调度器，{@link #start()} 后非空。
     */
    private ScheduledExecutorService scheduler;

    /**
     * 构造采样器。
     *
     * @param launcher osgi 启动器
     * @param config   统计采样配置，{@code null} 时按默认值
     */
    public OsgiStatsCollector(OsgiLauncher launcher, OsgiProperties.Stats config) {
        this.launcher = launcher;
        OsgiProperties.Stats safe = config == null ? new OsgiProperties.Stats() : config;
        this.enabled = safe.isEnabled();
        this.intervalSeconds = Math.max(MIN_INTERVAL_SECONDS, safe.getIntervalSeconds());
        this.historySize = Math.max(MIN_HISTORY_SIZE, Math.min(MAX_HISTORY_SIZE, safe.getHistorySize()));
    }

    /**
     * 启动周期采样。重复调用无副作用；{@code plugin.osgi.stats.enabled=false} 时不启动。
     */
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        if (!enabled) {
            log.info("[OSGi] 框架体征采样未开启（plugin.osgi.stats.enabled=false），趋势接口返回空列表");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "osgi-stats-collector");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::sample, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        log.info("[OSGi] 框架体征采样已启动: 间隔 {}s，保留最近 {} 个采样点", intervalSeconds, historySize);
    }

    /**
     * 停止采样并释放线程。随 Bean 销毁调用。
     */
    public void stop() {
        ScheduledExecutorService current;
        synchronized (this) {
            current = scheduler;
            scheduler = null;
        }
        if (current != null) {
            current.shutdownNow();
        }
    }

    /**
     * 读取体征历史（时间升序的不可变快照）。
     *
     * @return 采样点列表，未采样时为空
     */
    public List<OsgiStatSample> history() {
        synchronized (history) {
            return List.copyOf(history);
        }
    }

    /**
     * 读取操作统计（按总次数降序）。
     *
     * @return 操作统计列表，暂无操作时为空
     */
    public List<OsgiActionStat> actionStats() {
        return actions.entrySet().stream()
                .map(entry -> entry.getValue().toStat(entry.getKey()))
                .sorted(Comparator.comparingLong(OsgiActionStat::total).reversed()
                        .thenComparing(OsgiActionStat::action))
                .toList();
    }

    /**
     * 监听插件操作日志事件，聚合终态到操作统计表。
     *
     * @param event 插件操作日志事件
     */
    @EventListener
    public void onOsgiLog(OsgiLogEvent event) {
        if (event == null || event.action() == null || event.action().isBlank()) {
            return;
        }
        boolean success = OsgiLogEvent.STATUS_SUCCESS.equals(event.status());
        boolean failure = OsgiLogEvent.STATUS_FAILURE.equals(event.status());
        if (!success && !failure) {
            return; // start 阶段不计数，避免同一次操作重复统计
        }
        String action = event.action();
        if (actions.size() >= MAX_ACTION_ENTRIES && !actions.containsKey(action)) {
            return;
        }
        try {
            actions.computeIfAbsent(action, key -> new ActionAgg())
                    .record(success, event.duration(), event.startedAt() + event.duration());
        } catch (Exception e) {
            log.debug("[OSGi] 操作统计聚合失败（旁路降级）: action={}, reason={}", action, e.getMessage());
        }
    }

    /**
     * 执行一次体征采样，写入环形缓冲。
     */
    private void sample() {
        try {
            Map<String, Object> stats = launcher.getFrameworkStats();
            Map<String, Long> states = new LinkedHashMap<>();
            if (stats.get("bundleStates") instanceof Map<?, ?> raw) {
                for (Map.Entry<?, ?> entry : raw.entrySet()) {
                    states.put(String.valueOf(entry.getKey()),
                            entry.getValue() instanceof Number number ? number.longValue() : 0L);
                }
            }
            OsgiStatSample point = new OsgiStatSample(
                    System.currentTimeMillis(),
                    Boolean.TRUE.equals(stats.get("active")),
                    intValue(stats.get("totalBundles")),
                    intValue(stats.get("activeBundles")),
                    intValue(stats.get("registeredServiceTypes")),
                    intValue(stats.get("autoStartFailures")),
                    states);
            synchronized (history) {
                history.addLast(point);
                while (history.size() > historySize) {
                    history.removeFirst();
                }
            }
        } catch (Throwable e) {
            // 采样是旁路：框架抖动 / 停机中读数失败不影响宿主，降级为 debug 日志
            log.debug("[OSGi] 框架体征采样失败（旁路降级）: {}", e.getMessage());
        }
    }

    /**
     * 安全取整数值。
     *
     * @param value 统计值
     * @return 整数值，非数值时为 0
     */
    private static int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    /**
     * 单个操作类型的聚合值（内部可变结构，对外以 {@link OsgiActionStat} record 暴露）。
     */
    private static final class ActionAgg {

        private long total;
        private long success;
        private long failure;
        private long durationSum;
        private long lastTimestamp;

        /**
         * 记录一次终态。
         *
         * @param ok       是否成功
         * @param duration 耗时（毫秒）
         * @param at       事件时间戳（毫秒）
         */
        synchronized void record(boolean ok, long duration, long at) {
            total++;
            if (ok) {
                success++;
            } else {
                failure++;
            }
            durationSum += Math.max(0, duration);
            lastTimestamp = at;
        }

        /**
         * 转成对外统计记录。
         *
         * @param action 操作类型
         * @return 统计记录
         */
        synchronized OsgiActionStat toStat(String action) {
            return new OsgiActionStat(action, total, success, failure,
                    total == 0 ? 0 : durationSum / total, lastTimestamp);
        }
    }
}
