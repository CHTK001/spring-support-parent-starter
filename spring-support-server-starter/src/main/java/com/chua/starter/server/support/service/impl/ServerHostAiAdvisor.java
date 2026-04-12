package com.chua.starter.server.support.service.impl;

import com.chua.starter.ai.support.chat.ChatClient;
import com.chua.starter.ai.support.chat.ChatResponse;
import com.chua.starter.ai.support.chat.ChatScope;
import com.chua.starter.server.support.entity.ServerAlertEvent;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerHostAiAdvice;
import com.chua.starter.server.support.model.ServerMetricsDetail;
import com.chua.starter.server.support.model.ServerMetricsSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServerHostAiAdvisor {

    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[\\s\\S]*}");

    private final ObjectProvider<ChatClient> chatClientProvider;
    private final ObjectMapper objectMapper;

    public ServerHostAiAdvice analyze(
            ServerHost host,
            ServerMetricsSnapshot snapshot,
            ServerMetricsDetail detail,
            List<ServerAlertEvent> alerts
    ) {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null || host == null || snapshot == null) {
            return null;
        }
        String prompt = """
                你是服务器稳定性分析助手。
                请基于服务器当前指标、系统事实和最近告警，输出当前稳定性结论。
                只返回 JSON，不要 Markdown，不要解释。
                JSON 结构固定：
                {
                  "summary": "一句话稳定性结论",
                  "riskLevel": "LOW/MEDIUM/HIGH",
                  "suggestion": "最多三条具体建议，合并成一段文本"
                }

                服务器名称：%s
                服务器编码：%s
                接入类型：%s
                操作系统：%s
                主机地址：%s
                实际系统：%s
                公网地址：%s
                CPU：%s%%
                内存：%s%%
                磁盘：%s%%
                网络收包：%s 包/秒
                网络发包：%s 包/秒
                延迟：%s ms
                最近告警：
                %s
                """.formatted(
                safe(host.getServerName()),
                safe(host.getServerCode()),
                safe(host.getServerType()),
                safe(host.getOsType()),
                safe(host.getHost()),
                safe(detail == null ? null : detail.getActualOsName()),
                safe(detail == null ? null : detail.getPublicIp()),
                safe(snapshot.getCpuUsage()),
                safe(snapshot.getMemoryUsage()),
                safe(snapshot.getDiskUsage()),
                safe(snapshot.getNetworkRxPacketsPerSecond()),
                safe(snapshot.getNetworkTxPacketsPerSecond()),
                safe(snapshot.getLatencyMs()),
                buildAlertText(alerts));
        try {
            ChatResponse response = chatClient.chat(ChatScope.builder()
                    .input(prompt)
                    .systemPrompt("你只返回合法 JSON。")
                    .mcpEnabled(false)
                    .build());
            return parseAdvice(response, chatClient);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 分析某个指标的历史趋势，输出风险结论与处置建议。
     */
    public ServerHostAiAdvice analyzeMetricHistory(
            ServerHost host,
            String metricType,
            List<ServerMetricsSnapshot> history,
            List<ServerAlertEvent> alerts
    ) {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null || host == null || !StringUtils.hasText(metricType) || history == null || history.isEmpty()) {
            return null;
        }
        List<ServerMetricsSnapshot> sortedHistory = history.stream()
                .sorted(Comparator.comparing(item -> item.getCollectTimestamp() == null ? 0L : item.getCollectTimestamp()))
                .toList();
        String prompt = """
                你是服务器指标历史分析助手。
                请基于服务器某一项指标的历史数据和最近告警，输出趋势结论。
                只返回 JSON，不要 Markdown，不要解释。
                JSON 结构固定：
                {
                  "summary": "一句话总结该指标历史表现和当前风险",
                  "riskLevel": "LOW/MEDIUM/HIGH",
                  "suggestion": "最多三条具体建议，合并成一段文本"
                }

                服务器名称：%s
                服务器编码：%s
                主机地址：%s
                指标类型：%s
                样本数：%s
                历史摘要：
                %s
                最近告警：
                %s
                """.formatted(
                safe(host.getServerName()),
                safe(host.getServerCode()),
                safe(host.getHost()),
                safe(metricType),
                sortedHistory.size(),
                buildMetricHistorySummary(metricType, sortedHistory),
                buildAlertText(alerts));
        try {
            ChatResponse response = chatClient.chat(ChatScope.builder()
                    .input(prompt)
                    .systemPrompt("你只返回合法 JSON。")
                    .mcpEnabled(false)
                    .build());
            return parseAdvice(response, chatClient);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 分析历史告警，输出风险结论与处置建议。
     */
    public ServerHostAiAdvice analyzeAlertHistory(
            ServerHost host,
            List<ServerAlertEvent> alerts
    ) {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null || host == null || alerts == null || alerts.isEmpty()) {
            return null;
        }
        List<ServerAlertEvent> sortedAlerts = alerts.stream()
                .sorted(Comparator.comparing(item -> item.getCreateTime() == null ? "" : String.valueOf(item.getCreateTime())))
                .toList();
        String prompt = """
                你是服务器告警历史分析助手。
                请基于服务器最近一段时间的告警历史，输出告警模式、风险结论和处理建议。
                只返回 JSON，不要 Markdown，不要解释。
                JSON 结构固定：
                {
                  "summary": "一句话总结告警整体趋势和当前风险",
                  "riskLevel": "LOW/MEDIUM/HIGH",
                  "suggestion": "最多三条具体建议，合并成一段文本"
                }

                服务器名称：%s
                服务器编码：%s
                主机地址：%s
                告警总数：%s
                告警摘要：
                %s
                """.formatted(
                safe(host.getServerName()),
                safe(host.getServerCode()),
                safe(host.getHost()),
                sortedAlerts.size(),
                buildAlertTimeline(sortedAlerts));
        try {
            ChatResponse response = chatClient.chat(ChatScope.builder()
                    .input(prompt)
                    .systemPrompt("你只返回合法 JSON。")
                    .mcpEnabled(false)
                    .build());
            return parseAdvice(response, chatClient);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ServerHostAiAdvice parseAdvice(ChatResponse response, ChatClient chatClient) throws Exception {
        String raw = response == null ? null : response.getText();
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        Matcher matcher = JSON_PATTERN.matcher(raw);
        String json = matcher.find() ? matcher.group() : raw.trim();
        JsonNode node = objectMapper.readTree(json);
        return ServerHostAiAdvice.builder()
                .summary(text(node, "summary"))
                .riskLevel(text(node, "riskLevel"))
                .suggestion(text(node, "suggestion"))
                .provider(response == null ? chatClient.getProvider() : response.getProvider())
                .model(response == null ? chatClient.getDefaultModel() : response.getModel())
                .build();
    }

    private String buildAlertText(List<ServerAlertEvent> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            return "最近没有告警";
        }
        StringBuilder builder = new StringBuilder();
        alerts.stream().limit(5).forEach(item -> builder
                .append("- ")
                .append(safe(item.getMetricType()))
                .append(" / ")
                .append(safe(item.getSeverity()))
                .append(" / ")
                .append(safe(item.getAlertMessage()))
                .append('\n'));
        return builder.toString();
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

    private String text(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String safe(Object value) {
        return value == null ? "-" : String.valueOf(value).trim();
    }
}
