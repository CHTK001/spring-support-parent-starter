package com.chua.starter.server.support.service.impl;

import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerUserView;
import com.chua.starter.server.support.model.SshPublicKeyView;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.ServerUserService;
import com.chua.starter.server.support.util.ServerCommandSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServerUserServiceImpl implements ServerUserService {

    private final ServerHostService serverHostService;
    private final ServerHostCommandExecutor serverHostCommandExecutor;

    @Override
    public List<ServerUserView> listUsers(Integer serverId) throws Exception {
        ServerHost host = requireHost(serverId);
        boolean windows = isWindows(host);

        if (windows) {
            String output = execute(host,
                    "Get-LocalUser | Select-Object Name,Enabled,LastLogon,Description,PrincipalSource | ConvertTo-Json");
            return parseWindowsUsers(output);
        } else {
            String output = execute(host,
                    "cat /etc/passwd | awk -F: '{print $1\":\"$3\":\"$4\":\"$6\":\"$7}'");
            return parseLinuxUsers(output);
        }
    }

    @Override
    public void createUser(Integer serverId, String username, String password, List<String> groups) throws Exception {
        ServerHost host = requireHost(serverId);
        boolean windows = isWindows(host);

        if (windows) {
            String cmd = String.format(
                    "New-LocalUser -Name %s -Password (ConvertTo-SecureString %s -AsPlainText -Force)",
                    powershellQuote(username), powershellQuote(password));
            if (groups != null && !groups.isEmpty()) {
                for (String g : groups) {
                    cmd += String.format(" | Add-LocalGroupMember -Group %s", powershellQuote(g.trim()));
                }
            }
            execute(host, cmd);
        } else {
            String cmd = String.format("useradd -m %s && echo '%s:%s' | chpasswd", username, username, password);
            if (groups != null && !groups.isEmpty()) {
                String groupStr = groups.stream().map(String::trim).filter(StringUtils::hasText)
                        .collect(Collectors.joining(","));
                cmd += String.format(" && usermod -aG %s %s", groupStr, username);
            }
            execute(host, cmd);
        }
    }

    @Override
    public void deleteUser(Integer serverId, String username) throws Exception {
        ServerHost host = requireHost(serverId);
        if (isWindows(host)) {
            execute(host, String.format("Remove-LocalUser -Name %s -Force", powershellQuote(username)));
        } else {
            execute(host, String.format("userdel -r %s 2>/dev/null || userdel %s 2>/dev/null", username, username));
        }
    }

    @Override
    public void changePassword(Integer serverId, String username, String newPassword) throws Exception {
        ServerHost host = requireHost(serverId);
        if (isWindows(host)) {
            execute(host, String.format(
                    "$sec = ConvertTo-SecureString %s -AsPlainText -Force; " +
                    "Set-LocalUser -Name %s -Password $sec",
                    powershellQuote(newPassword), powershellQuote(username)));
        } else {
            execute(host, String.format("echo '%s:%s' | chpasswd", username, newPassword));
        }
    }

    @Override
    public List<SshPublicKeyView> listSshKeys(Integer serverId, String username) throws Exception {
        ServerHost host = requireHost(serverId);
        if (isWindows(host)) {
            return new ArrayList<>();
        }
        String user = StringUtils.hasText(username) ? username : "root";
        String output = execute(host, String.format(
                "cat /home/%s/.ssh/authorized_keys 2>/dev/null || cat ~%s/.ssh/authorized_keys 2>/dev/null || echo ''",
                user, user));
        return parseSshKeys(output);
    }

    @Override
    public void addSshKey(Integer serverId, String username, String publicKey) throws Exception {
        ServerHost host = requireHost(serverId);
        String user = StringUtils.hasText(username) ? username : "root";
        String escapedKey = publicKey.replace("'", "'\\''");
        execute(host, String.format(
                "mkdir -p /home/%s/.ssh && echo '%s' >> /home/%s/.ssh/authorized_keys && " +
                "chmod 600 /home/%s/.ssh/authorized_keys && chown %s:%s /home/%s/.ssh/authorized_keys",
                user, escapedKey, user, user, user, user, user));
    }

    @Override
    public void deleteSshKey(Integer serverId, String username, Integer keyId) throws Exception {
        ServerHost host = requireHost(serverId);
        String user = StringUtils.hasText(username) ? username : "root";
        execute(host, String.format(
                "sed -i '%dd' /home/%s/.ssh/authorized_keys 2>/dev/null || sed -i '%dd' ~%s/.ssh/authorized_keys 2>/dev/null",
                keyId, user, keyId, user));
    }

    private List<ServerUserView> parseWindowsUsers(String json) {
        List<ServerUserView> users = new ArrayList<>();
        if (!StringUtils.hasText(json)) return users;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root;
            if (json.trim().startsWith("[")) root = mapper.readTree(json);
            else root = mapper.createArrayNode().add(mapper.readTree(json));
            for (com.fasterxml.jackson.databind.JsonNode item : root) {
                users.add(ServerUserView.builder()
                        .username(item.has("Name") ? item.get("Name").asText() : "")
                        .enabled(!"False".equals(item.has("Enabled") ? item.get("Enabled").asText() : "True"))
                        .lastLogon(item.has("LastLogon") ? item.get("LastLogon").asText() : "")
                        .description(item.has("Description") ? item.get("Description").asText() : "")
                        .build());
            }
        } catch (Exception ignored) {}
        return users;
    }

    private List<ServerUserView> parseLinuxUsers(String output) {
        List<ServerUserView> users = new ArrayList<>();
        if (!StringUtils.hasText(output)) return users;
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed)) continue;
            String[] parts = trimmed.split(":");
            if (parts.length >= 5) {
                try {
                    users.add(ServerUserView.builder()
                            .username(parts[0])
                            .uid(tryParseInt(parts[1]))
                            .gid(tryParseInt(parts[2]))
                            .homeDir(parts[3])
                            .shell(parts.length > 4 ? parts[4] : "")
                            .enabled(true)
                            .build());
                } catch (Exception ignored) {}
            }
        }
        return users;
    }

    private List<SshPublicKeyView> parseSshKeys(String output) {
        List<SshPublicKeyView> keys = new ArrayList<>();
        if (!StringUtils.hasText(output)) return keys;
        String[] lines = output.split("\n");
        int id = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed) || trimmed.startsWith("#")) continue;
            id++;
            String[] parts = trimmed.split("\\s+");
            String keyType = parts.length > 0 ? parts[0] : "";
            String keyContent = parts.length > 1 ? parts[1] : "";
            String comment = parts.length > 2 ? parts[2] : "";
            String fingerprint = generateFingerprint(keyContent);
            keys.add(SshPublicKeyView.builder()
                    .id(id).keyType(keyType).fingerprint(fingerprint)
                    .comment(comment).content(trimmed).build());
        }
        return keys;
    }

    private String generateFingerprint(String keyContent) {
        if (!StringUtils.hasText(keyContent)) return "";
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] decoded = java.util.Base64.getDecoder().decode(keyContent);
            byte[] digest = md.digest(decoded);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < digest.length; i++) {
                if (i > 0) sb.append(":");
                sb.append(String.format("%02x", digest[i] & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return keyContent.length() > 20 ? keyContent.substring(0, 20) + "..." : keyContent;
        }
    }

    private Integer tryParseInt(String value) {
        try { return Integer.parseInt(value.trim()); } catch (Exception e) { return null; }
    }

    private String powershellQuote(String value) {
        return ServerCommandSupport.powershellQuote(value);
    }

    private ServerHost requireHost(Integer serverId) {
        ServerHost host = serverHostService.getHost(serverId);
        if (host == null) throw new IllegalArgumentException("未找到服务器");
        return host;
    }

    private boolean isWindows(ServerHost host) {
        String osType = host == null || !StringUtils.hasText(host.getOsType())
                ? System.getProperty("os.name") : host.getOsType();
        return ServerCommandSupport.isWindows(osType);
    }

    private String execute(ServerHost host, String command) throws Exception {
        ServerCommandSupport.CommandResult result = serverHostCommandExecutor.execute(host, command);
        return result != null ? result.output() : "";
    }
}
