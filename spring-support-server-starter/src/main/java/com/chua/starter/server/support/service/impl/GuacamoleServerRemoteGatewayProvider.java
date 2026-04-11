package com.chua.starter.server.support.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chua.starter.server.support.config.ServerManagementProperties;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerGuacamoleConfig;
import com.chua.starter.server.support.service.ServerRemoteGatewayProvider;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class GuacamoleServerRemoteGatewayProvider implements ServerRemoteGatewayProvider {

    private final ServerManagementProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public String getProvider() {
        return "guacamole";
    }

    @Override
    public ServerGuacamoleConfig buildConfig(ServerHost host, Map<String, Object> metadata) {
        if (host == null) {
            return null;
        }
        String gatewayUrl = firstText(
                metadata.get("remoteGatewayUrl"),
                firstText(metadata.get("guacamoleGatewayUrl"), properties.getGuacamole().getGatewayUrl()));
        boolean enabled = resolveEnabled(metadata, gatewayUrl);
        String protocol = resolveProtocol(host, metadata);
        String connectionId = resolveConnectionId(host, metadata);
        String normalizedGateway = normalizeUrl(gatewayUrl);
        String websocketPath = firstText(
                metadata.get("remoteGatewayWebsocketPath"),
                firstText(metadata.get("guacamoleWebsocketPath"), properties.getGuacamole().getWebsocketPath()));
        String launchPath = firstText(
                metadata.get("remoteGatewayLaunchPath"),
                firstText(metadata.get("guacamoleLaunchPath"), properties.getGuacamole().getLaunchPath()));
        Map<String, String> parameters = buildParameters(host, metadata, protocol);
        String launchUrl = null;
        String message = resolveMessage(enabled, gatewayUrl);
        if (enabled) {
            try {
                launchUrl = buildLaunchUrl(host, metadata, normalizedGateway, launchPath, connectionId, protocol, parameters);
            } catch (IllegalStateException ex) {
                message = ex.getMessage();
            }
        }
        boolean available = enabled && StringUtils.hasText(launchUrl);
        return ServerGuacamoleConfig.builder()
                .provider(getProvider())
                .enabled(available)
                .protocol(protocol)
                .gatewayUrl(normalizedGateway)
                .websocketUrl(available ? normalizedGateway + normalizePath(websocketPath) : null)
                .launchUrl(available ? launchUrl : null)
                .connectionId(connectionId)
                .message(available ? null : message)
                .parameters(parameters)
                .build();
    }

    private Map<String, String> buildParameters(ServerHost host, Map<String, Object> metadata, String protocol) {
        Map<String, String> parameters = new LinkedHashMap<>();
        String normalizedProtocol = StringUtils.hasText(protocol)
                ? protocol.trim().toLowerCase(Locale.ROOT)
                : "ssh";
        parameters.put("protocol", normalizedProtocol);
        parameters.put("hostname", resolveHostname(host, metadata));
        parameters.put("port", String.valueOf(resolvePort(host, metadata, normalizedProtocol)));
        parameters.put("username", StringUtils.hasText(host.getUsername()) ? host.getUsername() : "");
        parameters.put("password", StringUtils.hasText(host.getPassword()) ? host.getPassword() : "");
        if ("rdp".equals(normalizedProtocol)) {
            parameters.put("security", "any");
            parameters.put("ignore-cert", "true");
        }
        return parameters;
    }

    private String resolveHostname(ServerHost host, Map<String, Object> metadata) {
        String explicit = firstText(
                metadata.get("remoteGatewayHostname"),
                firstText(metadata.get("guacamoleHostname"), null));
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        if ("LOCAL".equalsIgnoreCase(host.getServerType())) {
            return "127.0.0.1";
        }
        return StringUtils.hasText(host.getHost()) ? host.getHost() : "127.0.0.1";
    }

    private int resolvePort(ServerHost host, Map<String, Object> metadata, String protocol) {
        Integer explicit = integerValue(
                metadata.get("remoteGatewayPort"),
                integerValue(metadata.get("guacamolePort"), null));
        if (explicit != null && explicit > 0) {
            return explicit;
        }
        if ("ssh".equalsIgnoreCase(protocol)) {
            if (host.getPort() != null && host.getPort() > 0) {
                return host.getPort();
            }
            return 22;
        }
        if ("rdp".equalsIgnoreCase(protocol)) {
            return 3389;
        }
        if ("vnc".equalsIgnoreCase(protocol)) {
            return 5900;
        }
        return host.getPort() != null && host.getPort() > 0 ? host.getPort() : 0;
    }

    private String resolveProtocol(ServerHost host, Map<String, Object> metadata) {
        String explicit = firstText(
                metadata.get("remoteGatewayProtocol"),
                firstText(metadata.get("guacamoleProtocol"), null));
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        if ("WINRM".equalsIgnoreCase(host.getServerType()) || "windows".equalsIgnoreCase(host.getOsType())) {
            return properties.getGuacamole().getDefaultWindowsProtocol();
        }
        return properties.getGuacamole().getDefaultLinuxProtocol();
    }

    private boolean resolveEnabled(Map<String, Object> metadata, String gatewayUrl) {
        Object explicit = metadata.get("remoteGatewayEnabled");
        if (explicit == null) {
            explicit = metadata.get("guacamoleEnabled");
        }
        if (explicit instanceof Boolean value) {
            return value && StringUtils.hasText(gatewayUrl);
        }
        if (explicit instanceof String value && StringUtils.hasText(value)) {
            return Boolean.parseBoolean(value.trim()) && StringUtils.hasText(gatewayUrl);
        }
        return (properties.getRemoteGateway().isEnable() || properties.getGuacamole().isEnable())
                && StringUtils.hasText(gatewayUrl);
    }

    private String resolveConnectionId(ServerHost host, Map<String, Object> metadata) {
        String explicit = firstText(
                metadata.get("remoteGatewayConnectionId"),
                firstText(metadata.get("guacamoleConnectionId"), null));
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        return "server-" + host.getServerId();
    }

    private String buildLaunchUrl(
            ServerHost host,
            Map<String, Object> metadata,
            String gatewayUrl,
            String launchPath,
            String connectionId,
            String protocol,
            Map<String, String> parameters
    ) {
        if (!StringUtils.hasText(gatewayUrl)) {
            throw new IllegalStateException("远程网关地址未配置");
        }
        String authMode = firstText(
                metadata.get("remoteGatewayAuthMode"),
                firstText(metadata.get("guacamoleAuthMode"), properties.getGuacamole().getAuthMode()));
        if ("json".equalsIgnoreCase(authMode)) {
            String secretKey = firstText(
                    metadata.get("remoteGatewaySecretKey"),
                    firstText(metadata.get("guacamoleSecretKey"), properties.getGuacamole().getJsonSecretKey()));
            if (!StringUtils.hasText(secretKey)) {
                throw new IllegalStateException("Guacamole JSON Auth 密钥未配置");
            }
            return buildJsonLaunchUrl(host, gatewayUrl, connectionId, protocol, parameters, secretKey);
        }
        return gatewayUrl + normalizeLaunchPath(launchPath) + connectionId;
    }

    private String buildJsonLaunchUrl(
            ServerHost host,
            String gatewayUrl,
            String connectionId,
            String protocol,
            Map<String, String> parameters,
            String secretKey
    ) {
        try {
            byte[] cipherKey = decodeHex(secretKey);
            if (cipherKey.length != 16 && cipherKey.length != 24 && cipherKey.length != 32) {
                throw new IllegalStateException("Guacamole JSON Auth 密钥必须是 16/24/32 字节十六进制字符串");
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("username", resolveJsonUsername(host));
            payload.put("expires", System.currentTimeMillis()
                    + Math.max(properties.getGuacamole().getJsonExpiresSeconds(), 30L) * 1000L);
            Map<String, Object> connections = new LinkedHashMap<>();
            Map<String, Object> connection = new LinkedHashMap<>();
            connection.put("protocol", protocol);
            connection.put("parameters", parameters);
            connections.put(resolveJsonConnectionName(host, connectionId), connection);
            payload.put("connections", connections);
            String encoded = encodeSignedPayload(payload, cipherKey);
            return gatewayUrl + "/?data=" + URLEncoder.encode(encoded, StandardCharsets.UTF_8);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Guacamole JSON Auth 入口生成失败", ex);
        }
    }

    private String resolveJsonUsername(ServerHost host) {
        String configured = properties.getGuacamole().getJsonUsername();
        if (StringUtils.hasText(configured)) {
            return configured + "-" + host.getServerId();
        }
        return "server-console-" + host.getServerId();
    }

    private String resolveJsonConnectionName(ServerHost host, String connectionId) {
        if (StringUtils.hasText(host.getServerCode())) {
            return host.getServerCode();
        }
        if (StringUtils.hasText(host.getServerName())) {
            return host.getServerName();
        }
        return connectionId;
    }

    private String encodeSignedPayload(Map<String, Object> payload, byte[] key) throws Exception {
        byte[] jsonBytes = objectMapper.writeValueAsBytes(payload);
        byte[] signature = signPayload(jsonBytes, key);
        byte[] signed = new byte[signature.length + jsonBytes.length];
        System.arraycopy(signature, 0, signed, 0, signature.length);
        System.arraycopy(jsonBytes, 0, signed, signature.length, jsonBytes.length);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new IvParameterSpec(new byte[16]));
        byte[] encrypted = cipher.doFinal(signed);
        return java.util.Base64.getEncoder().encodeToString(encrypted);
    }

    private byte[] signPayload(byte[] payload, byte[] key) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(payload);
    }

    private byte[] decodeHex(String value) {
        String normalized = value.trim();
        if ((normalized.length() & 1) == 1) {
            throw new IllegalStateException("Guacamole JSON Auth 密钥不是合法的十六进制字符串");
        }
        byte[] bytes = new byte[normalized.length() / 2];
        for (int i = 0; i < normalized.length(); i += 2) {
            int high = Character.digit(normalized.charAt(i), 16);
            int low = Character.digit(normalized.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalStateException("Guacamole JSON Auth 密钥不是合法的十六进制字符串");
            }
            bytes[i / 2] = (byte) ((high << 4) + low);
        }
        return bytes;
    }

    private String resolveMessage(boolean enabled, String gatewayUrl) {
        if (enabled) {
            return null;
        }
        if (!StringUtils.hasText(gatewayUrl)) {
            return "远程网关地址未配置";
        }
        if (!properties.getRemoteGateway().isEnable() && !properties.getGuacamole().isEnable()) {
            return "远程网关已关闭";
        }
        return "远程网关当前不可用";
    }

    private String firstText(Object primary, String fallback) {
        if (primary instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        return StringUtils.hasText(fallback) ? fallback.trim() : null;
    }

    private Integer integerValue(Object primary, Integer fallback) {
        if (primary instanceof Number number) {
            return number.intValue();
        }
        if (primary instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String normalizeUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String normalizePath(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.startsWith("/") ? value : "/" + value;
    }

    private String normalizeLaunchPath(String value) {
        if (!StringUtils.hasText(value)) {
            return "/#/client/";
        }
        String normalized = value.startsWith("/") ? value : "/" + value;
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }
}
