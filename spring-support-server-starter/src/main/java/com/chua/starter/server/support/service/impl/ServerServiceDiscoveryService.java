package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.oauth.client.support.execute.AuthClientExecute;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.entity.ServerService;
import com.chua.starter.server.support.mapper.ServerServiceMapper;
import com.chua.starter.server.support.util.ServerCommandSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServerServiceDiscoveryService {

    private static final String SYSTEM_AUDIT_USER_ID = "0";
    private static final String SYSTEM_AUDIT_USERNAME = "server-system";

    private static final List<String> WINDOWS_BLACKLIST_KEYWORDS = Arrays.asList(
            "update",
            "updater",
            "elevation",
            "installer",
            "licensing",
            "license",
            "collector",
            "network sharing",
            "autoinstall",
            "diagnosticshub",
            "edgeupdate",
            "googleupdater",
            "quarkupdater",
            "wxworkupgrader",
            "wmpnetworksvc",
            "wpscloud",
            "defender",
            "security",
            "protect",
            "macrovision",
            "visual studio",
            "advanced systemcare",
            "baidunetdisk",
            "syscleanpro",
            "steam client service",
            "\\360\\");

    private final ServerHostCommandExecutor commandExecutor;
    private final ServerServiceMapper serverServiceMapper;
    private final ObjectMapper objectMapper;

    public List<ServerService> detectAndSync(ServerHost host) throws Exception {
        List<DetectedService> detected = ServerCommandSupport.isWindows(host.getOsType())
                ? detectWindowsServices(host)
                : detectLinuxServices(host);
        Map<String, ServerService> existingMap = new LinkedHashMap<>();
        serverServiceMapper.selectList(Wrappers.<ServerService>lambdaQuery()
                        .eq(ServerService::getServerId, host.getServerId()))
                .forEach(item -> existingMap.put(item.getServiceName(), item));
        List<DetectedService> filtered = detected.stream()
                .filter(item -> shouldKeepDetectedService(item, existingMap.get(item.serviceName())))
                .toList();
        List<ServerService> result = new ArrayList<>();
        for (DetectedService item : filtered) {
            ServerService service = existingMap.getOrDefault(item.serviceName(), new ServerService());
            service.setServerId(host.getServerId());
            service.setServiceName(item.serviceName());
            service.setServiceType(item.serviceType());
            service.setRuntimeStatus(item.runtimeStatus());
            if (!StringUtils.hasText(service.getInstallPath())) {
                service.setInstallPath(item.installPath());
            }
            if (!StringUtils.hasText(service.getDescription())) {
                service.setDescription(item.description());
            }
            service.setMetadataJson(item.metadataJson());
            if (service.getEnabled() == null) {
                service.setEnabled(Boolean.TRUE);
            }
            if (service.getServerServiceId() == null) {
                runWithSystemAudit(() -> serverServiceMapper.insert(service));
            } else {
                runWithSystemAudit(() -> serverServiceMapper.updateById(service));
            }
            result.add(service);
        }
        cleanupStaleDetectedServices(host.getServerId(), filtered, existingMap);
        return result;
    }

    private List<DetectedService> detectLinuxServices(ServerHost host) throws Exception {
        ServerCommandSupport.CommandResult result = commandExecutor.execute(host,
                "systemctl list-units --type=service --all --no-legend --no-pager --plain | awk '{name=$1; load=$2; active=$3; substate=$4; $1=\"\"; $2=\"\"; $3=\"\"; $4=\"\"; sub(/^ +/, \"\", $0); printf \"%s|%s|%s|%s|%s\\n\", name, load, active, substate, $0}'");
        if (!result.success() && !StringUtils.hasText(result.output())) {
            throw new IllegalStateException("自动检测 Linux 服务失败: " + result.output());
        }
        List<DetectedService> services = new ArrayList<>();
        for (String line : splitLines(result.output())) {
            String[] parts = line.split("\\|", 5);
            if (parts.length < 4 || !StringUtils.hasText(parts[0])) {
                continue;
            }
            Map<String, Object> metadata = buildDetectionMetadata(host, "systemd", "systemctl");
            metadata.put("loadState", parts[1]);
            metadata.put("activeState", parts[2]);
            metadata.put("subState", parts[3]);
            metadata.put("displayName", parts.length > 4 ? parts[4] : parts[0]);
            services.add(DetectedService.builder()
                    .serviceName(parts[0].trim())
                    .serviceType("SYSTEMD_SERVICE")
                    .runtimeStatus(resolveRuntimeStatus(parts[2], parts[3]))
                    .description(parts.length > 4 ? trim(parts[4]) : null)
                    .metadataJson(writeJson(metadata))
                    .build());
        }
        return services;
    }

    private List<DetectedService> detectWindowsServices(ServerHost host) throws Exception {
        ServerCommandSupport.CommandResult result = commandExecutor.execute(host,
                "Get-CimInstance Win32_Service | Select-Object Name,DisplayName,State,StartMode,PathName | ConvertTo-Json -Depth 3 -Compress");
        if (!result.success() && !StringUtils.hasText(result.output())) {
            throw new IllegalStateException("自动检测 Windows 服务失败: " + result.output());
        }
        JsonNode root = objectMapper.readTree(normalizeJsonArray(result.output()));
        List<DetectedService> services = new ArrayList<>();
        for (JsonNode node : root) {
            Map<String, Object> metadata = buildDetectionMetadata(host, "windows-service", "Get-CimInstance Win32_Service");
            metadata.put("displayName", text(node, "DisplayName"));
            metadata.put("startMode", text(node, "StartMode"));
            metadata.put("pathName", text(node, "PathName"));
            services.add(DetectedService.builder()
                    .serviceName(text(node, "Name"))
                    .serviceType("WINDOWS_SERVICE")
                    .runtimeStatus(resolveWindowsRuntimeStatus(text(node, "State")))
                    .installPath(resolveWindowsInstallPath(text(node, "PathName")))
                    .description(text(node, "DisplayName"))
                    .metadataJson(writeJson(metadata))
                    .build());
        }
        return services;
    }

    private boolean shouldKeepDetectedService(DetectedService detectedService, ServerService existing) {
        if (detectedService == null || !StringUtils.hasText(detectedService.serviceName())) {
            return false;
        }
        if (shouldRetainExisting(existing)) {
            return true;
        }
        if (!"WINDOWS_SERVICE".equalsIgnoreCase(detectedService.serviceType())) {
            return true;
        }
        return isManageableWindowsPath(detectedService.installPath())
                && isManageableWindowsService(detectedService);
    }

    private boolean shouldRetainExisting(ServerService existing) {
        if (existing == null) {
            return false;
        }
        return existing.getSoftInstallationId() != null
                || StringUtils.hasText(existing.getConfigPathsJson())
                || StringUtils.hasText(existing.getLogPathsJson())
                || StringUtils.hasText(existing.getConfigTemplate())
                || StringUtils.hasText(existing.getInitScript())
                || StringUtils.hasText(existing.getInstallScript())
                || StringUtils.hasText(existing.getUninstallScript())
                || StringUtils.hasText(existing.getDetectScript())
                || StringUtils.hasText(existing.getRegisterScript())
                || StringUtils.hasText(existing.getUnregisterScript())
                || StringUtils.hasText(existing.getStartScript())
                || StringUtils.hasText(existing.getStopScript())
                || StringUtils.hasText(existing.getRestartScript())
                || StringUtils.hasText(existing.getStatusScript())
                || !isAutoDetected(existing);
    }

    private boolean isManageableWindowsPath(String installPath) {
        String path = safe(installPath).replace('/', '\\').toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(path)) {
            return false;
        }
        return !path.startsWith("c:\\windows")
                && !path.startsWith("c:\\program files\\windowsapps")
                && !path.startsWith("c:\\program files\\common files")
                && !path.startsWith("c:\\programdata\\microsoft")
                && !path.contains("\\windows defender")
                && !path.contains("\\microsoft\\edgeupdate")
                && !path.contains("\\google\\googleupdater")
                && !path.contains("\\quarkupdater")
                && !path.contains("\\windows media player")
                && !path.contains("\\visual studio\\")
                && !path.contains("\\kingsoft\\office");
    }

    private boolean isManageableWindowsService(DetectedService service) {
        String name = safe(service.serviceName()).toLowerCase(Locale.ROOT);
        String description = safe(service.description()).toLowerCase(Locale.ROOT);
        String path = safe(service.installPath()).replace('/', '\\').toLowerCase(Locale.ROOT);
        String metadata = safe(service.metadataJson()).toLowerCase(Locale.ROOT);
        String combined = String.join("|", name, description, path, metadata);
        for (String keyword : WINDOWS_BLACKLIST_KEYWORDS) {
            if (combined.contains(keyword)) {
                return false;
            }
        }
        return true;
    }

    private boolean isAutoDetected(ServerService service) {
        JsonNode metadata = readMetadata(service == null ? null : service.getMetadataJson());
        return metadata.path("detected").asBoolean(false);
    }

    private JsonNode readMetadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(metadataJson);
            return node == null ? objectMapper.createObjectNode() : node;
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private void cleanupStaleDetectedServices(
            Integer serverId,
            List<DetectedService> detectedServices,
            Map<String, ServerService> existingMap
    ) {
        if (serverId == null || existingMap.isEmpty()) {
            return;
        }
        Map<String, Boolean> detectedNames = new LinkedHashMap<>();
        for (DetectedService item : detectedServices) {
            if (item != null && StringUtils.hasText(item.serviceName())) {
                detectedNames.put(item.serviceName(), Boolean.TRUE);
            }
        }
        existingMap.values().stream()
                .filter(this::isAutoDetected)
                .filter(item -> !shouldRetainExisting(item))
                .filter(item -> !detectedNames.containsKey(item.getServiceName()))
                .map(ServerService::getServerServiceId)
                .filter(id -> id != null && id > 0)
                .forEach(id -> runWithSystemAudit(() -> serverServiceMapper.deleteById(id)));
    }

    private List<String> splitLines(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return value.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String resolveRuntimeStatus(String active, String subState) {
        String text = (safe(active) + " " + safe(subState)).toLowerCase(Locale.ROOT);
        if (text.contains("failed") || text.contains("error")) {
            return "ERROR";
        }
        if (text.contains("active") || text.contains("running")) {
            return "RUNNING";
        }
        if (text.contains("inactive") || text.contains("dead") || text.contains("stopped")) {
            return "STOPPED";
        }
        return "UNKNOWN";
    }

    private String resolveWindowsRuntimeStatus(String state) {
        String text = safe(state).toLowerCase(Locale.ROOT);
        if (text.contains("running")) {
            return "RUNNING";
        }
        if (text.contains("stopped") || text.contains("stop pending")) {
            return "STOPPED";
        }
        if (text.contains("pause") || text.contains("pending")) {
            return "UNKNOWN";
        }
        return StringUtils.hasText(state) ? state.trim().toUpperCase(Locale.ROOT) : "UNKNOWN";
    }

    private Map<String, Object> buildDetectionMetadata(ServerHost host, String source, String command) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("detected", true);
        metadata.put("manageMode", "SPI");
        metadata.put("detectedSource", source);
        metadata.put("detectedAt", new Date().getTime());
        metadata.put("executionProvider", host == null ? null : host.getServerType());
        metadata.put("spiChannel", host == null ? null : host.getServerType());
        metadata.put("systemCapability", source);
        metadata.put("detectCommand", command);
        return metadata;
    }

    private String normalizeJsonArray(String text) {
        String trimmed = trim(text);
        if (!StringUtils.hasText(trimmed)) {
            return "[]";
        }
        return trimmed.startsWith("[") ? trimmed : "[" + trimmed + "]";
    }

    private String resolveWindowsInstallPath(String pathName) {
        if (!StringUtils.hasText(pathName)) {
            return null;
        }
        String trimmed = pathName.trim();
        String normalized = trimmed.startsWith("\"")
                ? trimmed.substring(1, trimmed.indexOf('"', 1) > 0 ? trimmed.indexOf('"', 1) : trimmed.length())
                : trimmed.split("\\s+", 2)[0];
        try {
            Path path = Paths.get(normalized);
            Path parent = path.getParent();
            return parent == null ? normalized : parent.toString();
        } catch (Exception ignored) {
            return normalized;
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return null;
        }
        return trim(node.get(field).asText());
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void runWithSystemAudit(Runnable action) {
        AuthClientExecute authClientExecute = AuthClientExecute.getInstance();
        UserResult current = authClientExecute.getSafeUserResult();
        if (current != null) {
            action.run();
            return;
        }
        String previousUsername = AuthClientExecute.getUsername();
        UserResume previousUser = AuthClientExecute.getUserInfo(UserResume.class);
        UserResult systemUser = new UserResult();
        systemUser.setUserId(SYSTEM_AUDIT_USER_ID);
        systemUser.setUsername(SYSTEM_AUDIT_USERNAME);
        AuthClientExecute.setUsername(SYSTEM_AUDIT_USERNAME);
        AuthClientExecute.setUserInfo(systemUser);
        try {
            action.run();
        } finally {
            if (previousUsername != null) {
                AuthClientExecute.setUsername(previousUsername);
            } else {
                AuthClientExecute.removeUsername();
            }
            if (previousUser != null) {
                AuthClientExecute.setUserInfo(previousUser);
            } else {
                AuthClientExecute.removeUserInfo();
            }
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Builder
    private record DetectedService(
            String serviceName,
            String serviceType,
            String runtimeStatus,
            String installPath,
            String description,
            String metadataJson
    ) {
    }
}
