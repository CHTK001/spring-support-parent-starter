// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.logger.store;

import com.chua.starter.logger.event.LoggerEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 有界内存日志存储（engine=memory）：开发、联调与单机验证用。
 *
 * <p>超出容量丢最旧事件；游标为事件单调序号，分页稳定。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public class MemoryLoggerEventStore implements LoggerEventStore {

    private final int capacity;
    private final ArrayDeque<Entry> deque;
    private final AtomicLong seqGen = new AtomicLong();

    /**
     * @param capacity 最大保留条数
     */
    public MemoryLoggerEventStore(int capacity) {
        this.capacity = Math.max(1000, capacity);
        this.deque = new ArrayDeque<>(this.capacity);
    }

    @Override
    public void append(List<LoggerEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        synchronized (deque) {
            for (LoggerEvent event : events) {
                deque.addLast(new Entry(seqGen.incrementAndGet(), event));
            }
            while (deque.size() > capacity) {
                deque.pollFirst();
            }
        }
    }

    @Override
    public LoggerPage query(String app, String instance, String level, String keyword, String traceId,
                            Long start, Long end, String cursor, int limit) {
        List<Entry> snapshot = snapshot();
        long cursorSeq = parseCursor(cursor);
        Set<String> levels = levels(level);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();

        List<LoggerEvent> matched = new ArrayList<>();
        long lastSeq = 0L;
        for (Entry entry : snapshot) {
            if (entry.seq <= cursorSeq || !match(entry.event, app, instance, levels, kw, traceId, start, end)) {
                continue;
            }
            if (matched.size() >= limit) {
                break;
            }
            matched.add(entry.event);
            lastSeq = entry.seq;
        }
        final long lastSeqValue = lastSeq;
        boolean hasMore = matched.size() >= limit && snapshot.stream().anyMatch(e ->
                e.seq > lastSeqValue && match(e.event, app, instance, levels, kw, traceId, start, end));
        return new LoggerPage(matched, hasMore ? String.valueOf(lastSeq) : "");
    }

    @Override
    public List<Map<String, Object>> traces(String app, int limit) {
        List<Entry> snapshot = snapshot();
        List<LoggerEvent> events = new ArrayList<>(snapshot.size());
        for (Entry entry : snapshot) {
            events.add(entry.event);
        }
        return summarizeTraces(events, app, limit);
    }

    @Override
    public Map<String, Object> stats(String app, String instance) {
        List<Entry> snapshot = snapshot();
        List<LoggerEvent> filtered = new ArrayList<>();
        for (Entry entry : snapshot) {
            if (match(entry.event, app, instance, null, "", null, null, null)) {
                filtered.add(entry.event);
            }
        }
        return StatsCalculator.compute(filtered, null);
    }

    @Override
    public List<Map<String, Object>> apps() {
        List<Entry> snapshot = snapshot();
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Entry entry : snapshot) {
            LoggerEvent e = entry.event;
            String key = e.getApp() + "|" + e.getInstance();
            Map<String, Object> row = result.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("app", e.getApp());
                m.put("instance", e.getInstance());
                m.put("host", hostOf(e.getInstance()));
                m.put("lastSeen", 0L);
                m.put("eventCount", 0L);
                m.put("errorCount", 0L);
                return m;
            });
            row.put("lastSeen", Math.max((Long) row.get("lastSeen"), e.getTs()));
            row.put("eventCount", (Long) row.get("eventCount") + 1);
            if ("ERROR".equalsIgnoreCase(e.getLevel())) {
                row.put("errorCount", (Long) row.get("errorCount") + 1);
            }
        }
        return new ArrayList<>(result.values());
    }

    @Override
    public int delete(String app, String instance, String level, Long before) {
        int removed = 0;
        synchronized (deque) {
            removed = deque.size();
            deque.removeIf(entry -> match(entry.event, app, instance, levels(level), "", null, null, before));
            removed -= deque.size();
        }
        return removed;
    }

    private List<Entry> snapshot() {
        synchronized (deque) {
            return new ArrayList<>(deque);
        }
    }

    /**
     * 事件是否命中过滤条件（null/空条件视为不过滤）；供 server 包的 SSE 订阅过滤复用
     */
    public static boolean match(LoggerEvent e, String app, String instance, Set<String> levels,
                                String keyword, String traceId, Long start, Long end) {
        if (app != null && !app.isBlank() && !app.equals(e.getApp())) {
            return false;
        }
        if (instance != null && !instance.isBlank() && !instance.equals(e.getInstance())) {
            return false;
        }
        if (levels != null && !levels.isEmpty() && !levels.contains(e.getLevel())) {
            return false;
        }
        if (keyword != null && !keyword.isEmpty()) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            String logger = e.getLogger() == null ? "" : e.getLogger().toLowerCase();
            if (!message.contains(keyword) && !logger.contains(keyword)) {
                return false;
            }
        }
        if (traceId != null && !traceId.isBlank() && !traceId.equals(e.getTraceId())) {
            return false;
        }
        if (start != null && e.getTs() < start) {
            return false;
        }
        return end == null || e.getTs() <= end;
    }

    /**
     * 按 traceId 聚合链路摘要（内存/KV 两个存储共用）；无 traceId 的事件不参与聚合
     */
    static List<Map<String, Object>> summarizeTraces(List<LoggerEvent> events, String app, int limit) {
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (LoggerEvent e : events) {
            String traceId = e.getTraceId();
            if (traceId == null || traceId.isBlank()) {
                continue;
            }
            if (app != null && !app.isBlank() && !app.equals(e.getApp())) {
                continue;
            }
            Map<String, Object> row = grouped.computeIfAbsent(traceId, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("traceId", traceId);
                m.put("count", 0L);
                m.put("errorCount", 0L);
                m.put("firstTs", Long.MAX_VALUE);
                m.put("lastTs", 0L);
                m.put("apps", new LinkedHashSet<String>());
                m.put("instances", new LinkedHashSet<String>());
                return m;
            });
            row.put("count", (Long) row.get("count") + 1);
            if ("ERROR".equalsIgnoreCase(e.getLevel())) {
                row.put("errorCount", (Long) row.get("errorCount") + 1);
            }
            row.put("firstTs", Math.min((Long) row.get("firstTs"), e.getTs()));
            row.put("lastTs", Math.max((Long) row.get("lastTs"), e.getTs()));
            ((Set<String>) row.get("apps")).add(String.valueOf(e.getApp()));
            ((Set<String>) row.get("instances")).add(String.valueOf(e.getInstance()));
        }
        List<Map<String, Object>> result = new ArrayList<>(grouped.values());
        for (Map<String, Object> row : result) {
            long firstTs = (Long) row.get("firstTs");
            long lastTs = (Long) row.get("lastTs");
            row.put("durationMs", Math.max(0L, lastTs - firstTs));
            ((Set<String>) row.get("apps")).remove("null");
            ((Set<String>) row.get("instances")).remove("null");
        }
        result.sort((a, b) -> Long.compare((Long) b.get("lastTs"), (Long) a.get("lastTs")));
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return result.size() > safeLimit ? new ArrayList<>(result.subList(0, safeLimit)) : result;
    }

    static Set<String> levels(String level) {
        if (level == null || level.isBlank()) {
            return null;
        }
        Set<String> result = new HashSet<>();
        for (String part : level.split(",")) {
            if (!part.isBlank()) {
                result.add(part.trim().toUpperCase());
            }
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * 解析逗号分隔级别为集合（null/空返回 null）；供 server 包的 SSE 订阅过滤复用
     */
    public static Set<String> levelSet(String level) {
        return levels(level);
    }

    static long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(cursor.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    static String hostOf(String instance) {
        if (instance == null) {
            return "";
        }
        int at = instance.indexOf('@');
        return at >= 0 ? instance.substring(at + 1) : instance;
    }

    /**
     * 序号条目
     */
    private record Entry(long seq, LoggerEvent event) {
    }

    /**
     * 统计计算器：内存/KV 两个存储共用
     */
    static final class StatsCalculator {

        private StatsCalculator() {
        }

        /**
         * 基于事件列表计算统计；eps 曲线若传入（来自 SeriesEngine）则直接使用
         */
        static Map<String, Object> compute(List<LoggerEvent> events, List<List<Object>> eps) {
            Map<String, Object> result = new LinkedHashMap<>();
            Map<String, Long> levels = new HashMap<>();
            Map<String, Long> loggers = new HashMap<>();
            Set<String> apps = new HashSet<>();
            Set<String> instances = new HashSet<>();
            for (LoggerEvent e : events) {
                levels.merge(String.valueOf(e.getLevel()), 1L, Long::sum);
                loggers.merge(String.valueOf(e.getLogger()), 1L, Long::sum);
                apps.add(e.getApp());
                instances.add(e.getApp() + "|" + e.getInstance());
            }
            result.put("total", events.size());
            result.put("byLevel", levels);
            List<Map<String, Object>> topLoggers = new ArrayList<>();
            loggers.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .forEach(en -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("logger", en.getKey());
                        m.put("count", en.getValue());
                        topLoggers.add(m);
                    });
            result.put("topLoggers", topLoggers);
            result.put("eps", eps == null ? epsOfLastHour(events) : toEpsPoints(eps));
            result.put("apps", apps.size());
            result.put("instances", instances.size());
            return result;
        }

        /**
         * 近 1 小时按分钟聚合的事件数曲线；点结构为 {ts, count}（与前端契约一致）
         */
        private static List<Map<String, Object>> epsOfLastHour(List<LoggerEvent> events) {
            long now = System.currentTimeMillis();
            long minute = 60_000L;
            Map<Long, Long> buckets = new HashMap<>();
            for (LoggerEvent e : events) {
                if (e.getTs() >= now - 60 * minute) {
                    buckets.merge(e.getTs() / minute, 1L, Long::sum);
                }
            }
            List<Map<String, Object>> curve = new ArrayList<>();
            for (long i = 59; i >= 0; i--) {
                long bucketTs = (now / minute - i) * minute;
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("ts", bucketTs);
                point.put("count", buckets.getOrDefault(bucketTs / minute, 0L));
                curve.add(point);
            }
            return curve;
        }

        /**
         * 时序引擎返回的 [ts, count] 元组转 {ts, count} 点结构
         */
        private static List<Map<String, Object>> toEpsPoints(List<List<Object>> tuples) {
            List<Map<String, Object>> points = new ArrayList<>();
            if (tuples == null) {
                return points;
            }
            for (List<Object> tuple : tuples) {
                if (tuple == null || tuple.isEmpty()) {
                    continue;
                }
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("ts", tuple.get(0));
                point.put("count", tuple.size() > 1 ? tuple.get(1) : 0L);
                points.add(point);
            }
            return points;
        }
    }

    /**
     * 排序比较器（时间升序）
     */
    static final Comparator<LoggerEvent> TS_ORDER = Comparator.comparingLong(LoggerEvent::getTs);
}
