package com.chua.starter.server.support.service.impl;

import com.chua.starter.ai.support.chat.ChatClient;
import com.chua.starter.ai.support.chat.ChatResponse;
import com.chua.starter.ai.support.chat.ChatScope;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.entity.ServerService;
import com.chua.starter.server.support.enums.ServerServiceOperationType;
import com.chua.starter.server.support.model.ServerServiceAiAdvice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServerServiceAiAdvisor {

    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[\\s\\S]*}");

    private final ObjectProvider<ChatClient> chatClientProvider;
    private final ObjectMapper objectMapper;

    public ServerServiceAiAdvice diagnose(
            ServerHost host,
            ServerService service,
            ServerServiceOperationType operationType,
            Integer exitCode,
            String runtimeStatus,
            String output
    ) {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null || !StringUtils.hasText(output)) {
            return null;
        }
        String prompt = """
                你是服务器服务故障诊断助手。
                请基于下面信息分析这次服务操作失败原因，并给出可执行的修复建议。
                只返回 JSON，不要加 Markdown，不要解释。
                JSON 结构固定：
                {
                  "reason": "失败原因",
                  "solution": "处理方案",
                  "fixScript": "可直接执行的修复脚本，没有就返回空字符串"
                }

                操作类型：%s
                服务器名称：%s
                接入类型：%s
                操作系统：%s
                服务名称：%s
                服务类型：%s
                安装目录：%s
                当前运行状态：%s
                退出码：%s
                执行输出：
                %s
                """.formatted(
                operationType == null ? "" : operationType.name(),
                nullSafe(host == null ? null : host.getServerName()),
                nullSafe(host == null ? null : host.getServerType()),
                nullSafe(host == null ? null : host.getOsType()),
                nullSafe(service == null ? null : service.getServiceName()),
                nullSafe(service == null ? null : service.getServiceType()),
                nullSafe(service == null ? null : service.getInstallPath()),
                nullSafe(runtimeStatus),
                exitCode == null ? "" : exitCode,
                limit(output, 6000));
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

    private ServerServiceAiAdvice parseAdvice(ChatResponse response, ChatClient chatClient) throws Exception {
        String raw = response == null ? null : response.getText();
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        Matcher matcher = JSON_PATTERN.matcher(raw);
        String json = matcher.find() ? matcher.group() : raw.trim();
        JsonNode node = objectMapper.readTree(json);
        return ServerServiceAiAdvice.builder()
                .reason(text(node, "reason"))
                .solution(text(node, "solution"))
                .fixScript(text(node, "fixScript"))
                .provider(response == null ? chatClient.getProvider() : response.getProvider())
                .model(response == null ? chatClient.getDefaultModel() : response.getModel())
                .build();
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String nullSafe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
