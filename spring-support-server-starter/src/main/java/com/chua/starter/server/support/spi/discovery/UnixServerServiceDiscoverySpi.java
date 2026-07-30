package com.chua.starter.server.support.spi.discovery;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerDetectedService;
import com.chua.starter.server.support.service.impl.ServerHostCommandExecutor;
import com.chua.starter.server.support.spi.ServerServiceDiscoverySpi;
import com.chua.starter.server.support.util.ServerCommandSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * Unix 服务发现 SPI。
 *
 * @author CH
 */
@Spi("unix")
public class UnixServerServiceDiscoverySpi implements ServerServiceDiscoverySpi {

    private static final String DETECT_COMMAND =
            "systemctl list-units --type=service --all --no-legend --no-pager " +
            "| awk '{print $1\"|\"$2\"|\"$3\"|\"$4}'";

    @Override
    public List<ServerDetectedService> detect(
            ServerHost host,
            ServerHostCommandExecutor commandExecutor,
            ObjectMapper objectMapper
    ) throws Exception {
        ServerCommandSupport.CommandResult result = commandExecutor.execute(host, DETECT_COMMAND);
        if (!result.success() && !StringUtils.hasText(result.output())) {
            throw new IllegalStateException("自动检测 Linux 服务失败: " + result.output());
        }
        List<ServerDetectedService> services = new ArrayList<>();
        for (String line : splitLines(result.output())) {
            String[] parts = line.split("\\|", 5);
            if (parts.length < 4 || !StringUtils.hasText(parts[0])) {
                continue;
            }
            Map<String, Object> metadata = baseMetadata(host, "systemd", "systemctl");
            metadata.put("loadState", parts[1]);
            metadata.put("activeState", parts[2]);
            metadata.put("subState", parts[3]);
            metadata.put("displayName", parts.length > 4 ? parts[4] : parts[0]);
            services.add(ServerDetectedService.builder()
                    .serviceName(parts[0].trim())
                    .serviceType("SYSTEMD_SERVICE")
                    .runtimeStatus(resolveRuntimeStatus(parts[2], parts[3]))
                    .description(parts.length > 4 ? trim(parts[4]) : null)
                    .metadataJson(objectMapper.writeValueAsString(metadata))
                    .build());
        }
        return services;
    }

    /**
 * 处理splitlines相关逻辑。
 */
    private List<String> splitLines(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return value.lines().map(String::trim).filter(StringUtils::hasText).toList();
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
 * 处理resolveruntimestatus相关逻辑。
 */
    private String resolveRuntimeStatus(String active, String subState) {
        String text = (safe(active) + " " + safe(subState)).toLowerCase();
        if (text.contains("failed") || text.contains("error")) return "ERROR";
        if (text.contains("active") || text.contains("running")) return "RUNNING";
        if (text.contains("inactive") || text.contains("dead") || text.contains("stopped")) return "STOPPED";
        return "UNKNOWN";
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
}

