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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
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
        if (!StringUtils.hasText(output)) {
            return null;
        }
        if (chatClient == null) {
            return buildHeuristicAdvice(service, output, "未装配 ChatClient，已回落启发式诊断");
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
            ServerServiceAiAdvice advice = parseAdvice(response, chatClient);
            return hasUsefulAdvice(advice)
                    ? advice
                    : buildHeuristicAdvice(service, output, "AI 未返回有效 JSON 字段，已回落启发式诊断");
        } catch (Exception ex) {
            log.warn("服务 AI 诊断失败，serviceId={}, serviceName={}, reason={}",
                    service == null ? null : service.getServerServiceId(),
                    service == null ? null : service.getServiceName(),
                    ex.getMessage());
            return buildHeuristicAdvice(service, output, "AI 诊断异常，已回落启发式诊断");
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

    /**
     * 判断 AI 是否至少返回了原因或方案，避免空壳结果落库。
     */
    private boolean hasUsefulAdvice(ServerServiceAiAdvice advice) {
        return advice != null
                && (StringUtils.hasText(advice.getReason()) || StringUtils.hasText(advice.getSolution()));
    }

    /**
     * 当 AI 不可用或返回异常时，使用操作系统错误输出来构造可执行的保底诊断。
     */
    private ServerServiceAiAdvice buildHeuristicAdvice(ServerService service, String output, String fallbackMessage) {
        String normalized = output == null ? "" : output.toLowerCase();
        String serviceName = service == null ? "目标服务" : nullSafe(service.getServiceName());
        if (normalized.contains("unit not found")) {
            return ServerServiceAiAdvice.builder()
                    .reason(serviceName + " 对应的 systemd 单元不存在，当前服务名或注册信息无效。")
                    .solution("先执行 `systemctl list-unit-files --type=service | grep -i \""
                            + sanitizeShellToken(service == null ? null : service.getServiceName())
                            + "\"` 确认真实服务名；若不存在，检查服务主档里的 serviceName、registerScript、startScript 是否写错。")
                    .fixScript("systemctl list-unit-files --type=service | grep -i \""
                            + sanitizeShellToken(service == null ? null : service.getServiceName())
                            + "\"")
                    .provider("LOCAL_HEURISTIC")
                    .model(fallbackMessage)
                    .build();
        }
        if (normalized.contains("permission denied") || normalized.contains("access denied")) {
            return ServerServiceAiAdvice.builder()
                    .reason("当前执行账号没有足够权限操作该服务或其脚本。")
                    .solution("确认服务器接入账号是否具备 sudo/管理员权限，并检查脚本文件是否具备执行权限。")
                    .fixScript("id && whoami && ls -l " + sanitizeShellPath(service == null ? null : service.getInstallPath()))
                    .provider("LOCAL_HEURISTIC")
                    .model(fallbackMessage)
                    .build();
        }
        if (normalized.contains("address already in use") || normalized.contains("port already in use")) {
            return ServerServiceAiAdvice.builder()
                    .reason("服务启动依赖的端口已被其他进程占用。")
                    .solution("先定位占用端口的进程并确认是否应释放，然后再重新启动当前服务。")
                    .fixScript("ss -lntp | grep -E 'LISTEN|"
                            + sanitizeShellToken(service == null ? null : service.getServiceName())
                            + "'")
                    .provider("LOCAL_HEURISTIC")
                    .model(fallbackMessage)
                    .build();
        }
        if (normalized.contains("no such file or directory") || normalized.contains("not found")) {
            return ServerServiceAiAdvice.builder()
                    .reason("服务依赖的脚本、二进制或配置路径不存在。")
                    .solution("检查 installPath、configPaths 和脚本模板里的真实路径，确认文件是否已经部署到目标服务器。")
                    .fixScript("ls -la " + sanitizeShellPath(service == null ? null : service.getInstallPath()))
                    .provider("LOCAL_HEURISTIC")
                    .model(fallbackMessage)
                    .build();
        }
        return ServerServiceAiAdvice.builder()
                .reason("服务执行失败，操作系统返回了错误输出，但 AI 未生成结构化诊断。")
                .solution("先根据失败输出检查服务名、脚本路径、权限和端口占用，再决定是修正主档还是直接修复服务器环境。")
                .fixScript("")
                .provider("LOCAL_HEURISTIC")
                .model(fallbackMessage)
                .build();
    }

    /**
     * 过滤命令片段里的引号，避免生成脚本时破坏 shell 语义。
     */
    private String sanitizeShellToken(String value) {
        if (!StringUtils.hasText(value)) {
            return "service";
        }
        return value.replace("\"", "").replace("'", "").trim();
    }

    /**
     * 给路径型脚本参数补一个保底目录。
     */
    private String sanitizeShellPath(String value) {
        if (!StringUtils.hasText(value)) {
            return "/";
        }
        return value.replace("\"", "").replace("'", "").trim();
    }
}
