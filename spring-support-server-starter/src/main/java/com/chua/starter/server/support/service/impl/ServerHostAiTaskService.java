package com.chua.starter.server.support.service.impl;

import com.chua.starter.common.support.constant.Constant;
import com.chua.starter.server.support.constants.ServerSocketEvents;
import com.chua.starter.server.support.entity.ServerAlertEvent;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerAiTaskPayload;
import com.chua.starter.server.support.model.ServerAiTaskTicket;
import com.chua.starter.server.support.model.ServerAlertSettings;
import com.chua.starter.server.support.model.ServerHostAiAdvice;
import com.chua.starter.server.support.model.ServerMetricsDetail;
import com.chua.starter.server.support.model.ServerMetricsSnapshot;
import com.chua.starter.server.support.service.ServerAlertService;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.ServerMetricsService;
import com.chua.starter.server.support.service.ServerRealtimePublisher;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ServerHostAiTaskService {

    private final Executor executor;
    private final ServerHostService serverHostService;
    private final ServerMetricsService serverMetricsService;
    private final ServerAlertService serverAlertService;
    private final ServerHostAiAdvisor serverHostAiAdvisor;
    private final ServerRealtimePublisher serverRealtimePublisher;

    public ServerHostAiTaskService(
            @Qualifier(Constant.DEFAULT_EXECUTOR) Executor executor,
            ServerHostService serverHostService,
            ServerMetricsService serverMetricsService,
            ServerAlertService serverAlertService,
            ServerHostAiAdvisor serverHostAiAdvisor,
            ServerRealtimePublisher serverRealtimePublisher
    ) {
        this.executor = executor == null ? ForkJoinPool.commonPool() : executor;
        this.serverHostService = serverHostService;
        this.serverMetricsService = serverMetricsService;
        this.serverAlertService = serverAlertService;
        this.serverHostAiAdvisor = serverHostAiAdvisor;
        this.serverRealtimePublisher = serverRealtimePublisher;
    }

    public ServerAiTaskTicket scheduleStabilityAnalysis(Integer serverId) {
        String taskId = UUID.randomUUID().toString();
        publish(taskId, ServerAiTaskPayload.builder()
                .taskId(taskId)
                .taskType("ANALYZE_HOST_STABILITY")
                .status("PENDING")
                .serverId(serverId)
                .message("AI 稳定性分析任务已受理")
                .build());
        CompletableFuture.runAsync(() -> runStabilityAnalysis(taskId, serverId), executor);
        return ServerAiTaskTicket.builder()
                .taskId(taskId)
                .taskType("ANALYZE_HOST_STABILITY")
                .status("PENDING")
                .message("AI 稳定性分析任务已受理")
                .build();
    }

    /**
     * 异步分析指定指标的历史趋势。
     */
    public ServerAiTaskTicket scheduleMetricHistoryAnalysis(
            Integer serverId,
            String metricType,
            Integer minutes,
            Long startTime,
            Long endTime,
            String stateFilter
    ) {
        String taskId = UUID.randomUUID().toString();
        String filterKey = buildMetricHistoryFilterKey(serverId, metricType, minutes, startTime, endTime, stateFilter);
        publish(taskId, ServerAiTaskPayload.builder()
                .taskId(taskId)
                .taskType("ANALYZE_METRIC_HISTORY")
                .status("PENDING")
                .serverId(serverId)
                .metricType(metricType)
                .minutes(minutes)
                .startTime(startTime)
                .endTime(endTime)
                .stateFilter(stateFilter)
                .filterKey(filterKey)
                .message("指标历史 AI 分析任务已受理")
                .build());
        CompletableFuture.runAsync(
                () -> runMetricHistoryAnalysis(taskId, serverId, metricType, minutes, startTime, endTime, stateFilter),
                executor);
        return ServerAiTaskTicket.builder()
                .taskId(taskId)
                .taskType("ANALYZE_METRIC_HISTORY")
                .status("PENDING")
                .serverId(serverId)
                .metricType(metricType)
                .minutes(minutes)
                .startTime(startTime)
                .endTime(endTime)
                .stateFilter(stateFilter)
                .filterKey(filterKey)
                .message("指标历史 AI 分析任务已受理")
                .build();
    }

    /**
     * 异步分析历史告警。
     */
    public ServerAiTaskTicket scheduleAlertHistoryAnalysis(
            Integer serverId,
            String metricType,
            String severity,
            Long startTime,
            Long endTime,
            Integer limit
    ) {
        String taskId = UUID.randomUUID().toString();
        String filterKey = buildAlertHistoryFilterKey(serverId, metricType, severity, startTime, endTime, limit);
        publish(taskId, ServerAiTaskPayload.builder()
                .taskId(taskId)
                .taskType("ANALYZE_ALERT_HISTORY")
                .status("PENDING")
                .serverId(serverId)
                .metricType(metricType)
                .severity(severity)
                .startTime(startTime)
                .endTime(endTime)
                .filterKey(filterKey)
                .message("告警历史 AI 分析任务已受理")
                .build());
        CompletableFuture.runAsync(
                () -> runAlertHistoryAnalysis(taskId, serverId, metricType, severity, startTime, endTime, limit),
                executor);
        return ServerAiTaskTicket.builder()
                .taskId(taskId)
                .taskType("ANALYZE_ALERT_HISTORY")
                .status("PENDING")
                .serverId(serverId)
                .metricType(metricType)
                .severity(severity)
                .startTime(startTime)
                .endTime(endTime)
                .filterKey(filterKey)
                .message("告警历史 AI 分析任务已受理")
                .build();
    }

    private void runStabilityAnalysis(String taskId, Integer serverId) {
        publish(taskId, ServerAiTaskPayload.builder()
                .taskId(taskId)
                .taskType("ANALYZE_HOST_STABILITY")
                .status("RUNNING")
                .serverId(serverId)
                .message("AI 正在分析当前服务器稳定性")
                .build());
        try {
            ServerHost host = serverHostService.getHost(serverId);
            if (host == null) {
                publish(taskId, ServerAiTaskPayload.builder()
                        .taskId(taskId)
                        .taskType("ANALYZE_HOST_STABILITY")
                        .status("FAILED")
                        .serverId(serverId)
                        .message("未找到服务器")
                        .finishedAt(System.currentTimeMillis())
                        .build());
                return;
            }
            ServerMetricsSnapshot snapshot = serverMetricsService.getSnapshot(serverId);
            ServerMetricsDetail detail = serverMetricsService.getDetail(serverId);
            List<ServerAlertEvent> alerts = serverAlertService.listAlerts(serverId, 5);
            ServerHostAiAdvice advice = serverHostAiAdvisor.analyze(host, snapshot, detail, alerts);
            if (advice == null || !StringUtils.hasText(advice.getSummary())) {
                publish(taskId, ServerAiTaskPayload.builder()
                        .taskId(taskId)
                        .taskType("ANALYZE_HOST_STABILITY")
                        .status("FAILED")
                        .serverId(serverId)
                        .message("AI 未返回有效的稳定性分析结果")
                        .finishedAt(System.currentTimeMillis())
                        .build());
                return;
            }
            publish(taskId, ServerAiTaskPayload.builder()
                    .taskId(taskId)
                    .taskType("ANALYZE_HOST_STABILITY")
                    .status("COMPLETED")
                    .serverId(serverId)
                    .message("AI 稳定性分析完成" + (StringUtils.hasText(advice.getRiskLevel()) ? " · " + advice.getRiskLevel() : ""))
                    .aiReason(advice.getSummary())
                    .aiSolution(advice.getSuggestion())
                    .aiProvider(advice.getProvider())
                    .aiModel(advice.getModel())
                    .finishedAt(System.currentTimeMillis())
                    .build());
        } catch (Exception e) {
            publish(taskId, ServerAiTaskPayload.builder()
                    .taskId(taskId)
                    .taskType("ANALYZE_HOST_STABILITY")
                    .status("FAILED")
                    .serverId(serverId)
                    .message("AI 稳定性分析失败: " + e.getMessage())
                    .finishedAt(System.currentTimeMillis())
                    .build());
        }
    }

    private void runMetricHistoryAnalysis(
            String taskId,
            Integer serverId,
            String metricType,
            Integer minutes,
            Long startTime,
            Long endTime,
            String stateFilter
    ) {
        String filterKey = buildMetricHistoryFilterKey(serverId, metricType, minutes, startTime, endTime, stateFilter);
        publish(taskId, ServerAiTaskPayload.builder()
                .taskId(taskId)
                .taskType("ANALYZE_METRIC_HISTORY")
                .status("RUNNING")
                .serverId(serverId)
                .metricType(metricType)
                .minutes(minutes)
                .startTime(startTime)
                .endTime(endTime)
                .stateFilter(stateFilter)
                .filterKey(filterKey)
                .message("AI 正在分析指标历史趋势")
                .build());
        try {
            ServerHost host = serverHostService.getHost(serverId);
            if (host == null) {
                publish(taskId, ServerAiTaskPayload.builder()
                        .taskId(taskId)
                        .taskType("ANALYZE_METRIC_HISTORY")
                        .status("FAILED")
                        .serverId(serverId)
                        .metricType(metricType)
                        .minutes(minutes)
                        .startTime(startTime)
                        .endTime(endTime)
                        .stateFilter(stateFilter)
                        .filterKey(filterKey)
                        .message("未找到服务器")
                        .finishedAt(System.currentTimeMillis())
                        .build());
                return;
            }
            List<ServerMetricsSnapshot> history = serverMetricsService.listHistory(serverId, minutes);
            history = history.stream()
                    .filter(item -> item != null && item.getCollectTimestamp() != null)
                    .filter(item -> startTime == null || item.getCollectTimestamp() >= startTime)
                    .filter(item -> endTime == null || item.getCollectTimestamp() <= endTime)
                    .toList();
            history = filterMetricHistoryByState(serverId, metricType, history, stateFilter);
            if (history.isEmpty()) {
                publish(taskId, ServerAiTaskPayload.builder()
                        .taskId(taskId)
                        .taskType("ANALYZE_METRIC_HISTORY")
                        .status("FAILED")
                        .serverId(serverId)
                        .metricType(metricType)
                        .minutes(minutes)
                        .startTime(startTime)
                        .endTime(endTime)
                        .stateFilter(stateFilter)
                        .filterKey(filterKey)
                        .message("当前筛选条件下没有历史指标数据")
                        .finishedAt(System.currentTimeMillis())
                        .build());
                return;
            }
            List<ServerAlertEvent> alerts = serverAlertService.listAlerts(serverId, metricType, null, startTime, endTime, 12);
            ServerHostAiAdvice advice = serverHostAiAdvisor.analyzeMetricHistory(host, metricType, history, alerts);
            if (advice == null || !StringUtils.hasText(advice.getSummary())) {
                publish(taskId, ServerAiTaskPayload.builder()
                        .taskId(taskId)
                        .taskType("ANALYZE_METRIC_HISTORY")
                        .status("FAILED")
                        .serverId(serverId)
                        .metricType(metricType)
                        .message("AI 未返回有效的历史分析结果")
                        .finishedAt(System.currentTimeMillis())
                        .build());
                return;
            }
            publish(taskId, ServerAiTaskPayload.builder()
                    .taskId(taskId)
                    .taskType("ANALYZE_METRIC_HISTORY")
                    .status("COMPLETED")
                    .serverId(serverId)
                    .metricType(metricType)
                    .minutes(minutes)
                    .startTime(startTime)
                    .endTime(endTime)
                    .stateFilter(stateFilter)
                    .filterKey(filterKey)
                    .message("指标历史 AI 分析完成" + (StringUtils.hasText(advice.getRiskLevel()) ? " · " + advice.getRiskLevel() : ""))
                    .aiReason(advice.getSummary())
                    .aiSolution(advice.getSuggestion())
                    .aiProvider(advice.getProvider())
                    .aiModel(advice.getModel())
                    .finishedAt(System.currentTimeMillis())
                    .build());
        } catch (Exception e) {
            publish(taskId, ServerAiTaskPayload.builder()
                    .taskId(taskId)
                    .taskType("ANALYZE_METRIC_HISTORY")
                    .status("FAILED")
                    .serverId(serverId)
                    .metricType(metricType)
                    .minutes(minutes)
                    .startTime(startTime)
                    .endTime(endTime)
                    .stateFilter(stateFilter)
                    .filterKey(filterKey)
                    .message("指标历史 AI 分析失败: " + e.getMessage())
                    .finishedAt(System.currentTimeMillis())
                    .build());
        }
    }

    private void runAlertHistoryAnalysis(
            String taskId,
            Integer serverId,
            String metricType,
            String severity,
            Long startTime,
            Long endTime,
            Integer limit
    ) {
        String filterKey = buildAlertHistoryFilterKey(serverId, metricType, severity, startTime, endTime, limit);
        publish(taskId, ServerAiTaskPayload.builder()
                .taskId(taskId)
                .taskType("ANALYZE_ALERT_HISTORY")
                .status("RUNNING")
                .serverId(serverId)
                .metricType(metricType)
                .severity(severity)
                .startTime(startTime)
                .endTime(endTime)
                .filterKey(filterKey)
                .message("AI 正在分析历史告警")
                .build());
        try {
            ServerHost host = serverHostService.getHost(serverId);
            if (host == null) {
                publish(taskId, ServerAiTaskPayload.builder()
                        .taskId(taskId)
                        .taskType("ANALYZE_ALERT_HISTORY")
                        .status("FAILED")
                        .serverId(serverId)
                        .metricType(metricType)
                        .severity(severity)
                        .startTime(startTime)
                        .endTime(endTime)
                        .filterKey(filterKey)
                        .message("未找到服务器")
                        .finishedAt(System.currentTimeMillis())
                        .build());
                return;
            }
            List<ServerAlertEvent> alerts = serverAlertService.listAlerts(
                    serverId,
                    metricType,
                    severity,
                    startTime,
                    endTime,
                    limit == null ? 80 : limit);
            if (alerts.isEmpty()) {
                publish(taskId, ServerAiTaskPayload.builder()
                        .taskId(taskId)
                        .taskType("ANALYZE_ALERT_HISTORY")
                        .status("FAILED")
                        .serverId(serverId)
                        .metricType(metricType)
                        .severity(severity)
                        .startTime(startTime)
                        .endTime(endTime)
                        .filterKey(filterKey)
                        .message("当前筛选条件下没有历史告警")
                        .finishedAt(System.currentTimeMillis())
                        .build());
                return;
            }
            ServerHostAiAdvice advice = serverHostAiAdvisor.analyzeAlertHistory(host, alerts);
            if (advice == null || !StringUtils.hasText(advice.getSummary())) {
                publish(taskId, ServerAiTaskPayload.builder()
                        .taskId(taskId)
                        .taskType("ANALYZE_ALERT_HISTORY")
                        .status("FAILED")
                        .serverId(serverId)
                        .metricType(metricType)
                        .message("AI 未返回有效的告警分析结果")
                        .finishedAt(System.currentTimeMillis())
                        .build());
                return;
            }
            publish(taskId, ServerAiTaskPayload.builder()
                    .taskId(taskId)
                    .taskType("ANALYZE_ALERT_HISTORY")
                    .status("COMPLETED")
                    .serverId(serverId)
                    .metricType(metricType)
                    .severity(severity)
                    .startTime(startTime)
                    .endTime(endTime)
                    .filterKey(filterKey)
                    .message("告警历史 AI 分析完成" + (StringUtils.hasText(advice.getRiskLevel()) ? " · " + advice.getRiskLevel() : ""))
                    .aiReason(advice.getSummary())
                    .aiSolution(advice.getSuggestion())
                    .aiProvider(advice.getProvider())
                    .aiModel(advice.getModel())
                    .finishedAt(System.currentTimeMillis())
                    .build());
        } catch (Exception e) {
            publish(taskId, ServerAiTaskPayload.builder()
                    .taskId(taskId)
                    .taskType("ANALYZE_ALERT_HISTORY")
                    .status("FAILED")
                    .serverId(serverId)
                    .metricType(metricType)
                    .severity(severity)
                    .startTime(startTime)
                    .endTime(endTime)
                    .filterKey(filterKey)
                    .message("告警历史 AI 分析失败: " + e.getMessage())
                    .finishedAt(System.currentTimeMillis())
                    .build());
        }
    }

    /**
     * 基于服务器阈值配置对历史样本做状态筛选，保持与前端过滤语义一致。
     */
    private List<ServerMetricsSnapshot> filterMetricHistoryByState(
            Integer serverId,
            String metricType,
            List<ServerMetricsSnapshot> history,
            String stateFilter
    ) {
        if (!StringUtils.hasText(stateFilter) || "all".equalsIgnoreCase(stateFilter) || history == null || history.isEmpty()) {
            return history;
        }
        ServerAlertSettings settings = serverAlertService.getHostSettings(serverId);
        return history.stream()
                .filter(item -> stateFilter.equalsIgnoreCase(resolveMetricState(metricType, item, settings)))
                .toList();
    }

    private String resolveMetricState(String metricType, ServerMetricsSnapshot snapshot, ServerAlertSettings settings) {
        Double metricValue = extractMetricValue(metricType, snapshot);
        if (metricValue == null) {
            return "normal";
        }
        double warning = resolveWarningThreshold(metricType, settings);
        double danger = resolveDangerThreshold(metricType, settings);
        if (metricValue >= danger) {
            return "danger";
        }
        if (metricValue >= warning) {
            return "warning";
        }
        return "normal";
    }

    private Double extractMetricValue(String metricType, ServerMetricsSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        String normalized = StringUtils.hasText(metricType) ? metricType.trim().toUpperCase() : "CPU";
        return switch (normalized) {
            case "MEMORY" -> snapshot.getMemoryUsage();
            case "DISK" -> snapshot.getDiskUsage();
            case "IO" -> safeDouble(snapshot.getIoReadBytesPerSecond()) + safeDouble(snapshot.getIoWriteBytesPerSecond());
            case "LATENCY" -> snapshot.getLatencyMs() == null ? null : snapshot.getLatencyMs().doubleValue();
            default -> snapshot.getCpuUsage();
        };
    }

    private double resolveWarningThreshold(String metricType, ServerAlertSettings settings) {
        if (settings == null) {
            return 0D;
        }
        String normalized = StringUtils.hasText(metricType) ? metricType.trim().toUpperCase() : "CPU";
        return switch (normalized) {
            case "MEMORY" -> safeDouble(settings.getMemoryWarningPercent());
            case "DISK" -> safeDouble(settings.getDiskWarningPercent());
            case "IO" -> safeDouble(settings.getIoWarningBytesPerSecond());
            case "LATENCY" -> safeDouble(settings.getLatencyWarningMs() == null ? null : settings.getLatencyWarningMs().doubleValue());
            default -> safeDouble(settings.getCpuWarningPercent());
        };
    }

    private double resolveDangerThreshold(String metricType, ServerAlertSettings settings) {
        if (settings == null) {
            return 0D;
        }
        String normalized = StringUtils.hasText(metricType) ? metricType.trim().toUpperCase() : "CPU";
        return switch (normalized) {
            case "MEMORY" -> safeDouble(settings.getMemoryDangerPercent());
            case "DISK" -> safeDouble(settings.getDiskDangerPercent());
            case "IO" -> safeDouble(settings.getIoDangerBytesPerSecond());
            case "LATENCY" -> safeDouble(settings.getLatencyDangerMs() == null ? null : settings.getLatencyDangerMs().doubleValue());
            default -> safeDouble(settings.getCpuDangerPercent());
        };
    }

    private double safeDouble(Double value) {
        return value == null ? 0D : value;
    }

    private String buildMetricHistoryFilterKey(
            Integer serverId,
            String metricType,
            Integer minutes,
            Long startTime,
            Long endTime,
            String stateFilter
    ) {
        return "metric:%s:%s:%s:%s:%s:%s".formatted(
                serverId == null ? 0 : serverId,
                StringUtils.hasText(metricType) ? metricType.trim().toUpperCase() : "UNKNOWN",
                minutes == null ? 0 : Math.max(minutes, 0),
                startTime == null ? 0L : Math.max(startTime, 0L),
                endTime == null ? 0L : Math.max(endTime, 0L),
                StringUtils.hasText(stateFilter) ? stateFilter.trim().toLowerCase() : "all");
    }

    private String buildAlertHistoryFilterKey(
            Integer serverId,
            String metricType,
            String severity,
            Long startTime,
            Long endTime,
            Integer limit
    ) {
        return "alert:%s:%s:%s:%s:%s:%s".formatted(
                serverId == null ? 0 : serverId,
                StringUtils.hasText(metricType) ? metricType.trim().toUpperCase() : "ALL",
                StringUtils.hasText(severity) ? severity.trim().toUpperCase() : "ALL",
                startTime == null ? 0L : Math.max(startTime, 0L),
                endTime == null ? 0L : Math.max(endTime, 0L),
                limit == null ? 0 : Math.max(limit, 0));
    }

    private void publish(String taskId, ServerAiTaskPayload payload) {
        serverRealtimePublisher.publish(ServerSocketEvents.MODULE, ServerSocketEvents.SERVER_AI_TASK, taskId, payload);
    }
}
