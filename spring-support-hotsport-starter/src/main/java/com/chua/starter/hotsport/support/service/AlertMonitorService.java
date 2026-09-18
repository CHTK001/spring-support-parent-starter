package com.chua.starter.hotsport.support.service;

import com.chua.starter.hotsport.support.config.HotsportProperties;
import com.chua.runtime.apm.storage.ApmStorage;
import com.chua.runtime.apm.storage.Query;
import com.chua.runtime.apm.storage.TransmissionEvent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 告警监控服务 — 阈值巡检事件流（可配置规则 + 事件状态机）。
 *
 * <p>3 条默认规则（SkyWalking 风格饱和/健康度告警）：
 * <ul>
 *   <li>堆使用率 &gt; 90%（CRITICAL）</li>
 *   <li>错误率 &gt; 10%（CRITICAL）</li>
 *   <li>活跃泄漏 &gt; 3（WARNING）</li>
 * </ul>
 * 规则支持运行时增删改/启停（{@link #listRules()} 等）；同一指标命中后按规则
 * 去抖时间抑制，事件入 100 条环形缓冲。事件带状态机：
 * OPEN → ACKNOWLEDGED / MUTED / IGNORED，{@link #acknowledgeEvent(long, String)}
 * 由 {@code /alerts/{id}/...} 处置端端点驱动。</p>
 *
 * <p>经 {@link JvmMetricsService#registerSampleHook} 挂在每次 JVM 采样后
 * （默认 5s）巡检，{@link #alerts(int)} 供 {@code /alerts} 读取。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class AlertMonitorService {

    /** 事件环形缓冲容量。 */
    private static final int EVENT_CAPACITY = 100;

    /**
    * 告警规则（可动态增删改）。
    */
    public static class AlertRule {

        public long id;
        public String metric;      // heap_percent / error_rate / active_leaks
        public double threshold;
        public String severity;    // CRITICAL / WARNING
        public long debounceMs;
        public boolean enabled;

        public AlertRule() {
        }

        public AlertRule(long id, String metric, double threshold, String severity,
                         long debounceMs, boolean enabled) {
            this.id = id;
            this.metric = metric;
            this.threshold = threshold;
            this.severity = severity;
            this.debounceMs = debounceMs;
            this.enabled = enabled;
        }
    }

    /**
    * 告警事件（带状态机）。
    */
    public static class AlertEvent {

        public long id;
        public long ts;
        public String metric;
        public double current;
        public double threshold;
        public String severity;
        public String message;
        public String status;      // OPEN / ACKNOWLEDGED / MUTED / IGNORED
        public long statusTs;
    }

    /** 事件 ID 原子计数。 */
    private final AtomicLong eventIdGen = new AtomicLong(1);

    /** 规则 ID 原子计数。 */
    private final AtomicLong ruleIdGen = new AtomicLong(1);

    /** 规则表（metric → AlertRule），初始 3 条默认规则。 */
    private final ConcurrentHashMap<String, AlertRule> rules = new ConcurrentHashMap<>();

    /** 事件环形缓冲。 */
    private final ConcurrentLinkedDeque<AlertEvent> events = new ConcurrentLinkedDeque<>();

    /** 各指标上次触发时间（去抖）。 */
    private final ConcurrentHashMap<String, Long> lastFired = new ConcurrentHashMap<>();

    private final JvmMetricsService jvmMetricsService;

    /**
    * 构造器（@Lazy 打破 JvmMetricsService ← AlertMonitorService 循环依赖）。
    *
    * @param jvmMetricsService JVM 指标服务
    */
    public AlertMonitorService(@Lazy JvmMetricsService jvmMetricsService) {
        this.jvmMetricsService = jvmMetricsService;
    }

    /** 注册钩子：初始化 3 条默认规则 + 挂接 JVM 采样。 */
    @PostConstruct
    public void init() {
        rules.put("heap_percent",
                new AlertRule(ruleIdGen.getAndIncrement(), "heap_percent", 90.0, "CRITICAL", 5000L, true));
        rules.put("error_rate",
                new AlertRule(ruleIdGen.getAndIncrement(), "error_rate", 10.0, "CRITICAL", 5000L, true));
        rules.put("active_leaks",
                new AlertRule(ruleIdGen.getAndIncrement(), "active_leaks", 3.0, "WARNING", 5000L, true));
        jvmMetricsService.registerSampleHook(this::check);
        log.info("[Hotsport] 告警监控启动（{} 条规则）", rules.size());
    }

    /**
    * 巡检一次（采样钩子调用）：遍历启用的规则，检查对应指标。
    *
    * <p>heap_percent → {@code lastSample().memory.heapUsedPercent}；
    * error_rate → {@link #computeErrorRate()}；active_leaks →
    * {@code jvmMetricsService.activeLeakCount()}。</p>
    */
    public void check() {
        try {
            Map<String, Object> sample = jvmMetricsService.lastSample();
            Map<String, Object> memory = (Map<String, Object>) sample.get("memory");
            double heapPercent = ((Number) memory.get("heapUsedPercent")).doubleValue();
            double errorRate = computeErrorRate();
            int activeLeaks = jvmMetricsService.activeLeakCount();

            for (AlertRule rule : rules.values()) {
                if (rule == null || !rule.enabled) {
                    continue;
                }
                double current;
                switch (rule.metric) {
                    case "heap_percent":
                        current = heapPercent;
                        break;
                    case "error_rate":
                        current = errorRate;
                        break;
                    case "active_leaks":
                        current = activeLeaks;
                        break;
                    default:
                        continue;
                }
                fireIfExceeded(rule, current);
            }
        } catch (Exception e) {
            log.debug("[Hotsport] 告警巡检失败: {}", e.getMessage());
        }
    }

    /**
    * 阈值命中且过去规则 {@code debounceMs} 内未触发 → 记事件（状态 OPEN）。
    *
    * @param rule    命中的规则（threshold/debounceMs 取规则配置）
    * @param current 当前值
    */
    private void fireIfExceeded(AlertRule rule, double current) {
        if (current < rule.threshold) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastFired.get(rule.metric);
        if (last != null && now - last < rule.debounceMs) {
            return;
        }
        lastFired.put(rule.metric, now);
        AlertEvent event = new AlertEvent();
        event.id = eventIdGen.getAndIncrement();
        event.ts = now;
        event.metric = rule.metric;
        event.current = current;
        event.threshold = rule.threshold;
        event.severity = rule.severity;
        event.message = buildMessage(rule.metric, current, rule.threshold);
        event.status = "OPEN";
        event.statusTs = now;
        events.addLast(event);
        while (events.size() > EVENT_CAPACITY) {
            events.pollFirst();
        }
        log.info("[Hotsport] 告警触发 [{}] {}: {}", rule.severity, rule.metric, event.message);
    }

    /** 构建人类可读告警消息。 */
    private static String buildMessage(String metric, double current, double threshold) {
        switch (metric) {
            case "heap_percent":
                return "堆使用率 " + Math.round(current) + "% 超过阈值 " + Math.round(threshold) + "%";
            case "error_rate":
                return "错误率 " + String.format("%.1f", current) + "% 超过阈值 " + Math.round(threshold) + "%";
            case "active_leaks":
                return "活跃泄漏 " + Math.round(current) + " 个超过阈值 " + Math.round(threshold);
            default:
                return metric + " 指标超阈值 " + threshold;
        }
    }

    /** 错误率 = 最近 1 分钟传输错误数 / 总数 * 100（无数据返回 0）。 */
    private double computeErrorRate() {
        try {
            ApmStorage storage = HotsportProperties.storage();
            if (storage == null) {
                return 0.0;
            }
            long cutoff = System.currentTimeMillis() - 60_000L;
            List<TransmissionEvent> all =
                    storage.queryTransmissions(new Query().setLimit(100000).setStartTime(cutoff));
            if (all == null || all.isEmpty()) {
                return 0.0;
            }
            long errors = all.stream()
                    .filter(e -> e.getStatus() == com.chua.runtime.protocol.StatusCode.ERROR)
                    .count();
            return Math.round(10000.0 * errors / all.size()) / 100.0;
        } catch (Exception e) {
            log.debug("[Hotsport] 错误率统计失败: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
    * 事件状态机操作：OPEN → {@code toStatus}（ACKNOWLEDGED / MUTED / IGNORED）。
    *
    * @param eventId  事件 ID
    * @param toStatus 目标状态
    * @return 是否找到并更新
    */
    public boolean acknowledgeEvent(long eventId, String toStatus) {
        for (AlertEvent e : events) {
            if (e.id == eventId) {
                e.status = toStatus;
                e.statusTs = System.currentTimeMillis();
                return true;
            }
        }
        return false;
    }

    /**
    * 最近告警事件（新事件在前，转 Map 保持后端 API 兼容）。
    *
    * @param limit 返回上限
    * @return 事件列表
    */
    public List<Map<String, Object>> alerts(int limit) {
        List<AlertEvent> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparingLong((AlertEvent e) -> e.ts).reversed());
        List<Map<String, Object>> out = new ArrayList<>();
        for (AlertEvent e : sorted) {
            if (out.size() >= limit) {
                break;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id);
            m.put("ts", e.ts);
            m.put("metric", e.metric);
            m.put("current", e.current);
            m.put("threshold", e.threshold);
            m.put("severity", e.severity);
            m.put("message", e.message);
            m.put("status", e.status);
            m.put("statusTs", e.statusTs);
            out.add(m);
        }
        return out;
    }

    /**
    * 最近 3 条告警（SSE 帧并入用）。
    *
    * @return 事件列表
    */
    public List<Map<String, Object>> recentAlerts() {
        return alerts(3);
    }

    // ------------------------------------------------------------------
    // 规则 CRUD
    // ------------------------------------------------------------------

    /**
    * 规则列表。
    *
    * @return 当前全部规则
    */
    public List<AlertRule> listRules() {
        return new ArrayList<>(rules.values());
    }

    /**
    * 新增规则（自动分配 ID，同名 metric 覆盖旧规则）。
    *
    * @param rule 规则（id 忽略，可传 0）
    * @return 保存后的规则
    */
    public AlertRule addRule(AlertRule rule) {
        rule.id = ruleIdGen.getAndIncrement();
        rules.put(rule.metric, rule);
        return rule;
    }

    /**
    * 更新规则（按 metric 覆盖）。
    *
    * @param rule 规则
    * @return 保存后的规则
    */
    public AlertRule updateRule(AlertRule rule) {
        rules.put(rule.metric, rule);
        return rule;
    }

    /**
    * 删除规则。
    *
    * @param metric 指标键
    */
    public void deleteRule(String metric) {
        rules.remove(metric);
    }

    /**
    * 启停规则。
    *
    * @param metric  指标键
    * @param enabled 是否启用
    */
    public void toggleRule(String metric, boolean enabled) {
        AlertRule r = rules.get(metric);
        if (r != null) {
            r.enabled = enabled;
        }
    }
}
