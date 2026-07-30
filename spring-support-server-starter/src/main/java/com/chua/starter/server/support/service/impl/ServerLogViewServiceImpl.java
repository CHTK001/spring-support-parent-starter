package com.chua.starter.server.support.service.impl;

import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.SystemLogEntry;
import com.chua.starter.server.support.model.SystemLogSource;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.ServerLogViewService;
import com.chua.starter.server.support.util.ServerCommandSupport;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServerLogViewServiceImpl implements ServerLogViewService {

    private final ServerHostService serverHostService;
    private final ServerHostCommandExecutor serverHostCommandExecutor;

    @Override
    public List<SystemLogSource> listSources(Integer serverId) throws Exception {
        ServerHost host = requireHost(serverId);
        boolean windows = isWindows(host);

        List<SystemLogSource> sources = new ArrayList<>();
        if (windows) {
            sources.add(createSource("Application", "应用程序日志", "Windows 应用程序事件日志", "windows"));
            sources.add(createSource("System", "系统日志", "Windows 系统事件日志", "windows"));
            sources.add(createSource("Security", "安全日志", "Windows 安全事件日志", "windows"));
        } else {
            sources.add(createSource("syslog", "系统日志", "系统日志 (syslog)", "linux"));
            sources.add(createSource("journal", "Journald 日志", "systemd journal 日志", "linux"));
            sources.add(createSource("dmesg", "内核日志", "Linux 内核日志 (dmesg)", "linux"));
            sources.add(createSource("auth", "认证日志", "用户认证与授权日志", "linux"));
        }
        return sources;
    }

    @Override
    public List<SystemLogEntry> listLogs(Integer serverId, String source, String level,
                                          String keyword, Integer limit) throws Exception {
        ServerHost host = requireHost(serverId);
        boolean windows = isWindows(host);
        String output;

        if (windows) {
            String logName = StringUtils.hasText(source) ? source : "Application";
            String maxEvents = limit != null ? String.valueOf(Math.min(limit, 500)) : "200";
            String cmd = String.format(
                "Get-WinEvent -LogName %s -MaxEvents %s | Select-Object TimeCreated,LevelDisplayName,ProviderName,Id,Message | ConvertTo-Json",
                powershellQuote(logName), maxEvents);
            output = execute(host, cmd);
            return parseWindowsEvents(output, level, keyword);
        } else {
            String src = StringUtils.hasText(source) ? source : "syslog";
            int maxLines = limit != null ? Math.min(limit, 500) : 200;
            String cmd = buildLinuxLogCommand(src, maxLines);
            output = execute(host, cmd);
            return parseLinuxLogs(output, src, level, keyword);
        }
    }

    private String buildLinuxLogCommand(String source, int maxLines) {
        return switch (source) {
            case "journal" -> String.format("journalctl --no-pager -n %d --output=cat 2>/dev/null || echo ''", maxLines);
            case "dmesg" -> String.format("dmesg --level=err,warn 2>/dev/null | tail -n %d || dmesg | tail -n %d", maxLines, maxLines);
            case "auth" -> String.format("tail -n %d /var/log/auth.log 2>/dev/null || tail -n %d /var/log/secure 2>/dev/null || echo ''", maxLines, maxLines);
            default -> String.format("tail -n %d /var/log/syslog 2>/dev/null || tail -n %d /var/log/messages 2>/dev/null || echo ''", maxLines, maxLines);
        };
    }

    private List<SystemLogEntry> parseWindowsEvents(String json, String level, String keyword) {
        List<SystemLogEntry> entries = new ArrayList<>();
        if (!StringUtils.hasText(json) || json.trim().equals("[]") || json.trim().equals("null")) {
            return entries;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root;
            if (json.trim().startsWith("[")) {
                root = mapper.readTree(json);
            } else {
                root = mapper.createArrayNode().add(mapper.readTree(json));
            }
            long id = 0;
            for (com.fasterxml.jackson.databind.JsonNode item : root) {
                id++;
                String msg = item.has("Message") ? item.get("Message").asText("") : "";
                String lvl = item.has("LevelDisplayName") ? item.get("LevelDisplayName").asText("") : "";
                String time = item.has("TimeCreated") ? item.get("TimeCreated").asText("") : "";
                String src = item.has("ProviderName") ? item.get("ProviderName").asText("") : "";
                if (shouldSkipByLevel(lvl, level)) continue;
                if (StringUtils.hasText(keyword) && !msg.toLowerCase().contains(keyword.toLowerCase())) continue;
                entries.add(SystemLogEntry.builder()
                    .id(id).timestamp(time).level(normalizeLevel(lvl))
                    .source(src).message(truncate(msg, 500)).build());
            }
        } catch (Exception ignored) {
        }
        return entries;
    }

    private List<SystemLogEntry> parseLinuxLogs(String output, String source, String level, String keyword) {
        List<SystemLogEntry> entries = new ArrayList<>();
        if (!StringUtils.hasText(output)) return entries;
        String[] lines = output.split("\n");
        long id = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed)) continue;
            id++;
            if (StringUtils.hasText(keyword) && !trimmed.toLowerCase().contains(keyword.toLowerCase())) continue;
            String detectedLevel = detectLevel(trimmed);
            if (shouldSkipByLevel(detectedLevel, level)) continue;
            entries.add(SystemLogEntry.builder()
                .id(id).timestamp(extractTimestamp(trimmed))
                .level(detectedLevel).source(source)
                .message(truncate(trimmed, 500)).build());
        }
        return entries;
    }

    private String detectLevel(String line) {
        String upper = line.toUpperCase();
        if (upper.contains("ERROR") || upper.contains("FATAL") || upper.contains("EMERG")) return "ERROR";
        if (upper.contains("WARN") || upper.contains("WARNING")) return "WARN";
        if (upper.contains("INFO")) return "INFO";
        if (upper.contains("DEBUG") || upper.contains("TRACE")) return "DEBUG";
        return "INFO";
    }

    private String extractTimestamp(String line) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "^\\w{3}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2}");
        java.util.regex.Matcher m = p.matcher(line);
        if (m.find()) return m.group();
        p = java.util.regex.Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
        m = p.matcher(line);
        if (m.find()) return m.group();
        return "";
    }

    private boolean shouldSkipByLevel(String actual, String filter) {
        if (!StringUtils.hasText(filter) || "ALL".equalsIgnoreCase(filter)) return false;
        return !actual.equalsIgnoreCase(filter);
    }

    private String normalizeLevel(String winLevel) {
        if (winLevel == null) return "INFO";
        String upper = winLevel.toUpperCase();
        if (upper.contains("ERROR") || upper.contains("CRITICAL")) return "ERROR";
        if (upper.contains("WARN")) return "WARN";
        if (upper.contains("DEBUG") || upper.contains("VERBOSE")) return "DEBUG";
        return "INFO";
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private String powershellQuote(String value) {
        return ServerCommandSupport.powershellQuote(value);
    }

    private SystemLogSource createSource(String name, String label, String description, String platform) {
        return SystemLogSource.builder().name(name).label(label).description(description).platform(platform).build();
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
