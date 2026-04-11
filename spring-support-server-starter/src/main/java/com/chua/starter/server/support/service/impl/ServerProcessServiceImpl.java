package com.chua.starter.server.support.service.impl;

import com.chua.starter.server.support.constants.ServerSocketEvents;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerProcessAiAdvice;
import com.chua.starter.server.support.model.ServerProcessCommandResult;
import com.chua.starter.server.support.model.ServerProcessRealtimePayload;
import com.chua.starter.server.support.model.ServerProcessView;
import com.chua.starter.server.support.service.ServerRealtimePublisher;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.ServerProcessService;
import com.chua.starter.server.support.util.ServerCommandSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServerProcessServiceImpl implements ServerProcessService {

    private static final int DEFAULT_LIMIT = 80;
    private static final int MAX_LIMIT = 200;

    private final ServerHostService serverHostService;
    private final ServerHostCommandExecutor serverHostCommandExecutor;
    private final ServerProcessAiAdvisor serverProcessAiAdvisor;
    private final ServerRealtimePublisher serverRealtimePublisher;
    private final ObjectMapper objectMapper;

    @Override
    public List<ServerProcessView> listProcesses(Integer serverId, String keyword, Integer limit) throws Exception {
        ServerHost host = requireHost(serverId);
        List<ServerProcessView> processes = isWindows(host)
                ? parseWindowsProcesses(host, execute(host, buildWindowsListCommand()))
                : parseUnixProcesses(host, execute(host, buildUnixListCommand()));
        String normalizedKeyword = normalizeKeyword(keyword);
        List<ServerProcessView> result = processes.stream()
                .filter(item -> matchesKeyword(item, normalizedKeyword))
                .sorted(processComparator())
                .limit(resolveLimit(limit))
                .toList();
        publishProcesses(host, normalizedKeyword, resolveLimit(limit), result);
        return result;
    }

    @Override
    public ServerProcessView getProcess(Integer serverId, Long pid) throws Exception {
        if (pid == null || pid <= 0) {
            return null;
        }
        return listProcesses(serverId, null, MAX_LIMIT).stream()
                .filter(item -> pid.equals(item.getPid()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public ServerProcessCommandResult terminateProcess(Integer serverId, Long pid, boolean force) throws Exception {
        ServerHost host = requireHost(serverId);
        if (pid == null || pid <= 0) {
            return ServerProcessCommandResult.builder()
                    .serverId(serverId)
                    .pid(pid)
                    .success(Boolean.FALSE)
                    .exitCode(-1)
                    .force(force)
                    .message("无效的进程 ID")
                    .output("")
                    .build();
        }
        ServerCommandSupport.CommandResult result = serverHostCommandExecutor.execute(
                host,
                isWindows(host) ? buildWindowsTerminateCommand(pid, force) : buildUnixTerminateCommand(pid, force));
        return ServerProcessCommandResult.builder()
                .serverId(serverId)
                .pid(pid)
                .success(result.success())
                .exitCode(result.exitCode())
                .force(force)
                .message(result.success()
                        ? (force ? "已强制结束进程" : "已发送结束进程指令")
                        : "结束进程失败")
                .output(result.output())
                .build();
    }

    @Override
    public ServerProcessAiAdvice analyzeProcess(Integer serverId, Long pid) throws Exception {
        ServerHost host = requireHost(serverId);
        List<ServerProcessView> processes = listProcesses(serverId, null, 40);
        ServerProcessView process = processes.stream()
                .filter(item -> pid != null && pid.equals(item.getPid()))
                .findFirst()
                .orElseGet(() -> {
                    try {
                        return getProcess(serverId, pid);
                    } catch (Exception ignored) {
                        return null;
                    }
                });
        if (process == null) {
            return ServerProcessAiAdvice.builder()
                    .pid(pid)
                    .riskLevel("UNKNOWN")
                    .summary("未找到目标进程，可能已经退出")
                    .suggestion("请刷新进程列表后重试，如需排查闪退可先查看服务日志或系统事件。")
                    .provider("LOCAL_HEURISTIC")
                    .model("local")
                    .build();
        }
        ServerProcessAiAdvice advice = serverProcessAiAdvisor.analyze(host, process, processes);
        return advice != null ? advice : buildHeuristicAdvice(process);
    }

    private ServerHost requireHost(Integer serverId) {
        ServerHost host = serverHostService.getHost(serverId);
        if (host == null) {
            throw new IllegalArgumentException("未找到服务器");
        }
        return host;
    }

    private String execute(ServerHost host, String command) throws Exception {
        ServerCommandSupport.CommandResult result = serverHostCommandExecutor.execute(host, command);
        if (!result.success() && !StringUtils.hasText(result.output())) {
            throw new IllegalStateException("进程查询失败，退出码: " + result.exitCode());
        }
        return result.output();
    }

    private boolean isWindows(ServerHost host) {
        String osType = host == null || !StringUtils.hasText(host.getOsType())
                ? System.getProperty("os.name")
                : host.getOsType();
        return ServerCommandSupport.isWindows(osType);
    }

    private long resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String normalizeKeyword(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;
    }

    private boolean matchesKeyword(ServerProcessView item, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        return contains(item.getName(), keyword)
                || contains(item.getCommand(), keyword)
                || contains(item.getCommandLine(), keyword)
                || contains(item.getUser(), keyword)
                || contains(item.getPid(), keyword);
    }

    private boolean contains(Object value, String keyword) {
        return value != null && String.valueOf(value).toLowerCase(Locale.ROOT).contains(keyword);
    }

    private Comparator<ServerProcessView> processComparator() {
        return Comparator
                .comparing(ServerProcessView::getCpuPercent, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ServerProcessView::getMemoryPercent, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ServerProcessView::getMemoryBytes, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ServerProcessView::getPid, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private void publishProcesses(ServerHost host, String keyword, long limit, List<ServerProcessView> processes) {
        if (host == null || host.getServerId() == null || processes == null) {
            return;
        }
        serverRealtimePublisher.publish(
                ServerSocketEvents.MODULE,
                ServerSocketEvents.SERVER_PROCESS,
                host.getServerId(),
                ServerProcessRealtimePayload.builder()
                        .serverId(host.getServerId())
                        .serverCode(host.getServerCode())
                        .keyword(keyword)
                        .limit((int) limit)
                        .processCount(processes.size())
                        .refreshedAt(System.currentTimeMillis())
                        .message("process-list")
                        .executionProvider(host.getServerType())
                        .spiChannel(host.getServerType())
                        .processes(processes)
                        .build());
    }

    private String buildUnixListCommand() {
        return "ps -eo pid=,ppid=,pcpu=,pmem=,rss=,nlwp=,etimes=,user=,stat=,comm=,args= --sort=-pcpu | head -n 220";
    }

    private String buildWindowsListCommand() {
        return """
                $items = Get-CimInstance Win32_Process | ForEach-Object {
                  [PSCustomObject]@{
                    ProcessId = $_.ProcessId
                    ParentProcessId = $_.ParentProcessId
                    Name = $_.Name
                    CommandLine = $_.CommandLine
                    WorkingSetSize = [int64]($_.WorkingSetSize)
                    ThreadCount = [int]($_.ThreadCount)
                    CreationDate = if ($_.CreationDate) { ([System.Management.ManagementDateTimeConverter]::ToDateTime($_.CreationDate)).ToString('o') } else { $null }
                    State = 'Running'
                  }
                };
                $items | ConvertTo-Json -Depth 4 -Compress
                """;
    }

    private String buildUnixTerminateCommand(Long pid, boolean force) {
        return (force ? "kill -KILL " : "kill -TERM ") + pid;
    }

    private String buildWindowsTerminateCommand(Long pid, boolean force) {
        return force
                ? "Stop-Process -Id %d -Force -ErrorAction Stop; Write-Output 'Process %d terminated'".formatted(pid, pid)
                : "Stop-Process -Id %d -ErrorAction Stop; Write-Output 'Process %d terminated'".formatted(pid, pid);
    }

    private List<ServerProcessView> parseUnixProcesses(ServerHost host, String output) {
        List<ServerProcessView> result = new ArrayList<>();
        if (!StringUtils.hasText(output)) {
            return result;
        }
        String[] lines = output.split("\\r?\\n");
        for (String line : lines) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            String[] parts = line.trim().split("\\s+", 11);
            if (parts.length < 10) {
                continue;
            }
            long elapsedSeconds = parseLong(parts[6], 0L);
            result.add(ServerProcessView.builder()
                    .serverId(host.getServerId())
                    .serverCode(host.getServerCode())
                    .pid(parseLongObject(parts[0]))
                    .parentPid(parseLongObject(parts[1]))
                    .cpuPercent(parseDoubleObject(parts[2]))
                    .memoryPercent(parseDoubleObject(parts[3]))
                    .memoryBytes(toBytes(parseLong(parts[4], 0L)))
                    .threadCount(parseIntegerObject(parts[5]))
                    .elapsed(formatElapsed(elapsedSeconds))
                    .startTime(resolveStartTime(elapsedSeconds))
                    .user(parts[7])
                    .state(parts[8])
                    .name(parts[9])
                    .command(parts[9])
                    .commandLine(parts.length > 10 ? parts[10] : parts[9])
                    .alive(Boolean.TRUE)
                    .executionProvider(host.getServerType())
                    .spiChannel(host.getServerType())
                    .build());
        }
        return result;
    }

    private List<ServerProcessView> parseWindowsProcesses(ServerHost host, String output) throws Exception {
        List<ServerProcessView> result = new ArrayList<>();
        if (!StringUtils.hasText(output)) {
            return result;
        }
        JsonNode root = objectMapper.readTree(output);
        if (root == null || root.isNull()) {
            return result;
        }
        if (root.isArray()) {
            Iterator<JsonNode> iterator = root.elements();
            while (iterator.hasNext()) {
                addWindowsProcess(result, host, iterator.next());
            }
            return result;
        }
        addWindowsProcess(result, host, root);
        return result;
    }

    private void addWindowsProcess(List<ServerProcessView> result, ServerHost host, JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        String commandLine = text(node, "CommandLine");
        String name = firstText(node, "Name", "ProcessName");
        result.add(ServerProcessView.builder()
                .serverId(host.getServerId())
                .serverCode(host.getServerCode())
                .pid(longValue(node, "ProcessId"))
                .parentPid(longValue(node, "ParentProcessId"))
                .name(name)
                .command(name)
                .commandLine(StringUtils.hasText(commandLine) ? commandLine : name)
                .user(text(node, "UserName"))
                .state(firstText(node, "State", "Status"))
                .cpuPercent(doubleValue(node, "CpuPercent"))
                .memoryPercent(doubleValue(node, "MemoryPercent"))
                .memoryBytes(longValue(node, "WorkingSetSize"))
                .threadCount(integerValue(node, "ThreadCount"))
                .elapsed(text(node, "Elapsed"))
                .startTime(firstText(node, "CreationDate", "StartTime"))
                .alive(Boolean.TRUE)
                .executionProvider(host.getServerType())
                .spiChannel(host.getServerType())
                .build());
    }

    private ServerProcessAiAdvice buildHeuristicAdvice(ServerProcessView process) {
        double cpu = process.getCpuPercent() == null ? 0D : process.getCpuPercent();
        double memory = process.getMemoryPercent() == null ? 0D : process.getMemoryPercent();
        if (cpu >= 80D || memory >= 30D) {
            return ServerProcessAiAdvice.builder()
                    .pid(process.getPid())
                    .riskLevel("HIGH")
                    .summary("目标进程资源占用偏高，已经接近异常进程特征。")
                    .suggestion("优先确认该进程是否处于批处理、编译或备份高峰；如非预期负载，请先查看命令行和上级服务，再决定是否结束进程。")
                    .provider("LOCAL_HEURISTIC")
                    .model("local")
                    .build();
        }
        if (cpu >= 40D || memory >= 15D) {
            return ServerProcessAiAdvice.builder()
                    .pid(process.getPid())
                    .riskLevel("MEDIUM")
                    .summary("目标进程当前存在中等资源压力，建议持续观察。")
                    .suggestion("建议保持实时刷新，结合线程数、命令行和最近日志判断是否持续升高；如属于核心服务，请先查看服务日志再处理。")
                    .provider("LOCAL_HEURISTIC")
                    .model("local")
                    .build();
        }
        return ServerProcessAiAdvice.builder()
                .pid(process.getPid())
                .riskLevel("LOW")
                .summary("目标进程当前资源占用平稳，没有明显异常信号。")
                .suggestion("建议继续观察实时刷新结果，如需进一步排查可对照同类进程的 CPU、内存和命令行差异。")
                .provider("LOCAL_HEURISTIC")
                .model("local")
                .build();
    }

    private Long toBytes(long rssKb) {
        return rssKb <= 0 ? null : rssKb * 1024L;
    }

    private String resolveStartTime(long elapsedSeconds) {
        if (elapsedSeconds <= 0) {
            return null;
        }
        return OffsetDateTime.ofInstant(
                        Instant.now().minusSeconds(elapsedSeconds),
                        ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private String formatElapsed(long elapsedSeconds) {
        if (elapsedSeconds <= 0) {
            return null;
        }
        long hours = elapsedSeconds / 3600;
        long minutes = (elapsedSeconds % 3600) / 60;
        long seconds = elapsedSeconds % 60;
        if (hours > 0) {
            return "%dh %02dm %02ds".formatted(hours, minutes, seconds);
        }
        if (minutes > 0) {
            return "%dm %02ds".formatted(minutes, seconds);
        }
        return "%ds".formatted(seconds);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String firstText(JsonNode node, String first, String second) {
        String value = text(node, first);
        return StringUtils.hasText(value) ? value : text(node, second);
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.longValue();
        }
        String text = value.asText();
        return StringUtils.hasText(text) ? parseLongObject(text) : null;
    }

    private Integer integerValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.intValue();
        }
        String text = value.asText();
        return StringUtils.hasText(text) ? parseIntegerObject(text) : null;
    }

    private Double doubleValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.doubleValue();
        }
        String text = value.asText();
        return StringUtils.hasText(text) ? parseDoubleObject(text) : null;
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(StringUtils.trimWhitespace(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Long parseLongObject(String value) {
        try {
            return Long.parseLong(StringUtils.trimWhitespace(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer parseIntegerObject(String value) {
        try {
            return Integer.parseInt(StringUtils.trimWhitespace(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double parseDoubleObject(String value) {
        try {
            return Double.parseDouble(StringUtils.trimWhitespace(value));
        } catch (Exception ignored) {
            return null;
        }
    }
}
