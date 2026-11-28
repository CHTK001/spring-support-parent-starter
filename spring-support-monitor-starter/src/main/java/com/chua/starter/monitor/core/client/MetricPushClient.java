package com.chua.starter.monitor.core.client;

import com.chua.starter.monitor.core.model.AgentMetricsPushVO;
import com.chua.starter.monitor.core.spi.MetricPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * monitor 客户端推送引擎（agent 模式核心）。
 *
 * <p>复用服务端 HTTP 上报管线（AgentEndpointController，X-Agent-Key 鉴权）：
 * 周期心跳 {@code GET /ms/monitor/agent/heartbeat} + 周期指标推送
 * {@code POST /ms/monitor/agent/metrics}。指标经 {@link MonitorMetricCollector}
 * 采集，负载结构与服务端 {@link AgentMetricsPushVO} 一致，落库/告警/任务下发
 * 全部走服务端既有实现，服务端零新增。</p>
 *
 * <p>推送前先过 {@link MonitorCapabilityDetector}：目标未运行 monitor 时静默
 * 降级。单线程守护调度，任务体内捕获一切异常，保证调度器不被单次失败杀死。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class MetricPushClient {

    /**
     * 心跳端点（服务端 AgentEndpointController）
     */
    private static final String HEARTBEAT_PATH = "/ms/monitor/agent/heartbeat";

    /**
     * 指标推送端点
     */
    private static final String METRICS_PATH = "/ms/monitor/agent/metrics";

    /**
     * 日志里指标点数量上限（避免大 payload 刷日志）
     */
    private static final int LOG_POINTS_LIMIT = 8;

    private final MonitorClientProperties properties;
    private final MonitorMetricCollector collector;
    private final MonitorCapabilityDetector capabilityDetector;
    private final HttpClient httpClient;

    /**
     * 负载序列化器（线程安全，单实例复用）
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 守护调度器；null 表示未启动（含 server-url 未配置的空转状态）
     */
    private ScheduledExecutorService scheduler;

    /**
     * 构造推送引擎。
     *
     * @param properties         客户端配置
     * @param collector          指标采集器
     * @param capabilityDetector 能力探测器
     * @param httpClient         共享 HTTP 客户端
     */
    public MetricPushClient(MonitorClientProperties properties,
                            MonitorMetricCollector collector,
                            MonitorCapabilityDetector capabilityDetector,
                            HttpClient httpClient) {
        this.properties = properties;
        this.collector = collector;
        this.capabilityDetector = capabilityDetector;
        this.httpClient = httpClient;
    }

    /**
     * 启动心跳与推送调度。server-url 未配置时仅告警不启动。
     */
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        String serverUrl = properties.getServerUrl();
        if (serverUrl == null || serverUrl.isBlank()) {
            log.warn("[monitor-client] plugin.monitor.client.server-url 未配置，监控客户端引擎不启动");
            return;
        }
        long heartbeatInterval = Math.max(5, properties.getHeartbeatIntervalSeconds());
        long pushInterval = Math.max(5, properties.getPushIntervalSeconds());
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "monitor-client");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::safeHeartbeat, heartbeatInterval, heartbeatInterval, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::safePush, pushInterval, pushInterval, TimeUnit.SECONDS);
        log.info("[monitor-client] 监控客户端引擎已启动: server={}, agentKey={}, heartbeat={}s, push={}s",
                serverUrl, mask(properties.getAgentKey()), heartbeatInterval, pushInterval);
    }

    /**
     * 停止调度并释放线程。
     */
    public synchronized void stop() {
        if (scheduler == null) {
            return;
        }
        scheduler.shutdownNow();
        scheduler = null;
        log.info("[monitor-client] 监控客户端引擎已停止");
    }

    /**
     * 心跳任务（吞掉一切异常，保护调度器）。
     */
    private void safeHeartbeat() {
        try {
            heartbeat();
        } catch (Throwable e) {
            log.debug("[monitor-client] 心跳异常: {}", e.getMessage());
        }
    }

    /**
     * 推送任务（吞掉一切异常，保护调度器）。
     */
    private void safePush() {
        try {
            push();
        } catch (Throwable e) {
            log.debug("[monitor-client] 指标推送异常: {}", e.getMessage());
        }
    }

    /**
     * 上报一次心跳。
     *
     * @throws Exception 网络异常（由 safeHeartbeat 兜底）
     */
    void heartbeat() throws Exception {
        if (!capabilityDetector.isSupported() || !hasAgentKey()) {
            return;
        }
        HttpRequest request = MonitorHttp.get(properties, HEARTBEAT_PATH);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200 && MonitorHttp.businessOk(response.body())) {
            capabilityDetector.markConfirmed();
            log.debug("[monitor-client] 心跳成功");
        } else {
            log.warn("[monitor-client] 心跳被拒绝: http={}, body={}",
                    response.statusCode(), abbreviate(response.body()));
        }
    }

    /**
     * 采集并推送一轮指标。
     *
     * @throws Exception 网络异常（由 safePush 兜底）
     */
    void push() throws Exception {
        if (!capabilityDetector.isSupported() || !hasAgentKey()) {
            return;
        }
        List<MetricPoint> points = collector.collect();
        if (points == null || points.isEmpty()) {
            return;
        }
        String json = objectMapper.writeValueAsString(new AgentMetricsPushVO(points));
        HttpRequest request = MonitorHttp.postJson(properties, METRICS_PATH, json);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200 && MonitorHttp.businessOk(response.body())) {
            capabilityDetector.markConfirmed();
            log.debug("[monitor-client] 指标推送成功: {} 点", points.size());
        } else {
            log.warn("[monitor-client] 指标推送被拒绝: http={}, points={}, body={}",
                    response.statusCode(), summarize(points), abbreviate(response.body()));
        }
    }

    /**
     * 是否配置了探针标识。
     *
     * @return agentKey 非空
     */
    private boolean hasAgentKey() {
        String agentKey = properties.getAgentKey();
        return agentKey != null && !agentKey.isBlank();
    }

    /**
     * 指标点摘要（日志用，最多列前 N 个指标名）。
     *
     * @param points 指标点
     * @return 形如 [cpu_usage, memory_usage, ...] 的摘要
     */
    private String summarize(List<MetricPoint> points) {
        StringBuilder builder = new StringBuilder("[");
        int limit = Math.min(points.size(), LOG_POINTS_LIMIT);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            MetricPoint point = points.get(i);
            builder.append(point == null ? "null" : point.getMetricName());
        }
        if (points.size() > limit) {
            builder.append(", +").append(points.size() - limit);
        }
        return builder.append(']').toString();
    }

    /**
     * 响应体缩略（日志用）。
     *
     * @param body 响应体
     * @return 截断到 200 字符
     */
    private String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.trim();
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200) + "...";
    }

    /**
     * 探针标识脱敏（日志用）。
     *
     * @param agentKey 探针标识
     * @return 前 4 位 + ***
     */
    private String mask(String agentKey) {
        if (agentKey == null || agentKey.isBlank()) {
            return "<未配置>";
        }
        String trimmed = agentKey.trim();
        return trimmed.length() <= 4 ? trimmed : trimmed.substring(0, 4) + "***";
    }
}
