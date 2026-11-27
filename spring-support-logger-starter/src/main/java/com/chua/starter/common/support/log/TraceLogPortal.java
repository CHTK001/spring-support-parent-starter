package com.chua.starter.common.support.log;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import com.chua.common.support.utils.StringUtils;

/**
 * 链路日志汇聚内核（纯 JDK，不依赖任何日志框架/Spring）。
 *
 * <p>把同一 traceId 产生的多条日志事件归组，链路收口时一次性渲染成一个「块」交给
 * {@code sink}：MDC 只在块头打印一次，正文每行只保留 时间/级别/logger/消息，
 * 从而解决「异步/逐行输出导致 MDC 分散在各行、难以按链路阅读」的问题。</p>
 *
 * <p>收口触发有四个，任一命中即 flush 该组：</p>
 * <ol>
 *   <li>请求边界：{@link #flushCurrent(String)}（由 MDC 过滤器在 finally 调用）；</li>
 *   <li>组内条数达到 {@link Config#maxEventsPerTrace}；</li>
 *   <li>组内空闲超过 {@link Config#idleMillis}（长连接/SSE 的兜底，daemon 清扫线程）；</li>
 *   <li>待收口链路数超过 {@link Config#maxPendingTraces}（淘汰最老的，防内存无限增长）。</li>
 * </ol>
 *
 * <p>全局单例、默认关闭：未调用 {@link #configure(Config, Consumer)} 时所有方法都是 no-op，
 * 不分配缓冲、不起线程，对宿主零影响。输出 sink 的任何异常都在内部吞掉，绝不阻断宿主。</p>
 *
 * @author CH
 * @since 4.0.0
 */
public final class TraceLogPortal {

    /**
     * 时间格式化器（线程安全）。
     */
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /**
     * 空值占位。
     */
    private static final String NA = "N/A";

    /**
     * 分隔线宽度。
     */
    private static final String RULE = "╠════════════════════════════════════════════════════════════";

    /**
     * 待收口的链路组。
     */
    private static final ConcurrentHashMap<String, Group> GROUPS = new ConcurrentHashMap<>();

    /**
     * 是否已启用。
     */
    private static volatile boolean enabled = false;

    /**
     * 运行期配置。
     */
    private static Config config = Config.defaults();

    /**
     * 块输出目标。
     */
    private static Consumer<String> sink = block -> {
        // 默认丢弃，configure 时必须提供真实 sink
    };

    /**
     * 空闲清扫线程。
     */
    private static ScheduledExecutorService sweeper;

    private TraceLogPortal() {
    }

    /**
     * 启用汇聚器（幂等）。仅在 plugin.log.portal.enabled=true 时由适配层调用。
     *
     * @param cfg    配置，空则取默认值
     * @param output 汇聚块输出目标，不能为空
     */
    public static synchronized void configure(Config cfg, Consumer<String> output) {
        if (enabled) {
            return;
        }
        config = (cfg == null) ? Config.defaults() : cfg;
        sink = (output == null) ? (block -> {
        }) : output;
        enabled = true;
        sweeper = new ScheduledThreadPoolExecutor(1, daemonFactory()); // [P3C C1 修复] 显式线程池替代 Executors 包装
        long interval = Math.max(100L, config.sweepIntervalMillis);
        // 注意：scheduleWithFixedDelay 的任务一旦抛异常就不会再被调度，sweep() 内部必须自吞异常
        sweeper.scheduleWithFixedDelay(TraceLogPortal::sweep, interval, interval, TimeUnit.MILLISECONDS);
    }

    /**
     * 关闭汇聚器：停清扫线程、把缓冲区全部 flush（幂等）。
     */
    public static synchronized void shutdown() {
        if (!enabled) {
            return;
        }
        // 先停清扫、再收口残余；shutdown 期间 append 已因 enabled=false 被挡住，不会再来新事件
        enabled = false;
        if (sweeper != null) {
            sweeper.shutdownNow();
            sweeper = null;
        }
        flushAll();
        sink = block -> {
        };
    }

    /**
     * 追加一条日志事件到对应链路组。
     *
     * @param event 事件（MDC 必须带 traceId，否则忽略）
     */
    public static void append(Event event) {
        if (!enabled || event == null) {
            return;
        }
        String traceId = (event.mdc == null) ? null : event.mdc.get("traceId");
        if (StringUtils.isEmpty(traceId)) {
            return;
        }
        Group group = GROUPS.compute(traceId, (key, old) -> {
            Group g = (old == null) ? new Group() : old;
            g.touch();
            synchronized (g) {
                g.events.add(event);
            }
            return g;
        });
        if (group.count() >= config.maxEventsPerTrace) {
            flush(traceId);
        }
        int pending = GROUPS.size();
        if (pending > config.maxPendingTraces) {
            evictOldest(pending - config.maxPendingTraces);
        }
    }

    /**
     * 请求边界 flush（由 MDC 过滤器传入当前 traceId）。未启用时 no-op。
     *
     * @param traceId 当前链路标识
     */
    public static void flushCurrent(String traceId) {
        if (!enabled || StringUtils.isEmpty(traceId)) {
            return;
        }
        flush(traceId);
    }

    /**
     * 收口并输出指定链路的块。
     *
     * @param traceId 链路标识
     */
    public static void flush(String traceId) {
        if (traceId == null) {
            return;
        }
        Group group = GROUPS.remove(traceId);
        if (group == null) {
            return;
        }
        List<Event> snapshot;
        synchronized (group) {
            snapshot = new ArrayList<>(group.events);
            group.events.clear();
        }
        if (snapshot.isEmpty()) {
            return;
        }
        try {
            sink.accept(render(traceId, snapshot));
        } catch (Throwable ignore) {
            // 观测组件绝不阻断宿主，且此处不能再打日志（会递归）
        }
    }

    /**
     * 收口所有缓冲区。
     */
    public static void flushAll() {
        for (String key : GROUPS.keySet()) {
            flush(key);
        }
    }

    /**
     * 空闲清扫 + 超限淘汰（自吞异常，保证调度不被终止）。
     */
    private static void sweep() {
        try {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Group> entry : GROUPS.entrySet()) {
                if (now - entry.getValue().lastTouch > config.idleMillis) {
                    flush(entry.getKey());
                }
            }
            int pending = GROUPS.size();
            if (pending > config.maxPendingTraces) {
                evictOldest(pending - config.maxPendingTraces);
            }
        } catch (Throwable ignore) {
            // 清扫失败不影响宿主，也不能让 ScheduledExecutorService 停摆
        }
    }

    /**
     * 淘汰最老的若干组（按最后活动时间升序）。
     *
     * @param count 淘汰数量
     */
    private static void evictOldest(int count) {
        List<Map.Entry<String, Group>> all = new ArrayList<>(GROUPS.entrySet());
        all.sort(Comparator.comparingLong(e -> e.getValue().lastTouch));
        for (int i = 0; i < Math.min(count, all.size()); i++) {
            flush(all.get(i).getKey());
        }
    }

    /**
     * 渲染一条链路的汇聚块。
     *
     * @param traceId 链路标识
     * @param events  事件快照
     * @return 多行文本
     */
    private static String render(String traceId, List<Event> events) {
        events.sort(Comparator.comparingLong(e -> e.timestamp));
        Event first = events.get(0);
        Event last = events.get(events.size() - 1);
        StringBuilder sb = new StringBuilder(512 + events.size() * 96);
        sb.append("╔══ 链路 [").append(traceId).append("] 共 ").append(events.size()).append(" 条 ══\n");
        sb.append("║ MDC ");
        Map<String, String> mdc = (first.mdc == null) ? Map.of() : first.mdc;
        if (mdc.isEmpty()) {
            sb.append("(空)");
        } else {
            for (Map.Entry<String, String> entry : mdc.entrySet()) {
                sb.append(entry.getKey()).append('=')
                        .append(entry.getValue() == null ? NA : entry.getValue()).append(' ');
            }
        }
        sb.append('\n');
        sb.append("║ 起 ").append(formatTime(first.timestamp))
                .append("  止 ").append(formatTime(last.timestamp))
                .append("  跨度 ").append(last.timestamp - first.timestamp).append("ms\n");
        sb.append(RULE).append('\n');
        for (Event event : events) {
            sb.append("║ ").append(formatTime(event.timestamp)).append(' ')
                    .append(pad(event.level, 5)).append(" [")
                    .append(nullToEmpty(event.thread)).append("] ")
                    .append(nullToEmpty(event.logger)).append(" - ")
                    .append(nullToEmpty(event.message)).append('\n');
            if (event.throwableText != null && !event.throwableText.isEmpty()) {
                for (String line : event.throwableText.split("\n")) {
                    sb.append("║     ").append(line).append('\n');
                }
            }
        }
        sb.append("╚══ 链路结束 ══\n");
        return sb.toString();
    }

    /**
     * 格式化时间戳。
     *
     * @param timestamp 毫秒时间戳
     * @return HH:mm:ss.SSS
     */
    private static String formatTime(long timestamp) {
        return TIME.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()));
    }

    /**
     * 右侧补空格到指定宽度。
     *
     * @param value 原值
     * @param width 宽度
     * @return 补齐后的文本
     */
    private static String pad(String value, int width) {
        String text = nullToEmpty(value);
        if (text.length() >= width) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /**
     * null 转空串。
     *
     * @param value 原值
     * @return 非空串
     */
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * daemon 线程工厂。
     *
     * @return 线程工厂
     */
    private static ThreadFactory daemonFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "trace-log-portal-sweeper");
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * 单条日志事件的纯数据快照（由适配层从 ILoggingEvent 转换）。
     */
    public static final class Event {
        /**
         * 时间戳。
         */
        public final long timestamp;
        /**
         * 级别。
         */
        public final String level;
        /**
         * logger 名。
         */
        public final String logger;
        /**
         * 线程名。
         */
        public final String thread;
        /**
         * 格式化消息。
         */
        public final String message;
        /**
         * 异常栈文本（已渲染），可空。
         */
        public final String throwableText;
        /**
         * 事件创建时的 MDC 快照。
         */
        public final Map<String, String> mdc;

        /**
         * 构造事件。
         *
         * @param timestamp     时间戳
         * @param level         级别
         * @param logger        logger 名
         * @param thread        线程名
         * @param message       格式化消息
         * @param throwableText 异常栈文本
         * @param mdc           MDC 快照
         */
        public Event(long timestamp, String level, String logger, String thread,
                     String message, String throwableText, Map<String, String> mdc) {
            this.timestamp = timestamp;
            this.level = level;
            this.logger = logger;
            this.thread = thread;
            this.message = message;
            this.throwableText = throwableText;
            this.mdc = mdc;
        }
    }

    /**
     * 运行期配置。
     */
    public static final class Config {
        /**
         * 单链路最大缓冲条数。
         */
        public final int maxEventsPerTrace;
        /**
         * 空闲多久（毫秒）自动收口。
         */
        public final long idleMillis;
        /**
         * 最大待收口链路数。
         */
        public final int maxPendingTraces;
        /**
         * 清扫周期（毫秒）。
         */
        public final long sweepIntervalMillis;

        /**
         * 构造配置。
         *
         * @param maxEventsPerTrace     单链路最大缓冲条数
         * @param idleMillis            空闲收口阈值
         * @param maxPendingTraces      最大待收口链路数
         * @param sweepIntervalMillis   清扫周期
         */
        public Config(int maxEventsPerTrace, long idleMillis,
                      int maxPendingTraces, long sweepIntervalMillis) {
            this.maxEventsPerTrace = Math.max(1, maxEventsPerTrace);
            this.idleMillis = Math.max(100L, idleMillis);
            this.maxPendingTraces = Math.max(1, maxPendingTraces);
            this.sweepIntervalMillis = Math.max(100L, sweepIntervalMillis);
        }

        /**
         * 默认配置。
         *
         * @return 配置
         */
        public static Config defaults() {
            return new Config(200, 3000L, 512, 1000L);
        }
    }

    /**
     * 一条链路的缓冲组。
     */
    private static final class Group {
        /**
         * 事件列表（所有访问都 synchronized(group)）。
         */
        private final List<Event> events = new ArrayList<>();
        /**
         * 最后活动时间（volatile 供清扫线程无锁读取）。
         */
        private volatile long lastTouch = System.currentTimeMillis();

        /**
         * 刷新最后活动时间。
         */
        void touch() {
            lastTouch = System.currentTimeMillis();
        }

        /**
         * 当前条数。
         *
         * @return 条数
         */
        int count() {
            synchronized (this) {
                return events.size();
            }
        }
    }
}
