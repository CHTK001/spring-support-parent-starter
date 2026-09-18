package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.service.LocalSystemInfoService;
import com.chua.starter.monitor.support.spi.SystemInfoProviderRouter;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
* 系统管理 —— 进程/服务/网络/磁盘/防火墙/驱动 管理端点。
*
* <p>通过执行系统命令获取主机运行时信息，支持 Windows（PowerShell）与 Linux（bash）。
* 所有命令在 boundedElastic 调度器上执行，避免阻塞事件循环。</p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Tag(name = "监控-系统管理", description = "进程/服务/网络/磁盘/防火墙/驱动 管理")
@RestController
@RequestMapping("/ms/system")
public class SystemManageController {

    /** 是否 Windows 系统 */
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");

    @Autowired
    /** localSystemInfo服务 */
    private LocalSystemInfoService localSystemInfoService;

    @Autowired
    /** 主机Mapper */
    private MsServerHostMapper hostMapper;

    @Autowired
    /** 提供者Router */
    private com.chua.starter.monitor.support.spi.SystemInfoProviderRouter providerRouter;
    /**
     * 远程执行命令（通过 SSH）
     * @param hostId 主机ID，不允许为 null
     * @param command 方法入参 command
     * @return 结果字符串
     */
    private String execRemoteCommand(Long hostId, String command) {
        if (hostId == null) {
            return execCommand(command.split(" "));
        }
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return "";
        }
        try {
            com.chua.common.support.network.protocol.ClientSetting setting = com.chua.common.support.network.protocol.ClientSetting.builder()
                    .host(host.getServerHostAddress())
                    .port(host.getServerHostPort() != null ? host.getServerHostPort() : 22)
                    .username("root")
                    .password("lvzhou2011")
                    .connectTimeout(10_000L)
                    .build();
            try (com.chua.ssh.support.client.LinuxExecClient client = new com.chua.ssh.support.client.LinuxExecClient(setting)) {
                client.connect();
                com.chua.ssh.support.client.SshClient.ExecResult exec = client.executeCommand(command, 30_000);
                return exec.stdout() != null ? exec.stdout().trim() : "";
            }
        } catch (Exception e) {
            log.error("SSH 远程执行失败 host={} cmd={}", hostId, command, e);
            return "";
        }
    }

    // ==================== 主机基础信息（OSHI SPI） ====================

    @Operation(summary = "获取主机信息")
    @GetMapping("/host-info")
    public Mono<ReturnResult<?>> hostInfo(@RequestParam(required = false) Long hostId,
                                          @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        return Mono.fromCallable(() -> {
            if (hostId == null) {
                return (ReturnResult<Map<String, Object>>)(ReturnResult<?>) ReturnResult.ok(localSystemInfoService.getHostInfo());
            }
            MsServerHost host = hostMapper.selectById(hostId);
            if (host == null) {
                return ReturnResult.error("主机不存在: " + hostId);
            }
            try {
                Map<String, Object> info = providerRouter.route(host).hostInfo(host);
                host.setServerHostName((String) info.getOrDefault("hostname", host.getServerHostName()));
                Object cpuObj = info.get("cpuCores");
                if (cpuObj instanceof Number) {
                    host.setServerHostCpuCores(((Number) cpuObj).intValue());
                }
                hostMapper.updateById(host);
                return ReturnResult.ok(info);
            } catch (Exception e) {
                log.error("刷新主机信息失败 host={}", hostId, e);
                return ReturnResult.error("刷新主机信息失败: " + e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    /**
     * 环境Vars。
     *
     * @return Mono 对象
     */
    @Operation(summary = "获取环境变量")
    @GetMapping("/env")
    public Mono<ReturnResult<List<Map<String, String>>>> envVars() {
        return Mono.fromCallable(() -> ReturnResult.ok(localSystemInfoService.getEnvVars()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ==================== 进程管理 ====================

    /**
    * 获取进程列表。
    *
    * @param keyword   搜索关键词（进程名/命令行），空表示不过滤
    * @param sortBy    排序字段：cpu/memory/pid/name，默认 cpu
    * @param sortOrder 排序方向：asc/desc，默认 desc
    * @param limit     返回条数，默认 50
    * @return 进程列表
    */
    @Operation(summary = "获取进程列表", description = "支持搜索、排序、限制条数；默认按 CPU 降序返回前 50 条")
    @GetMapping("/processes")
    public Mono<ReturnResult<List<Map<String, Object>>>> listProcesses(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "cpu") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) Long hostId) {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> processes;
            if (hostId != null) {
                // 远程 Linux: 通过 SSH 执行 ps
                String output = execRemoteCommand(hostId, "ps -eo pid,ppid,pcpu,pmem,rss,comm,args --no-headers");
                processes = parsePsOutput(output);
            } else {
                processes = IS_WINDOWS ? listProcessesWindows() : listProcessesLinux();
            }
            // 搜索过滤
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.toLowerCase(Locale.ROOT);
                processes = processes.stream()
                        .filter(p -> {
                            String name = String.valueOf(p.getOrDefault("name", "")).toLowerCase(Locale.ROOT);
                            String cmd = String.valueOf(p.getOrDefault("command", "")).toLowerCase(Locale.ROOT);
                            return name.contains(kw) || cmd.contains(kw);
                        })
                        .collect(Collectors.toList());
            }
            // 排序
            Comparator<Map<String, Object>> comparator = switch (sortBy) {
                case "memory" -> Comparator.comparingDouble(p -> ((Number) p.getOrDefault("memory", 0)).doubleValue());
                case "pid" -> Comparator.comparingInt(p -> ((Number) p.getOrDefault("pid", 0)).intValue());
                case "name" -> Comparator.comparing(p -> String.valueOf(p.getOrDefault("name", "")));
                default -> Comparator.comparingDouble(p -> ((Number) p.getOrDefault("cpu", 0)).doubleValue());
            };
            if ("desc".equalsIgnoreCase(sortOrder)) {
                comparator = comparator.reversed();
            }
            processes.sort(comparator);
            // 限制条数
            if (limit > 0 && processes.size() > limit) {
                processes = processes.subList(0, limit);
            }
            return ReturnResult.ok(processes);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.error("获取进程列表失败: " + e.getMessage())));
    }

    /**
    * 关闭进程。
    *
    * @param pid 进程 ID
    * @return 操作结果
    */
    @Operation(summary = "关闭进程")
    @PostMapping("/process/{pid}/kill")
    public Mono<ReturnResult<String>> killProcess(@PathVariable int pid) {
        return Mono.fromCallable(() -> {
            String[] cmd = IS_WINDOWS
                    ? new String[]{"taskkill", "/F", "/PID", String.valueOf(pid)}
                    : new String[]{"kill", "-9", String.valueOf(pid)};
            String output = execCommand(cmd);
            return ReturnResult.ok("进程 " + pid + " 已关闭: " + output);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.error("关闭进程失败: " + e.getMessage())));
    }

    /**
    * 获取进程的子进程。
    *
    * @param pid 进程 ID
    * @return 子进程列表
    */
    @Operation(summary = "获取进程的子进程")
    @GetMapping("/process/{pid}/children")
    public Mono<ReturnResult<List<Map<String, Object>>>> listProcessChildren(@PathVariable int pid) {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> all = IS_WINDOWS ? listProcessesWindows() : listProcessesLinux();
            List<Map<String, Object>> children = all.stream()
                    .filter(p -> pid == ((Number) p.getOrDefault("parentPid", -1)).intValue())
                    .collect(Collectors.toList());
            return ReturnResult.ok(children);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.error("获取子进程失败: " + e.getMessage())));
    }

    /**
    * 获取进程的线程列表。
    *
    * @param pid 进程 ID
    * @return 线程列表
    */
    @Operation(summary = "获取进程的线程列表")
    @GetMapping("/process/{pid}/threads")
    public Mono<ReturnResult<List<Map<String, Object>>>> listProcessThreads(@PathVariable int pid) {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> threads = new ArrayList<>();
            if (IS_WINDOWS) {
                // Windows: Get-Process -Id pid | Select-Object -ExpandProperty Threads
                String output = execCommand("powershell", "-Command",
                        "Get-Process -Id " + pid + " -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Threads | " +
                        "Select-Object Id, StartTime, TotalProcessorTime, UserProcessorTime, PriorityLevel, ThreadState, WaitReason | " +
                        "ConvertTo-Json -Depth 3");
                threads = parseJsonList(output);
            } else {
                // Linux: ps -L -p pid
                String output = execCommand("ps", "-L", "-p", String.valueOf(pid), "-o", "tid,pcpu,pmem,stat,comm,time");
                String[] lines = output.split("\\r?\\n");
                for (int i = 1; i < lines.length; i++) {
                    String[] parts = lines[i].trim().split("\\s+", 6);
                    if (parts.length >= 5) {
                        Map<String, Object> t = new LinkedHashMap<>();
                        t.put("tid", parseInt(parts[0]));
                        t.put("cpu", parseDouble(parts[1]));
                        t.put("memory", parseDouble(parts[2]));
                        t.put("state", parts[3]);
                        t.put("name", parts.length > 4 ? parts[4] : "");
                        t.put("cpuTime", parts.length > 5 ? parts[5] : "");
                        threads.add(t);
                    }
                }
            }
            return ReturnResult.ok(threads);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.error("获取线程列表失败: " + e.getMessage())));
    }

    // ==================== 服务管理 ====================

    /**
    * 获取系统服务列表。
    *
    * @param keyword 搜索关键词，空表示不过滤
    * @return 服务列表
    */
    @Operation(summary = "获取系统服务列表")
    @GetMapping("/services")
    public Mono<ReturnResult<List<Map<String, Object>>>> listServices(
            @RequestParam(required = false) String keyword) {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> services = IS_WINDOWS ? listServicesWindows() : listServicesLinux();
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.toLowerCase(Locale.ROOT);
                services = services.stream()
                        .filter(s -> {
                            String name = String.valueOf(s.getOrDefault("name", "")).toLowerCase(Locale.ROOT);
                            String display = String.valueOf(s.getOrDefault("displayName", "")).toLowerCase(Locale.ROOT);
                            return name.contains(kw) || display.contains(kw);
                        })
                        .collect(Collectors.toList());
            }
            return ReturnResult.ok(services);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.error("获取服务列表失败: " + e.getMessage())));
    }

    /**
    * 启动服务。
    *
    * @param name 服务名称
    * @return 操作结果
    */
    @Operation(summary = "启动服务")
    @PostMapping("/service/{name}/start")
    public Mono<ReturnResult<String>> startService(@PathVariable String name) {
        return Mono.fromCallable(() -> {
            String[] cmd = IS_WINDOWS
                    ? new String[]{"powershell", "-Command", "Start-Service", "-Name", name, "-PassThru"}
                    : new String[]{"systemctl", "start", name};
            String output = execCommand(cmd);
            return ReturnResult.ok("服务 " + name + " 已启动: " + output);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.error("启动服务失败: " + e.getMessage())));
    }

    /**
    * 停止服务。
    *
    * @param name 服务名称
    * @return 操作结果
    */
    @Operation(summary = "停止服务")
    @PostMapping("/service/{name}/stop")
    public Mono<ReturnResult<String>> stopService(@PathVariable String name) {
        return Mono.fromCallable(() -> {
            String[] cmd = IS_WINDOWS
                    ? new String[]{"powershell", "-Command", "Stop-Service", "-Name", name, "-Force"}
                    : new String[]{"systemctl", "stop", name};
            String output = execCommand(cmd);
            return ReturnResult.ok("服务 " + name + " 已停止: " + output);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.error("停止服务失败: " + e.getMessage())));
    }

    /**
    * 重启服务。
    *
    * @param name 服务名称
    * @return 操作结果
    */
    @Operation(summary = "重启服务")
    @PostMapping("/service/{name}/restart")
    public Mono<ReturnResult<String>> restartService(@PathVariable String name) {
        return Mono.fromCallable(() -> {
            String[] cmd = IS_WINDOWS
                    ? new String[]{"powershell", "-Command", "Restart-Service", "-Name", name, "-Force"}
                    : new String[]{"systemctl", "restart", name};
            String output = execCommand(cmd);
            return ReturnResult.ok("服务 " + name + " 已重启: " + output);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.error("重启服务失败: " + e.getMessage())));
    }

    // ==================== 网络管理 ====================

    /**
    * 获取网络接口列表。
    *
    * @return 网络接口列表
    */
    @Operation(summary = "获取网络接口列表")
    @GetMapping("/network/interfaces")
    public Mono<ReturnResult<List<Map<String, Object>>>> listNetworkInterfaces() {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> interfaces = new ArrayList<>();
            if (IS_WINDOWS) {
                String output = execCommand("powershell", "-Command",
                        "Get-NetAdapter | Select-Object Name, InterfaceDescription, Status, MacAddress, LinkSpeed, " +
                        "ifIndex, MediaType | ConvertTo-Json -Depth 3");
                interfaces = parseJsonList(output);
            } else {
                String output = execCommand("ip", "-j", "addr");
                interfaces = parseJsonList(output);
            }
            return ReturnResult.ok(interfaces);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.error("获取网络接口失败: " + e.getMessage())));
    }

    /**
    * 获取网络连接列表。
    *
    * @param state 连接状态过滤（LISTEN/ESTABLISHED 等），空表示不过滤
    * @return 网络连接列表
    */
    @Operation(summary = "获取网络连接列表")
    @GetMapping("/network/connections")
    public Mono<ReturnResult<List<Map<String, Object>>>> listNetworkConnections(
            @RequestParam(required = false) String state) {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> connections = new ArrayList<>();
            if (IS_WINDOWS) {
                String output = execCommand("powershell", "-Command",
                        "Get-NetTCPConnection | Select-Object LocalAddress, LocalPort, RemoteAddress, RemotePort, " +
                        "State, OwningProcess | ConvertTo-Json -Depth 3");
                connections = parseJsonList(output);
            } else {
                String output = execCommand("ss", "-tunap");
                String[] lines = output.split("\\r?\\n");
                for (int i = 1; i < lines.length; i++) {
                    String[] parts = lines[i].trim().split("\\s+");
                    if (parts.length >= 5) {
                        Map<String, Object> c = new LinkedHashMap<>();
                        c.put("proto", parts[0]);
                        c.put("state", parts[1]);
                        c.put("localAddress", parts[4]);
                        c.put("remoteAddress", parts.length > 5 ? parts[5] : "");
                        c.put("process", parts.length > 6 ? parts[parts.length - 1] : "");
                        connections.add(c);
                    }
                }
            }
            if (state != null && !state.isBlank()) {
                String s = state.toUpperCase(Locale.ROOT);
                connections = connections.stream()
                        .filter(c -> String.valueOf(c.getOrDefault("state", "")).toUpperCase(Locale.ROOT).contains(s))
                        .collect(Collectors.toList());
            }
            return ReturnResult.ok(connections);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.error("获取网络连接失败: " + e.getMessage())));
    }

    // ==================== 磁盘管理 ====================

    /**
    * 获取磁盘列表。
    *
    * @return 磁盘列表
    */
    @Operation(summary = "获取磁盘列表")
    @GetMapping("/disks")
    public Mono<ReturnResult<List<Map<String, Object>>>> listDisks() {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> disks = new ArrayList<>();
            if (IS_WINDOWS) {
                String output = execCommand("powershell", "-Command",
                        "Get-PhysicalDisk | Select-Object FriendlyName, MediaType, BusType, Size, HealthStatus, " +
                        "OperationalStatus, SerialNumber | ConvertTo-Json -Depth 3");
                disks = parseJsonList(output);
            } else {
                String output = execCommand("lsblk", "-J", "-o", "NAME,SIZE,TYPE,MOUNTPOINT,MODEL,TRAN,ROTA");
                disks = parseJsonList(output);
            }
            return ReturnResult.ok(disks);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.error("获取磁盘列表失败: " + e.getMessage())));
    }

    /**
    * 获取磁盘分区列表。
    *
    * @return 磁盘分区列表
    */
    @Operation(summary = "获取磁盘分区列表")
    @GetMapping("/partitions")
    public Mono<ReturnResult<List<Map<String, Object>>>> listPartitions() {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> partitions = new ArrayList<>();
            if (IS_WINDOWS) {
                String output = execCommand("powershell", "-Command",
                        "Get-Volume | Select-Object DriveLetter, FileSystemLabel, FileSystem, Size, SizeRemaining, " +
                        "HealthStatus, DriveType | ConvertTo-Json -Depth 3");
                partitions = parseJsonList(output);
            } else {
                String output = execCommand("df", "-hT");
                String[] lines = output.split("\\r?\\n");
                for (int i = 1; i < lines.length; i++) {
                    String[] parts = lines[i].trim().split("\\s+");
                    if (parts.length >= 7) {
                        Map<String, Object> p = new LinkedHashMap<>();
                        p.put("filesystem", parts[0]);
                        p.put("type", parts[1]);
                        p.put("size", parts[2]);
                        p.put("used", parts[3]);
                        p.put("available", parts[4]);
                        p.put("usePercent", parts[5]);
                        p.put("mountPoint", parts[6]);
                        partitions.add(p);
                    }
                }
            }
            return ReturnResult.ok(partitions);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.error("获取磁盘分区失败: " + e.getMessage())));
    }

    // ==================== 防火墙管理 ====================

    /**
    * 获取防火墙规则列表。
    *
    * @return 防火墙规则列表
    */
    @Operation(summary = "获取防火墙规则列表")
    @GetMapping("/firewall/rules")
    public Mono<ReturnResult<List<Map<String, Object>>>> listFirewallRules() {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> rules = new ArrayList<>();
            if (IS_WINDOWS) {
                String output = execCommand("powershell", "-Command",
                        "Get-NetFirewallRule | Select-Object DisplayName, Direction, Action, Enabled, Profile, " +
                        "Protocol, LocalPort, RemotePort | ConvertTo-Json -Depth 3");
                rules = parseJsonList(output);
            } else {
                String output = execCommand("iptables", "-L", "-n", "--line-numbers");
                String[] lines = output.split("\\r?\\n");
                String currentChain = "";
                for (String line : lines) {
                    if (line.startsWith("Chain")) {
                        currentChain = line.split("\\s+")[1];
                    } else if (line.matches("^\\d+.*")) {
                        String[] parts = line.trim().split("\\s+", 7);
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("chain", currentChain);
                        r.put("num", parseInt(parts[0]));
                        r.put("target", parts[1]);
                        r.put("protocol", parts[2]);
                        r.put("source", parts.length > 4 ? parts[4] : "");
                        r.put("destination", parts.length > 5 ? parts[5] : "");
                        r.put("info", parts.length > 6 ? parts[6] : "");
                        rules.add(r);
                    }
                }
            }
            return ReturnResult.ok(rules);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.error("获取防火墙规则失败: " + e.getMessage())));
    }

    // ==================== 驱动管理 ====================

    /**
    * 获取驱动列表。
    *
    * @return 驱动列表
    */
    @Operation(summary = "获取驱动列表")
    @GetMapping("/drivers")
    public Mono<ReturnResult<List<Map<String, Object>>>> listDrivers() {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> drivers = new ArrayList<>();
            if (IS_WINDOWS) {
                String output = execCommand("powershell", "-Command",
                        "Get-WindowsDriver -Online | Select-Object DriverFileName, OriginalFileName, ProviderName, " +
                        "DriverVersion, DriverDate, ClassName, BootCritical | ConvertTo-Json -Depth 3");
                drivers = parseJsonList(output);
            } else {
                String output = execCommand("lsmod");
                String[] lines = output.split("\\r?\\n");
                for (int i = 1; i < lines.length; i++) {
                    String[] parts = lines[i].trim().split("\\s+");
                    if (parts.length >= 3) {
                        Map<String, Object> d = new LinkedHashMap<>();
                        d.put("name", parts[0]);
                        d.put("size", parseLong(parts[1]));
                        d.put("usedBy", parseInt(parts[2]));
                        d.put("dependsOn", parts.length > 3 ? parts[3] : "");
                        drivers.add(d);
                    }
                }
            }
            return ReturnResult.ok(drivers);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.error("获取驱动列表失败: " + e.getMessage())));
    }

    // ==================== 工具方法 ====================

    /**
    * 执行系统命令并返回输出。
    *
    * @param cmd 命令及参数
    * @return 命令输出
    */
    private String execCommand(String... cmd) {
        try {
            // Windows: 确保 powershell 能被找到（Java CreateProcess 对 PATH 解析与 cmd 不同）
            String[] actualCmd = cmd;
            if (IS_WINDOWS && cmd.length > 0 && "powershell".equalsIgnoreCase(cmd[0])) {
                String psPath = System.getenv("SystemRoot") + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";
                actualCmd = new String[cmd.length];
                actualCmd[0] = psPath;
                // 强制 PowerShell 输出 UTF-8，避免中文乱码
                String psBody = cmd[1] != null ? cmd[1] : "";
                actualCmd[1] = "[Console]::OutputEncoding=[Text.Encoding]::UTF8;" + psBody;
                System.arraycopy(cmd, 2, actualCmd, 2, cmd.length - 2);
            }
            ProcessBuilder pb = new ProcessBuilder(actualCmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.forName("UTF-8")))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            process.waitFor();
            String result = output.toString().trim();
            log.info("执行命令: {} => 输出长度={}, 退出码={}", String.join(" ", actualCmd), result.length(), process.exitValue());
            return result;
        } catch (Exception e) {
            log.error("执行命令失败: {}", String.join(" ", cmd), e);
            return "";
        }
    }

    /**
    * Windows 进程列表。
    *
    * @return 进程列表
    */
    private List<Map<String, Object>> listProcessesWindows() {
        String output = execCommand("powershell", "-Command",
                "Get-Process | Select-Object Id, ProcessName, CPU, WorkingSet64, StartTime, Path, " +
                "ParentId, ThreadCount, Description | ConvertTo-Json -Depth 3");
        List<Map<String, Object>> list = parseJsonList(output);
        for (Map<String, Object> p : list) {
            p.put("pid", p.get("Id"));
            p.put("name", p.get("ProcessName"));
            Object cpuVal = p.get("CPU");
            p.put("cpu", cpuVal instanceof Number ? cpuVal : 0);
            Object memVal = p.get("WorkingSet64");
            double memMb = (memVal instanceof Number) ? ((Number) memVal).longValue() / (1024.0 * 1024.0) : 0;
            p.put("memory", memMb);
            p.put("command", p.get("Path"));
            p.put("parentPid", p.get("ParentId"));
            p.put("threadCount", p.get("ThreadCount"));
            p.put("description", p.get("Description"));
        }
        return list;
    }

    /**
    * Linux 进程列表。
    *
    * @return 进程列表
    */
    private List<Map<String, Object>> listProcessesLinux() {
        String output = execCommand("ps", "-eo", "pid,ppid,pcpu,pmem,rss,comm,args,etime,lwp", "--no-headers");
        List<Map<String, Object>> list = new ArrayList<>();
        String[] lines = output.split("\\r?\\n");
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.trim().split("\\s+", 10);
            if (parts.length >= 6) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("pid", parseInt(parts[0]));
                p.put("parentPid", parseInt(parts[1]));
                p.put("cpu", parseDouble(parts[2]));
                p.put("memory", parseDouble(parts[3]));
                p.put("memoryBytes", parseLong(parts[4]) * 1024L);
                p.put("name", parts[5]);
                p.put("command", parts.length > 6 ? parts[6] : "");
                p.put("elapsedTime", parts.length > 7 ? parts[7] : "");
                p.put("threadCount", parts.length > 8 ? parseInt(parts[8]) : 1);
                list.add(p);
            }
        }
        return list;
    }

    /**
    * Windows 服务列表。
    *
    * @return 服务列表
    */
    private List<Map<String, Object>> listServicesWindows() {
        String output = execCommand("powershell", "-Command",
                "Get-Service | Select-Object Name, DisplayName, Status, StartType, ServiceType | ConvertTo-Json -Depth 3");
        return parseJsonList(output);
    }

    /**
    * Linux 服务列表。
    *
    * @return 服务列表
    */
    private List<Map<String, Object>> listServicesLinux() {
        String output = execCommand("systemctl", "list-units", "--type=service", "--all", "--no-pager", "--no-legend");
        List<Map<String, Object>> list = new ArrayList<>();
        String[] lines = output.split("\\r?\\n");
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.trim().split("\\s+", 5);
            if (parts.length >= 3) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("name", parts[0].replace(".service", ""));
                s.put("loadState", parts[1]);
                s.put("activeState", parts[2]);
                s.put("subState", parts.length > 3 ? parts[3] : "");
                s.put("description", parts.length > 4 ? parts[4] : "");
                s.put("status", "active".equals(parts[2]) ? "Running" : "Stopped");
                list.add(s);
            }
        }
        return list;
    }

    /**
    * 解析 JSON 数组字符串为 List。
    *
    * @param json JSON 字符串
    * @return Map 列表
    */
    private static final com.fasterxml.jackson.databind.ObjectMapper JACKSON = new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 解析Json列出。
     *
     * @param json 方法入参 json
     * @return 结果列表，无数据时为空列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = JACKSON.readTree(json);
            if (root.isArray()) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (com.fasterxml.jackson.databind.JsonNode node : root) {
                    list.add(JACKSON.convertValue(node, Map.class));
                }
                return list;
            } else if (root.isObject()) {
                List<Map<String, Object>> single = new ArrayList<>();
                single.add(JACKSON.convertValue(root, Map.class));
                return single;
            }
        } catch (Exception e) {
            log.warn("解析 JSON 失败: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    /**
     * 解析Int。
     *
     * @param s 方法入参 s
     * @return 结果数值
     */
    private int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 解析Long。
     *
     * @param s 方法入参 s
     * @return 结果数值
     */
    private long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 解析Double。
     *
     * @param s 方法入参 s
     * @return 结果数值
     */
    private double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * 解析 ps 输出
     * @param output 方法入参 output
     * @return 结果列表，无数据时为空列表
     */
    private List<Map<String, Object>> parsePsOutput(String output) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (output == null || output.isBlank()) {
            return list;
        }
        for (String line : output.split("\\r?\\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.trim().split("\\s+", 9);
            if (parts.length >= 6) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("pid", parseInt(parts[0]));
                p.put("parentPid", parseInt(parts[1]));
                p.put("cpu", parseDouble(parts[2]));
                p.put("memory", parseDouble(parts[3]));
                p.put("memoryBytes", parseLong(parts[4]) * 1024L);
                p.put("name", parts[5]);
                p.put("command", parts.length > 6 ? parts[6] : "");
                list.add(p);
            }
        }
        return list;
    }
}
