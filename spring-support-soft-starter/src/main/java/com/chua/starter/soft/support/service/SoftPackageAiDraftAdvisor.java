package com.chua.starter.soft.support.service;

import com.chua.starter.ai.support.chat.ChatClient;
import com.chua.starter.ai.support.chat.ChatResponse;
import com.chua.starter.ai.support.chat.ChatScope;
import com.chua.starter.soft.support.model.SoftPackageAiDraftRequest;
import com.chua.starter.soft.support.model.SoftPackageAiDraftResponse;
import com.chua.starter.soft.support.util.SoftArtifactRepositorySupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class SoftPackageAiDraftAdvisor {

    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[\\s\\S]*}");

    private final ObjectProvider<ChatClient> chatClientProvider;
    private final ObjectMapper objectMapper;

    public SoftPackageAiDraftResponse generate(SoftPackageAiDraftRequest request) {
        SoftPackageAiDraftRequest safeRequest = request == null ? new SoftPackageAiDraftRequest() : request;
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) {
            return fallbackDraft(safeRequest, "CHAT_CLIENT_MISSING", "未装配 ChatClient，已回退到默认草稿");
        }
        try {
            ChatResponse response = chatClient.chat(ChatScope.builder()
                    .input(buildPrompt(safeRequest))
                    .systemPrompt("你只返回合法 JSON，不要输出 Markdown。")
                    .mcpEnabled(false)
                    .build());
            SoftPackageAiDraftResponse draft = parseDraft(response);
            fillDefaults(draft, safeRequest);
            draft.setAiGenerated(Boolean.TRUE);
            draft.setProvider(response == null ? chatClient.getProvider() : response.getProvider());
            draft.setModel(response == null ? chatClient.getDefaultModel() : response.getModel());
            draft.setFallbackReason(null);
            draft.setMessage("AI 草稿生成成功");
            return draft;
        } catch (Exception e) {
            return fallbackDraft(safeRequest, "AI_DRAFT_FAILED", e.getMessage());
        }
    }

    private SoftPackageAiDraftResponse parseDraft(ChatResponse response) throws Exception {
        String raw = response == null ? null : response.getText();
        if (!StringUtils.hasText(raw)) {
            throw new IllegalStateException("AI 未返回草稿内容");
        }
        Matcher matcher = JSON_PATTERN.matcher(raw);
        String json = matcher.find() ? matcher.group() : raw.trim();
        JsonNode node = objectMapper.readTree(json);

        SoftPackageAiDraftResponse draft = new SoftPackageAiDraftResponse();
        draft.setSummary(text(node, "summary"));
        draft.setPackageCode(text(node, "packageCode"));
        draft.setPackageName(text(node, "packageName"));
        draft.setPackageCategory(text(node, "packageCategory"));
        draft.setProfileCode(text(node, "profileCode"));
        draft.setOsType(text(node, "osType"));
        draft.setArchitecture(text(node, "architecture"));
        draft.setDescription(text(node, "description"));
        draft.setIconUrl(text(node, "iconUrl"));
        draft.setVersionCode(text(node, "versionCode"));
        draft.setVersionName(text(node, "versionName"));
        draft.setDownloadUrls(textList(node, "downloadUrls"));
        draft.setInstallScript(text(node, "installScript"));
        draft.setInitScript(text(node, "initScript"));
        draft.setStartScript(text(node, "startScript"));
        draft.setStopScript(text(node, "stopScript"));
        draft.setUninstallScript(text(node, "uninstallScript"));
        draft.setServiceRegisterScript(text(node, "serviceRegisterScript"));
        draft.setServiceUnregisterScript(text(node, "serviceUnregisterScript"));
        draft.setEnabled(bool(node, "enabled", true));
        draft.setIntegrateServerService(bool(node, "integrateServerService", false));
        draft.setServerServiceCode(text(node, "serverServiceCode"));
        draft.setServerServiceName(text(node, "serverServiceName"));
        draft.setServerServiceType(text(node, "serverServiceType"));
        draft.setServerServiceStartMode(text(node, "serverServiceStartMode"));
        draft.setServerExecutionProvider(text(node, "serverExecutionProvider"));
        return draft;
    }

    private SoftPackageAiDraftResponse fallbackDraft(
            SoftPackageAiDraftRequest request,
            String reason,
            String message
    ) {
        SoftPackageAiDraftResponse draft = new SoftPackageAiDraftResponse();
        fillDefaults(draft, request);
        draft.setAiGenerated(Boolean.FALSE);
        draft.setFallbackReason(reason);
        draft.setMessage(StringUtils.hasText(message) ? message : "AI 草稿生成失败，已回退默认模板");
        return draft;
    }

    private void fillDefaults(SoftPackageAiDraftResponse draft, SoftPackageAiDraftRequest request) {
        if (draft == null) {
            return;
        }
        String packageName = firstNonBlank(
                draft.getPackageName(),
                request.getPackageName(),
                "新软件");
        draft.setPackageName(packageName);

        String packageCode = SoftArtifactRepositorySupport.normalizeSoftwareKey(firstNonBlank(
                draft.getPackageCode(),
                request.getPackageCode(),
                packageName));
        if (!StringUtils.hasText(packageCode)) {
            packageCode = "new-software";
        }
        draft.setPackageCode(packageCode);

        draft.setPackageCategory(firstNonBlank(draft.getPackageCategory(), request.getPackageCategory(), "general"));
        draft.setOsType(firstNonBlank(draft.getOsType(), request.getOsType(), "LINUX"));
        draft.setArchitecture(firstNonBlank(draft.getArchitecture(), request.getArchitecture(), "AMD64"));
        draft.setVersionCode(firstNonBlank(draft.getVersionCode(), request.getVersionCode(), "latest"));
        draft.setVersionName(firstNonBlank(draft.getVersionName(), draft.getVersionCode()));
        draft.setEnabled(draft.getEnabled() == null ? Boolean.TRUE : draft.getEnabled());
        draft.setIntegrateServerService(draft.getIntegrateServerService() == null
                ? (request.getIntegrateServerService() != null && request.getIntegrateServerService())
                : draft.getIntegrateServerService());
        draft.setDescription(firstNonBlank(draft.getDescription(), "由 soft AI 草稿生成，可按实际场景调整。"));
        if (draft.getDownloadUrls() == null) {
            draft.setDownloadUrls(new ArrayList<>());
        }
        applyDefaultScripts(draft);
        if (Boolean.TRUE.equals(draft.getIntegrateServerService())) {
            draft.setServerServiceCode(firstNonBlank(draft.getServerServiceCode(), packageCode + "-service"));
            draft.setServerServiceName(firstNonBlank(draft.getServerServiceName(), packageName + " Service"));
            draft.setServerServiceType(firstNonBlank(draft.getServerServiceType(), defaultServiceType(draft.getOsType())));
            draft.setServerServiceStartMode(firstNonBlank(draft.getServerServiceStartMode(), "AUTO"));
            draft.setServerExecutionProvider(firstNonBlank(draft.getServerExecutionProvider(), defaultExecutionProvider(draft.getOsType())));
        }
        draft.setSummary(firstNonBlank(draft.getSummary(), packageName + " 软件草稿"));
    }

    private void applyDefaultScripts(SoftPackageAiDraftResponse draft) {
        String packageCode = draft.getPackageCode();
        String versionCode = draft.getVersionCode();
        String serviceCode = firstNonBlank(draft.getServerServiceCode(), packageCode + "-service");
        boolean windows = isWindows(draft.getOsType());
        if (windows) {
            draft.setInstallScript(firstNonBlank(draft.getInstallScript(),
                    "Write-Host \"install " + packageCode + " " + versionCode + "\""));
            draft.setInitScript(firstNonBlank(draft.getInitScript(),
                    "Write-Host \"init " + packageCode + "\""));
            draft.setStartScript(firstNonBlank(draft.getStartScript(),
                    "Write-Host \"start " + packageCode + "\""));
            draft.setStopScript(firstNonBlank(draft.getStopScript(),
                    "Write-Host \"stop " + packageCode + "\""));
            draft.setUninstallScript(firstNonBlank(draft.getUninstallScript(),
                    "Write-Host \"uninstall " + packageCode + "\""));
            draft.setServiceRegisterScript(firstNonBlank(draft.getServiceRegisterScript(),
                    "sc.exe create " + serviceCode + " binPath= \"<install-path>\\\\start.bat\" start= auto"));
            draft.setServiceUnregisterScript(firstNonBlank(draft.getServiceUnregisterScript(),
                    "sc.exe delete " + serviceCode));
            return;
        }
        draft.setInstallScript(firstNonBlank(draft.getInstallScript(),
                "echo \"install " + packageCode + " " + versionCode + "\""));
        draft.setInitScript(firstNonBlank(draft.getInitScript(),
                "echo \"init " + packageCode + "\""));
        draft.setStartScript(firstNonBlank(draft.getStartScript(),
                "systemctl start " + serviceCode));
        draft.setStopScript(firstNonBlank(draft.getStopScript(),
                "systemctl stop " + serviceCode));
        draft.setUninstallScript(firstNonBlank(draft.getUninstallScript(),
                "echo \"uninstall " + packageCode + "\""));
        draft.setServiceRegisterScript(firstNonBlank(draft.getServiceRegisterScript(),
                "echo \"register service " + serviceCode + "\""));
        draft.setServiceUnregisterScript(firstNonBlank(draft.getServiceUnregisterScript(),
                "echo \"unregister service " + serviceCode + "\""));
    }

    private String buildPrompt(SoftPackageAiDraftRequest request) {
        return """
                你是软件交付配置助手，请基于输入信息生成“添加软件”表单草稿。
                只输出 JSON，不要输出 Markdown，不要解释。
                输出字段固定如下：
                {
                  "summary":"一句话摘要",
                  "packageCode":"软件编码",
                  "packageName":"软件名称",
                  "packageCategory":"分类",
                  "profileCode":"画像编码",
                  "osType":"LINUX/WINDOWS/MACOS",
                  "architecture":"AMD64/ARM64",
                  "description":"描述",
                  "iconUrl":"图标地址",
                  "versionCode":"版本编码",
                  "versionName":"版本名称",
                  "downloadUrls":["下载地址1","下载地址2"],
                  "installScript":"安装脚本",
                  "initScript":"初始化脚本",
                  "startScript":"启动脚本",
                  "stopScript":"停止脚本",
                  "uninstallScript":"卸载脚本",
                  "serviceRegisterScript":"服务注册脚本",
                  "serviceUnregisterScript":"服务卸载脚本",
                  "enabled":true,
                  "integrateServerService":true,
                  "serverServiceCode":"服务编码",
                  "serverServiceName":"服务名称",
                  "serverServiceType":"服务类型",
                  "serverServiceStartMode":"启动方式",
                  "serverExecutionProvider":"执行通道"
                }
                要求：
                1. 脚本要与操作系统一致，优先可执行。
                2. downloadUrls 必须输出数组。
                3. 不确定字段返回空字符串或空数组，不要编造真实内网地址。
                4. 如果需要服务化接入，补齐 server* 字段。

                用户描述：%s
                软件名称提示：%s
                软件编码提示：%s
                分类提示：%s
                系统提示：%s
                架构提示：%s
                版本提示：%s
                是否接入服务：%s
                """.formatted(
                safe(request.getPrompt()),
                safe(request.getPackageName()),
                safe(request.getPackageCode()),
                safe(request.getPackageCategory()),
                safe(request.getOsType()),
                safe(request.getArchitecture()),
                safe(request.getVersionCode()),
                request.getIntegrateServerService() == null ? "-" : request.getIntegrateServerService()
        );
    }

    private List<String> textList(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        if (value.isArray()) {
            value.forEach(item -> {
                String text = trim(item == null ? null : item.asText());
                if (StringUtils.hasText(text)) {
                    result.add(text);
                }
            });
            return result;
        }
        String[] segments = value.asText("").split("[\\r\\n,;]+");
        for (String segment : segments) {
            String text = trim(segment);
            if (StringUtils.hasText(text)) {
                result.add(text);
            }
        }
        return result;
    }

    private Boolean bool(JsonNode node, String field, boolean defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        String text = value.asText("");
        if (!StringUtils.hasText(text)) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(text.trim()) || "1".equals(text.trim());
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return trim(value == null || value.isNull() ? null : value.asText());
    }

    private String defaultServiceType(String osType) {
        return isWindows(osType) ? "WINDOWS_SERVICE" : "SYSTEMD";
    }

    private String defaultExecutionProvider(String osType) {
        return isWindows(osType) ? "WINRM" : "SSH";
    }

    private boolean isWindows(String osType) {
        return trim(osType).toLowerCase().contains("win");
    }

    private String safe(String value) {
        String text = trim(value);
        return StringUtils.hasText(text) ? text : "-";
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
