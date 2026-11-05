package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.enums.ServiceAction;
import com.chua.starter.monitor.support.model.DiskPartitionRowVO;
import com.chua.starter.monitor.support.model.DiskRowVO;
import com.chua.starter.monitor.support.model.DriverRowVO;
import com.chua.starter.monitor.support.model.EnvVarVO;
import com.chua.starter.monitor.support.model.EnvVarInputVO;
import com.chua.starter.monitor.support.model.FileEntryVO;
import com.chua.starter.monitor.support.model.FirewallRuleInputVO;
import com.chua.starter.monitor.support.model.FirewallRuleRowVO;
import com.chua.starter.monitor.support.model.HostActionVO;
import com.chua.starter.monitor.support.model.HostInfoVO;
import com.chua.starter.monitor.support.model.NetProcessRowVO;
import com.chua.starter.monitor.support.model.NetworkConfigVO;
import com.chua.starter.monitor.support.model.NetworkInterfaceRowVO;
import com.chua.starter.monitor.support.model.ProcessRowVO;
import com.chua.starter.monitor.support.model.ProcessThreadRowVO;
import com.chua.starter.monitor.support.model.ServiceRowVO;
import com.chua.starter.monitor.support.model.SystemListVO;
import com.chua.starter.datasourcehub.support.spi.Platforms;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.monitor.support.spi.SystemInfoProviderRouter;
import com.chua.starter.monitor.support.support.ShellSupport;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.enums.HostCollectType;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
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

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 系统管理 —— 进程/服务/网络/磁盘/防火墙/驱动 管理端点。
 *
 * <p>本类不接触任何采集与命令实现：主机（含 hostId 为空的管控机本机）统一由
 * {@link SystemInfoProviderRouter} 选出 {@link SystemInfoProvider} 通道
 * （LOCAL / AGENT / SSH / WINRM），采集与动作都调通道能力方法，出入口一律是强类型视图。
 * 平台命令文本（taskkill、systemctl、nmcli、PowerShell 脚本）只存在于
 * {@link com.chua.starter.monitor.support.spi.HostOperations} 的平台子类里。</p>
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
     * 管控机自身平台标识
     *
     * <p>本机描述（{@link #localHost()}）也要带平台：通道与平台能力集都按它路由。
     * 以前这里写成 {@code IS_WINDOWS ? WINDOWS : LINUX} 的三元判断，macOS /
     * BSD 全被当成 Linux；{@code Platforms.normalize} 按 os.name 归一，
     * 新增系统只要归一逻辑认识它，本方法一行都不用改。</p>
     */
    private static final String LOCAL_PLATFORM = Platforms.normalize(System.getProperty("os.name"));
    /**
     * 关键词检索时的扫描窗口：必须远大于单页条数，否则过滤发生在截断之后，命中项只要排在窗口外就会返回空列表
     */
    private static final int KEYWORD_SCAN = 10000;

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
        host.setServerHostCollectMode(HostCollectType.LOCAL.getCode());
        host.setServerHostOsType(LOCAL_PLATFORM);
        return host;
    }

    /**
     * 动作结果 → 对外响应体。
     *
     * <p>成功与否由通道回查决定，本类不凭「命令有回显」推断；新进程号只拼进回显文本，
     * 保持既有接口的字符串载荷契约不变。</p>
     *
     * @param action 动作结果
     * @return 响应体
     */
    private static ReturnResult<String> toResult(HostActionVO action) {
        String message = action.message() + (action.newPid() == null ? "" : "，新 PID " + action.newPid());
        return action.success() ? ReturnResult.ok(message) : ReturnResult.<String>error(message);
    }

    // ==================== 主机基础信息 ====================

    /**
     * 获取主机信息。
     *
     * @param hostId 主机 ID，为空表示管控机本机
     * @param refresh 是否强制刷新缓存
     * @return 主机信息
     */
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

    /**
     * 获取环境变量。
     *
     * @param hostId 主机 ID，为空表示管控机本机
     * @return 环境变量列表
     */
    @Operation(summary = "获取环境变量")
    @GetMapping("/env")
    public Mono<ReturnResult<List<EnvVarVO>>> envVars(@RequestParam(required = false) Long hostId) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            return ReturnResult.ok(ch.provider().env(ch.host()));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<List<EnvVarVO>>error("获取环境变量失败: " + e.getMessage())));
    }

    /**
     * 设置系统级环境变量。
     *
     * @param hostId 主机 ID，为空表示管控机本机
     * @param body   变量名与值
     * @return 操作结果
     */
    @Operation(summary = "设置环境变量")
    @PostMapping("/env/set")
    public Mono<ReturnResult<String>> setEnvVar(@RequestParam(required = false) Long hostId,
                                                @RequestBody EnvVarInputVO body) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            return toResult(ch.provider().setEnvVar(ch.host(), body.name(), body.value()));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<String>error("设置环境变量失败: " + e.getMessage())));
    }

    /**
     * 删除系统级环境变量。
     *
     * @param hostId 主机 ID，为空表示管控机本机
     * @param name   变量名
     * @return 操作结果
     */
    @Operation(summary = "删除环境变量")
    @PostMapping("/env/delete")
    public Mono<ReturnResult<String>> deleteEnvVar(@RequestParam(required = false) Long hostId,
                                                   @RequestParam String name) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            return toResult(ch.provider().deleteEnvVar(ch.host(), name));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<String>error("删除环境变量失败: " + e.getMessage())));
    }

    // ==================== 进程管理 ====================

    /**
     * 获取进程列表。
     *
     * @param keyword   搜索关键词（进程名/命令行），空表示不过滤
     * @param sortBy    排序字段：cpu/memory/pid/name，默认 cpu
     * @param sortOrder 排序方向：asc/desc，默认 desc
     * @param limit     返回条数，默认 50；传 page/pageSize 时以 pageSize 为准
     * @param page      页码
     * @param pageSize  每页条数
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
            boolean filtered = keyword != null && !keyword.isBlank();
            List<ProcessRowVO> processes = new ArrayList<>(ch.provider().processes(ch.host(), 1, filtered ? KEYWORD_SCAN : want));
            if (filtered) {
                String kw = keyword.toLowerCase(Locale.ROOT);
                processes = processes.stream()
                        .filter(p -> containsKw(p.name(), kw) || containsKw(p.command(), kw))
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
            case "memory" -> Comparator.comparingDouble(ProcessRowVO::memory);
            case "pid" -> Comparator.comparingLong(ProcessRowVO::pid);
            case "name" -> Comparator.comparing(p -> p.name() == null ? "" : p.name());
            default -> Comparator.comparingDouble(ProcessRowVO::cpu);
        };
        return "desc".equalsIgnoreCase(sortOrder) ? comparator.reversed() : comparator;
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
            return toResult(ch.provider().killProcess(ch.host(), pid, null));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<String>error("关闭进程失败: " + e.getMessage())));
    }

    /**
     * 获取进程的子进程。
     *
     * @param pid    进程 ID
     * @param hostId 主机 ID，为空表示管控机本机
     * @return 子进程列表
     */
    @Operation(summary = "获取进程的子进程")
    @GetMapping("/process/{pid}/children")
    public Mono<ReturnResult<List<ProcessRowVO>>> listProcessChildren(@PathVariable int pid,
                                                                     @RequestParam(required = false) Long hostId) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            List<ProcessRowVO> children = ch.provider().processes(ch.host(), 1, KEYWORD_SCAN).stream()
                    .filter(p -> p.ppid() == pid)
                    .collect(Collectors.toList());
            return ReturnResult.ok(children);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<List<ProcessRowVO>>error("获取子进程失败: " + e.getMessage())));
    }

    /**
     * 获取进程的线程列表。
     *
     * @param pid    进程 ID
     * @param hostId 主机 ID，为空表示管控机本机
     * @return 线程列表
     */
    @Operation(summary = "获取进程的线程列表")
    @GetMapping("/process/{pid}/threads")
    public Mono<ReturnResult<List<ProcessThreadRowVO>>> listProcessThreads(@PathVariable int pid,
                                                                          @RequestParam(required = false) Long hostId) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            return ReturnResult.ok(ch.provider().processThreads(ch.host(), pid));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<List<ProcessThreadRowVO>>error("获取线程列表失败: " + e.getMessage())));
    }

    /**
     * 重启进程：读取原命令行后杀掉，再以原命令、原工作目录拉起。
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
            return toResult(ch.provider().restartProcess(ch.host(), pid));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<String>error("重启进程失败: " + e.getMessage())));
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
            boolean filtered = keyword != null && !keyword.isBlank();
            List<ServiceRowVO> services = new ArrayList<>(ch.provider().services(ch.host(), 1, filtered ? KEYWORD_SCAN : want));
            if (filtered) {
                String kw = keyword.toLowerCase(Locale.ROOT);
                services = services.stream()
                        .filter(s -> containsKw(s.name(), kw) || containsKw(s.displayName(), kw))
                        .collect(Collectors.toCollection(ArrayList::new));
            }
            int total = services.size();
            return ReturnResult.ok(SystemListVO.of(slice(services, page, pageSize == null ? 0 : pageSize), total));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<SystemListVO<ServiceRowVO>>error("获取服务列表失败: " + e.getMessage())));
    }

    /**
     * 对主机上的服务执行动作。
     *
     * @param hostId 主机 ID，为空表示管控机本机
     * @param name   服务名称
     * @param action 动作
     * @return 操作结果
     */
    private Mono<ReturnResult<String>> controlService(Long hostId, String name, ServiceAction action) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            return toResult(ch.provider().controlService(ch.host(), name, action));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<String>error("服务操作失败: " + e.getMessage())));
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
        return controlService(hostId, name, ServiceAction.START);
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
        return controlService(hostId, name, ServiceAction.STOP);
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
        return controlService(hostId, name, ServiceAction.RESTART);
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
                    .filter(c -> c.state() != null && c.state().toUpperCase(Locale.ROOT).contains(s))
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
    /**
     * Request Param
     * @param hostId 长整数
     * @return 返回值
    */
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
            if (pid <= 0) {
                return ReturnResult.<String>error("该连接无归属进程（PID " + pid + "），无法关闭");
            }
            HostChannel ch = channelOf(hostId);
            return toResult(ch.provider().killProcess(ch.host(), pid, port));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<String>error("关闭失败: " + e.getMessage())));
    }

    /**
     * 应用网络配置（IP / 网关 / DNS）。
     *
     * @param hostId 主机 ID，为空表示管控机本机
     * @param config 配置项
     * @return 操作结果
     */
    @Operation(summary = "应用网络配置")
    @PostMapping("/network/config")
    public Mono<ReturnResult<String>> applyNetworkConfig(@RequestParam(required = false) Long hostId,
                                                         @RequestBody NetworkConfigVO config) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            return toResult(ch.provider().applyNetworkConfig(ch.host(), config));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<String>error("应用网络配置失败: " + e.getMessage())));
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
     * 获取磁盘分区列表。
     *
     * @param hostId 主机 ID，为空表示管控机本机
     * @return 磁盘分区列表
     */
    @Operation(summary = "获取磁盘分区列表")
    @GetMapping("/partitions")
    public Mono<ReturnResult<List<DiskPartitionRowVO>>> listPartitions(@RequestParam(required = false) Long hostId) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            return ReturnResult.ok(ch.provider().diskPartitions(ch.host()));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<List<DiskPartitionRowVO>>error("获取磁盘分区失败: " + e.getMessage())));
    }

    // ==================== 防火墙管理 ====================

    /**
     * 获取防火墙规则列表。
     *
     * <p>命令与解析在平台实现里；采集失败与「确实没有规则」必须区分，不能把失败解析成空列表。</p>
     *
     * @param hostId 主机 ID，为空表示管控机本机
     * @return 防火墙规则列表
     */
    @Operation(summary = "获取防火墙规则列表")
    @GetMapping("/firewall/rules")
    public Mono<ReturnResult<List<FirewallRuleRowVO>>> listFirewallRules(@RequestParam(required = false) Long hostId) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            return ReturnResult.ok(ch.provider().firewallRules(ch.host()));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<List<FirewallRuleRowVO>>error("获取防火墙规则失败: " + e.getMessage())));
    }

    /**
     * 新增防火墙规则。
     *
     * @param hostId 主机 ID，为空表示管控机本机
     * @param rule   规则入参
     * @return 操作结果
     */
    @Operation(summary = "新增防火墙规则")
    @PostMapping("/firewall/add")
    public Mono<ReturnResult<String>> addFirewallRule(@RequestParam(required = false) Long hostId,
                                                      @RequestBody FirewallRuleInputVO rule) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            return toResult(ch.provider().addFirewallRule(ch.host(), rule));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<String>error("新增防火墙规则失败: " + e.getMessage())));
    }

    /**
     * 删除防火墙规则。
     *
     * @param hostId 主机 ID，为空表示管控机本机
     * @param name   规则名（Windows）
     * @param chain  链名（Linux）
     * @param num    规则序号（Linux）
     * @return 操作结果
     */
    @Operation(summary = "删除防火墙规则")
    @PostMapping("/firewall/delete")
    public Mono<ReturnResult<String>> deleteFirewallRule(@RequestParam(required = false) Long hostId,
                                                        @RequestParam(required = false) String name,
                                                        @RequestParam(required = false) String chain,
                                                        @RequestParam(required = false) Integer num) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            return toResult(ch.provider().deleteFirewallRule(ch.host(), name, chain, num));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<String>error("删除防火墙规则失败: " + e.getMessage())));
    }

    // ==================== 驱动管理 ====================

    /**
     * 获取驱动列表。
     *
     * @param hostId 主机 ID，为空表示管控机本机
     * @return 驱动列表
     */
    @Operation(summary = "获取驱动列表")
    @GetMapping("/drivers")
    public Mono<ReturnResult<List<DriverRowVO>>> listDrivers(@RequestParam(required = false) Long hostId) {
        return Mono.fromCallable(() -> {
            HostChannel ch = channelOf(hostId);
            return ReturnResult.ok(ch.provider().drivers(ch.host()));
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> Mono.just(ReturnResult.<List<DriverRowVO>>error("获取驱动列表失败: " + e.getMessage())));
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
     * <p>本端点的命令由调用方给出，因此按目标通道方言植入工作目录、退出码与完成三类探针，
     * 不替调用方解释命令内容。</p>
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
            ShellSupport.Probe probe = ShellSupport.compose(trim(request.cwd()), cmd,
                    ch.provider().shellDialect(ch.host()), true);
            ShellSupport.ProbeResult result = ShellSupport.interpret(ch.provider().exec(ch.host(), probe.line()), probe);
            if (result.failed()) {
                return ReturnResult.<String>error("命令执行失败" + detail(result));
            }
            return ReturnResult.ok(result.output());
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

    // ==================== 工具方法 ====================

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
     * 探针结果 → 失败说明后缀。
     *
     * @param result 探针还原结果
     * @return 形如 {@code : 原因}，无内容时返回空串
     */
    private static String detail(ShellSupport.ProbeResult result) {
        String reason = result.reason();
        String output = result.output() == null ? "" : result.output();
        String text = reason == null ? output : (output.isEmpty() ? reason : reason + " | " + output);
        return text.isBlank() ? "" : ": " + text.trim();
    }
}
