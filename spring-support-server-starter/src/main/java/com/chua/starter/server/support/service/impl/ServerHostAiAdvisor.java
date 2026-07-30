package com.chua.starter.server.support.service.impl;

import com.chua.starter.ai.support.model.ServerAiStructuredAdvice;
import com.chua.starter.ai.support.model.ServerAlertHistoryAnalyzeRequest;
import com.chua.starter.ai.support.model.ServerHostStabilityAnalyzeRequest;
import com.chua.starter.ai.support.model.ServerMetricHistoryAnalyzeRequest;
import com.chua.starter.ai.support.service.ServerAiAnalyzeService;
import com.chua.starter.server.support.entity.ServerAlertEvent;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerHostAiAdvice;
import com.chua.starter.server.support.model.ServerMetricsDetail;
import com.chua.starter.server.support.model.ServerMetricsSnapshot;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServerHostAiAdvisor {

    private final ServerAiAnalyzeService serverAiAnalyzeService;

    public ServerHostAiAdvice analyze(
            ServerHost host,
            ServerMetricsSnapshot snapshot,
            ServerMetricsDetail detail,
            List<ServerAlertEvent> alerts
    ) {
        if (host == null || snapshot == null) {
            return null;
        }
        ServerAiStructuredAdvice advice = serverAiAnalyzeService.analyzeHostStability(
                ServerHostStabilityAnalyzeRequest.builder()
                        .serverName(host.getServerName())
                        .serverCode(host.getServerCode())
                        .serverType(host.getServerType())
                        .osType(host.getOsType())
                        .host(host.getHost())
                        .actualOsName(detail == null ? null : detail.getActualOsName())
                        .publicIp(detail == null ? null : detail.getPublicIp())
                        .cpuUsage(snapshot.getCpuUsage())
                        .memoryUsage(snapshot.getMemoryUsage())
                        .diskUsage(snapshot.getDiskUsage())
                        .networkRxPacketsPerSecond(snapshot.getNetworkRxPacketsPerSecond())
                        .networkTxPacketsPerSecond(snapshot.getNetworkTxPacketsPerSecond())
                        .latencyMs(snapshot.getLatencyMs() == null ? null : snapshot.getLatencyMs().doubleValue())
                        .alerts(buildAlertLines(alerts, 5))
                        .build());
        return convertAdvice(advice);
    }

    public ServerHostAiAdvice analyzeMetricHistory(
            ServerHost host,
            String metricType,
            List<ServerMetricsSnapshot> history,
            List<ServerAlertEvent> alerts
    ) {
        if (host == null || !StringUtils.hasText(metricType) || history == null || history.isEmpty()) {
            return null;
        }
        List<ServerMetricsSnapshot> sortedHistory = history.stream()
                .sorted(Comparator.comparing(item -> item.getCollectTimestamp() == null ? 0L : item.getCollectTimestamp()))
                .toList();
        ServerAiStructuredAdvice advice = serverAiAnalyzeService.analyzeMetricHistory(
                ServerMetricHistoryAnalyzeRequest.builder()
                        .serverName(host.getServerName())
                        .serverCode(host.getServerCode())
                        .host(host.getHost())
                        .metricType(metricType)
                        .sampleCount(sortedHistory.size())
                        .historySummary(buildMetricHistorySummary(metricType, sortedHistory))
                        .alerts(buildAlertLines(alerts, 5))
                        .build());
        return convertAdvice(advice);
    }

    public ServerHostAiAdvice analyzeAlertHistory(
            ServerHost host,
            List<ServerAlertEvent> alerts
    ) {
        if (host == null || alerts == null || alerts.isEmpty()) {
            return null;
        }
        List<ServerAlertEvent> sortedAlerts = alerts.stream()
                .sorted(Comparator.comparing(item -> item.getCreateTime() == null ? "" : String.valueOf(item.getCreateTime())))
                .toList();
        ServerAiStructuredAdvice advice = serverAiAnalyzeService.analyzeAlertHistory(
                ServerAlertHistoryAnalyzeRequest.builder()
                        .serverName(host.getServerName())
                        .serverCode(host.getServerCode())
                        .host(host.getHost())
                        .alertCount(sortedAlerts.size())
                        .alertTimeline(buildAlertTimeline(sortedAlerts))
                        .build());
        return convertAdvice(advice);
    }

    private List<String> buildAlertLines(List<ServerAlertEvent> alerts, int limit) {
        if (alerts == null || alerts.isEmpty()) {
            return List.of();
        }
        return alerts.stream()
                .limit(limit)
                .map(item -> "- %s / %s / %s".formatted(
                        safe(item.getMetricType()),
                        safe(item.getSeverity()),
                        safe(item.getAlertMessage())))
                .toList();
    }

    private ServerHostAiAdvice convertAdvice(ServerAiStructuredAdvice advice) {
        if (advice == null || !StringUtils.hasText(advice.getSummary())) {
            return null;
        }
        return ServerHostAiAdvice.builder()
                .summary(advice.getSummary())
                .riskLevel(advice.getRiskLevel())
                .suggestion(advice.getSuggestion())
                .provider(advice.getProvider())
                .model(advice.getModel())
                .build();
    }

    private String buildMetricHistorySummary(String metricType, List<ServerMetricsSnapshot> history) {
        List<Double> values = history.stream()
                .map(item -> extractMetricValue(metricType, item))
                .filter(item -> item != null)
                .toList();
        if (values.isEmpty()) {
            return "没有可用的历史样本";
        }
        double latest = values.get(values.size() - 1);
        double first = values.get(0);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0D);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0D);
        double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
        String sampleTail = history.stream()
                .skip(Math.max(0, history.size() - 10))
                .map(item -> "%s=%s".formatted(
                        safe(item.getCollectTimestamp()),
                        formatMetricValue(metricType, extractMetricValue(metricType, item))))
                .collect(Collectors.joining("; "));
        return """
                首值：%s
                最新值：%s
                最小值：%s
                最大值：%s
                均值：%s
                趋势：%s
                最近10个样本：%s
                """.formatted(
                formatMetricValue(metricType, first),
                formatMetricValue(metricType, latest),
                formatMetricValue(metricType, min),
                formatMetricValue(metricType, max),
                formatMetricValue(metricType, avg),
                latest >= first ? "整体上升或持平" : "整体下降",
                sampleTail);
    }

    private String buildAlertTimeline(List<ServerAlertEvent> alerts) {
        return alerts.stream()
                .limit(20)
                .map(item -> "%s | %s | %s | %s | %s".formatted(
                        safe(item.getCreateTime()),
                        safe(item.getMetricType()),
                        safe(item.getSeverity()),
                        safe(item.getMetricValue()),
                        safe(item.getAlertMessage())))
                .collect(Collectors.joining("\n"));
    }

    private Double extractMetricValue(String metricType, ServerMetricsSnapshot snapshot) {
        if (snapshot == null || !StringUtils.hasText(metricType)) {
            return null;
        }
        String normalized = metricType.trim().toUpperCase(Locale.ROOT);
        if ("MEMORY".equals(normalized)) {
            return snapshot.getMemoryUsage();
        }
        if ("DISK".equals(normalized)) {
            return snapshot.getDiskUsage();
        }
        if ("IO".equals(normalized)) {
            return safeDouble(snapshot.getIoReadBytesPerSecond()) + safeDouble(snapshot.getIoWriteBytesPerSecond());
        }
        if ("LATENCY".equals(normalized)) {
            return snapshot.getLatencyMs() == null ? null : snapshot.getLatencyMs().doubleValue();
        }
        return snapshot.getCpuUsage();
    }

    private double safeDouble(Double value) {
        return value == null ? 0D : value;
    }

    private String formatMetricValue(String metricType, Double value) {
        if (value == null) {
            return "-";
        }
        String normalized = metricType == null ? "" : metricType.trim().toUpperCase(Locale.ROOT);
        if ("IO".equals(normalized)) {
            return Math.round(value) + " B/s";
        }
        if ("LATENCY".equals(normalized)) {
            return Math.round(value) + " ms";
        }
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    private String safe(Object value) {
        return value == null ? "-" : String.valueOf(value).trim();
    }
}
