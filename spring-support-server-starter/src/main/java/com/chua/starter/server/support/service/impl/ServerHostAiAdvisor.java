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
import java.util.List;
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
