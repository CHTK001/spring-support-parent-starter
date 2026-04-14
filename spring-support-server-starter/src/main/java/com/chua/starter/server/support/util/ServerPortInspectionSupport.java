package com.chua.starter.server.support.util;

import com.chua.common.support.core.utils.ServiceProvider;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerExposurePortView;
import com.chua.starter.server.support.spi.ServerCommandExecutorSpi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * 统一通过服务器命令执行 SPI 采集监听端口，兼容 LOCAL/SSH/WINRM。
 */
public final class ServerPortInspectionSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final com.chua.common.support.core.spi.ServiceProvider<ServerCommandExecutorSpi> COMMAND_EXECUTOR_PROVIDER =
            ServiceProvider.of(ServerCommandExecutorSpi.class);
    private static final Pattern SS_PATTERN =
            Pattern.compile("^(\\S+)\\s+(\\S+)\\s+\\S+\\s+\\S+\\s+(\\S+)\\s+(\\S+)(?:\\s+(.*))?$");
    private static final Pattern QUOTED_PROCESS_PATTERN = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern PID_PATTERN = Pattern.compile("pid=(\\d+)");
    private static final String UNIX_PORT_COMMAND =
            "if command -v ss >/dev/null 2>&1; then "
                    + "ss -lntupH 2>/dev/null; "
                    + "elif command -v netstat >/dev/null 2>&1; then "
                    + "netstat -lntup 2>/dev/null | tail -n +3; "
                    + "fi";
    private static final String WINDOWS_PORT_COMMAND =
            "$tcp=@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | ForEach-Object {"
                    + "$processName='';"
                    + "try{$process=Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue;if($process){$processName=$process.ProcessName}}catch{};"
                    + "[pscustomobject]@{Protocol='tcp';State='LISTEN';LocalAddress=$_.LocalAddress;Port=[int]$_.LocalPort;ProcessId=[long]$_.OwningProcess;ProcessName=$processName}"
                    + "});"
                    + "$udp=@(Get-NetUDPEndpoint -ErrorAction SilentlyContinue | ForEach-Object {"
                    + "$processName='';"
                    + "try{$process=Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue;if($process){$processName=$process.ProcessName}}catch{};"
                    + "[pscustomobject]@{Protocol='udp';State='BOUND';LocalAddress=$_.LocalAddress;Port=[int]$_.LocalPort;ProcessId=[long]$_.OwningProcess;ProcessName=$processName}"
                    + "});"
                    + "$items=@();if($tcp){$items+=$tcp};if($udp){$items+=$udp};"
                    + "$items | Sort-Object Protocol,Port,LocalAddress | ConvertTo-Json -Compress";

    private ServerPortInspectionSupport() {
    }

    /**
     * 采集服务器当前监听/占用端口。
     */
    public static List<ServerExposurePortView> inspect(ServerHost host) {
        if (host == null) {
            return List.of();
        }
        ServerCommandExecutorSpi executor = COMMAND_EXECUTOR_PROVIDER.getExtension(resolveExecutorType(host));
        if (executor == null) {
            return List.of();
        }
        try {
            String command = isWindowsHost(host) ? buildWindowsCommand(host) : UNIX_PORT_COMMAND;
            ServerCommandSupport.CommandResult result = executor.execute(host, command);
            if (result == null || !result.success() || !StringUtils.hasText(result.output())) {
                return List.of();
            }
            List<ServerExposurePortView> ports = isWindowsHost(host)
                    ? parseWindowsPorts(result.output())
                    : parseUnixPorts(result.output());
            ports.sort(Comparator
                    .comparing(ServerExposurePortView::getPort, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(item -> normalize(item.getProtocol()))
                    .thenComparing(item -> normalize(item.getLocalAddress())));
            return ports;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /**
     * 对采集结果应用展示策略，避免详情页一次渲染过多端口。
     */
    public static List<ServerExposurePortView> applyPolicy(
            List<ServerExposurePortView> source,
            boolean includeUdp,
            boolean includeLoopback,
            int maxPorts
    ) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<ServerExposurePortView> result = source.stream()
                .filter(item -> item != null && item.getPort() != null && item.getPort() > 0)
                .filter(item -> includeUdp || !"udp".equalsIgnoreCase(item.getProtocol()))
                .filter(item -> includeLoopback || !isLoopbackAddress(item.getLocalAddress()))
                .toList();
        if (maxPorts <= 0 || result.size() <= maxPorts) {
            return result;
        }
        return result.subList(0, maxPorts);
    }

    private static String buildWindowsCommand(ServerHost host) {
        if ("WINRM".equalsIgnoreCase(host.getServerType())) {
            return "powershell -NoProfile -Command \"" + WINDOWS_PORT_COMMAND.replace("\"", "\\\"") + "\"";
        }
        return WINDOWS_PORT_COMMAND;
    }

    private static List<ServerExposurePortView> parseUnixPorts(String output) {
        List<ServerExposurePortView> items = new ArrayList<>();
        for (String line : output.split("\\R")) {
            String trimmed = line == null ? null : line.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            ServerExposurePortView value = trimmed.startsWith("tcp") || trimmed.startsWith("udp")
                    ? parseSsLine(trimmed)
                    : null;
            if (value == null && (trimmed.startsWith("tcp") || trimmed.startsWith("udp"))) {
                value = parseNetstatLine(trimmed);
            }
            if (value != null && value.getPort() != null) {
                items.add(value);
            }
        }
        return deduplicate(items);
    }

    private static ServerExposurePortView parseSsLine(String line) {
        Matcher matcher = SS_PATTERN.matcher(line);
        if (!matcher.find()) {
            return null;
        }
        String protocol = normalizeProtocol(matcher.group(1));
        String state = matcher.group(2);
        Endpoint endpoint = parseEndpoint(matcher.group(3));
        ProcessInfo processInfo = parseSsProcess(matcher.group(5));
        return buildPortView(protocol, state, endpoint, processInfo.processId(), processInfo.processName());
    }

    private static ServerExposurePortView parseNetstatLine(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 5) {
            return null;
        }
        String protocol = normalizeProtocol(parts[0]);
        String state;
        String processText;
        if (protocol.startsWith("tcp")) {
            state = parts.length > 5 ? parts[5] : "LISTEN";
            processText = parts.length > 6 ? parts[6] : null;
        } else {
            state = "BOUND";
            processText = parts.length > 5 ? parts[5] : null;
        }
        Endpoint endpoint = parseEndpoint(parts[3]);
        ProcessInfo processInfo = parseNetstatProcess(processText);
        return buildPortView(protocol, state, endpoint, processInfo.processId(), processInfo.processName());
    }

    private static List<ServerExposurePortView> parseWindowsPorts(String output) {
        List<ServerExposurePortView> items = new ArrayList<>();
        try {
            JsonNode node = OBJECT_MAPPER.readTree(output.trim());
            if (node == null || node.isNull()) {
                return List.of();
            }
            if (node.isArray()) {
                for (JsonNode item : node) {
                    ServerExposurePortView value = parseWindowsPort(item);
                    if (value != null) {
                        items.add(value);
                    }
                }
            } else {
                ServerExposurePortView value = parseWindowsPort(node);
                if (value != null) {
                    items.add(value);
                }
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return deduplicate(items);
    }

    private static ServerExposurePortView parseWindowsPort(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        Integer port = node.path("Port").asInt(node.path("port").asInt(0));
        if (port == null || port <= 0) {
            return null;
        }
        String protocol = normalizeProtocol(textValue(node, "Protocol", "protocol"));
        String address = textValue(node, "LocalAddress", "localAddress");
        String state = textValue(node, "State", "state");
        Long processId = node.path("ProcessId").asLong(node.path("processId").asLong(0L));
        String processName = textValue(node, "ProcessName", "processName");
        return buildPortView(
                protocol,
                StringUtils.hasText(state) ? state : ("udp".equalsIgnoreCase(protocol) ? "BOUND" : "LISTEN"),
                new Endpoint(address, port),
                processId <= 0 ? null : processId,
                processName);
    }

    private static ServerExposurePortView buildPortView(
            String protocol,
            String state,
            Endpoint endpoint,
            Long processId,
            String processName
    ) {
        String normalizedProtocol = normalizeProtocol(protocol);
        String normalizedProcess = StringUtils.hasText(processName) ? processName.trim() : null;
        String serviceName = resolveServiceName(endpoint.port(), normalizedProtocol, normalizedProcess);
        return ServerExposurePortView.builder()
                .port(endpoint.port())
                .localAddress(endpoint.address())
                .protocol(normalizedProtocol)
                .state(StringUtils.hasText(state) ? state.trim().toUpperCase(Locale.ROOT) : null)
                .serviceName(serviceName)
                .processId(processId)
                .processName(normalizedProcess)
                .tags(buildTags(endpoint.address(), normalizedProtocol, serviceName, normalizedProcess))
                .build();
    }

    private static List<ServerExposurePortView> deduplicate(List<ServerExposurePortView> items) {
        Map<String, ServerExposurePortView> result = new java.util.LinkedHashMap<>();
        for (ServerExposurePortView item : items) {
            if (item == null || item.getPort() == null || item.getPort() <= 0) {
                continue;
            }
            String key = normalize(item.getProtocol()) + "|" + normalize(item.getLocalAddress()) + "|" + item.getPort();
            result.putIfAbsent(key, item);
        }
        return new ArrayList<>(result.values());
    }

    private static ProcessInfo parseSsProcess(String text) {
        if (!StringUtils.hasText(text)) {
            return new ProcessInfo(null, null);
        }
        String processName = null;
        Matcher processMatcher = QUOTED_PROCESS_PATTERN.matcher(text);
        if (processMatcher.find()) {
            processName = processMatcher.group(1);
        }
        Long processId = null;
        Matcher pidMatcher = PID_PATTERN.matcher(text);
        if (pidMatcher.find()) {
            try {
                processId = Long.parseLong(pidMatcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return new ProcessInfo(processId, processName);
    }

    private static ProcessInfo parseNetstatProcess(String text) {
        if (!StringUtils.hasText(text)) {
            return new ProcessInfo(null, null);
        }
        String value = text.trim();
        int index = value.indexOf('/');
        if (index > 0) {
            Long pid = null;
            try {
                pid = Long.parseLong(value.substring(0, index));
            } catch (NumberFormatException ignored) {
            }
            return new ProcessInfo(pid, value.substring(index + 1).trim());
        }
        return new ProcessInfo(null, value);
    }

    private static Endpoint parseEndpoint(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return new Endpoint(null, null);
        }
        String value = endpoint.trim();
        if (value.startsWith("[")) {
            int bracketIndex = value.lastIndexOf("]:");
            if (bracketIndex > 0 && bracketIndex + 2 < value.length()) {
                return new Endpoint(value.substring(1, bracketIndex), parseInteger(value.substring(bracketIndex + 2)));
            }
        }
        int index = value.lastIndexOf(':');
        if (index > 0 && index + 1 < value.length()) {
            return new Endpoint(value.substring(0, index), parseInteger(value.substring(index + 1)));
        }
        return new Endpoint(value, null);
    }

    private static List<String> buildTags(
            String localAddress,
            String protocol,
            String serviceName,
            String processName
    ) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (StringUtils.hasText(protocol)) {
            tags.add(protocol.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(localAddress) && localAddress.contains(":")) {
            tags.add("IPv6");
        }
        if (StringUtils.hasText(localAddress)
                && ("0.0.0.0".equals(localAddress.trim()) || "::".equals(localAddress.trim()) || "*".equals(localAddress.trim()))) {
            tags.add("ALL");
        }
        if (StringUtils.hasText(serviceName)) {
            tags.add(serviceName.trim());
        }
        if (StringUtils.hasText(processName)) {
            tags.add(processName.trim());
        }
        return new ArrayList<>(tags);
    }

    private static String resolveServiceName(Integer port, String protocol, String processName) {
        if (StringUtils.hasText(processName)) {
            return processName.trim();
        }
        if (port == null) {
            return null;
        }
        return switch (port) {
            case 21 -> "ftp";
            case 22 -> "ssh";
            case 25 -> "smtp";
            case 53 -> "dns";
            case 80 -> "http";
            case 110 -> "pop3";
            case 123 -> "ntp";
            case 135 -> "rpc";
            case 139 -> "netbios";
            case 143 -> "imap";
            case 443 -> "https";
            case 445 -> "smb";
            case 465 -> "smtps";
            case 587 -> "submission";
            case 993 -> "imaps";
            case 995 -> "pop3s";
            case 1433 -> "sqlserver";
            case 1521 -> "oracle";
            case 2181 -> "zookeeper";
            case 2375, 2376 -> "docker";
            case 3306 -> "mysql";
            case 3389 -> "rdp";
            case 5432 -> "postgresql";
            case 5672 -> "rabbitmq";
            case 5900 -> "vnc";
            case 6379 -> "redis";
            case 6443 -> "kubernetes";
            case 8080, 8081, 8888 -> "http-app";
            case 8443 -> "https-app";
            case 9090 -> "prometheus";
            case 9200 -> "elasticsearch";
            case 9300 -> "elasticsearch-transport";
            case 27017 -> "mongodb";
            default -> "udp".equalsIgnoreCase(protocol) ? "udp-service" : null;
        };
    }

    private static String textValue(JsonNode node, String... names) {
        if (node == null || names == null) {
            return null;
        }
        for (String name : names) {
            JsonNode field = node.path(name);
            if (!field.isMissingNode() && !field.isNull()) {
                String value = field.asText(null);
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private static int parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean isWindowsHost(ServerHost host) {
        return host != null && ServerCommandSupport.isWindows(host.getOsType());
    }

    private static String resolveExecutorType(ServerHost host) {
        if (host == null || !StringUtils.hasText(host.getServerType())) {
            return "local";
        }
        return host.getServerType().trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeProtocol(String protocol) {
        if (!StringUtils.hasText(protocol)) {
            return null;
        }
        String value = protocol.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("tcp")) {
            return "tcp";
        }
        if (value.startsWith("udp")) {
            return "udp";
        }
        return value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isLoopbackAddress(String address) {
        if (!StringUtils.hasText(address)) {
            return false;
        }
        String value = address.trim().toLowerCase(Locale.ROOT);
        return "localhost".equals(value)
                || "::1".equals(value)
                || value.startsWith("127.")
                || value.startsWith("::ffff:127.");
    }

    private record Endpoint(String address, Integer port) {
    }

    private record ProcessInfo(Long processId, String processName) {
    }
}
