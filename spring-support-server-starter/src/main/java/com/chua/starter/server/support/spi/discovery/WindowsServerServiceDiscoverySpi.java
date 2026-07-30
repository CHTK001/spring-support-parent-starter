package com.chua.starter.server.support.spi.discovery;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerDetectedService;
import com.chua.starter.server.support.service.impl.ServerHostCommandExecutor;
import com.chua.starter.server.support.spi.ServerServiceDiscoverySpi;
import com.chua.starter.server.support.util.ServerCommandSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * Windows 服务发现 SPI。
 *
 * @author CH
 */
@Spi("windows")
public class WindowsServerServiceDiscoverySpi implements ServerServiceDiscoverySpi {

    private static final String DETECT_COMMAND =
            "powershell Get-CimInstance Win32_Service | Select-Object Name,DisplayName,State,StartMode,PathName | ConvertTo-Json -Compress";

    @Override
    public List<ServerDetectedService> detect(
            ServerHost host,
            ServerHostCommandExecutor commandExecutor,
            ObjectMapper objectMapper
    ) throws Exception {
        ServerCommandSupport.CommandResult result = commandExecutor.execute(host, DETECT_COMMAND);
        if (!result.success() && !StringUtils.hasText(result.output())) {
            throw new IllegalStateException("自动检测 Windows 服务失败: " + result.output());
        }
        JsonNode root = objectMapper.readTree(normalizeJsonArray(result.output()));
        List<ServerDetectedService> services = new ArrayList<>();
        for (JsonNode node : root) {
            Map<String, Object> metadata = baseMetadata(host, "windows-service", "Get-CimInstance Win32_Service");
            metadata.put("displayName", text(node, "DisplayName"));
            metadata.put("startMode", text(node, "StartMode"));
            metadata.put("pathName", text(node, "PathName"));
            services.add(ServerDetectedService.builder()
                    .serviceName(text(node, "Name"))
                    .serviceType("WINDOWS_SERVICE")
                    .runtimeStatus(resolveWindowsRuntimeStatus(text(node, "State")))
                    .installPath(resolveWindowsInstallPath(text(node, "PathName")))
                    .description(text(node, "DisplayName"))
                    .metadataJson(objectMapper.writeValueAsString(metadata))
                    .build());
        }
        return services;
    }

    /**
 * 处理basemetadata相关逻辑。
 */
    private Map<String, Object> baseMetadata(ServerHost host, String source, String command) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("detected", true);
        metadata.put("manageMode", "SPI");
        metadata.put("detectedSource", source);
        metadata.put("detectedAt", System.currentTimeMillis());
        metadata.put("executionProvider", host == null ? null : host.getServerType());
        metadata.put("spiChannel", host == null ? null : host.getServerType());
        metadata.put("systemCapability", source);
        metadata.put("detectCommand", command);
        return metadata;
    }

    /**
 * 处理normalizejsonarray相关逻辑。
 */
    private String normalizeJsonArray(String text) {
        String trimmed = trim(text);
        if (!StringUtils.hasText(trimmed)) return "[]";
        return trimmed.startsWith("[") ? trimmed : "[" + trimmed + "]";
    }

    /**
 * 处理text相关逻辑。
 */
    private String text(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) return null;
        return trim(node.get(field).asText());
    }

    /**
 * 处理trim相关逻辑。
 */
    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
 * 处理safe相关逻辑。
 */
    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
 * 处理resolvewindowsruntimestatus相关逻辑。
 */
    private String resolveWindowsRuntimeStatus(String state) {
        String text = safe(state).toLowerCase();
        if (text.contains("running")) return "RUNNING";
        if (text.contains("stopped") || text.contains("stop pending")) return "STOPPED";
        if (text.contains("pause") || text.contains("pending")) return "UNKNOWN";
        return StringUtils.hasText(state) ? state.trim().toUpperCase() : "UNKNOWN";
    }

    /**
 * 处理resolvewindowsinstallpath相关逻辑。
 */
    private String resolveWindowsInstallPath(String pathName) {
        if (!StringUtils.hasText(pathName)) return null;
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
}

