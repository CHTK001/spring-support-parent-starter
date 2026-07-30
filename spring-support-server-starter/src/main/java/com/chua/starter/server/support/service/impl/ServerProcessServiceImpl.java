package com.chua.starter.server.support.service.impl;

import com.chua.starter.server.support.constants.ServerSocketEvents;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerProcessAiAdvice;
import com.chua.starter.server.support.model.ServerProcessCommandResult;
import com.chua.starter.server.support.model.ServerProcessRealtimePayload;
import com.chua.starter.server.support.model.ServerProcessRelationGraph;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
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
    public List<ServerProcessView> listProcesses(Integer serverId, String keyword, Integer limit, String stateFilter) throws Exception {
        ServerHost host = requireHost(serverId);
        boolean windows = isWindows(host);
        List<ServerProcessView> processes;
        try {
            processes = windows
                    ? parseWindowsProcesses(host, execute(host, buildWindowsListCommand()))
                    : parseUnixProcesses(host, execute(host, buildUnixListCommand()));
        } catch (Exception ex) {
            if (isLocalWindows(host)) {
                processes = listLocalWindowsProcesses(host);
            } else {
                throw ex;
            }
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        Comparator<ServerProcessView> comparator = processComparator();
        List<ServerProcessView> result = processes.stream()
                .filter(item -> matchesKeyword(item, normalizedKeyword))
                .filter(item -> matchesState(item, stateFilter))
                .sorted(comparator)
                .limit(resolveLimit(limit))
                .toList();
        publishProcesses(host, normalizedKeyword, resolveLimit(limit), result);
        return result;
    }

    /**
     * 判断进程是否匹配状态过滤器。
     * stateFilter 格式：单个字母（如 Z / R / S）或空（不过滤）。
     */
    private boolean matchesState(ServerProcessView item, String stateFilter) {
        if (!StringUtils.hasText(stateFilter)) {
            return true;
        }
        String state = item.getState();
        if (!StringUtils.hasText(state)) {
            return false;
        }
        // ps stat 可能是 "Z", "Zs", "R+" 等多字符格式，取首字母
        return state.trim().startsWith(stateFilter.trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public ServerProcessView getProcess(Integer serverId, Long pid) throws Exception {
        if (pid == null || pid <= 0) {
            return null;
        }
        return listProcesses(serverId, null, MAX_LIMIT, null).stream()
                .filter(item -> pid.equals(item.getPid()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public Map<String, Integer> getProcessStats(Integer serverId) throws Exception {
        ServerHost host = requireHost(serverId);
        if (!isWindows(host)) {
            // Linux: use direct ps stat count to avoid CPU-sort cutoff of zombie processes
            try {
                return linuxStats(host);
            } catch (Exception e) {
                log.warn("Linux stats command failed, falling back to list-based counting: {}", e.getMessage());
            }
        }
        List<ServerProcessView> all = listProcesses(serverId, null, MAX_LIMIT, null);
        return countStats(all);
    }

    /**
     * 在 Linux 上通过 {@code ps -eo stat=} 直接统计所有进程状态，
     * 避免 {@code --sort=-pcpu | head -n 200} 截断 0% CPU 的僵尸进程。
     */
    private Map<String, Integer> linuxStats(ServerHost host) throws Exception {
        // ps -eo stat= 输出每行一个状态字母（可能带后缀如 Zs）
        // awk 取首字母聚合后输出 "total\nR\nS\nZ\nT\n…" 各行的数量
        String command = "ps -eo stat= | awk '{a[substr($1,1,1)]++} END {print NR; for(s in a) print s, a[s]}'";
        String output = execute(host, command);
        if (!StringUtils.hasText(output)) {
            throw new IllegalStateException("ps stats returned empty");
        }
        Map<String, Integer> stats = new LinkedHashMap<>();
        String[] lines = output.trim().split("\\r?\\n");
        // 首行是总进程数
        int total = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (i == 0) {
                total = parseInt(line, 0);
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length == 2) {
                String state = parts[0];
                int count = parseInt(parts[1], 0);
                switch (state) {
                    case "R" -> stats.put("running", count);
                    case "S", "D" -> stats.merge("sleeping", count, Integer::sum);
                    case "Z" -> stats.put("zombie", count);
                    case "T", "t" -> stats.merge("stopped", count, Integer::sum);
                    default -> {}
                }
            }
        }
        stats.putIfAbsent("running", 0);
        stats.putIfAbsent("sleeping", 0);
        stats.putIfAbsent("zombie", 0);
        stats.putIfAbsent("stopped", 0);
        stats.put("total", Math.max(total, stats.values().stream().mapToInt(Integer::intValue).sum()));
        return stats;
    }

    private int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private Map<String, Integer> countStats(List<ServerProcessView> all) {
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("total", all.size());
        stats.put("running", (int) all.stream().filter(p -> {
            String s = p.getState();
            return s != null && s.startsWith("R");
        }).count());
        stats.put("sleeping", (int) all.stream().filter(p -> {
            String s = p.getState();
            return s != null && (s.startsWith("S") || s.startsWith("D"));
        }).count());
        stats.put("zombie", (int) all.stream().filter(p -> {
            String s = p.getState();
            return s != null && s.startsWith("Z");
        }).count());
        stats.put("stopped", (int) all.stream().filter(p -> {
            String s = p.getState();
            return s != null && (s.startsWith("T") || s.startsWith("t"));
        }).count());
        return stats;
    }

    @Override
    public ServerProcessCommandResult zombieCleanup(Integer serverId, Long zombiePid) throws Exception {
        ServerHost host = requireHost(serverId);
        if (zombiePid == null || zombiePid <= 0) {
            return ServerProcessCommandResult.builder()
                    .serverId(serverId).success(false).message("无效的进程 ID").build();
        }
        // 获取目标僵尸进程信息
        ServerProcessView zombie = getProcess(serverId, zombiePid);
        if (zombie == null) {
            return ServerProcessCommandResult.builder()
                    .serverId(serverId).pid(zombiePid).success(false)
                    .message("未找到该进程，可能已退出").build();
        }
        Long ppid = zombie.getParentPid();
        if (ppid == null || ppid <= 0) {
            return ServerProcessCommandResult.builder()
                    .serverId(serverId).pid(zombiePid).success(false)
                    .message("僵尸进程无父进程信息，无法清理").build();
        }
        // 步骤1：向父进程发送 SIGCHLD 信号，促使其回收子进程
        try {
            ServerCommandSupport.CommandResult sigchldResult = serverHostCommandExecutor.execute(
                    host, "kill -CHLD " + ppid + " 2>&1 || echo 'SIGCHLD_FAIL'");
            if (sigchldResult.output() != null && sigchldResult.output().contains("SIGCHLD_FAIL")) {
                // SIGCHLD 发送失败，可能是父进程不存在了
                return ServerProcessCommandResult.builder()
                        .serverId(serverId).pid(zombiePid).success(false)
                        .message("发送 SIGCHLD 给父进程失败，父进程可能已退出").build();
            }
        } catch (Exception e) {
            return ServerProcessCommandResult.builder()
                    .serverId(serverId).pid(zombiePid).success(false)
                    .message("发送 SIGCHLD 异常: " + e.getMessage()).build();
        }
        return ServerProcessCommandResult.builder()
                .serverId(serverId).pid(zombiePid).success(true)
                .message("已向父进程(PID " + ppid + ")发送 SIGCHLD 信号，僵尸进程应被回收")
                .output("kill -CHLD " + ppid)
                .build();
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
        List<ServerProcessView> processes = listProcesses(serverId, null, 40, null);
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

    @Override
    public ServerProcessRelationGraph getProcessRelations(Integer serverId, Long pid) throws Exception {
        ServerHost host = requireHost(serverId);
        boolean windows = isWindows(host);

        if (windows) {
            return getWindowsProcessRelations(host, pid);
        }
        return getUnixProcessRelations(host, pid);
    }

    private ServerProcessRelationGraph getUnixProcessRelations(ServerHost host, Long pid) throws Exception {
        String raw = execute(host, "ps -eo pid=,ppid=,comm=,args= --no-headers 2>/dev/null | head -n 1000");
        String[] lines = raw.split("\\r?\\n");

        // Parse all processes
        List<long[]> allProcs = new ArrayList<>();
        long targetPpid = -1;
        String targetName = "";
        String targetArgs = "";

        for (String line : lines) {
            if (!StringUtils.hasText(line)) continue;
            String[] parts = line.trim().split("\\s+", 4);
            if (parts.length < 3) continue;
            long p = parseLong(parts[0], -1L);
            long pp = parseLong(parts[1], -1L);
            String name = parts.length > 2 ? parts[2] : "?";
            String args = parts.length > 3 ? parts[3] : name;
            allProcs.add(new long[]{p, pp});
            if (p == pid) {
                targetPpid = pp;
                targetName = name;
                targetArgs = args;
            }
        }

        // Build nodes
        List<ServerProcessRelationGraph.Node> nodes = new ArrayList<>();
        List<ServerProcessRelationGraph.Edge> edges = new ArrayList<>();

        // Target node
        nodes.add(ServerProcessRelationGraph.Node.builder()
                .pid(pid).name(targetName).command(targetArgs)
                .isTarget(true).build());

        // Parent node
        if (targetPpid > 0) {
            String parentName = "?";
            for (long[] proc : allProcs) {
                if (proc[0] == targetPpid) {
                    parentName = "PID " + targetPpid;
                    break;
                }
            }
            nodes.add(ServerProcessRelationGraph.Node.builder()
                    .pid(targetPpid).name(parentName)
                    .isTarget(false).build());
            edges.add(ServerProcessRelationGraph.Edge.builder()
                    .source(targetPpid).target(pid)
                    .relation("parent-child").build());
        }

        // Child nodes
        for (long[] proc : allProcs) {
            if (proc[1] == pid) {
                nodes.add(ServerProcessRelationGraph.Node.builder()
                        .pid(proc[0]).name("PID " + proc[0])
                        .isTarget(false).build());
                edges.add(ServerProcessRelationGraph.Edge.builder()
                        .source(pid).target(proc[0])
                        .relation("parent-child").build());
            }
        }

        // Client IPs via ss
        try {
            String ssRaw = execute(host, "ss -tnp 2>/dev/null | grep -i \"pid=" + pid + "\" || true");
            if (StringUtils.hasText(ssRaw)) {
                java.util.regex.Pattern ipPattern = java.util.regex.Pattern.compile("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}):\\d+");
                java.util.regex.Matcher matcher = ipPattern.matcher(ssRaw);
                java.util.Set<String> seen = new java.util.HashSet<>();
                while (matcher.find()) {
                    String ip = matcher.group(1);
                    if (seen.add(ip) && !"0.0.0.0".equals(ip) && !"127.0.0.1".equals(ip)) {
                        nodes.add(ServerProcessRelationGraph.Node.builder()
                                .pid(-1L).name(ip)
                                .isTarget(false).build());
                        edges.add(ServerProcessRelationGraph.Edge.builder()
                                .source(pid).target(-1L)
                                .relation("client-ip").label(ip).build());
                    }
                }
            }
        } catch (Exception ignored) {
            // ss not available, skip client IPs
        }

        return ServerProcessRelationGraph.builder()
                .nodes(nodes).edges(edges).build();
    }

    private ServerProcessRelationGraph getWindowsProcessRelations(ServerHost host, Long pid) throws Exception {
        String cmd = "powershell -NoProfile -Command \""
                + "$p = Get-CimInstance Win32_Process -Filter 'ProcessId=' + " + pid + ";"
                + "$parent = if($p) { Get-CimInstance Win32_Process -Filter 'ProcessId=' + $p.ParentProcessId };"
                + "$children = Get-CimInstance Win32_Process | Where-Object { $_.ParentProcessId -eq " + pid + " };"
                + "$result = @{"
                + "  'target'=@('\"'+$p.ProcessId+'\"','\"'+$p.Name+'\"','\"'+$p.CommandLine+'\"');"
                + "  'parent'=if($parent){@($parent.ProcessId,$parent.Name)}else{$null};"
                + "  'children'=$children | ForEach-Object {@($_.ProcessId,$_.Name)}"
                + "};"
                + "ConvertTo-Json -Compress"
                + "\"";

        List<ServerProcessRelationGraph.Node> nodes = new ArrayList<>();
        List<ServerProcessRelationGraph.Edge> edges = new ArrayList<>();

        try {
            String output = execute(host, cmd);
            if (StringUtils.hasText(output)) {
                JsonNode root = objectMapper.readTree(output);
                // Parse target
                JsonNode target = root.get("target");
                if (target != null) {
                    nodes.add(ServerProcessRelationGraph.Node.builder()
                            .pid(pid).name(target.get(1) != null ? target.get(1).asText() : "?")
                            .isTarget(true).build());
                }
                // Parse parent
                JsonNode parent = root.get("parent");
                if (parent != null && !parent.isNull() && parent.isArray() && parent.size() >= 2) {
                    long parentPid = parent.get(0).asLong();
                    nodes.add(ServerProcessRelationGraph.Node.builder()
                            .pid(parentPid).name(parent.get(1).asText())
                            .isTarget(false).build());
                    edges.add(ServerProcessRelationGraph.Edge.builder()
                            .source(parentPid).target(pid)
                            .relation("parent-child").build());
                }
                // Parse children
                JsonNode children = root.get("children");
                if (children != null && children.isArray()) {
                    for (int i = 0; i < children.size(); i++) {
                        JsonNode child = children.get(i);
                        if (child.isArray() && child.size() >= 2) {
                            long childPid = child.get(0).asLong();
                            nodes.add(ServerProcessRelationGraph.Node.builder()
                                    .pid(childPid).name(child.get(1).asText())
                                    .isTarget(false).build());
                            edges.add(ServerProcessRelationGraph.Edge.builder()
                                    .source(pid).target(childPid)
                                    .relation("parent-child").build());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            log.warn("获取 Windows 进程关系失败, pid={}", pid, ignored);
        }

        if (nodes.isEmpty()) {
            nodes.add(ServerProcessRelationGraph.Node.builder()
                    .pid(pid).name("PID " + pid)
                    .isTarget(true).build());
        }

        return ServerProcessRelationGraph.builder()
                .nodes(nodes).edges(edges).build();
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

    private boolean isLocalWindows(ServerHost host) {
        return host != null
                && "LOCAL".equalsIgnoreCase(host.getServerType())
                && isWindows(host);
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
        return "$os=Get-CimInstance Win32_OperatingSystem;"
                + "$total=[double]($os.TotalVisibleMemorySize)*1024;"
                + "$stats=Get-CimInstance Win32_PerfFormattedData_PerfProc_Process "
                + "| Where-Object { $_.IDProcess -gt 0 -and $_.Name -ne '_Total' -and $_.Name -ne 'Idle' } "
                + "| Sort-Object @{Expression='PercentProcessorTime';Descending=$true},@{Expression='WorkingSetPrivate';Descending=$true} "
                + "| Select-Object -First 220;"
                + "$procMap=@{};"
                + "$selected=@{};"
                + "$stats | ForEach-Object {$selected[[int]$_.IDProcess]=$true};"
                + "Get-CimInstance Win32_Process | Where-Object {$selected.ContainsKey([int]$_.ProcessId)} | ForEach-Object {$procMap[[int]$_.ProcessId]=$_};"
                + "$items=$stats | ForEach-Object {"
                + "$stat=$_;"
                + "$proc=$procMap[[int]$stat.IDProcess];"
                + "$workingSet=if($null -ne $stat.WorkingSetPrivate){[int64]$stat.WorkingSetPrivate}elseif($proc -and $null -ne $proc.WorkingSetSize){[int64]$proc.WorkingSetSize}else{$null};"
                + "$created=$null;"
                + "if($proc -and $proc.CreationDate){"
                + "try{$created=([System.Management.ManagementDateTimeConverter]::ToDateTime([string]$proc.CreationDate)).ToString('o')}catch{"
                + "try{$created=([datetime]::Parse([string]$proc.CreationDate)).ToString('o')}catch{$created=$null}"
                + "}"
                + "};"
                + "[pscustomobject]@{"
                + "ProcessId=[int64]$stat.IDProcess;"
                + "ParentProcessId=if($proc){[int64]$proc.ParentProcessId}else{$null};"
                + "Name=if($proc -and $proc.Name){$proc.Name}else{$stat.Name};"
                + "CommandLine=if($proc){$proc.CommandLine}else{$null};"
                + "UserName=$null;"
                + "State='Running';"
                + "CpuPercent=if($null -ne $stat.PercentProcessorTime){[double]$stat.PercentProcessorTime}else{$null};"
                + "MemoryPercent=if($total -gt 0 -and $workingSet -gt 0){[math]::Round(($workingSet / $total) * 100, 2)}else{$null};"
                + "WorkingSetSize=$workingSet;"
                + "ThreadCount=if($null -ne $stat.ThreadCount){[int]$stat.ThreadCount}else{$null};"
                + "Elapsed=if($null -ne $stat.ElapsedTime){[string]$stat.ElapsedTime}else{$null};"
                + "CreationDate=$created"
                + "}"
                + "};"
                + "$items | ConvertTo-Json -Depth 4 -Compress";
    }

    private String buildUnixTerminateCommand(Long pid, boolean force) {
        return (force ? "kill -KILL " : "kill -TERM ") + pid;
    }

    private String buildWindowsTerminateCommand(Long pid, boolean force) {
        return force
                ? "Stop-Process -Id %d -Force -ErrorAction Stop; Write-Output 'Process %d terminated'".formatted(pid, pid)
                : "Stop-Process -Id %d -ErrorAction Stop; Write-Output 'Process %d terminated'".formatted(pid, pid);
    }

    private List<ServerProcessView> listLocalWindowsProcesses(ServerHost host) {
        List<ServerProcessView> result = new ArrayList<>();
        ProcessHandle.allProcesses().forEach(handle -> {
            if (!handle.isAlive() || handle.pid() <= 0) {
                return;
            }
            ProcessHandle.Info info = handle.info();
            String command = info.command().orElse(null);
            String commandLine = info.commandLine().orElseGet(() -> buildCommandLine(command, info.arguments().orElse(null)));
            String name = resolveProcessName(StringUtils.hasText(command) ? command : commandLine);
            result.add(ServerProcessView.builder()
                    .serverId(host.getServerId())
                    .serverCode(host.getServerCode())
                    .pid(handle.pid())
                    .parentPid(handle.parent().map(ProcessHandle::pid).orElse(null))
                    .name(name)
                    .command(name)
                    .commandLine(StringUtils.hasText(commandLine) ? commandLine : name)
                    .user(info.user().orElse(null))
                    .state("Running")
                    .startTime(info.startInstant()
                            .map(instant -> OffsetDateTime.ofInstant(instant, ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                            .orElse(null))
                    .alive(Boolean.TRUE)
                    .executionProvider(host.getServerType())
                    .spiChannel(host.getServerType())
                    .build());
        });
        return result;
    }

    private String buildCommandLine(String command, String[] arguments) {
        if (!StringUtils.hasText(command)) {
            return null;
        }
        if (arguments == null || arguments.length == 0) {
            return command;
        }
        return command + " " + String.join(" ", Arrays.asList(arguments));
    }

    private String resolveProcessName(String command) {
        if (!StringUtils.hasText(command)) {
            return "unknown";
        }
        String normalized = command.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
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
