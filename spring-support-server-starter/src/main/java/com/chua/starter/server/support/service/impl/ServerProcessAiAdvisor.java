package com.chua.starter.server.support.service.impl;

import com.chua.starter.ai.support.chat.ChatClient;
import com.chua.starter.ai.support.chat.ChatResponse;
import com.chua.starter.ai.support.chat.ChatScope;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerProcessAiAdvice;
import com.chua.starter.server.support.model.ServerProcessView;
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
public class ServerProcessAiAdvisor {

    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[\\s\\S]*}");

    private final ObjectProvider<ChatClient> chatClientProvider;
    private final ObjectMapper objectMapper;

    public ServerProcessAiAdvice analyze(
            ServerHost host,
            ServerProcessView process,
            List<ServerProcessView> topProcesses
    ) {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null || host == null || process == null) {
            return null;
        }
        String prompt = """
                你是服务器进程诊断助手。
                请结合目标进程和当前主机的热点进程列表，给出风险判断和处理建议。
                只返回 JSON，不要 Markdown，不要解释。
                JSON 结构固定：
                {
                  "summary": "一句话判断当前进程是否异常",
                  "riskLevel": "LOW/MEDIUM/HIGH",
                  "suggestion": "最多三条合并后的具体建议"
                }

                服务器名称：%s
                服务器编码：%s
                接入类型：%s
                操作系统：%s
                主机地址：%s

                目标进程：
                PID：%s
                名称：%s
                用户：%s
                状态：%s
                CPU：%s
                内存占比：%s
                内存字节：%s
                线程数：%s
                已运行：%s
                启动时间：%s
                命令：%s

                热点进程参考：
                %s
                """.formatted(
                safe(host.getServerName()),
                safe(host.getServerCode()),
                safe(host.getServerType()),
                safe(host.getOsType()),
                safe(host.getHost()),
                safe(process.getPid()),
                safe(process.getName()),
                safe(process.getUser()),
                safe(process.getState()),
                safe(process.getCpuPercent()),
                safe(process.getMemoryPercent()),
                safe(process.getMemoryBytes()),
                safe(process.getThreadCount()),
                safe(process.getElapsed()),
                safe(process.getStartTime()),
                safe(process.getCommandLine()),
                buildProcessText(topProcesses));
        try {
            ChatResponse response = chatClient.chat(ChatScope.builder()
                    .input(prompt)
                    .systemPrompt("你只返回合法 JSON。")
                    .mcpEnabled(false)
                    .build());
            return parseAdvice(response, chatClient, process.getPid());
        } catch (Exception ignored) {
            return null;
        }
    }

    private ServerProcessAiAdvice parseAdvice(ChatResponse response, ChatClient chatClient, Long pid) throws Exception {
        String raw = response == null ? null : response.getText();
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        Matcher matcher = JSON_PATTERN.matcher(raw);
        String json = matcher.find() ? matcher.group() : raw.trim();
        JsonNode node = objectMapper.readTree(json);
        return ServerProcessAiAdvice.builder()
                .pid(pid)
                .summary(text(node, "summary"))
                .riskLevel(text(node, "riskLevel"))
                .suggestion(text(node, "suggestion"))
                .provider(response == null ? chatClient.getProvider() : response.getProvider())
                .model(response == null ? chatClient.getDefaultModel() : response.getModel())
                .build();
    }

    private String buildProcessText(List<ServerProcessView> processes) {
        if (processes == null || processes.isEmpty()) {
            return "暂无可参考的热点进程";
        }
        StringBuilder builder = new StringBuilder();
        processes.stream().limit(8).forEach(item -> builder
                .append("- PID ")
                .append(safe(item.getPid()))
                .append(" / ")
                .append(safe(item.getName()))
                .append(" / CPU ")
                .append(safe(item.getCpuPercent()))
                .append(" / MEM ")
                .append(safe(item.getMemoryPercent()))
                .append(" / ")
                .append(safe(item.getCommandLine()))
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
