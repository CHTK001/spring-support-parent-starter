package com.chua.report.client.starter.report;

import com.chua.report.client.starter.sync.MonitorTopics;
import com.chua.sync.support.client.SyncClient;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * URL QPS 统计上报器
 * <p>
 * 收集 URL 请求统计数据并定时上报到服务端
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025/01/17
 */
@Slf4j
public class UrlQpsReporter {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UrlQpsReporter.class);

    private static final UrlQpsReporter INSTANCE = new UrlQpsReporter();

    @Getter
    @Setter
    private SyncClient syncClient;

    /**
     * 设置 SyncClient
     *
     * @param syncClient SyncClient 实例
     */
    public void setSyncClient(SyncClient syncClient) {
        this.syncClient = syncClient;
    }

    @Getter
    @Setter
    private String appName;

    /**
     * 设置应用名称
     *
     * @param appName 应用名称
     */
    public void setAppName(String appName) {
        this.appName = appName;
    }

    @Getter
    @Setter
    private String host;

    /**
     * 设置主机地址
     *
     * @param host 主机地址
     */
    public void setHost(String host) {
        this.host = host;
    }

    @Getter
    @Setter
    private long intervalSeconds = 30;

    /**
     * 设置上报间隔
     *
     * @param intervalSeconds 上报间隔（秒）
     */
    public void setIntervalSeconds(long intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    /**
     * URL 统计数据
     * key: URL pattern
     * value: UrlStats
     */
    private final ConcurrentHashMap<String, UrlStats> urlStatsMap = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    private UrlQpsReporter() {
    }

    public static UrlQpsReporter getInstance() {
        return INSTANCE;
    }

    /**
     * 记录请求
     *
     * @param url          请求 URL
     * @param method       HTTP 方法
     * @param duration     请求耗时（毫秒）
     * @param success      是否成功
     * @param statusCode   HTTP 状态码
     */
    public void recordRequest(String url, String method, long duration, boolean success, int statusCode) {
        if (!running) {
            return;
        }

        String key = method + " " + url;
        UrlStats stats = urlStatsMap.computeIfAbsent(key, k -> new UrlStats(url, method));
        stats.record(duration, success, statusCode);
    }

    /**
     * 启动上报
     */
    public synchronized void start() {
        if (running) {
            log.warn("[UrlQpsReporter] 已经在运行中");
            return;
        }

        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "url-qps-reporter");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::reportStats, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        log.info("[UrlQpsReporter] 已启动，上报间隔: {}秒", intervalSeconds);
    }

    /**
     * 停止上报
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // 最后上报一次
        reportStats();
        urlStatsMap.clear();
        log.info("[UrlQpsReporter] 已停止");
    }

    /**
     * 上报统计数据
     */
    private void reportStats() {
        if (syncClient == null || !syncClient.isConnected()) {
            log.debug("[UrlQpsReporter] SyncClient 未连接，跳过上报");
            return;
        }

        if (urlStatsMap.isEmpty()) {
            log.debug("[UrlQpsReporter] 无数据需要上报");
            return;
        }

        try {
            // 收集并重置统计数据
            List<Map<String, Object>> statsList = new ArrayList<>();
            for (Map.Entry<String, UrlStats> entry : urlStatsMap.entrySet()) {
                UrlStats stats = entry.getValue();
                Map<String, Object> data = stats.collectAndReset();
                if (data != null) {
                    statsList.add(data);
                }
            }

            if (statsList.isEmpty()) {
                return;
            }

            // 构建上报数据
            Map<String, Object> reportData = new HashMap<>();
            reportData.put("appName", appName);
            reportData.put("host", host);
            reportData.put("timestamp", System.currentTimeMillis());
            reportData.put("intervalSeconds", intervalSeconds);
            reportData.put("stats", statsList);

            // 发送到服务端
            syncClient.publish(MonitorTopics.URL_QPS_REPORT, reportData);
            log.debug("[UrlQpsReporter] 上报成功，URL 数量: {}", statsList.size());

        } catch (Exception e) {
            log.error("[UrlQpsReporter] 上报失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取当前统计快照（用于调试）
     *
     * @return 统计数据快照
     */
    public Map<String, Map<String, Object>> getStatsSnapshot() {
        Map<String, Map<String, Object>> snapshot = new HashMap<>();
        for (Map.Entry<String, UrlStats> entry : urlStatsMap.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().getSnapshot());
        }
        return snapshot;
    }

    /**
     * URL 统计数据
     */
    private static class UrlStats {
        private final String url;
        private final String method;

        // 当前周期统计
        private final AtomicLong totalCount = new AtomicLong(0);
        private final AtomicLong successCount = new AtomicLong(0);
        private final AtomicLong failCount = new AtomicLong(0);
        private final AtomicLong totalDuration = new AtomicLong(0);
        private volatile long minDuration = Long.MAX_VALUE;
        private volatile long maxDuration = 0;

        // 历史累计统计
        private final AtomicLong historyTotalCount = new AtomicLong(0);
        private final AtomicLong historySuccessCount = new AtomicLong(0);
        private final AtomicLong historyFailCount = new AtomicLong(0);

        // 状态码统计
        private final ConcurrentHashMap<Integer, AtomicLong> statusCodeCount = new ConcurrentHashMap<>();

        UrlStats(String url, String method) {
            this.url = url;
            this.method = method;
        }

        /**
         * 记录请求
         */
        void record(long duration, boolean success, int statusCode) {
            totalCount.incrementAndGet();
            historyTotalCount.incrementAndGet();
            totalDuration.addAndGet(duration);

            if (success) {
                successCount.incrementAndGet();
                historySuccessCount.incrementAndGet();
            } else {
                failCount.incrementAndGet();
                historyFailCount.incrementAndGet();
            }

            // 更新最小/最大耗时
            if (duration < minDuration) {
                minDuration = duration;
            }
            if (duration > maxDuration) {
                maxDuration = duration;
            }

            // 统计状态码
            statusCodeCount.computeIfAbsent(statusCode, k -> new AtomicLong(0)).incrementAndGet();
        }

        /**
         * 收集数据并重置当前周期计数
         *
         * @return 统计数据，如果无数据返回 null
         */
        Map<String, Object> collectAndReset() {
            long count = totalCount.getAndSet(0);
            if (count == 0) {
                return null;
            }

            long success = successCount.getAndSet(0);
            long fail = failCount.getAndSet(0);
            long duration = totalDuration.getAndSet(0);
            long min = minDuration;
            long max = maxDuration;

            // 重置 min/max
            minDuration = Long.MAX_VALUE;
            maxDuration = 0;

            // 收集状态码统计
            Map<String, Long> statusCodes = new HashMap<>();
            for (Map.Entry<Integer, AtomicLong> entry : statusCodeCount.entrySet()) {
                long statusCount = entry.getValue().getAndSet(0);
                if (statusCount > 0) {
                    statusCodes.put(String.valueOf(entry.getKey()), statusCount);
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("url", url);
            data.put("method", method);
            data.put("count", count);
            data.put("successCount", success);
            data.put("failCount", fail);
            data.put("avgDuration", count > 0 ? duration / count : 0);
            data.put("minDuration", min == Long.MAX_VALUE ? 0 : min);
            data.put("maxDuration", max);
            data.put("qps", count / 30.0);
            data.put("statusCodes", statusCodes);
            // 历史累计
            data.put("historyTotalCount", historyTotalCount.get());
            data.put("historySuccessCount", historySuccessCount.get());
            data.put("historyFailCount", historyFailCount.get());

            return data;
        }

        /**
         * 获取快照（不重置）
         */
        Map<String, Object> getSnapshot() {
            Map<String, Object> data = new HashMap<>();
            data.put("url", url);
            data.put("method", method);
            data.put("currentCount", totalCount.get());
            data.put("historyTotalCount", historyTotalCount.get());
            data.put("historySuccessCount", historySuccessCount.get());
            data.put("historyFailCount", historyFailCount.get());
            return data;
        }
    }
}
