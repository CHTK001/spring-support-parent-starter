// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.logger.store;

import com.chua.starter.logger.event.LoggerEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    public LoggerPage query(String app, String instance, String level, String keyword,
                            Long start, Long end, String cursor, int limit) {
        List<Entry> snapshot = snapshot();
        long cursorSeq = parseCursor(cursor);
        Set<String> levels = levels(level);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();

        List<LoggerEvent> matched = new ArrayList<>();
        long lastSeq = 0L;
        for (Entry entry : snapshot) {
            if (entry.seq <= cursorSeq || !match(entry.event, app, instance, levels, kw, start, end)) {
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
                e.seq > lastSeqValue && match(e.event, app, instance, levels, kw, start, end));
        return new LoggerPage(matched, hasMore ? String.valueOf(lastSeq) : "");
    }

    @Override
    public Map<String, Object> stats(String app, String instance) {
        List<Entry> snapshot = snapshot();
        List<LoggerEvent> filtered = new ArrayList<>();
        for (Entry entry : snapshot) {
            if (match(entry.event, app, instance, null, "", null, null)) {
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
    public int delete(String app, String instance, Long before) {
        int removed = 0;
        synchronized (deque) {
            removed = deque.size();
            deque.removeIf(entry -> match(entry.event, app, instance, null, "", null, before));
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
     * 事件是否命中过滤条件（null/空条件视为不过滤）
     */
    static boolean match(LoggerEvent e, String app, String instance, Set<String> levels,
                         String keyword, Long start, Long end) {
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
        if (start != null && e.getTs() < start) {
            return false;
        }
        return end == null || e.getTs() <= end;
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
