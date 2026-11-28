package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.model.DiskPartitionRowVO;
import com.chua.starter.server.support.model.DiskRowVO;
import com.chua.starter.server.support.model.DriverRowVO;
import com.chua.starter.server.support.model.EnvVarInputVO;
import com.chua.starter.server.support.model.EnvVarVO;
import com.chua.starter.server.support.model.FileEntryVO;
import com.chua.starter.server.support.model.FirewallRuleInputVO;
import com.chua.starter.server.support.model.FirewallRuleRowVO;
import com.chua.starter.server.support.model.HostInfoVO;
import com.chua.starter.server.support.model.NetProcessRowVO;
import com.chua.starter.server.support.model.NetworkConfigVO;
import com.chua.starter.server.support.model.NetworkInterfaceRowVO;
import com.chua.starter.server.support.model.ProcessRowVO;
import com.chua.starter.server.support.model.ProcessThreadRowVO;
import com.chua.starter.server.support.model.ServiceRowVO;
import com.chua.starter.server.support.model.SystemListVO;
import com.chua.starter.server.support.model.UploadResultVO;
import com.chua.starter.server.support.service.impl.HostRuntimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 系统管理 REST 入口；主机采集、命令与结果判定统一委托 {@link HostRuntimeService}。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "监控-系统管理", description = "进程/服务/网络/磁盘/防火墙/驱动 管理")
@RestController
@RequestMapping("/ms/system")
@RequiredArgsConstructor
public class SystemManageController {

    private final HostRuntimeService runtimeService;

    /** 获取主机信息。 */
    @Operation(summary = "获取主机信息")
    @GetMapping("/host-info")
    public Mono<ReturnResult<HostInfoVO>> hostInfo(@RequestParam(required = false) Long hostId,
                                                   @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        return runtimeService.hostInfo(hostId, refresh);
    }

    /** 获取环境变量。 */
    @Operation(summary = "获取环境变量")
    @GetMapping("/env")
    public Mono<ReturnResult<List<EnvVarVO>>> envVars(@RequestParam(required = false) Long hostId) {
        return runtimeService.envVars(hostId);
    }

    /** 设置系统级环境变量。 */
    @Operation(summary = "设置环境变量")
    @PostMapping("/env/set")
    public Mono<ReturnResult<String>> setEnvVar(@RequestParam(required = false) Long hostId,
                                                @RequestBody EnvVarInputVO body) {
        return runtimeService.setEnvVar(hostId, body);
    }

    /** 删除系统级环境变量。 */
    @Operation(summary = "删除环境变量")
    @PostMapping("/env/delete")
    public Mono<ReturnResult<String>> deleteEnvVar(@RequestParam(required = false) Long hostId,
                                                   @RequestParam String name) {
        return runtimeService.deleteEnvVar(hostId, name);
    }

    /** 获取进程列表。 */
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
        return runtimeService.listProcesses(keyword, sortBy, sortOrder, limit, page, pageSize, hostId);
    }

    /** 关闭进程。 */
    @Operation(summary = "关闭进程")
    @PostMapping("/process/{pid}/kill")
    public Mono<ReturnResult<String>> killProcess(@PathVariable int pid,
                                                  @RequestParam(required = false) Long hostId) {
        return runtimeService.killProcess(pid, hostId);
    }

    /** 获取子进程。 */
    @Operation(summary = "获取进程的子进程")
    @GetMapping("/process/{pid}/children")
    public Mono<ReturnResult<List<ProcessRowVO>>> listProcessChildren(@PathVariable int pid,
                                                                     @RequestParam(required = false) Long hostId) {
        return runtimeService.listProcessChildren(pid, hostId);
    }

    /** 获取线程列表。 */
    @Operation(summary = "获取进程的线程列表")
    @GetMapping("/process/{pid}/threads")
    public Mono<ReturnResult<List<ProcessThreadRowVO>>> listProcessThreads(@PathVariable int pid,
                                                                          @RequestParam(required = false) Long hostId) {
        return runtimeService.listProcessThreads(pid, hostId);
    }

    /** 获取进程环境变量。 */
    @Operation(summary = "获取进程的环境变量", description = "POSIX 读 /proc/<pid>/environ；Windows 无调试权限时返回空表")
    @GetMapping("/process/{pid}/env")
    public Mono<ReturnResult<List<EnvVarVO>>> processEnv(@PathVariable int pid,
                                                         @RequestParam(required = false) Long hostId) {
        return runtimeService.processEnv(pid, hostId);
    }

    /** 获取进程工作目录。 */
    @Operation(summary = "获取进程的工作目录")
    @GetMapping("/process/{pid}/cwd")
    public Mono<ReturnResult<String>> processCwd(@PathVariable int pid,
                                                 @RequestParam(required = false) Long hostId) {
        return runtimeService.processCwd(pid, hostId);
    }

    /** 重启进程。 */
    @Operation(summary = "重启进程")
    @PostMapping("/process/restart")
    public Mono<ReturnResult<String>> restartProcess(@RequestParam(required = false) Long hostId,
                                                     @RequestParam int pid) {
        return runtimeService.restartProcess(hostId, pid);
    }

    /** 获取系统服务列表。 */
    @Operation(summary = "获取系统服务列表")
    @GetMapping("/services")
    public Mono<ReturnResult<SystemListVO<ServiceRowVO>>> listServices(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long hostId) {
        return runtimeService.listServices(keyword, page, pageSize, hostId);
    }

    /** 启动服务。 */
    @Operation(summary = "启动服务")
    @PostMapping("/service/{name}/start")
    public Mono<ReturnResult<String>> startService(@PathVariable String name,
                                                   @RequestParam(required = false) Long hostId) {
        return runtimeService.startService(name, hostId);
    }

    /** 停止服务。 */
    @Operation(summary = "停止服务")
    @PostMapping("/service/{name}/stop")
    public Mono<ReturnResult<String>> stopService(@PathVariable String name,
                                                  @RequestParam(required = false) Long hostId) {
        return runtimeService.stopService(name, hostId);
    }

    /** 重启服务。 */
    @Operation(summary = "重启服务")
    @PostMapping("/service/{name}/restart")
    public Mono<ReturnResult<String>> restartService(@PathVariable String name,
                                                     @RequestParam(required = false) Long hostId) {
        return runtimeService.restartService(name, hostId);
    }

    /** 获取网络接口。 */
    @Operation(summary = "获取网络接口列表")
    @GetMapping("/network/interfaces")
    public Mono<ReturnResult<List<NetworkInterfaceRowVO>>> listNetworkInterfaces(
            @RequestParam(required = false) Long hostId) {
        return runtimeService.listNetworkInterfaces(hostId);
    }

    /** 获取网络连接。 */
    @Operation(summary = "获取网络连接列表")
    @GetMapping("/network/connections")
    public Mono<ReturnResult<List<NetProcessRowVO>>> listNetworkConnections(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) Long hostId) {
        return runtimeService.listNetworkConnections(state, hostId);
    }

    /** 获取网络监听与占用进程。 */
    @Operation(summary = "获取网络监听与占用进程")
    @GetMapping("/netproc")
    public Mono<ReturnResult<List<NetProcessRowVO>>> listNetProcesses(@RequestParam(required = false) Long hostId) {
        return runtimeService.listNetProcesses(hostId);
    }

    /** 关闭占用端口的进程。 */
    @Operation(summary = "关闭占用端口的进程")
    @PostMapping("/netproc/close")
    public Mono<ReturnResult<String>> closeNetProcess(@RequestParam(required = false) Long hostId,
                                                      @RequestParam int pid,
                                                      @RequestParam(required = false) Integer port) {
        return runtimeService.closeNetProcess(hostId, pid, port);
    }

    /** 应用网络配置。 */
    @Operation(summary = "应用网络配置")
    @PostMapping("/network/config")
    public Mono<ReturnResult<String>> applyNetworkConfig(@RequestParam(required = false) Long hostId,
                                                         @RequestBody NetworkConfigVO config) {
        return runtimeService.applyNetworkConfig(hostId, config);
    }

    /** 获取磁盘列表。 */
    @Operation(summary = "获取磁盘列表")
    @GetMapping("/disks")
    public Mono<ReturnResult<List<DiskRowVO>>> listDisks(@RequestParam(required = false) Long hostId) {
        return runtimeService.listDisks(hostId);
    }

    /** 获取磁盘分区。 */
    @Operation(summary = "获取磁盘分区列表")
    @GetMapping("/partitions")
    public Mono<ReturnResult<List<DiskPartitionRowVO>>> listPartitions(@RequestParam(required = false) Long hostId) {
        return runtimeService.listPartitions(hostId);
    }

    /** 获取防火墙规则。 */
    @Operation(summary = "获取防火墙规则列表")
    @GetMapping("/firewall/rules")
    public Mono<ReturnResult<List<FirewallRuleRowVO>>> listFirewallRules(@RequestParam(required = false) Long hostId) {
        return runtimeService.listFirewallRules(hostId);
    }

    /** 新增防火墙规则。 */
    @Operation(summary = "新增防火墙规则")
    @PostMapping("/firewall/add")
    public Mono<ReturnResult<String>> addFirewallRule(@RequestParam(required = false) Long hostId,
                                                      @RequestBody FirewallRuleInputVO rule) {
        return runtimeService.addFirewallRule(hostId, rule);
    }

    /** 删除防火墙规则。 */
    @Operation(summary = "删除防火墙规则")
    @PostMapping("/firewall/delete")
    public Mono<ReturnResult<String>> deleteFirewallRule(@RequestParam(required = false) Long hostId,
                                                        @RequestParam(required = false) String name,
                                                        @RequestParam(required = false) String chain,
                                                        @RequestParam(required = false) Integer num) {
        return runtimeService.deleteFirewallRule(hostId, name, chain, num);
    }

    /** 获取驱动列表。 */
    @Operation(summary = "获取驱动列表")
    @GetMapping("/drivers")
    public Mono<ReturnResult<List<DriverRowVO>>> listDrivers(@RequestParam(required = false) Long hostId) {
        return runtimeService.listDrivers(hostId);
    }

    /** 列出目录文件。 */
    @Operation(summary = "列出目录文件")
    @GetMapping("/files/list")
    public Mono<ReturnResult<List<FileEntryVO>>> listFiles(@RequestParam(required = false) Long hostId,
                                                          @RequestParam(required = false) String path) {
        return runtimeService.listFiles(hostId, path);
    }

    /** 执行文件管理命令。 */
    @Operation(summary = "文件管理命令执行")
    @PostMapping("/files/exec")
    public Mono<ReturnResult<String>> filesExec(@RequestBody FileExecRequest request) {
        return runtimeService.filesExec(request);
    }

    /** 上传文件。 */
    @Operation(summary = "上传文件")
    @PostMapping("/files/upload")
    public Mono<ReturnResult<UploadResultVO>> uploadFile(@RequestParam(required = false) Long hostId,
                                                         @RequestParam String path,
                                                         @RequestParam(required = false) Long sourceLastModified,
                                                         @RequestParam(required = false, defaultValue = "false") boolean force,
                                                         @RequestParam("file") MultipartFile file) {
        return runtimeService.uploadFile(hostId, path, sourceLastModified, force, file);
    }

    /** 下载主机文件。 */
    @Operation(summary = "下载文件")
    @GetMapping("/files/download")
    public void downloadFile(@RequestParam(required = false) Long hostId,
                             @RequestParam String path,
                             HttpServletResponse response) {
        runtimeService.downloadFile(hostId, path, response);
    }
}
