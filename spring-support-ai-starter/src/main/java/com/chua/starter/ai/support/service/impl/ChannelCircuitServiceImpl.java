package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.model.ChannelCircuitSnapshot;
import com.chua.starter.ai.support.service.ChannelCircuitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 请求级熔断器实现（内存状态机）。
 *
 * <p>状态迁移：</p>
 * <pre>
 *   CLOSED --(连续失败 >= 阈值)--> OPEN --(冷却到期)--> HALF_OPEN
 *   HALF_OPEN --(成功)--> CLOSED
 *   HALF_OPEN --(失败)--> OPEN（重置冷却计时）
 *   OPEN --(冷却期内拒绝路由，不发上游请求)
 * </pre>
 *
 * <p>并发口径：允许并发请求同时进入 {@code HALF_OPEN} 的试探窗口（不做单飞限制）。
 * 理由：单飞需要额外锁与等待队列，在高并发下反而把熔断器变成瓶颈；而试探请求本身
 * 最多并发 N 条（N 为请求线程数），失败立即重新打开，代价可控且实现简单可靠。</p>
 *
 * <p>键为 {@code providerId::model}；条目按 {@code lastUpdatedAt} 惰性清理：
 * 长时间（默认 30 分钟）没有新结果的 CLOSED 条目会被移除，避免无界增长。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelCircuitServiceImpl implements ChannelCircuitService {

    /**
     * 设置分组（与渠道健康探针共用 health 组）
     */
    private static final String GROUP = "health";

    /**
     * 熔断总开关
     */
    private static final String SETTING_ENABLED = "channel_circuit_enabled";

    /**
     * 连续失败阈值
     */
    private static final String SETTING_FAILURE_THRESHOLD = "channel_circuit_failure_threshold";

    /**
     * 熔断冷却秒数
     */
    private static final String SETTING_OPEN_SECONDS = "channel_circuit_open_seconds";

    /**
     * 默认连续失败阈值
     */
    private static final int DEFAULT_FAILURE_THRESHOLD = 3;

    /**
     * 默认冷却秒数
     */
    private static final int DEFAULT_OPEN_SECONDS = 30;

    /**
     * 空闲条目保留时长（30 分钟）：超过该时长无更新的 CLOSED 条目会被清理
     */
    private static final long IDLE_EXPIRE_MILLIS = 30 * 60 * 1000L;

    /**
     * 全局设置读取器
     */
    private final GlobalSettingReader settingReader;

    /**
     * 熔断条目：key = providerId::model
     */
    private final Map<String, Entry> entries = new ConcurrentHashMap<>(64);

    /**
     * 上一次清理时间戳（避免每条请求都遍历清理）
     */
    private final AtomicReference<Long> lastSweepAt = new AtomicReference<>(0L);

    @Override
    public boolean allowRoute(String providerId, String model) {
        try {
            if (!enabled()) {
                return true;
            }
            sweepIfDue();
            String key = keyOf(providerId, model);
            Entry entry = entries.get(key);
            if (entry == null) {
                return true;
            }
            long now = System.currentTimeMillis();
            // 读到 OPEN 且冷却到期 → 等价 HALF_OPEN：放行试探请求
            if (STATE_OPEN.equals(entry.state) && now >= entry.openUntil) {
                entry.state = STATE_HALF_OPEN;
                entry.lastUpdatedAt = now;
                log.info("[ChannelCircuit] 冷却结束进入半开试探 provider={} model={}", providerId, model);
                return true;
            }
            if (STATE_OPEN.equals(entry.state)) {
                log.debug("[ChannelCircuit] 熔断中拒绝路由 provider={} model={} 剩余={}ms",
                        providerId, model, entry.openUntil - now);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("[ChannelCircuit] 判定失败，放行: {}", e.getMessage());
            return true;
        }
    }

    @Override
    public void recordSuccess(String providerId, String model) {
        try {
            if (!enabled()) {
                return;
            }
            Entry entry = entries.get(keyOf(providerId, model));
            if (entry == null) {
                return;
            }
            boolean wasOpen = STATE_OPEN.equals(entry.state) || STATE_HALF_OPEN.equals(entry.state);
            entry.state = STATE_CLOSED;
            entry.consecutiveFailures = 0;
            entry.openUntil = 0L;
            entry.lastError = null;
            entry.lastUpdatedAt = System.currentTimeMillis();
            if (wasOpen) {
                log.info("[ChannelCircuit] 熔断恢复 provider={} model={}", providerId, model);
            }
        } catch (Exception e) {
            log.warn("[ChannelCircuit] 记录成功失败（忽略）: {}", e.getMessage());
        }
    }

    @Override
    public void recordFailure(String providerId, String model, String error) {
        try {
            if (!enabled()) {
                return;
            }
            int threshold = failureThreshold();
            int openSeconds = openSeconds();
            String key = keyOf(providerId, model);
            Entry entry = entries.computeIfAbsent(key, k -> new Entry(providerId, model));
            long now = System.currentTimeMillis();
            entry.consecutiveFailures++;
            entry.lastError = StringUtils.isBlank(error) ? "上游请求失败" : error;
            entry.lastUpdatedAt = now;
            if (STATE_HALF_OPEN.equals(entry.state) || entry.consecutiveFailures >= threshold) {
                entry.state = STATE_OPEN;
                entry.openUntil = now + openSeconds * 1000L;
                log.warn("[ChannelCircuit] 熔断打开 provider={} model={} failures={}/{} 冷却={}s 原因={}",
                        providerId, model, entry.consecutiveFailures, threshold, openSeconds, entry.lastError);
            }
        } catch (Exception e) {
            log.warn("[ChannelCircuit] 记录失败失败（忽略）: {}", e.getMessage());
        }
    }

    @Override
    public List<ChannelCircuitSnapshot> snapshots() {
        long now = System.currentTimeMillis();
        List<ChannelCircuitSnapshot> result = new ArrayList<>(entries.size());
        for (Entry entry : entries.values()) {
            long remaining = STATE_OPEN.equals(entry.state) ? Math.max(0L, entry.openUntil - now) : 0L;
            result.add(new ChannelCircuitSnapshot(
                    entry.providerId,
                    entry.model,
                    entry.state,
                    entry.consecutiveFailures,
                    entry.openUntil,
                    remaining,
                    entry.lastError,
                    entry.lastUpdatedAt));
        }
        result.sort(Comparator.comparingLong(ChannelCircuitSnapshot::lastUpdatedAt).reversed());
        return result;
    }

    @Override
    public boolean reset(String providerId, String model) {
        return entries.remove(keyOf(providerId, model)) != null;
    }

    @Override
    public int resetAll() {
        int size = entries.size();
        entries.clear();
        return size;
    }

    /**
     * 熔断总开关（默认开）。
     *
     * @return true 表示启用
     */
    private boolean enabled() {
        return settingReader.getBool(GROUP, SETTING_ENABLED, true);
    }

    /**
     * 连续失败阈值（最小 1）。
     *
     * @return 阈值
     */
    private int failureThreshold() {
        int threshold = settingReader.getInt(GROUP, SETTING_FAILURE_THRESHOLD, DEFAULT_FAILURE_THRESHOLD);
        return Math.max(1, threshold);
    }

    /**
     * 冷却秒数（最小 1）。
     *
     * @return 秒数
     */
    private int openSeconds() {
        int seconds = settingReader.getInt(GROUP, SETTING_OPEN_SECONDS, DEFAULT_OPEN_SECONDS);
        return Math.max(1, seconds);
    }

    /**
     * 惰性清理：每分钟最多扫一次，移除长时间无更新的 CLOSED 条目。
     *
     * <p>OPEN / HALF_OPEN 条目一律保留 —— 它们还参与路由判定。</p>
     */
    private void sweepIfDue() {
        long now = System.currentTimeMillis();
        Long last = lastSweepAt.get();
        if (last != null && now - last < 60_000L) {
            return;
        }
        if (!lastSweepAt.compareAndSet(last, now)) {
            return;
        }
        entries.entrySet().removeIf(e -> {
            Entry entry = e.getValue();
            return STATE_CLOSED.equals(entry.state)
                    && now - entry.lastUpdatedAt > IDLE_EXPIRE_MILLIS;
        });
    }

    /**
     * 组装条目键。
     *
     * @param providerId 供应商标识
     * @param model      模型名
     * @return 键
     */
    private static String keyOf(String providerId, String model) {
        return (StringUtils.isBlank(providerId) ? "_" : providerId.trim())
                + "::" + (StringUtils.isBlank(model) ? "_" : model.trim());
    }

    /**
     * 熔断条目（volatile 字段，读多写少，单条目内不要求原子组合）。
     */
    private static final class Entry {

        /**
         * 供应商标识
         */
        private final String providerId;

        /**
         * 模型名
         */
        private final String model;

        /**
         * 当前状态
         */
        private volatile String state = STATE_CLOSED;

        /**
         * 连续失败次数
         */
        private volatile int consecutiveFailures;

        /**
         * 冷却截止时间戳（毫秒）
         */
        private volatile long openUntil;

        /**
         * 最近失败原因
         */
        private volatile String lastError;

        /**
         * 最近更新时间戳（毫秒）
         */
        private volatile long lastUpdatedAt = System.currentTimeMillis();

        private Entry(String providerId, String model) {
            this.providerId = providerId;
            this.model = model;
        }
    }
}
