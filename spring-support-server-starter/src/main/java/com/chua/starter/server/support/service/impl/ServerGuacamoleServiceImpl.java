package com.chua.starter.server.support.service.impl;

import com.chua.starter.server.support.config.ServerManagementProperties;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerGuacamoleConfig;
import com.chua.starter.server.support.model.ServerRemoteGatewaySettings;
import com.chua.starter.server.support.service.ServerGuacamoleService;
import com.chua.starter.server.support.service.ServerRemoteGatewaySettingsService;
import com.chua.starter.server.support.service.ServerRemoteGatewayProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServerGuacamoleServiceImpl implements ServerGuacamoleService {

    private final ServerManagementProperties properties;
    private final ObjectMapper objectMapper;
    private final List<ServerRemoteGatewayProvider> providers;
    private final ServerRemoteGatewaySettingsService serverRemoteGatewaySettingsService;

    @Override
    public ServerGuacamoleConfig buildConfig(ServerHost host) {
        if (host == null) {
            return null;
        }
        Map<String, Object> metadata = mergeGlobalSettings(parseMetadata(host.getMetadataJson()), serverRemoteGatewaySettingsService.getGlobalSettings());
        String providerCode = firstText(
                metadata.get("remoteGatewayProvider"),
                properties.getRemoteGateway().getDefaultProvider());
        ServerRemoteGatewayProvider provider = providers.stream()
                .filter(item -> item.supports(providerCode))
                .findFirst()
                .orElseGet(() -> providers.stream()
                        .filter(item -> item.supports(properties.getRemoteGateway().getDefaultProvider()))
                        .findFirst()
                        .orElseGet(() -> providers.stream().findFirst().orElse(null)));
        if (provider == null) {
            return ServerGuacamoleConfig.builder()
                    .enabled(false)
                    .provider(providerCode)
                    .message("未找到可用的远程网关实现")
                    .build();
        }
        return provider.buildConfig(host, metadata);
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String firstText(Object primary, String fallback) {
        if (primary instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        return StringUtils.hasText(fallback) ? fallback.trim() : null;
    }

    private Map<String, Object> mergeGlobalSettings(
            Map<String, Object> metadata,
            ServerRemoteGatewaySettings globalSettings
    ) {
        if (globalSettings == null) {
            return metadata;
        }
        Map<String, Object> merged = new java.util.LinkedHashMap<>(metadata);
        merged.putIfAbsent("remoteGatewayEnabled", globalSettings.getEnabled());
        merged.putIfAbsent("remoteGatewayProvider", globalSettings.getProvider());
        merged.putIfAbsent("remoteGatewayUrl", globalSettings.getGatewayUrl());
        merged.putIfAbsent("remoteGatewayProtocol", globalSettings.getProtocol());
        merged.putIfAbsent("remoteGatewayLaunchPath", globalSettings.getLaunchPath());
        merged.putIfAbsent("remoteGatewayWebsocketPath", globalSettings.getWebsocketPath());
        return merged;
    }
}
