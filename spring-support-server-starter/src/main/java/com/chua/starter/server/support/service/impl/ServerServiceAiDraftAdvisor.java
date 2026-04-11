package com.chua.starter.server.support.service.impl;

import com.chua.starter.ai.support.chat.ChatClient;
import com.chua.starter.ai.support.chat.ChatResponse;
import com.chua.starter.ai.support.chat.ChatScope;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.entity.ServerService;
import com.chua.starter.server.support.model.ServerServiceAiDraft;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServerServiceAiDraftAdvisor {

    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[\\s\\S]*}");

    private final ObjectProvider<ChatClient> chatClientProvider;
    private final ObjectMapper objectMapper;

    public ServerServiceAiDraft generate(ServerHost host, ServerService service) {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null || host == null || service == null) {
            return null;
        }
        String prompt = """
                你是服务器服务主档整理助手。
                请基于下面信息，为当前服务器服务输出可直接保存的主档草稿。
                只返回 JSON，不要输出 Markdown，不要解释。
                要求：
                1. 所有脚本都要和当前服务器接入方式、操作系统一致。
                2. 若字段无法确定，返回空字符串或空数组，不要编造路径。
                3. configPaths / logPaths 必须返回 JSON 数组语义。
                4. statusScript 必须尽量返回能判断 RUNNING / STOPPED / ERROR 的脚本。
                5. 如果是 Windows 原生服务，registerScript / unregisterScript 可以留空。

                JSON 结构固定：
                {
                  "summary": "一句话摘要",
                  "description": "服务描述",
                  "configPaths": ["配置路径1"],
                  "logPaths": ["日志路径1"],
                  "configTemplate": "配置模板",
                  "initScript": "初始化脚本",
                  "installScript": "安装脚本",
                  "uninstallScript": "卸载脚本",
                  "detectScript": "检测脚本",
                  "registerScript": "注册脚本",
                  "unregisterScript": "取消注册脚本",
                  "startScript": "启动脚本",
                  "stopScript": "停止脚本",
                  "restartScript": "重启脚本",
                  "statusScript": "状态脚本"
                }

                服务器名称：%s
                主机地址：%s
                接入类型：%s
                操作系统：%s
                架构：%s
                服务名称：%s
                服务类型：%s
                安装目录：%s
                当前状态：%s
                当前描述：%s
                当前配置路径JSON：%s
                当前日志路径JSON：%s
                当前配置模板：%s
                当前初始化脚本：%s
                当前安装脚本：%s
                当前卸载脚本：%s
                当前检测脚本：%s
                当前注册脚本：%s
                当前取消注册脚本：%s
                当前启动脚本：%s
                当前停止脚本：%s
                当前重启脚本：%s
                当前状态脚本：%s
                元数据：%s
                """.formatted(
                safe(host.getServerName()),
                safe(host.getHost()),
                safe(host.getServerType()),
                safe(host.getOsType()),
                safe(host.getArchitecture()),
                safe(service.getServiceName()),
                safe(service.getServiceType()),
                safe(service.getInstallPath()),
                safe(service.getRuntimeStatus()),
                safe(service.getDescription()),
                safe(service.getConfigPathsJson()),
                safe(service.getLogPathsJson()),
                limit(service.getConfigTemplate(), 2000),
                limit(service.getInitScript(), 1200),
                limit(service.getInstallScript(), 1200),
                limit(service.getUninstallScript(), 1200),
                limit(service.getDetectScript(), 1200),
                limit(service.getRegisterScript(), 1200),
                limit(service.getUnregisterScript(), 1200),
                limit(service.getStartScript(), 1200),
                limit(service.getStopScript(), 1200),
                limit(service.getRestartScript(), 1200),
                limit(service.getStatusScript(), 1200),
                limit(service.getMetadataJson(), 2500));
        try {
            ChatResponse response = chatClient.chat(ChatScope.builder()
                    .input(prompt)
                    .systemPrompt("你只返回合法 JSON。")
                    .mcpEnabled(false)
                    .build());
            return parse(response, chatClient);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ServerServiceAiDraft parse(ChatResponse response, ChatClient chatClient) throws Exception {
        String raw = response == null ? null : response.getText();
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        Matcher matcher = JSON_PATTERN.matcher(raw);
        String json = matcher.find() ? matcher.group() : raw.trim();
        JsonNode node = objectMapper.readTree(json);
        return ServerServiceAiDraft.builder()
                .summary(text(node, "summary"))
                .description(text(node, "description"))
                .configPathsJson(arrayJson(node, "configPaths"))
                .logPathsJson(arrayJson(node, "logPaths"))
                .configTemplate(text(node, "configTemplate"))
                .initScript(text(node, "initScript"))
                .installScript(text(node, "installScript"))
                .uninstallScript(text(node, "uninstallScript"))
                .detectScript(text(node, "detectScript"))
                .registerScript(text(node, "registerScript"))
                .unregisterScript(text(node, "unregisterScript"))
                .startScript(text(node, "startScript"))
                .stopScript(text(node, "stopScript"))
                .restartScript(text(node, "restartScript"))
                .statusScript(text(node, "statusScript"))
                .provider(response == null ? chatClient.getProvider() : response.getProvider())
                .model(response == null ? chatClient.getDefaultModel() : response.getModel())
                .build();
    }

    private String arrayJson(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        ArrayNode arrayNode = objectMapper.createArrayNode();
        if (value.isArray()) {
            value.forEach(item -> {
                String text = item == null ? null : item.asText();
                if (StringUtils.hasText(text)) {
                    arrayNode.add(text.trim());
                }
            });
        } else {
            for (String item : split(value.asText())) {
                arrayNode.add(item);
            }
        }
        return arrayNode.isEmpty() ? null : arrayNode.toString();
    }

    private List<String> split(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String item : value.split("[\\r\\n,;]+")) {
            if (StringUtils.hasText(item)) {
                result.add(item.trim());
            }
        }
        return result;
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
