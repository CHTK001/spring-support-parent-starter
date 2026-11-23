// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.logger.store;

import com.chua.common.support.lang.datasource.kv.KvEngine;
import com.chua.common.support.lang.datasource.series.SeriesEngine;
import com.chua.starter.logger.event.LoggerEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 持久化日志存储（engine=datasource-bean / redis-bean）：
 * 原始事件落 {@link KvEngine}（带保留期 TTL），每秒事件速率落 {@link SeriesEngine}
 * （target=logger_eps），应用/实例登记与计数复用 KvEngine。
 *
 * <p>键布局：
 * <ul>
 *   <li>事件：{@code logger:{app}:{ts:016d}:{seq:06d}}（字典序即时间序）</li>
 *   <li>应用登记：{@code logger-app:{app}}</li>
 *   <li>实例登记：{@code logger-inst:{app}:{instanceHash}}（TTL 2h）</li>
 *   <li>计数：{@code logger-cnt:{app}:total|error}</li>
 * </ul></p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class KvLoggerEventStore implements LoggerEventStore {

    /**
     * JSON 序列化
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 事件速率指标名
     */
    private static final String EPS_TARGET = "logger_eps";

    /**
     * eps 保留期清理最小间隔：事件 KV 有 TTL，时序表没有，靠本清理兜底防无限增长
     */
    private static final long EPS_PURGE_INTERVAL_MILLIS = 3600_000L;

    /**
     * 流式扫描批大小：前缀查询按批拉取、逐批消费，
     * 避免一次性物化全量 KV（大库可达成十万条）把堆打爆
     */
    private static final int SCAN_BATCH = 500;

    private final KvEngine kv;
    private final SeriesEngine series;
    private final long retentionHours;
    private final AtomicInteger seq = new AtomicInteger();

    /**
     * 同类失败仅告警一次的开关（实例级）：主链路旁路写入/查询失败若逐条打印，
     * 会以每秒一批的频率刷屏（缺表事故即因此长期潜伏在静默里）
     */
    private final AtomicBoolean kvWriteWarned = new AtomicBoolean();
    private final AtomicBoolean epsWriteWarned = new AtomicBoolean();
    private final AtomicBoolean epsReadWarned = new AtomicBoolean();
    private final AtomicBoolean epsPurgeWarned = new AtomicBoolean();
    private final AtomicBoolean counterWriteWarned = new AtomicBoolean();
    private final AtomicBoolean counterReadWarned = new AtomicBoolean();
    private final AtomicBoolean registryWarned = new AtomicBoolean();
    private final AtomicBoolean corruptEventWarned = new AtomicBoolean();

    /**
     * 上次 eps 保留期清理时间（毫秒），CAS 防并发重复触发
     */
    private final AtomicLong lastEpsPurgeAt = new AtomicLong();

    /**
     * @param kv             KV 引擎（必填）
     * @param series         时序引擎（可空：无则 eps 由事件扫描聚合）
     * @param retentionHours 保留时长（小时）
     */
    public KvLoggerEventStore(KvEngine kv, SeriesEngine series, long retentionHours) {
        this.kv = kv;
        this.series = series;
        this.retentionHours = retentionHours <= 0 ? 72L : retentionHours;
    }

    @Override
    public void append(List<LoggerEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        Duration ttl = Duration.ofHours(retentionHours);
        Map<String, Integer> epsCounts = new HashMap<>();
        Set<String> touched = new HashSet<>();
        for (LoggerEvent event : events) {
            String key = eventKey(event);
            try {
                String json = MAPPER.writeValueAsString(event);
                if (kv.supportsTtl()) {
                    kv.put(key, json, ttl);
                } else {
                    kv.put(key, json);
                }
            } catch (Exception e) {
                // 单条写入失败不阻断批次；但必须留痕——静默会掩盖缺表/断链等系统性故障
                if (kvWriteWarned.compareAndSet(false, true)) {
                    log.warn("[logger] 日志事件 KV 落库失败（本实例仅告警一次）: {}", e.getMessage());
                }
                continue;
            }
            // 同一批同 (app, instance) 只探测登记一次，避免逐条 kv.get 的读放大
            if (touched.add(event.getApp() + "|" + event.getInstance())) {
                touchRegistry(event, ttl);
            }
            epsCounts.merge(String.valueOf(event.getApp()), 1, Integer::sum);
            String cntKey = "logger-cnt:" + event.getApp() + ":total";
            String errKey = "logger-cnt:" + event.getApp() + ":error";
            try {
                kv.incr(cntKey);
                if ("ERROR".equalsIgnoreCase(event.getLevel())) {
                    kv.incr(errKey);
                }
            } catch (Exception e) {
                // 计数失败不影响主流程；但必须留痕，否则 total/error 会悄悄失真
                if (counterWriteWarned.compareAndSet(false, true)) {
                    log.warn("[logger] 日志计数累加失败（本实例仅告警一次），total/error 计数可能失真: {}",
                            e.getMessage());
                }
            }
        }
        writeEps(epsCounts);
    }

    @Override
    public LoggerPage query(String app, String instance, String level, String keyword, String traceId,
                            Long start, Long end, String cursor, int limit) {
        Set<String> levels = MemoryLoggerEventStore.levels(level);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        long cTs = 0L;
        long cSeq = 0L;
        String cursorKey = cursor == null ? "" : cursor.trim();
        if (!cursorKey.isEmpty()) {
            String[] parts = cursorKey.split(":");
            if (parts.length >= 2) {
                cTs = parse(parts[0]);
                cSeq = parse(parts[1]);
            }
        }

        String prefix = "logger:" + (app == null || app.isBlank() ? "" : app.trim() + ":");
        // 流式分批扫描：凑满 limit+1 条匹配即可判定还有下一页并收口，
        // 避免全量物化大前缀数据把堆打爆
        List<Object[]> matched = new ArrayList<>();
        String afterKey = "";
        while (matched.size() < limit + 1) {
            Map<String, String> batch = kv.findAllByPrefix(prefix, afterKey, SCAN_BATCH);
            if (batch.isEmpty()) {
                break;
            }
            for (Map.Entry<String, String> en : batch.entrySet()) {
                afterKey = en.getKey();
                long[] order = keyOrder(en.getKey());
                if (order == null) {
                    continue;
                }
                if (order[0] < cTs || (order[0] == cTs && order[1] <= cSeq)) {
                    continue;
                }
                LoggerEvent event = parseEvent(en.getValue());
                if (event == null) {
                    continue;
                }
                if (MemoryLoggerEventStore.match(event, app, instance, levels, kw, traceId, start, end)) {
                    matched.add(new Object[]{order[0], order[1], event});
                    if (matched.size() > limit) {
                        break;
                    }
                }
            }
        }
        boolean hasMore = matched.size() > limit;
        List<LoggerEvent> items = new ArrayList<>();
        String nextCursor = "";
        int bound = Math.min(limit, matched.size());
        for (int i = 0; i < bound; i++) {
            Object[] row = matched.get(i);
            items.add((LoggerEvent) row[2]);
            nextCursor = row[0] + ":" + row[1];
        }
        return new LoggerPage(items, hasMore ? nextCursor : "");
    }

    @Override
    public List<Map<String, Object>> traces(String app, int limit) {
        return MemoryLoggerEventStore.summarizeTraces(scanEvents(app, 50000), app, limit);
    }

    @Override
    public Map<String, Object> stats(String app, String instance) {
        List<LoggerEvent> events = scanEvents(app, 50000);
        List<LoggerEvent> filtered = new ArrayList<>();
        for (LoggerEvent e : events) {
            if (MemoryLoggerEventStore.match(e, app, instance, null, "", null, null, null)) {
                filtered.add(e);
            }
        }
        List<List<Object>> eps = epsFromSeries(app);
        return MemoryLoggerEventStore.StatsCalculator.compute(filtered, eps);
    }

    @Override
    public List<Map<String, Object>> apps() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> en : kv.findAllByPrefix("logger-app:").entrySet()) {
            Map<String, Object> node = readMap(en.getValue());
            if (node == null || node.get("app") == null) {
                continue;
            }
            String app = String.valueOf(node.get("app"));
            Map<String, Object> row = result.computeIfAbsent("app|" + app, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("app", app);
                m.put("lastSeen", 0L);
                m.put("eventCount", counter(app, "total"));
                m.put("errorCount", counter(app, "error"));
                return m;
            });
            row.put("lastSeen", Math.max((Long) row.get("lastSeen"), asLong(node.get("lastSeen"))));
        }
        for (Map.Entry<String, String> en : kv.findAllByPrefix("logger-inst:").entrySet()) {
            Map<String, Object> node = readMap(en.getValue());
            if (node == null || node.get("app") == null || node.get("instance") == null) {
                continue;
            }
            String app = String.valueOf(node.get("app"));
            Map<String, Object> row = result.get("app|" + app);
            if (row == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("app", app);
            m.put("instance", String.valueOf(node.get("instance")));
            m.put("host", String.valueOf(node.getOrDefault("host", "")));
            m.put("lastSeen", asLong(node.get("lastSeen")));
            m.put("eventCount", counter(app, "total"));
            m.put("errorCount", counter(app, "error"));
            result.put("inst|" + app + "|" + node.get("instance"), m);
        }
        return new ArrayList<>(result.values());
    }

    @Override
    public int delete(String app, String instance, String level, Long before) {
        int removed = 0;
        String prefix = "logger:" + (app == null || app.isBlank() ? "" : app.trim() + ":");
        Set<String> levelSet = MemoryLoggerEventStore.levels(level);
        // 被删事件按应用聚合 (total, error) 扣减量：删除是低频管理操作，按批合并扣减避免逐条读改写
        Map<String, long[]> removedByApp = new HashMap<>();
        // 流式分批扫描删除：已删键位于游标之后不会被重访，游标单调推进保证收敛
        String afterKey = "";
        while (true) {
            Map<String, String> batch = kv.findAllByPrefix(prefix, afterKey, SCAN_BATCH);
            if (batch.isEmpty()) {
                break;
            }
            for (Map.Entry<String, String> en : batch.entrySet()) {
                afterKey = en.getKey();
                LoggerEvent event = parseEvent(en.getValue());
                if (event == null) {
                    continue;
                }
                if (MemoryLoggerEventStore.match(event, app, instance, levelSet, "", null, null, before)
                        && kv.delete(en.getKey())) {
                    removed++;
                    long[] counters = removedByApp.computeIfAbsent(String.valueOf(event.getApp()),
                            k -> new long[2]);
                    counters[0]++;
                    if ("ERROR".equalsIgnoreCase(event.getLevel())) {
                        counters[1]++;
                    }
                }
            }
        }
        decrCounters(removedByApp);
        return removed;
    }

    /**
     * 计数器批量扣减（{@link KvEngine} 只有 {@code incr}，无 decr）：读-改-写回退。
     * 计数本身允许短暂漂移（incr 失败仅告警不阻断），这里扣减失败同样只告警，且下限截到 0。
     */
    private void decrCounters(Map<String, long[]> removedByApp) {
        for (Map.Entry<String, long[]> en : removedByApp.entrySet()) {
            adjustCounter("logger-cnt:" + en.getKey() + ":total", -en.getValue()[0]);
            if (en.getValue()[1] > 0) {
                adjustCounter("logger-cnt:" + en.getKey() + ":error", -en.getValue()[1]);
            }
        }
    }

    private void adjustCounter(String key, long delta) {
        try {
            String current = kv.get(key);
            long value = current == null ? 0L : Long.parseLong(current);
            kv.put(key, String.valueOf(Math.max(0L, value + delta)));
        } catch (Exception e) {
            if (counterWriteWarned.compareAndSet(false, true)) {
                log.warn("[logger] 日志计数扣减失败（本实例仅告警一次），应用计数可能偏大: {}", e.getMessage());
            }
        }
    }

    private void touchRegistry(LoggerEvent event, Duration ttl) {
        try {
            long now = System.currentTimeMillis();
            String appKey = "logger-app:" + event.getApp();
            Map<String, Object> appNode = readMap(kv.get(appKey));
            if (appNode == null || asLong(appNode.get("lastSeen")) < now - 30_000L) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("app", event.getApp());
                node.put("lastSeen", now);
                kv.put(appKey, MAPPER.writeValueAsString(node));
            }
            String instKey = "logger-inst:" + event.getApp() + ":" + shortHash(event.getInstance());
            Map<String, Object> instNode = readMap(kv.get(instKey));
            if (instNode == null || asLong(instNode.get("lastSeen")) < now - 30_000L) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("app", event.getApp());
                node.put("instance", event.getInstance());
                node.put("host", MemoryLoggerEventStore.hostOf(event.getInstance()));
                node.put("lastSeen", now);
                if (kv.supportsTtl()) {
                    kv.put(instKey, MAPPER.writeValueAsString(node), Duration.ofHours(2));
                } else {
                    kv.put(instKey, MAPPER.writeValueAsString(node));
                }
            }
        } catch (Exception e) {
            // 登记失败不影响事件写入；但必须留痕，否则应用/实例列表会静默缺员
            if (registryWarned.compareAndSet(false, true)) {
                log.warn("[logger] 应用/实例登记失败（本实例仅告警一次），应用列表可能缺员: {}",
                        e.getMessage());
            }
        }
    }

    private void writeEps(Map<String, Integer> epsCounts) {
        if (series == null || epsCounts.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Integer> en : epsCounts.entrySet()) {
            try {
                series.writePoint(EPS_TARGET, appId(en.getKey()), "count", en.getValue(), now);
            } catch (Exception e) {
                // 指标写入失败不影响主流程；但必须留痕，否则缺表等故障会静默退化成扫描聚合
                if (epsWriteWarned.compareAndSet(false, true)) {
                    log.warn("[logger] eps 指标写入失败（本实例仅告警一次），eps 曲线将退化为事件扫描聚合: {}",
                            e.getMessage());
                }
            }
        }
        maybePurgeEps();
    }

    /**
     * 顺带清理过期 eps 点：事件 KV 有保留期 TTL，而时序表没有，
     * 不清理会随时间无限增长。每小时至多触发一次，失败不影响写入主流程。
     */
    private void maybePurgeEps() {
        long now = System.currentTimeMillis();
        long last = lastEpsPurgeAt.get();
        if (now - last < EPS_PURGE_INTERVAL_MILLIS) {
            return;
        }
        if (!lastEpsPurgeAt.compareAndSet(last, now)) {
            return;
        }
        try {
            series.purge(EPS_TARGET, null, now - retentionHours * 3600_000L);
        } catch (Exception e) {
            if (epsPurgeWarned.compareAndSet(false, true)) {
                log.warn("[logger] logger_eps 保留期清理失败（本实例仅告警一次）: {}", e.getMessage());
            }
        }
    }

    private List<List<Object>> epsFromSeries(String app) {
        if (series == null) {
            return null;
        }
        try {
            Long monitorId = app == null || app.isBlank() ? null : appId(app);
            return series.series(EPS_TARGET, monitorId, "count", 1, "1m");
        } catch (Exception e) {
            // 查询失败退化为事件扫描聚合（慢路径）；必须留痕，静默会掩盖缺表等故障
            if (epsReadWarned.compareAndSet(false, true)) {
                log.warn("[logger] eps 曲线查询失败（本实例仅告警一次），统计退化为事件扫描聚合: {}",
                        e.getMessage());
            }
            return null;
        }
    }

    private List<LoggerEvent> scanEvents(String app, int max) {
        List<LoggerEvent> events = new ArrayList<>();
        String prefix = "logger:" + (app == null || app.isBlank() ? "" : app.trim() + ":");
        // 流式分批扫描：取满 max 条即收口，避免全量物化大前缀数据把堆打爆
        String afterKey = "";
        while (events.size() < max) {
            Map<String, String> batch = kv.findAllByPrefix(prefix, afterKey, SCAN_BATCH);
            if (batch.isEmpty()) {
                break;
            }
            for (Map.Entry<String, String> en : batch.entrySet()) {
                afterKey = en.getKey();
                LoggerEvent event = parseEvent(en.getValue());
                if (event != null) {
                    events.add(event);
                    if (events.size() >= max) {
                        break;
                    }
                }
            }
        }
        events.sort(MemoryLoggerEventStore.TS_ORDER);
        return events;
    }

    private long counter(String app, String type) {
        try {
            String value = kv.get("logger-cnt:" + app + ":" + type);
            return value == null ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            if (counterReadWarned.compareAndSet(false, true)) {
                log.warn("[logger] 日志计数读取失败（本实例仅告警一次），按 0 处理: {}", e.getMessage());
            }
            return 0L;
        }
    }

    private String eventKey(LoggerEvent event) {
        long ts = event.getTs() <= 0 ? System.currentTimeMillis() : event.getTs();
        event.setTs(ts);
        int s = seq.incrementAndGet() & 0xFFFFF;
        return "logger:" + event.getApp() + ":"
                + String.format("%016d", ts) + ":" + String.format("%06d", s);
    }

    /**
     * 从事件键解析 (ts, seq)；解析失败返回 null
     */
    private static long[] keyOrder(String key) {
        String[] parts = key.split(":");
        if (parts.length < 4) {
            return null;
        }
        long ts = parse(parts[2]);
        long seqVal = parse(parts[3]);
        return new long[]{ts, seqVal};
    }

    private LoggerEvent parseEvent(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, LoggerEvent.class);
        } catch (Exception e) {
            // 坏行静默丢弃会让「少了几条日志」无从察觉，必须留痕（只记长度防刷屏/注入）
            if (corruptEventWarned.compareAndSet(false, true)) {
                log.warn("[logger] 日志事件反序列化失败（本实例仅告警一次），坏行已跳过: 长度={} 原因={}",
                        json.length(), e.getMessage());
            }
            return null;
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static long parse(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static Long appId(String app) {
        return (long) Math.abs(app.hashCode());
    }

    private static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "00000000";
        }
    }
}
