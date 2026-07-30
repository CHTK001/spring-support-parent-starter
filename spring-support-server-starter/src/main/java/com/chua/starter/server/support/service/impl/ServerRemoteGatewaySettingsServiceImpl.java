package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.server.support.config.ServerManagementProperties;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.entity.ServerSetting;
import com.chua.starter.server.support.mapper.ServerHostMapper;
import com.chua.starter.server.support.mapper.ServerSettingMapper;
import com.chua.starter.server.support.model.ServerRemoteGatewaySettings;
import com.chua.starter.server.support.service.ServerRemoteGatewaySettingsService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServerRemoteGatewaySettingsServiceImpl implements ServerRemoteGatewaySettingsService {

    private static final String REMOTE_GATEWAY_SETTING_KEY = "REMOTE_GATEWAY_GLOBAL";

    private final ServerSettingMapper serverSettingMapper;
    private final ServerHostMapper serverHostMapper;
    private final ObjectMapper objectMapper;
    private final ServerManagementProperties properties;

    @Override
    public ServerRemoteGatewaySettings getGlobalSettings() {
        ServerSetting setting = serverSettingMapper.selectOne(Wrappers.<ServerSetting>lambdaQuery()
                .eq(ServerSetting::getSettingKey, REMOTE_GATEWAY_SETTING_KEY)
                .last("limit 1"));
        String defaultProvider = textValue(properties.getRemoteGateway().getDefaultProvider(), "guacamole");
        String defaultGatewayUrl = defaultGatewayUrl(defaultProvider);
        ServerRemoteGatewaySettings defaults = ServerRemoteGatewaySettings.builder()
                .inheritGlobal(false)
                .enabled(defaultEnabled(defaultProvider, defaultGatewayUrl))
                .provider(defaultProvider)
                .gatewayUrl(defaultGatewayUrl)
                .protocol(null)
                .launchPath(defaultLaunchPath(defaultProvider))
                .websocketPath(defaultWebsocketPath(defaultProvider))
                .connectionId(null)
                .build();
        if (setting == null || !StringUtils.hasText(setting.getSettingValue())) {
            return defaults;
        }
        try {
            ServerRemoteGatewaySettings stored = objectMapper.readValue(setting.getSettingValue(), ServerRemoteGatewaySettings.class);
            return normalizeSettings(mergeSettings(defaults, stored), false);
        } catch (Exception ignored) {
            return defaults;
        }
    }

    @Override
    public ServerRemoteGatewaySettings saveGlobalSettings(ServerRemoteGatewaySettings settings) {
        ServerRemoteGatewaySettings normalized = normalizeSettings(settings, false);
        ServerSetting current = serverSettingMapper.selectOne(Wrappers.<ServerSetting>lambdaQuery()
                .eq(ServerSetting::getSettingKey, REMOTE_GATEWAY_SETTING_KEY)
                .last("limit 1"));
        if (current == null) {
            current = new ServerSetting();
            current.setSettingKey(REMOTE_GATEWAY_SETTING_KEY);
        }
        current.setSettingValue(writeValueAsString(normalized));
        if (current.getServerSettingId() == null) {
            serverSettingMapper.insert(current);
        } else {
            serverSettingMapper.updateById(current);
        }
        return getGlobalSettings();
    }

    @Override
    public ServerRemoteGatewaySettings getHostSettings(Integer serverId) {
        ServerHost host = requireHost(serverId);
        Map<String, Object> metadata = parseMetadata(host.getMetadataJson());
        boolean hasOverride = hasHostOverride(metadata);
        ServerRemoteGatewaySettings global = getGlobalSettings();
        ServerRemoteGatewaySettings settings = ServerRemoteGatewaySettings.builder()
                .inheritGlobal(!hasOverride)
                .enabled(readBoolean(
                        metadata.get("remoteGatewayEnabled"),
                        readBoolean(metadata.get("guacamoleEnabled"), global.getEnabled())))
                .provider(textValue(
                        metadata.get("remoteGatewayProvider"),
                        textValue(metadata.get("guacamoleProvider"), global.getProvider())))
                .gatewayUrl(textValue(
                        metadata.get("remoteGatewayUrl"),
                        textValue(metadata.get("guacamoleGatewayUrl"), global.getGatewayUrl())))
                .protocol(textValue(
                        metadata.get("remoteGatewayProtocol"),
                        textValue(metadata.get("guacamoleProtocol"), global.getProtocol())))
                .launchPath(textValue(
                        metadata.get("remoteGatewayLaunchPath"),
                        textValue(metadata.get("guacamoleLaunchPath"), global.getLaunchPath())))
                .websocketPath(textValue(
                        metadata.get("remoteGatewayWebsocketPath"),
                        textValue(metadata.get("guacamoleWebsocketPath"), global.getWebsocketPath())))
                .connectionId(textValue(
                        metadata.get("remoteGatewayConnectionId"),
                        textValue(metadata.get("guacamoleConnectionId"), null)))
                .build();
        return normalizeSettings(settings, true);
    }

    @Override
    public ServerRemoteGatewaySettings saveHostSettings(Integer serverId, ServerRemoteGatewaySettings settings) {
        ServerHost host = requireHost(serverId);
        ServerRemoteGatewaySettings normalized = normalizeSettings(settings, true);
        Map<String, Object> metadata = new LinkedHashMap<>(parseMetadata(host.getMetadataJson()));
        removeRemoteGatewayKeys(metadata);
        if (!Boolean.TRUE.equals(normalized.getInheritGlobal())) {
            metadata.put("remoteGatewayEnabled", Boolean.TRUE.equals(normalized.getEnabled()));
            if (StringUtils.hasText(normalized.getProvider())) {
                metadata.put("remoteGatewayProvider", normalized.getProvider());
            }
            if (StringUtils.hasText(normalized.getGatewayUrl())) {
                metadata.put("remoteGatewayUrl", normalized.getGatewayUrl());
            }
            if (StringUtils.hasText(normalized.getProtocol())) {
                metadata.put("remoteGatewayProtocol", normalized.getProtocol());
            }
            if (StringUtils.hasText(normalized.getLaunchPath())) {
                metadata.put("remoteGatewayLaunchPath", normalized.getLaunchPath());
            }
            if (StringUtils.hasText(normalized.getWebsocketPath())) {
                metadata.put("remoteGatewayWebsocketPath", normalized.getWebsocketPath());
            }
            if (StringUtils.hasText(normalized.getConnectionId())) {
                metadata.put("remoteGatewayConnectionId", normalized.getConnectionId());
            }
        }
        host.setMetadataJson(metadata.isEmpty() ? null : writeValueAsString(metadata));
        serverHostMapper.updateById(host);
        return getHostSettings(serverId);
    }

    private ServerHost requireHost(Integer serverId) {
        ServerHost host = serverHostMapper.selectById(serverId);
        if (host == null) {
            throw new IllegalStateException("服务器不存在: " + serverId);
        }
        return host;
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

    private void removeRemoteGatewayKeys(Map<String, Object> metadata) {
        metadata.remove("remoteGatewayEnabled");
        metadata.remove("remoteGatewayProvider");
        metadata.remove("remoteGatewayUrl");
        metadata.remove("remoteGatewayProtocol");
        metadata.remove("remoteGatewayLaunchPath");
        metadata.remove("remoteGatewayWebsocketPath");
        metadata.remove("remoteGatewayConnectionId");
        metadata.remove("remoteGatewayAuthMode");
        metadata.remove("remoteGatewaySecretKey");
        metadata.remove("remoteGatewayHostname");
        metadata.remove("remoteGatewayPort");
        metadata.remove("guacamoleEnabled");
        metadata.remove("guacamoleProvider");
        metadata.remove("guacamoleGatewayUrl");
        metadata.remove("guacamoleProtocol");
        metadata.remove("guacamoleLaunchPath");
        metadata.remove("guacamoleWebsocketPath");
        metadata.remove("guacamoleConnectionId");
        metadata.remove("guacamoleAuthMode");
        metadata.remove("guacamoleSecretKey");
        metadata.remove("guacamoleHostname");
        metadata.remove("guacamolePort");
    }

    private boolean hasHostOverride(Map<String, Object> metadata) {
        return metadata.containsKey("remoteGatewayEnabled")
                || metadata.containsKey("remoteGatewayProvider")
                || metadata.containsKey("remoteGatewayUrl")
                || metadata.containsKey("remoteGatewayProtocol")
                || metadata.containsKey("remoteGatewayLaunchPath")
                || metadata.containsKey("remoteGatewayWebsocketPath")
                || metadata.containsKey("remoteGatewayConnectionId")
                || metadata.containsKey("remoteGatewayAuthMode")
                || metadata.containsKey("remoteGatewaySecretKey")
                || metadata.containsKey("remoteGatewayHostname")
                || metadata.containsKey("remoteGatewayPort")
                || metadata.containsKey("guacamoleEnabled")
                || metadata.containsKey("guacamoleProvider")
                || metadata.containsKey("guacamoleGatewayUrl")
                || metadata.containsKey("guacamoleProtocol")
                || metadata.containsKey("guacamoleLaunchPath")
                || metadata.containsKey("guacamoleWebsocketPath")
                || metadata.containsKey("guacamoleConnectionId")
                || metadata.containsKey("guacamoleAuthMode")
                || metadata.containsKey("guacamoleSecretKey")
                || metadata.containsKey("guacamoleHostname")
                || metadata.containsKey("guacamolePort");
    }

    private ServerRemoteGatewaySettings mergeSettings(ServerRemoteGatewaySettings base, ServerRemoteGatewaySettings override) {
        if (override == null) {
            return base;
        }
        return ServerRemoteGatewaySettings.builder()
                .inheritGlobal(Boolean.TRUE.equals(override.getInheritGlobal()))
                .enabled(override.getEnabled() != null ? override.getEnabled() : base.getEnabled())
                .provider(textValue(override.getProvider(), base.getProvider()))
                .gatewayUrl(textValue(override.getGatewayUrl(), base.getGatewayUrl()))
                .protocol(textValue(override.getProtocol(), base.getProtocol()))
                .launchPath(textValue(override.getLaunchPath(), base.getLaunchPath()))
                .websocketPath(textValue(override.getWebsocketPath(), base.getWebsocketPath()))
                .connectionId(textValue(override.getConnectionId(), base.getConnectionId()))
                .build();
    }

    private ServerRemoteGatewaySettings normalizeSettings(ServerRemoteGatewaySettings settings, boolean supportInheritGlobal) {
        ServerRemoteGatewaySettings global = supportInheritGlobal ? getGlobalSettings() : null;
        boolean inheritGlobal = supportInheritGlobal && Boolean.TRUE.equals(settings.getInheritGlobal());
        String provider = textValue(settings.getProvider(), supportInheritGlobal && inheritGlobal ? global.getProvider() : properties.getRemoteGateway().getDefaultProvider());
        String gatewayUrl = textValue(settings.getGatewayUrl(), supportInheritGlobal && inheritGlobal ? global.getGatewayUrl() : null);
        String launchPath = textValue(
                settings.getLaunchPath(),
                supportInheritGlobal && inheritGlobal ? global.getLaunchPath() : defaultLaunchPath(provider));
        String websocketPath = textValue(
                settings.getWebsocketPath(),
                supportInheritGlobal && inheritGlobal ? global.getWebsocketPath() : defaultWebsocketPath(provider));
        String protocol = textValue(settings.getProtocol(), supportInheritGlobal && inheritGlobal ? global.getProtocol() : null);
        String connectionId = textValue(settings.getConnectionId(), null);
        Boolean enabled = settings.getEnabled();
        if (enabled == null) {
            enabled = supportInheritGlobal && inheritGlobal ? global.getEnabled() : Boolean.FALSE;
        }
        return ServerRemoteGatewaySettings.builder()
                .inheritGlobal(supportInheritGlobal ? inheritGlobal : false)
                .enabled(enabled)
                .provider(provider)
                .gatewayUrl(gatewayUrl)
                .protocol(protocol)
                .launchPath(launchPath)
                .websocketPath(websocketPath)
                .connectionId(connectionId)
                .build();
    }

    private String defaultLaunchPath(String provider) {
        return textValue(properties.getGuacamole().getLaunchPath(), "/#/client/");
    }

    private String defaultWebsocketPath(String provider) {
        return textValue(properties.getGuacamole().getWebsocketPath(), "/guacamole/websocket-tunnel");
    }

    private String defaultGatewayUrl(String provider) {
        if ("guacamole".equalsIgnoreCase(provider)) {
            return textValue(properties.getGuacamole().getGatewayUrl(), null);
        }
        return null;
    }

    private boolean defaultEnabled(String provider, String gatewayUrl) {
        if (!properties.getRemoteGateway().isEnable()) {
            return false;
        }
        return !"guacamole".equalsIgnoreCase(provider) || StringUtils.hasText(gatewayUrl);
    }

    private Boolean readBoolean(Object value, Boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Boolean.parseBoolean(text.trim());
        }
        return fallback;
    }

    private String textValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : (StringUtils.hasText(fallback) ? fallback.trim() : null);
    }

    private String textValue(Object value, String fallback) {
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        return textValue(fallback, null);
    }

    private String writeValueAsString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化远程代理配置失败", e);
        }
    }
}
