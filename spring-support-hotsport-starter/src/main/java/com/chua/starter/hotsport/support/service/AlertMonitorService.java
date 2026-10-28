package com.chua.starter.hotsport.support.service;

import com.chua.starter.hotsport.support.config.HotsportProperties;
import com.chua.runtime.apm.storage.ApmStorage;
import com.chua.runtime.apm.storage.Query;
import com.chua.runtime.apm.storage.TransmissionEvent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

    /**
     * 事件环形缓冲容量。
    */
    private static final int EVENT_CAPACITY = 100;

    /**
     * Webhook 通知渠道（type=webhook 时使用）。
     */
    public static class AlertChannel {

        public long id;
        public String name;       // 渠道名称，如：值班群
        public String type;       // webhook / dingtalk / feishu / email
        public String url;        // Webhook URL（webhook/dingtalk/feishu 用）
        public String secret;     // 可选签名密钥（dingtalk 签名等）
        public boolean enabled;   // 是否启用

        public AlertChannel() {
        }

        public AlertChannel(long id, String name, String type, String url, String secret, boolean enabled) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.url = url;
            this.secret = secret;
            this.enabled = enabled;
        }
    }

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
     * 告警事件（带状态机 + 降噪指纹）。
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
        /**
         * 降噪指纹：metric + severity，用于同指标连续告警抑制。
         */
        public String fingerprint;
        /**
         * 静默截点（epoch ms）；未静默为 0。
         */
        public long silencedUntil;
        /**
         * 同指纹累计触发次数（降噪后仍保留统计）。
         */
        public long repeatCount;
    }

    /**
     * 事件 ID 原子计数。
    */
    private final AtomicLong eventIdGen = new AtomicLong(1);

    /**
     * 规则 ID 原子计数。
    */
    private final AtomicLong ruleIdGen = new AtomicLong(1);

    /**
     * 渠道 ID 原子计数。
    */
    private final AtomicLong channelIdGen = new AtomicLong(1);

    /**
     * 规则表（metric → AlertRule），初始 6 条默认规则。
    */
    private final ConcurrentHashMap<String, AlertRule> rules = new ConcurrentHashMap<>();

    /**
     * 通知渠道表（id → AlertChannel）。
    */
    private final ConcurrentHashMap<Long, AlertChannel> channels = new ConcurrentHashMap<>();

    /**
     * 事件环形缓冲。
    */
    private final ConcurrentLinkedDeque<AlertEvent> events = new ConcurrentLinkedDeque<>();

    /**
     * 各指标上次触发时间（去抖）。
    */
    private final ConcurrentHashMap<String, Long> lastFired = new ConcurrentHashMap<>();

    /**
     * Webhook 异步推送客户端（触发告警时不阻塞巡检线程）。
    */
    private final HttpClient webhookClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final JvmMetricsService jvmMetricsService;

    /**
     * 构造器（@Lazy 打破 JvmMetricsService ← AlertMonitorService 循环依赖）。
     *
     * @param jvmMetricsService JVM 指标服务
     */
    public AlertMonitorService(@Lazy JvmMetricsService jvmMetricsService) {
        this.jvmMetricsService = jvmMetricsService;
    }

    /**
     * 注册钩子：初始化 6 条默认规则 + 挂接 JVM 采样。
    */
    @PostConstruct
    public void init() {
        rules.put("heap_percent",
                new AlertRule(ruleIdGen.getAndIncrement(), "heap_percent", 90.0, "CRITICAL", 5000L, true));
        rules.put("error_rate",
                new AlertRule(ruleIdGen.getAndIncrement(), "error_rate", 10.0, "CRITICAL", 5000L, true));
        rules.put("active_leaks",
                new AlertRule(ruleIdGen.getAndIncrement(), "active_leaks", 3.0, "WARNING", 5000L, true));
        rules.put("cpu_percent",
                new AlertRule(ruleIdGen.getAndIncrement(), "cpu_percent", 80.0, "WARNING", 5000L, true));
        rules.put("thread_count",
                new AlertRule(ruleIdGen.getAndIncrement(), "thread_count", 300.0, "WARNING", 5000L, false));
        rules.put("transmission_rate",
                new AlertRule(ruleIdGen.getAndIncrement(), "transmission_rate", 100.0, "WARNING", 5000L, false));
        jvmMetricsService.registerSampleHook(this::check);
        log.info("[Hotsport] 告警监控启动（{} 条规则）", rules.size());
    }

    /**
     * 巡检一次（采样钩子调用）：遍历启用的规则，检查对应指标。
     *
     * <p>heap_percent → {@code lastSample().memory.heapUsedPercent}；
     * error_rate → {@link #computeErrorRate()}；active_leaks →
     * {@code jvmMetricsService.activeLeakCount()}；cpu_percent →
     * {@code lastSample().cpu.processCpuLoad}；thread_count →
     * {@code lastSample().threads.liveThreads}；transmission_rate →
     * {@link #computeTransmissionRate()}。</p>
     */
    public void check() {
        try {
            Map<String, Object> sample = jvmMetricsService.lastSample();
            Map<String, Object> memory = (Map<String, Object>) sample.get("memory");
            double heapPercent = ((Number) memory.get("heapUsedPercent")).doubleValue();
            double errorRate = computeErrorRate();
            int activeLeaks = jvmMetricsService.activeLeakCount();
            double cpuPercent = metricDouble(sample, "cpu", "processCpuLoad");
            double threadCount = metricDouble(sample, "threads", "liveThreads");
            double transmissionRate = computeTransmissionRate();

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
                    case "cpu_percent":
                        current = cpuPercent;
                        break;
                    case "thread_count":
                        current = threadCount;
                        break;
                    case "transmission_rate":
                        current = transmissionRate;
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
     * 从 JVM 采样快照按两级键取数值指标（缺字段返回 0）。
     *
     * @param sample 采样快照
     * @param group  一级键（memory/cpu/threads/os/...）
     * @param key    二级键
     * @return 数值；缺字段/非数值返回 0
     */
    private static double metricDouble(Map<String, Object> sample, String group, String key) {
        try {
            Object g = sample.get(group);
            if (g instanceof Map<?, ?> m) {
                Object v = m.get(key);
                return v instanceof Number n ? n.doubleValue() : 0.0;
            }
        } catch (Exception e) {
            // 采样缺字段时按 0 处理，不中断巡检
        }
        return 0.0;
    }

    /**
     * 传输速率：最近 1 分钟新增传输记录数 / 60。
     *
     * <p>从存储层查询最近 1 分钟记录数（真实计数），无存储返回 0。</p>
     *
     * @return 每秒传输条数
     */
    private double computeTransmissionRate() {
        try {
            ApmStorage storage = HotsportProperties.storage();
            if (storage == null) {
                return 0.0;
            }
            long cutoff = System.currentTimeMillis() - 60_000L;
            List<TransmissionEvent> all =
                    storage.queryTransmissions(new Query().setLimit(100000).setStartTime(cutoff));
            if (all == null) {
                return 0.0;
            }
            return Math.round(all.size() * 100.0 / 60.0) / 100.0;
        } catch (Exception e) {
            log.debug("[Hotsport] 传输速率统计失败: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * 阈值命中且过去规则 {@code debounceMs} 内未触发 → 记事件（状态 OPEN）。
     *
     * <p>降噪规则：同 {@code metric+severity} 指纹在静默窗口内不重复入队，
     * 而是累加最近事件的 {@code repeatCount}；这样高频抖动不会刷爆事件列表，
     * 但页面仍能看出该指标反复触发。</p>
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

        String fingerprint = rule.metric + "|" + rule.severity;
        // 静默窗口：已有同一指纹 OPEN 事件且静默未到期 → 只累加计数，不新增
        AlertEvent recent = events.peekLast();
        if (recent != null && fingerprint.equals(recent.fingerprint) && now < recent.silencedUntil) {
            recent.repeatCount++;
            recent.current = current;
            recent.ts = now;
            log.debug("[Hotsport] 告警静默期内重复触发，累计 {} 次: {}", recent.repeatCount, rule.metric);
            return;
        }
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
        event.fingerprint = fingerprint;
        event.silencedUntil = now + rule.debounceMs * 3;
        event.repeatCount = 1;
        events.addLast(event);
        while (events.size() > EVENT_CAPACITY) {
            events.pollFirst();
        }
        notifyChannels(event);
        log.info("[Hotsport] 告警触发 [{}] {}: {}", rule.severity, rule.metric, event.message);
    }

    /**
     * 把新告警事件分发给全部启用的通知渠道（异步 Webhook，不阻塞巡检线程）。
     *
     * <p>失败只记日志不重试——观测组件不因推送失败干扰告警主流程。</p>
     *
     * @param event 触发的事件
     */
    private void notifyChannels(AlertEvent event) {
        List<AlertChannel> enabledChannels = new ArrayList<>();
        for (AlertChannel c : channels.values()) {
            if (c != null && c.enabled && c.url != null && !c.url.isBlank()) {
                enabledChannels.add(c);
            }
        }
        if (enabledChannels.isEmpty()) {
            return;
        }
        for (AlertChannel c : enabledChannels) {
            final AlertChannel ch = c;
            CompletableFuture.runAsync(() -> pushWebhook(ch, event));
        }
    }

    /**
     * 单渠道 Webhook 推送：POST JSON（钉钉/飞书/通用 webhook 同构）。
     *
     * @param channel 渠道
     * @param event   告警事件
     */
    private void pushWebhook(AlertChannel channel, AlertEvent event) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("msgtype", "text");
            Map<String, Object> text = new LinkedHashMap<>();
            String ts = new java.text.SimpleDateFormat("MM-dd HH:mm:ss")
                    .format(new java.util.Date(event.ts));
            text.put("content", String.format(
                    "[Hotsport 告警 %s] %s\n指标: %s  当前: %s  阈值: %s\n时间: %s",
                    event.severity, event.message, event.metric, formatNum(event.current),
                    formatNum(event.threshold), ts));
            payload.put("text", text);
            payload.put("metric", event.metric);
            payload.put("severity", event.severity);
            payload.put("ts", event.ts);
            String body = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(channel.url.trim()))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = webhookClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                log.warn("[Hotsport] Webhook 推送失败 [{}] {}: HTTP {}", channel.name, channel.url, resp.statusCode());
            } else {
                log.debug("[Hotsport] Webhook 推送成功 [{}] {}", channel.name, resp.statusCode());
            }
        } catch (Exception e) {
            log.debug("[Hotsport] Webhook 推送异常 [{}]: {}", channel.name, e.getMessage());
        }
    }

    /**
     * 数值格式化：整数不带小数，非整数保留两位。
     *
     * @param v 数值
     * @return 格式化字符串
     */
    private static String formatNum(double v) {
        if (v == Math.rint(v)) {
            return String.valueOf(Math.round(v));
        }
        return String.format("%.2f", v);
    }

    /**
     * 构建人类可读告警消息。
     * @param metric 方法入参 metric
     * @param current 当前，不允许为 null
     * @param threshold 方法入参 threshold
     * @return 结果字符串
     */
    private static String buildMessage(String metric, double current, double threshold) {
        switch (metric) {
            case "heap_percent":
                return "堆使用率 " + Math.round(current) + "% 超过阈值 " + Math.round(threshold) + "%";
            case "error_rate":
                return "错误率 " + String.format("%.1f", current) + "% 超过阈值 " + Math.round(threshold) + "%";
            case "active_leaks":
                return "活跃泄漏 " + Math.round(current) + " 个超过阈值 " + Math.round(threshold);
            case "cpu_percent":
                return "进程 CPU " + String.format("%.1f", current) + "% 超过阈值 " + Math.round(threshold) + "%";
            case "thread_count":
                return "线程数 " + Math.round(current) + " 超过阈值 " + Math.round(threshold);
            case "transmission_rate":
                return "传输速率 " + String.format("%.2f", current) + "/s 超过阈值 " + Math.round(threshold) + "/s";
            default:
                return metric + " 指标超阈值 " + threshold;
        }
    }

    /**
     * 错误率 = 最近 1 分钟传输错误数 / 总数 * 100（无数据返回 0）。
     * @return 结果数值
     */
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
     * 手动静默事件（同指纹继续触发时仅累加计数，不新增事件）。
     *
     * @param eventId    事件 ID
     * @param durationMs 静默时长（毫秒）
     * @return 是否找到并更新
     */
    public boolean silenceEvent(long eventId, long durationMs) {
        long until = System.currentTimeMillis() + Math.max(1000, durationMs);
        for (AlertEvent e : events) {
            if (e.id == eventId) {
                e.silencedUntil = until;
                e.status = "MUTED";
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
            m.put("fingerprint", e.fingerprint);
            m.put("repeatCount", e.repeatCount);
            m.put("silencedUntil", e.silencedUntil);
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

    /**
     * 未处置告警数（{@code status = OPEN}）。
     *
     * <p>侧栏红点要的是「全部未处置条数」，而 {@link #recentAlerts()} 只取最近 3 条，
     * 拿它计数会少算。这里直接对事件队列计数，随 SSE 帧下发，前端就不必再为红点
     * 轮询 {@code /api/apm/alerts}。</p>
     *
     * @return 未处置告警条数
     */
    public int openAlertCount() {
        int count = 0;
        for (AlertEvent e : events) {
            if ("OPEN".equals(e.status)) {
                count++;
            }
        }
        return count;
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

    // ------------------------------------------------------------------
    // 通知渠道 CRUD（Webhook 推送目标）
    // ------------------------------------------------------------------

    /**
     * 通知渠道列表（按 id 升序）。
     *
     * @return 渠道列表
     */
    public List<AlertChannel> listChannels() {
        List<AlertChannel> out = new ArrayList<>(channels.values());
        out.sort(Comparator.comparingLong(c -> c.id));
        return out;
    }

    /**
     * 新增通知渠道（自动分配 ID）。
     *
     * @param channel 渠道（id 忽略）
     * @return 保存后的渠道
     */
    public AlertChannel addChannel(AlertChannel channel) {
        channel.id = channelIdGen.getAndIncrement();
        channels.put(channel.id, channel);
        return channel;
    }

    /**
     * 删除通知渠道。
     *
     * @param id 渠道 ID
     * @return 是否删除成功
     */
    public boolean deleteChannel(long id) {
        return channels.remove(id) != null;
    }

    /**
     * 启停通知渠道。
     *
     * @param id      渠道 ID
     * @param enabled 是否启用
     * @return 是否更新成功
     */
    public boolean toggleChannel(long id, boolean enabled) {
        AlertChannel c = channels.get(id);
        if (c == null) {
            return false;
        }
        c.enabled = enabled;
        return true;
    }
}
