package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.model.DiskPartitionRowVO;
import com.chua.starter.monitor.support.model.DiskRowVO;
import com.chua.starter.monitor.support.model.DriverRowVO;
import com.chua.starter.monitor.support.model.EnvVarVO;
import com.chua.starter.monitor.support.model.FileEntryVO;
import com.chua.starter.monitor.support.model.FirewallRuleRowVO;
import com.chua.starter.monitor.support.model.HostInfoVO;
import com.chua.starter.monitor.support.model.NetProcessRowVO;
import com.chua.starter.monitor.support.model.NetworkInterfaceRowVO;
import com.chua.starter.monitor.support.model.ProcessRowVO;
import com.chua.starter.monitor.support.model.ProcessThreadRowVO;
import com.chua.starter.monitor.support.model.ServiceRowVO;
import com.chua.starter.monitor.support.model.SystemListVO;
import com.chua.starter.monitor.support.spi.Platforms;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.monitor.support.spi.SystemInfoProviderRouter;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 系统管理 —— 进程/服务/网络/磁盘/防火墙/驱动 管理端点。
 *
 * <p>本类不接触任何采集实现：主机（含 hostId 为空的管控机本机）统一由
 * {@link SystemInfoProviderRouter} 选出 {@link SystemInfoProvider} 通道
 * （LOCAL / AGENT / SSH / WINRM），出入口一律是强类型视图。防火墙、驱动、分区、线程
 * 四个只读的机器级端点仍走本机 shell，因为台账里没有对应的主机配置可路由。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Tag(name = "监控-系统管理", description = "进程/服务/网络/磁盘/防火墙/驱动 管理")
@RestController
@RequestMapping("/ms/system")
public class SystemManageController {

    /**
     * 是否 Windows 系统
     */
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");

    /**
     * 一 GiB 的字节数
     */
    private static final double GIB_BYTES = 1024.0 * 1024.0 * 1024.0;

    /**
     * 本机命令输出的 JSON 解析器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 主机 映射器
     */
    @Autowired
    private MsServerHostMapper hostMapper;

    /**
     * 采集通道路由器
     */
    @Autowired
    private SystemInfoProviderRouter providerRouter;

    /**
     * 主机与其可用通道 Provider 的配对结果。
     *
     * @param host     目标主机
     * @param provider 按主机配置选出的 Provider
     */
    private record HostChannel(MsServerHost host, SystemInfoProvider provider) {
    }

    /**
     * 解析主机并选出可用通道；hostId 为空表示管控机本机。
     *
     * @param hostId 主机 ID，可为空
     * @return 主机与 Provider 的配对
     * @throws IllegalStateException 主机不存在或无可用通道
     */
    private HostChannel channelOf(Long hostId) {
        MsServerHost host = hostId == null ? localHost() : hostMapper.selectById(hostId);
        if (host == null) {
            throw new IllegalStateException("主机不存在: " + hostId);
        }
        return new HostChannel(host, providerRouter.route(host));
    }

    /**
     * 未落台账的本机描述：让本机也经 SPI 通道采集，而不是在控制层直连采集服务。
     *
     * @return 回环地址主机
     */
    private static MsServerHost localHost() {
        MsServerHost host = new MsServerHost();
        host.setServerHostName("localhost");
        host.setServerHostAddress("127.0.0.1");
        host.setServerHostOsType("LOCAL");
        return host;
    }

    /**
     * 目标主机的真实平台：osType 可能只表达了接入方式（AGENT / LOCAL）。
     *
     * @param ch 主机与通道
     * @return WINDOWS / LINUX / MAC，通道无法判定时按管控端所在平台兜底
     */
    private static String platformOf(HostChannel ch) {
        String platform = ch.provider().platform(ch.host());
        if (platform != null && !platform.isEmpty()) {
            return platform;
        }
        return IS_WINDOWS ? Platforms.WINDOWS : Platforms.LINUX;
    }

    /**
     * 目标主机是否为 Windows。
     *
     * @param ch 主机与通道
     * @return 是返回 true
     */
    private static boolean isWindowsTarget(HostChannel ch) {
        return Platforms.WINDOWS.equals(platformOf(ch));
    }

    /**
     * 拼装带工作目录的命令行。
     *
     * @param cwd     工作目录，可为空
     * @param cmd     命令
     * @param windows 目标是否 Windows
     * @return 可在目标 shell 执行的命令行
     */
    private static String withCwd(String cwd, String cmd, boolean windows) {
        if (cwd == null || cwd.isEmpty()) {
            return cmd;
        }
        if (windows) {
            // cmd.exe 的 cd 不认正斜杠，必须先归一为反斜杠
            return "cd /d \"" + cwd.replace("/", "\\").replace("\"", "") + "\" && " + cmd;
        }
        return "cd '" + cwd.replace("'", "") + "' && " + cmd;
    }

    // ==================== 主机基础信息 ====================

    @Operation(summary = "获取主机信息")
    @GetMapping("/host-info")
    public Mono<ReturnResult<HostInfoVO>> hostInfo(@RequestParam(required = false) Long hostId,
                                                   @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            HostInfoVO info = ch.provider().hostInfo(ch.host());
            if (info == null) {
                return ReturnResult.<HostInfoVO>error("未取到主机信息");
            }
            if (hostId != null) {
                MsServerHost host = ch.host();
                if (info.getHostname() != null && !info.getHostname().isEmpty()) {
                    host.setServerHostName(info.getHostname());
                }
                if (info.getCpuCores() != null) {
                    host.setServerHostCpuCores(info.getCpuCores());
                }
                hostMapper.updateById(host);
            }
            return ReturnResult.ok(info);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> {
              log.error("刷新主机信息失败 host={}", hostId, e);
              return Mono.just(ReturnResult.<HostInfoVO>error("刷新主机信息失败: " + e.getMessage()));
          });
    }

    @Operation(summary = "获取环境变量")
    @GetMapping("/env")
    public Mono<ReturnResult<List<EnvVarVO>>> envVars(@RequestParam(required = false) Long hostId) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            return ReturnResult.ok(ch.provider().env(ch.host()));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<List<EnvVarVO>>error("获取环境变量失败: " + e.getMessage())));
    }

    // ==================== 进程管理 ====================

    /**
     * 获取进程列表。
     *
     * @param keyword   搜索关键词（进程名/命令行），空表示不过滤
     * @param sortBy    排序字段：cpu/memory/pid/name，默认 cpu
     * @param sortOrder 排序方向：asc/desc，默认 desc
     * @param limit     返回条数，默认 50；传 page/pageSize 时以 pageSize 为准
     * @param hostId    主机 ID，为空表示管控机本机
     * @return 进程分页视图
     */
    @Operation(summary = "获取进程列表", description = "支持搜索、排序、分页；默认按 CPU 降序返回前 50 条")
    @GetMapping("/processes")
    public Mono<ReturnResult<SystemListVO<ProcessRowVO>>> listProcesses(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "cpu") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long hostId) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            int want = pageSize != null && pageSize > 0 ? pageSize : Math.max(limit, 500);
            List<ProcessRowVO> processes = new ArrayList<>(ch.provider().processes(ch.host(), 1, want));
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.toLowerCase(Locale.ROOT);
                processes = processes.stream()
                        .filter(p -> containsKw(p.getName(), kw) || containsKw(p.getCommand(), kw))
                        .collect(Collectors.toCollection(ArrayList::new));
            }
            processes.sort(comparatorOf(sortBy, sortOrder));
            int total = processes.size();
            return ReturnResult.ok(SystemListVO.of(slice(processes, page, pageSize != null && pageSize > 0 ? pageSize : limit), total));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<SystemListVO<ProcessRowVO>>error("获取进程列表失败: " + e.getMessage())));
    }

    /**
     * 取分页窗口；size 非正时返回原列表。
     *
     * @param rows     已排序的全量行
     * @param page     页码，空按 1
     * @param size     每页条数
     * @param <R>      行类型
     * @return 当前页数据
     */
    private static <R> List<R> slice(List<R> rows, Integer page, int size) {
        if (size <= 0) {
            return rows;
        }
        int from = Math.max(0, ((page != null && page > 0 ? page : 1) - 1) * size);
        return from >= rows.size() ? List.of() : new ArrayList<>(rows.subList(from, Math.min(rows.size(), from + size)));
    }

    /**
     * 关键词命中判断（大小写不敏感，空值不命中）。
     *
     * @param value 字段值
     * @param kw    已转小写的关键词
     * @return 命中返回 true
     */
    private static boolean containsKw(String value, String kw) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(kw);
    }

    /**
     * 进程排序器。
     *
     * @param sortBy    排序字段
     * @param sortOrder 排序方向
     * @return 比较器
     */
    private static Comparator<ProcessRowVO> comparatorOf(String sortBy, String sortOrder) {
        Comparator<ProcessRowVO> comparator = switch (sortBy) {
            case "memory" -> Comparator.comparingDouble(ProcessRowVO::getMemory);
            case "pid" -> Comparator.comparingLong(ProcessRowVO::getPid);
            case "name" -> Comparator.comparing(p -> p.getName() == null ? "" : p.getName());
            default -> Comparator.comparingDouble(ProcessRowVO::getCpu);
        };
        return "desc".equalsIgnoreCase(sortOrder) ? comparator.reversed() : comparator;
    }

    /**
     * 按目标平台给出杀进程命令。
     *
     * @param ch  主机与通道
     * @param pid 进程 ID
     * @return 命令行
     */
    private static String killCommand(HostChannel ch, int pid) {
        return isWindowsTarget(ch) ? "taskkill /F /PID " + pid : "kill -9 " + pid;
    }

    /**
     * 关闭进程。
     *
     * @param pid    进程 ID
     * @param hostId 主机 ID，为空表示管控机本机
     * @return 操作结果
     */
    @Operation(summary = "关闭进程")
    @PostMapping("/process/{pid}/kill")
    public Mono<ReturnResult<String>> killProcess(@PathVariable int pid,
                                                 @RequestParam(required = false) Long hostId) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            if (!processAlive(ch, pid)) {
                return ReturnResult.<String>error("进程 " + pid + " 不存在");
            }
            String out = ch.provider().exec(ch.host(), killCommand(ch, pid));
            return killedResult(ch, pid, out, "");
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<String>error("关闭进程失败: " + e.getMessage())));
    }

    /**
     * 依据复查结果给出关闭进程的返回。
     *
     * @param ch     主机与通道
     * @param pid    目标进程 ID
     * @param out    kill 命令的输出
     * @param suffix 回显后缀（端口等）
     * @return 进程确认消失返回成功，否则返回失败并带上命令输出
     */
    private ReturnResult<String> killedResult(HostChannel ch, int pid, String out, String suffix) {
        String detail = out == null ? "" : out.trim();
        for (int i = 0; i < 3; i++) {
            if (!processAlive(ch, pid)) {
                return ReturnResult.ok("进程 " + pid + " 已关闭" + suffix + (detail.isEmpty() ? "" : ": " + detail));
            }
            sleepQuietly(800);
        }
        return ReturnResult.<String>error("进程 " + pid + " 未能关闭" + suffix
                + (detail.isEmpty() ? "（进程可能不存在或权限不足）" : ": " + detail));
    }

    /**
     * 复查进程是否仍存在。
     *
     * <p>用一条存在性探测命令而不是拉全量进程表：Agent 上一次全表要几秒，kill 前后要复查多次，
     * 拉表会把请求拖到异步超时。</p>
     *
     * @param ch  主机与通道
     * @param pid 进程 ID
     * @return 存在返回 true
     */
    private boolean processAlive(HostChannel ch, int pid) {
        String probe;
        if (isWindowsTarget(ch)) {
            probe = "tasklist /FI \"PID eq " + pid + "\" /NH";
        } else {
            probe = "kill -0 " + pid + " 2>/dev/null && echo ALIVE || echo DEAD";
        }
        String out = ch.provider().exec(ch.host(), probe);
        if (out == null || out.isBlank()) {
            return false;
        }
        if (isWindowsTarget(ch)) {
            return out.contains(String.valueOf(pid));
        }
        String upper = out.toUpperCase(Locale.ROOT);
        return upper.contains("ALIVE") && !upper.contains("DEAD");
    }

    /**
     * 获取进程的子进程。
     *
     * @param pid 进程 ID
     * @return 子进程列表
     */
    @Operation(summary = "获取进程的子进程")
    @GetMapping("/process/{pid}/children")
    public Mono<ReturnResult<List<ProcessRowVO>>> listProcessChildren(@PathVariable int pid) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(null);
            List<ProcessRowVO> children = ch.provider().processes(ch.host(), 1, 10000).stream()
                    .filter(p -> p.getPpid() == pid)
                    .collect(Collectors.toList());
            return ReturnResult.ok(children);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<List<ProcessRowVO>>error("获取子进程失败: " + e.getMessage())));
    }

    /**
     * 获取进程的线程列表（本机端点，台账内无对应通道配置）。
     *
     * @param pid 进程 ID
     * @return 线程列表
     */
    @Operation(summary = "获取进程的线程列表")
    @GetMapping("/process/{pid}/threads")
    public Mono<ReturnResult<List<ProcessThreadRowVO>>> listProcessThreads(@PathVariable int pid) {
        return Mono.fromCallable(() -> {
            if (IS_WINDOWS) {
                String output = execCommand("powershell", "-Command",
                        "Get-Process -Id " + pid + " -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Threads | "
                        + "Select-Object Id, ThreadState, WaitReason, TotalProcessorTime | ConvertTo-Json -Depth 3");
                return ReturnResult.ok(parseRows(output, SystemManageController::windowsThread));
            }
            String output = execCommand("ps", "-L", "-p", String.valueOf(pid), "-o", "tid,pcpu,pmem,stat,comm,time");
            List<ProcessThreadRowVO> threads = new ArrayList<>();
            String[] lines = output.split("\\r?\\n");
            for (int i = 1; i < lines.length; i++) {
                String[] parts = lines[i].trim().split("\\s+", 6);
                if (parts.length < 5) {
                    continue;
                }
                ProcessThreadRowVO vo = new ProcessThreadRowVO();
                vo.setTid(parseLong(parts[0]));
                vo.setCpu(parseDouble(parts[1]));
                vo.setMemory(parseDouble(parts[2]));
                vo.setState(parts[3]);
                vo.setName(parts.length > 4 ? parts[4] : "");
                vo.setCpuTime(parts.length > 5 ? parts[5] : "");
                threads.add(vo);
            }
            return ReturnResult.ok(threads);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<List<ProcessThreadRowVO>>error("获取线程列表失败: " + e.getMessage())));
    }

    /**
     * PowerShell ThreadObject → 线程视图。
     *
     * @param node 单条 JSON 节点
     * @return 线程视图
     */
    private static ProcessThreadRowVO windowsThread(JsonNode node) {
        ProcessThreadRowVO vo = new ProcessThreadRowVO();
        vo.setTid(number(node, "Id"));
        vo.setState(text(node, "ThreadState"));
        vo.setName(text(node, "WaitReason"));
        vo.setCpuTime(text(node, "TotalProcessorTime"));
        return vo;
    }

    // ==================== 服务管理 ====================

    /**
     * 获取系统服务列表。
     *
     * @param keyword  搜索关键词，空表示不过滤
     * @param page     页码
     * @param pageSize 每页条数
     * @param hostId   主机 ID，为空表示管控机本机
     * @return 服务分页视图
     */
    @Operation(summary = "获取系统服务列表")
    @GetMapping("/services")
    public Mono<ReturnResult<SystemListVO<ServiceRowVO>>> listServices(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long hostId) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            int want = pageSize != null && pageSize > 0 ? pageSize : 5000;
            List<ServiceRowVO> services = new ArrayList<>(ch.provider().services(ch.host(), 1, want));
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.toLowerCase(Locale.ROOT);
                services = services.stream()
                        .filter(s -> containsKw(s.getName(), kw) || containsKw(s.getDisplayName(), kw))
                        .collect(Collectors.toCollection(ArrayList::new));
            }
            int total = services.size();
            return ReturnResult.ok(SystemListVO.of(slice(services, page, pageSize == null ? 0 : pageSize), total));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<SystemListVO<ServiceRowVO>>error("获取服务列表失败: " + e.getMessage())));
    }

    /**
     * PowerShell 服务动作语句。
     *
     * @param name   服务名
     * @param action start / stop / restart
     * @return PowerShell 命令体
     */
    private static String psServiceBody(String name, String action) {
        String verb = switch (action) {
            case "start" -> "Start-Service";
            case "stop" -> "Stop-Service";
            default -> "Restart-Service";
        };
        return verb + " -Name " + sanitizeServiceName(name) + ("start".equals(action) ? " -PassThru" : " -Force");
    }

    /**
     * 剔除服务名里的 shell 元字符。
     *
     * @param name 服务名
     * @return 安全的服务名
     */
    private static String sanitizeServiceName(String name) {
        return name.replaceAll("[\";&|`$\\\\]", "");
    }

    /**
     * 服务动作命令：按目标主机的真实平台选择命令族。
     *
     * @param platform 真实平台（WINDOWS / LINUX / MAC）
     * @param name     服务名
     * @param action   start / stop / restart
     * @return 可在目标主机 shell 执行的命令行
     */
    private static String serviceCommand(String platform, String name, String action) {
        if (Platforms.WINDOWS.equalsIgnoreCase(platform)) {
            return "powershell -Command \"" + psServiceBody(name, action) + "\"";
        }
        return "systemctl " + action + " " + sanitizeServiceName(name);
    }

    /**
     * 对主机上的服务执行动作。
     *
     * @param hostId 主机 ID，为空表示管控机本机
     * @param name   服务名称
     * @param action start / stop / restart
     * @return 操作结果
     */
    private Mono<ReturnResult<String>> controlService(Long hostId, String name, String action) {
        return Mono.fromCallable(() -> {
            String label = switch (action) {
                case "start" -> "启动";
                case "stop" -> "停止";
                default -> "重启";
            };
            HostChannel ch = channelOf(hostId);
            String output = ch.provider().exec(ch.host(), serviceCommand(platformOf(ch), name, action));
            return serviceResult(ch, name, action, label, output);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<String>error("服务操作失败: " + e.getMessage())));
    }

    /**
     * 服务动作后复查真实状态，避免把"指令已下发"当成操作成功。
     *
     * @param ch     主机与通道
     * @param name   服务名
     * @param action start / stop / restart
     * @param label  中文动作名
     * @param out    动作命令的输出
     * @return 状态与期望一致返回成功，否则返回失败
     */
    private ReturnResult<String> serviceResult(HostChannel ch, String name, String action, String label, String out) {
        String expect = "stop".equals(action) ? "STOPPED" : "RUNNING";
        String detail = out == null ? "" : out.trim();
        String last = "";
        for (int i = 0; i < 4; i++) {
            last = serviceState(ch, name);
            if (expect.equals(last)) {
                return ReturnResult.ok("服务 " + name + " 已" + label + "（当前 " + last + "）"
                        + (detail.isEmpty() ? "" : ": " + detail));
            }
            if ("UNKNOWN".equals(last) || "MISSING".equals(last)) {
                break;
            }
            sleepQuietly(1000);
        }
        return ReturnResult.<String>error("服务 " + name + " " + label + "未生效，当前状态 " + last
                + (detail.isEmpty() ? "" : "：" + detail));
    }

    /**
     * 睡眠指定毫秒，被打断时恢复中断标记。
     *
     * @param millis 毫秒
     */
    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 查询单个服务的当前状态。
     *
     * <p>按名字单查而不是拉全量服务表：动作后最多复查 4 次，全表拉取会拖到异步超时。</p>
     *
     * @param ch   主机与通道
     * @param name 服务名（大小写不敏感）
     * @return RUNNING / STOPPED 等，找不到服务返回 MISSING
     */
    private String serviceState(HostChannel ch, String name) {
        String safe = sanitizeServiceName(name);
        String out;
        if (isWindowsTarget(ch)) {
            out = ch.provider().exec(ch.host(), "powershell -NoProfile -Command \"$s = Get-Service -Name '"
                    + safe + "' -ErrorAction SilentlyContinue; if ($s) { $s.Status } else { 'MISSING' }\"");
        } else {
            out = ch.provider().exec(ch.host(), "systemctl show -p LoadState,ActiveState --value " + safe + " 2>/dev/null");
        }
        if (out == null || out.isBlank()) {
            return "UNKNOWN";
        }
        String text = out.trim();
        if (text.contains("MISSING") || text.contains("not-found")) {
            return "MISSING";
        }
        String[] lines = text.split("\\r?\\n");
        String state = lines[lines.length - 1].trim();
        if ("running".equalsIgnoreCase(state) || "active".equalsIgnoreCase(state)) {
            return "RUNNING";
        }
        if ("stopped".equalsIgnoreCase(state) || "inactive".equalsIgnoreCase(state) || "failed".equalsIgnoreCase(state)) {
            return "STOPPED";
        }
        return "UNKNOWN";
    }

    /**
     * 启动服务。
     *
     * @param name   服务名称
     * @param hostId 主机 ID，为空表示管控机本机
     * @return 操作结果
     */
    @Operation(summary = "启动服务")
    @PostMapping("/service/{name}/start")
    public Mono<ReturnResult<String>> startService(@PathVariable String name,
                                                   @RequestParam(required = false) Long hostId) {
        return controlService(hostId, name, "start");
    }

    /**
     * 停止服务。
     *
     * @param name   服务名称
     * @param hostId 主机 ID，为空表示管控机本机
     * @return 操作结果
     */
    @Operation(summary = "停止服务")
    @PostMapping("/service/{name}/stop")
    public Mono<ReturnResult<String>> stopService(@PathVariable String name,
                                                  @RequestParam(required = false) Long hostId) {
        return controlService(hostId, name, "stop");
    }

    /**
     * 重启服务。
     *
     * @param name   服务名称
     * @param hostId 主机 ID，为空表示管控机本机
     * @return 操作结果
     */
    @Operation(summary = "重启服务")
    @PostMapping("/service/{name}/restart")
    public Mono<ReturnResult<String>> restartService(@PathVariable String name,
                                                     @RequestParam(required = false) Long hostId) {
        return controlService(hostId, name, "restart");
    }

    // ==================== 网络管理 ====================

    /**
     * 获取网络接口列表。
     *
     * @param hostId 主机 ID，为空表示管控机本机
     * @return 网络接口列表
     */
    @Operation(summary = "获取网络接口列表")
    @GetMapping("/network/interfaces")
    public Mono<ReturnResult<List<NetworkInterfaceRowVO>>> listNetworkInterfaces(
            @RequestParam(required = false) Long hostId) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            return ReturnResult.ok(ch.provider().networkInterfaces(ch.host()));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<List<NetworkInterfaceRowVO>>error("获取网络接口失败: " + e.getMessage())));
    }

    /**
     * 获取网络连接列表。
     *
     * @param state  连接状态过滤（LISTEN/ESTABLISHED 等），空表示不过滤
     * @param hostId 主机 ID，为空表示管控机本机
     * @return 网络连接列表
     */
    @Operation(summary = "获取网络连接列表")
    @GetMapping("/network/connections")
    public Mono<ReturnResult<List<NetProcessRowVO>>> listNetworkConnections(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) Long hostId) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            List<NetProcessRowVO> connections = ch.provider().netProcesses(ch.host());
            if (connections == null) {
                return ReturnResult.ok(List.<NetProcessRowVO>of());
            }
            if (state == null || state.isBlank()) {
                return ReturnResult.ok(connections);
            }
            String s = state.toUpperCase(Locale.ROOT);
            return ReturnResult.ok(connections.stream()
                    .filter(c -> c.getState() != null && c.getState().toUpperCase(Locale.ROOT).contains(s))
                    .collect(Collectors.toList()));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<List<NetProcessRowVO>>error("获取网络连接失败: " + e.getMessage())));
    }

    /**
     * 获取网络监听与占用进程。
     *
     * @param hostId 主机 ID，为空表示管控机本机
     * @return 结构化端口占用行
     */
    @Operation(summary = "获取网络监听与占用进程",
            description = "四类通道（本机/Agent/SSH/WinRM）统一输出 protocol/local/remote/port/state/pid/process 结构化行")
    @GetMapping("/netproc")
    public Mono<ReturnResult<List<NetProcessRowVO>>> listNetProcesses(@RequestParam(required = false) Long hostId) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            return ReturnResult.ok(ch.provider().netProcesses(ch.host()));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<List<NetProcessRowVO>>error("获取网络进程失败: " + e.getMessage())));
    }

    /**
     * 关闭占用端口的进程。
     *
     * @param hostId 主机 ID，为空表示管控机本机
     * @param pid    进程 ID
     * @param port   端口，仅用于回显
     * @return 操作结果
     */
    @Operation(summary = "关闭占用端口的进程")
    @PostMapping("/netproc/close")
    public Mono<ReturnResult<String>> closeNetProcess(@RequestParam(required = false) Long hostId,
                                                      @RequestParam int pid,
                                                      @RequestParam(required = false) Integer port) {
        return Mono.fromCallable(() -> {
            String suffix = port == null ? "" : " (端口 " + port + ")";
            HostChannel ch = channelOf(hostId);
            if (!processAlive(ch, pid)) {
                return ReturnResult.<String>error("进程 " + pid + " 不存在");
            }
            String out = ch.provider().exec(ch.host(), killCommand(ch, pid));
            return killedResult(ch, pid, out, suffix);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<String>error("关闭失败: " + e.getMessage())));
    }

    /**
     * 应用网络配置（IP / 网关 / DNS）。
     *
     * @param hostId  主机 ID，为空表示管控机本机
     * @param request 配置项
     * @return 操作结果
     */
    @Operation(summary = "应用网络配置")
    @PostMapping("/network/config")
    public Mono<ReturnResult<String>> applyNetworkConfig(@RequestParam(required = false) Long hostId,
                                                         @RequestBody NetworkConfigRequest request) {
        return Mono.fromCallable(() -> {
            String ifname = trim(request.ifname());
            if (ifname.isEmpty()) {
                return ReturnResult.<String>error("缺少网卡名 ifname");
            }
            String command = nmcliCommand(request, ifname);
            if (command == null) {
                return ReturnResult.<String>error("静态地址缺少 ip");
            }
            HostChannel ch = channelOf(hostId);
            String out = ch.provider().exec(ch.host(), command);
            if (out != null && (out.contains("Error") || out.contains("error") || out.contains("No such"))) {
                return ReturnResult.<String>error("网络配置未生效: " + out.trim());
            }
            return ReturnResult.ok("网络配置已应用" + (out == null || out.isBlank() ? "" : ": " + out.trim()));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<String>error("应用网络配置失败: " + e.getMessage())));
    }

    /**
     * 组装 NetworkManager 配置命令。
     *
     * @param request 配置项
     * @param ifname  已归一的网卡名
     * @return 命令行；静态模式缺 ip 时返回 null
     */
    private static String nmcliCommand(NetworkConfigRequest request, String ifname) {
        String con = "'" + ifname.replace("'", "") + "'";
        StringBuilder cmd = new StringBuilder();
        if ("static".equalsIgnoreCase(trim(request.ipMode()))) {
            String ip = trim(request.ip());
            if (ip.isEmpty()) {
                return null;
            }
            cmd.append("nmcli con mod ").append(con).append(" ipv4.addresses '").append(ip)
                    .append('/').append(toPrefix(request.mask())).append("' ipv4.method manual");
            String gateway = trim(request.gateway());
            if (!gateway.isEmpty()) {
                cmd.append(" ipv4.gateway '").append(gateway).append("'");
            }
            String dns = joinNonEmpty(" ", trim(request.dns1()), trim(request.dns2()));
            if (!dns.isEmpty()) {
                cmd.append(" ipv4.dns '").append(dns).append("' ipv4.ignore-auto-dns yes");
            }
        } else {
            cmd.append("nmcli con mod ").append(con).append(" ipv4.method auto");
        }
        return cmd.append(" && nmcli con up ").append(con).toString();
    }

    /**
     * 子网掩码转前缀长度。
     *
     * @param mask 点分掩码，空表示 24
     * @return 前缀长度
     */
    private static int toPrefix(String mask) {
        if (mask == null || mask.isBlank()) {
            return 24;
        }
        if (mask.matches("^\\d{1,2}$")) {
            return Integer.parseInt(mask);
        }
        int prefix = 0;
        for (String part : mask.split("\\.")) {
            prefix += Integer.bitCount(Integer.parseInt(part) & 0xFF);
        }
        return prefix == 0 ? 24 : prefix;
    }

    /**
     * 取字符串值，null 归空串。
     *
     * @param value 原始值
     * @return 字符串
     */
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 用分隔符拼接非空片段。
     *
     * @param sep   分隔符
     * @param parts 片段
     * @return 拼接结果
     */
    private static String joinNonEmpty(String sep, String... parts) {
        List<String> kept = new ArrayList<>();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                kept.add(p);
            }
        }
        return String.join(sep, kept);
    }

    // ==================== 磁盘管理 ====================

    /**
     * 获取磁盘列表。
     *
     * @param hostId 主机 ID，为空表示管控机本机
     * @return 磁盘列表
     */
    @Operation(summary = "获取磁盘列表")
    @GetMapping("/disks")
    public Mono<ReturnResult<List<DiskRowVO>>> listDisks(@RequestParam(required = false) Long hostId) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            return ReturnResult.ok(ch.provider().disks(ch.host()));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<List<DiskRowVO>>error("获取磁盘列表失败: " + e.getMessage())));
    }

    /**
     * 获取磁盘分区列表（本机端点，台账内无对应通道配置）。
     *
     * @return 磁盘分区列表
     */
    @Operation(summary = "获取磁盘分区列表")
    @GetMapping("/partitions")
    public Mono<ReturnResult<List<DiskPartitionRowVO>>> listPartitions() {
        return Mono.fromCallable(() -> {
            if (IS_WINDOWS) {
                String output = execCommand("powershell", "-Command",
                        "Get-Volume | Select-Object DriveLetter, FileSystemLabel, FileSystem, Size, SizeRemaining "
                        + "| ConvertTo-Json -Depth 3");
                return ReturnResult.ok(parseRows(output, SystemManageController::windowsPartition));
            }
            String output = execCommand("df", "-hT");
            List<DiskPartitionRowVO> partitions = new ArrayList<>();
            String[] lines = output.split("\\r?\\n");
            for (int i = 1; i < lines.length; i++) {
                String[] parts = lines[i].trim().split("\\s+", 7);
                if (parts.length < 7) {
                    continue;
                }
                DiskPartitionRowVO vo = new DiskPartitionRowVO();
                vo.setFilesystem(parts[0]);
                vo.setType(parts[1]);
                vo.setSize(parts[2]);
                vo.setUsed(parts[3]);
                vo.setAvailable(parts[4]);
                vo.setUsePercent(parts[5]);
                vo.setMountPoint(parts[6]);
                partitions.add(vo);
            }
            return ReturnResult.ok(partitions);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<List<DiskPartitionRowVO>>error("获取磁盘分区失败: " + e.getMessage())));
    }

    /**
     * PowerShell Get-Volume → 分区视图（容量统一为 GiB 文本）。
     *
     * @param node 单条 JSON 节点
     * @return 分区视图
     */
    private static DiskPartitionRowVO windowsPartition(JsonNode node) {
        long size = number(node, "Size");
        long remaining = number(node, "SizeRemaining");
        String letter = text(node, "DriveLetter");
        String label = text(node, "FileSystemLabel");
        DiskPartitionRowVO vo = new DiskPartitionRowVO();
        vo.setFilesystem(letter.isEmpty() ? label : letter + ":\\");
        vo.setType(text(node, "FileSystem"));
        vo.setSize(gibText(size));
        vo.setUsed(gibText(size - remaining));
        vo.setAvailable(gibText(remaining));
        vo.setUsePercent(size <= 0 ? "" : Math.round((size - remaining) * 100.0 / size) + "%");
        vo.setMountPoint(label);
        return vo;
    }

    /**
     * 字节数转 GiB 展示文本。
     *
     * @param bytes 字节数
     * @return 形如 {@code 12.3G}
     */
    private static String gibText(long bytes) {
        return String.format(Locale.ROOT, "%.1fG", bytes / GIB_BYTES);
    }

    // ==================== 防火墙管理 ====================

    /**
     * 获取防火墙规则列表（本机端点，台账内无对应通道配置）。
     *
     * @return 防火墙规则列表
     */
    @Operation(summary = "获取防火墙规则列表")
    @GetMapping("/firewall/rules")
    public Mono<ReturnResult<List<FirewallRuleRowVO>>> listFirewallRules() {
        return Mono.fromCallable(() -> {
            if (IS_WINDOWS) {
                String output = execCommand("powershell", "-Command",
                        "Get-NetFirewallRule | Select-Object DisplayName, Direction, Action, Enabled, Profile "
                        + "| ConvertTo-Json -Depth 3");
                return ReturnResult.ok(parseRows(output, SystemManageController::windowsFirewallRule));
            }
            String output = execCommand("iptables", "-L", "-n", "--line-numbers");
            List<FirewallRuleRowVO> rules = new ArrayList<>();
            String currentChain = "";
            for (String line : output.split("\\r?\\n")) {
                if (line.startsWith("Chain")) {
                    currentChain = line.split("\\s+")[1];
                    continue;
                }
                if (!line.matches("^\\d+.*")) {
                    continue;
                }
                String[] parts = line.trim().split("\\s+", 7);
                FirewallRuleRowVO vo = new FirewallRuleRowVO();
                vo.setChain(currentChain);
                vo.setNum((int) parseLong(parts[0]));
                vo.setTarget(part(parts, 1));
                vo.setProtocol(part(parts, 2));
                vo.setSource(part(parts, 4));
                vo.setDestination(part(parts, 5));
                vo.setInfo(part(parts, 6));
                rules.add(vo);
            }
            return ReturnResult.ok(rules);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<List<FirewallRuleRowVO>>error("获取防火墙规则失败: " + e.getMessage())));
    }

    /**
     * PowerShell Get-NetFirewallRule → 规则视图。
     *
     * @param node 单条 JSON 节点
     * @return 规则视图
     */
    private static FirewallRuleRowVO windowsFirewallRule(JsonNode node) {
        FirewallRuleRowVO vo = new FirewallRuleRowVO();
        vo.setName(text(node, "DisplayName"));
        vo.setChain(vo.getName());
        vo.setDirection(text(node, "Direction"));
        vo.setAction(text(node, "Action"));
        vo.setEnabled(text(node, "Enabled"));
        vo.setProfile(text(node, "Profile"));
        return vo;
    }

    // ==================== 驱动管理 ====================

    /**
     * 获取驱动列表（本机端点，台账内无对应通道配置）。
     *
     * @return 驱动列表
     */
    @Operation(summary = "获取驱动列表")
    @GetMapping("/drivers")
    public Mono<ReturnResult<List<DriverRowVO>>> listDrivers() {
        return Mono.fromCallable(() -> {
            if (IS_WINDOWS) {
                String output = execCommand("powershell", "-Command",
                        "Get-WindowsDriver -Online | Select-Object DriverFileName, OriginalFileName, ProviderName, "
                        + "DriverVersion, DriverDate, ClassName, BootCritical | ConvertTo-Json -Depth 3");
                return ReturnResult.ok(parseRows(output, SystemManageController::windowsDriver));
            }
            String output = execCommand("lsmod");
            List<DriverRowVO> drivers = new ArrayList<>();
            String[] lines = output.split("\\r?\\n");
            for (int i = 1; i < lines.length; i++) {
                String[] parts = lines[i].trim().split("\\s+", 4);
                if (parts.length < 3) {
                    continue;
                }
                DriverRowVO vo = new DriverRowVO();
                vo.setName(parts[0]);
                vo.setSize(parseLong(parts[1]));
                vo.setUsedBy((int) parseLong(parts[2]));
                vo.setDependsOn(part(parts, 3));
                drivers.add(vo);
            }
            return ReturnResult.ok(drivers);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<List<DriverRowVO>>error("获取驱动列表失败: " + e.getMessage())));
    }

    /**
     * PowerShell Get-WindowsDriver → 驱动视图。
     *
     * @param node 单条 JSON 节点
     * @return 驱动视图
     */
    private static DriverRowVO windowsDriver(JsonNode node) {
        DriverRowVO vo = new DriverRowVO();
        vo.setName(text(node, "DriverFileName"));
        vo.setFileName(text(node, "OriginalFileName"));
        vo.setProvider(text(node, "ProviderName"));
        vo.setVersion(text(node, "DriverVersion"));
        vo.setDate(text(node, "DriverDate"));
        vo.setClassName(text(node, "ClassName"));
        vo.setBootCritical("true".equalsIgnoreCase(text(node, "BootCritical")));
        return vo;
    }

    // ==================== 文件管理 ====================

    /**
     * 列出目录下的文件条目。
     *
     * @param hostId 主机 ID，为空表示管控机本机
     * @param path   目录路径，为空按根目录处理
     * @return 文件条目列表
     */
    @Operation(summary = "列出目录文件")
    @GetMapping("/files/list")
    public Mono<ReturnResult<List<FileEntryVO>>> listFiles(@RequestParam(required = false) Long hostId,
                                                           @RequestParam(required = false) String path) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            return ReturnResult.ok(ch.provider().files(ch.host(), path == null || path.isBlank() ? "/" : path));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<List<FileEntryVO>>error("列出文件失败: " + e.getMessage())));
    }

    /**
     * 在指定工作目录执行文件管理命令（新建/重命名/删除/解压等）。
     *
     * @param request 命令请求体
     * @return 命令输出
     */
    @Operation(summary = "文件管理命令执行")
    @PostMapping("/files/exec")
    public Mono<ReturnResult<String>> filesExec(@RequestBody FileExecRequest request) {
        return Mono.fromCallable(() -> {
            String cmd = trim(request.cmd());
            if (cmd.isEmpty()) {
                return ReturnResult.<String>error("缺少命令 cmd");
            }
            HostChannel ch = channelOf(request.hostId());
            String out = ch.provider().exec(ch.host(), withCwd(trim(request.cwd()), cmd, isWindowsTarget(ch)));
            return ReturnResult.ok(out == null ? "" : out);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<String>error("命令执行失败: " + e.getMessage())));
    }

    /**
     * 上传文件到主机。
     *
     * @param hostId 主机 ID，为空表示管控机本机
     * @param path   远端目标路径（含文件名）
     * @param file   上传的文件
     * @return 操作结果
     */
    @Operation(summary = "上传文件")
    @PostMapping("/files/upload")
    public Mono<ReturnResult<String>> uploadFile(@RequestParam(required = false) Long hostId,
                                                 @RequestParam String path,
                                                 @RequestParam("file") MultipartFile file) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            boolean done = ch.provider().uploadFile(ch.host(), path, file.getInputStream(), file.getSize());
            return done
                    ? ReturnResult.ok((hostId == null ? "已写入本机: " : "已上传: ") + path)
                    : ReturnResult.<String>error("上传失败: " + path);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<String>error("上传失败: " + e.getMessage())));
    }

    /**
     * 下载主机文件。
     *
     * @param hostId   主机 ID，为空表示管控机本机
     * @param path     远端文件路径
     * @param response 响应
     */
    @Operation(summary = "下载文件")
    @GetMapping("/files/download")
    public void downloadFile(@RequestParam(required = false) Long hostId,
                             @RequestParam String path,
                             HttpServletResponse response) {
        try (InputStream in = openDownload(hostId, path)) {
            if (in == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            String name = new File(path.replace('/', File.separatorChar)).getName();
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name.replace("\"", "") + "\"");
            in.transferTo(response.getOutputStream());
            response.flushBuffer();
        } catch (Exception e) {
            log.warn("下载文件失败 host={} path={}", hostId, path, e);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    /**
     * 打开下载流。
     *
     * @param hostId 主机 ID，为空表示管控机本机
     * @param path   文件路径
     * @return 输入流，取不到返回 null
     */
    private InputStream openDownload(Long hostId, String path) {
        HostChannel ch = channelOf(hostId);
        return ch.provider().downloadFile(ch.host(), path);
    }

    /**
     * 重启进程：读取原命令行后杀掉，再以原命令、原工作目录后台拉起。
     *
     * @param hostId 主机 ID，为空表示管控机本机
     * @param pid    进程 ID
     * @return 操作结果
     */
    @Operation(summary = "重启进程")
    @PostMapping("/process/restart")
    public Mono<ReturnResult<String>> restartProcess(@RequestParam(required = false) Long hostId,
                                                     @RequestParam int pid) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            if (isWindowsTarget(ch)) {
                return ReturnResult.<String>error("Windows 主机暂不支持按 PID 重启进程，请在服务列表中重启对应服务");
            }
            String command = "A=$(tr '\\0' ' ' < /proc/" + pid + "/cmdline); "
                    + "D=$(readlink /proc/" + pid + "/cwd); "
                    + "kill -9 " + pid + " 2>/dev/null; sleep 1; "
                    + "( cd \"$D\" && nohup $A >/dev/null 2>&1 & )";
            String out = ch.provider().exec(ch.host(), command);
            return ReturnResult.ok("进程 " + pid + " 重启指令已下发" + (out == null || out.isBlank() ? "" : ": " + out.trim()));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<String>error("重启进程失败: " + e.getMessage())));
    }

    // ==================== 工具方法 ====================

    /**
     * 执行本机命令并返回输出（仅只读的机器级端点使用）。
     *
     * @param cmd 命令及参数
     * @return 命令输出，失败返回空串
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
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            process.waitFor();
            return output.toString().trim();
        } catch (Exception e) {
            log.error("执行命令失败: {}", String.join(" ", cmd), e);
            return "";
        }
    }

    /**
     * 把命令输出的 JSON 解析成结构化行；PowerShell 只有一行时不带方括号，单对象也按一行处理。
     *
     * @param json   JSON 文本
     * @param mapper 节点 → 行视图
     * @param <R>    行类型
     * @return 行列表，解析失败为空列表
     */
    private static <R> List<R> parseRows(String json, Function<JsonNode, R> mapper) {
        List<R> rows = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return rows;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    rows.add(mapper.apply(node));
                }
            } else if (root.isObject()) {
                rows.add(mapper.apply(root));
            }
        } catch (Exception e) {
            log.warn("解析命令输出 JSON 失败: {}", e.getMessage());
        }
        return rows;
    }

    /**
     * 取文本字段；PowerShell 把枚举序列化成对象时取其 DisplayValue。
     *
     * @param node  JSON 节点
     * @param field 字段名
     * @return 文本，取不到返回空串
     */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isObject()) {
            JsonNode display = value.get("DisplayValue");
            return display == null ? value.toString() : display.asText();
        }
        return value.asText();
    }

    /**
     * 取数值字段。
     *
     * @param node  JSON 节点
     * @param field 字段名
     * @return 数值，取不到返回 0
     */
    private static long number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isNumber() ? 0L : value.asLong();
    }

    /**
     * 安全取数组片段。
     *
     * @param parts 片段数组
     * @param index 下标
     * @return 值，越界返回空串
     */
    private static String part(String[] parts, int index) {
        return parts.length > index ? parts[index] : "";
    }

    /**
     * 宽松 long 解析。
     *
     * @param text 数字字符串
     * @return 解析结果，非法时返回 0
     */
    private static long parseLong(String text) {
        try {
            return text == null ? 0L : Long.parseLong(text.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 宽松 double 解析。
     *
     * @param text 数字字符串
     * @return 解析结果，非法时返回 0
     */
    private static double parseDouble(String text) {
        try {
            return text == null ? 0.0 : Double.parseDouble(text.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }
}
