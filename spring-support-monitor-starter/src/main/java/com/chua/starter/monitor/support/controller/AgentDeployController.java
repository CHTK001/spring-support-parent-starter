package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.service.impl.MonitorPushService;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.spi.HostRemoteProvider;
import com.chua.starter.server.support.spi.HostRemoteProviderRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 自动部署控制器
 * <p>
 * 通过 SSH/SFTP（Linux）或 WinRM（Windows）远程上传 agent.jar + agent.yml 并启动，
 * 每步通过 socket 推送实时安装进度到前端。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Tag(name = "Agent自动部署", description = "远程安装/重装/升级 Monitor Agent")
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/host/agent")
@RequiredArgsConstructor
public class AgentDeployController {

    private final MsServerHostMapper hostMapper;
    private final ServerHostService serverHostService;
    private final HostRemoteProviderRegistry remoteRegistry;
    private final MonitorPushService pushService;

    /** 部署进度事件名（前端 subscribe("monitor","agent-deploy") 接收） */
    private static final String EVENT_DEPLOY = "agent-deploy";

    /**
    * 自动部署 Agent
    * @param id ID，不允许为 null
    * @return Mono 对象
    */
    @Operation(summary = "自动部署 Agent")
    @PostMapping("/{id}/deploy")
    public Mono<ReturnResult<String>> deploy(@PathVariable Long id) {
        return doDeploy(id, "install");
    }

    /**
    * 重装 Agent
    * @param id ID，不允许为 null
    * @return Mono 对象
    */
    @Operation(summary = "重装 Agent")
    @PostMapping("/{id}/reinstall")
    public Mono<ReturnResult<String>> reinstall(@PathVariable Long id) {
        return doDeploy(id, "reinstall");
    }

    /**
    * 升级 Agent
    * @param id ID，不允许为 null
    * @return Mono 对象
    */
    @Operation(summary = "升级 Agent")
    @PostMapping("/{id}/upgrade")
    public Mono<ReturnResult<String>> upgrade(@PathVariable Long id) {
        return doDeploy(id, "upgrade");
    }

    /**
    * 卸载 Agent
    * @param id ID，不允许为 null
    * @return Mono 对象
    */
    @Operation(summary = "卸载 Agent")
    @PostMapping("/{id}/uninstall")
    public Mono<ReturnResult<String>> uninstall(@PathVariable Long id) {
        return doDeploy(id, "uninstall");
    }

    /**
     * doDeploy。
     *
     * @param hostId 主机ID，不允许为 null
     * @param action 方法入参 action
     * @return Mono 对象
     */
    private Mono<ReturnResult<String>> doDeploy(Long hostId, String action) {
        try {
            MsServerHost host = hostMapper.selectById(hostId);
            if (host == null) {
                return Mono.just(ReturnResult.error("主机不存在: " + hostId));
            }
            // 异步执行部署（避免 HTTP 超时）
            new Thread(() -> runDeploy(host, action), "agent-deploy-" + hostId).start();
            return Mono.just(ReturnResult.success("部署任务已启动"));
        } catch (Exception e) {
            return Mono.just(ReturnResult.error("部署启动失败: " + e.getMessage()));
        }
    }

    /**
     * 运行Deploy。
     *
     * @param host 主机，不允许为 null
     * @param action 方法入参 action
     */
    private void runDeploy(MsServerHost host, String action) {
        Long hostId = host.getId();
        String hostName = host.getServerHostName();
        boolean isUninstall = "uninstall".equals(action);
        try {
            push(hostId, 0, isUninstall ? "开始卸载" : action.equals("reinstall") ? "开始重装" : action.equals("upgrade") ? "开始升级" : "开始安装", "info");

            // 1. 解析凭据
            push(hostId, 5, "解析主机凭据...", "info");
            ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, null);
            if (resolved == null || resolved.getData() == null) {
                push(hostId, 0, "未找到主机凭据，请先绑定 SSH/WinRM 凭据", "error");
                return;
            }
            SshCredential cred = resolved.getData();

            // 2. 解析远程 Provider
            push(hostId, 10, "建立远程连接...", "info");
            HostRemoteProvider provider = remoteRegistry.resolve(host).orElse(null);
            if (provider == null) {
                push(hostId, 0, "不支持的操作系统类型: " + host.getServerHostOsType(), "error");
                return;
            }

            // 卸载流程
            if (isUninstall) {
                push(hostId, 30, "停止 monitor-agent 服务...", "info");
                stopAgentService(provider, host, cred);
                push(hostId, 60, "删除 agent 文件...", "info");
                String remoteDir = provider.join(provider.tempDir(), "monitor-agent");
                provider.exec(host, cred, "rm -rf " + remoteDir, 10);
                // 标记未安装
                host.setServerHostAgentInstalled(0);
                hostMapper.updateById(host);
                push(hostId, 100, "Agent 卸载完成", "success");
                log.info("[agent-deploy] hostId={} uninstall 成功", hostId);
                return;
            }

            // 3. 准备远端目录
            String remoteDir = provider.join(provider.tempDir(), "monitor-agent");
            push(hostId, 20, "创建远端目录 " + remoteDir, "info");
            provider.mkdirs(host, cred, remoteDir);

            // 4. 上传 agent.jar
            Path agentJar = Paths.get("monitor-agent", "agent.jar").toAbsolutePath();
            if (!java.nio.file.Files.exists(agentJar)) {
                // 兜底：从项目根找
                agentJar = Paths.get("..", "spring-support-monitor-starter", "monitor-agent", "agent.jar").toAbsolutePath();
            }
            push(hostId, 40, "上传 agent.jar ...", "info");
            provider.upload(host, cred, provider.join(remoteDir, "agent.jar"), agentJar);

            // 5. 生成并上传 agent.yml（含 TCP 长连接配置）
            String serverTcpUrl = "tcp://" + getLocalAddress() + ":19390";
            String agentYml = "server:\n  tcpUrl: " + serverTcpUrl + "\n"
                    + "agent:\n  port: 19876\n  token: default-token\n"
                    + "  agentKey: " + host.getServerHostAgentKey() + "\n";
            Path ymlTmp = java.nio.file.Files.createTempFile("agent", ".yml");
            java.nio.file.Files.writeString(ymlTmp, agentYml);
            push(hostId, 60, "写入 agent.yml（TCP 长连接）", "info");
            provider.upload(host, cred, provider.join(remoteDir, "agent.yml"), ymlTmp);
            java.nio.file.Files.deleteIfExists(ymlTmp);

            // 6. 部署为系统服务并启动
            push(hostId, 80, "部署 monitor-agent 系统服务...", "info");
            installAgentService(provider, host, cred, remoteDir);

            // 7. 标记已安装
            host.setServerHostAgentInstalled(1);
            hostMapper.updateById(host);

            push(hostId, 100, "Agent " + action + " 完成", "success");
            log.info("[agent-deploy] hostId={} {} 成功", hostId, action);
        } catch (Exception e) {
            log.warn("[agent-deploy] hostId={} {} 失败: {}", hostId, action, e.getMessage());
            push(hostId, 0, "部署失败: " + e.getMessage(), "error");
        }
    }

    /**
    * 安装并启动 agent 系统服务（Linux: systemd, Windows: nssm）
    * @param provider 提供者，不允许为 null
    * @param host 主机，不允许为 null
    * @param cred 方法入参 cred
    * @param dir 目录，不允许为 null
    */
    private void installAgentService(HostRemoteProvider provider, MsServerHost host, SshCredential cred, String dir) {
        try {
            if (provider.shellDialect() == com.chua.starter.server.support.spi.HostShellDialect.POWERSHELL) {
                // Windows: 用 nssm 注册为 Windows Service
                String serviceName = "MonitorAgent";
                // 先停止旧服务
                provider.exec(host, cred, "Stop-Service -Name '" + serviceName + "' -ErrorAction SilentlyContinue", 10);
                // 删除旧服务
                provider.exec(host, cred, "sc.exe delete '" + serviceName + "' -ErrorAction SilentlyContinue", 10);
                // 用 nssm 安装服务
                String nssmCmd = "nssm install " + serviceName + " javaw -jar " + dir + "\\agent.jar";
                provider.exec(host, cred, nssmCmd, 15);
                // 设置工作目录
                provider.exec(host, cred, "nssm set " + serviceName + " AppDirectory " + dir, 10);
                // 启动服务
                provider.exec(host, cred, "Start-Service -Name '" + serviceName + "'", 10);
                log.info("[agent-deploy] Windows 服务 {} 安装成功", serviceName);
            } else {
                // Linux: 用 systemd
                String serviceName = "monitor-agent";
                // 先停止旧服务
                provider.exec(host, cred, "systemctl stop " + serviceName + " 2>/dev/null || true", 10);
                // 写 systemd service 文件
                String serviceContent = "[Unit]\n"
                        + "Description=Monitor Agent\n"
                        + "After=network.target\n\n"
                        + "[Service]\n"
                        + "Type=simple\n"
                        + "WorkingDirectory=" + dir + "\n"
                        + "ExecStart=/usr/bin/java -jar " + dir + "/agent.jar\n"
                        + "Restart=always\n"
                        + "RestartSec=5\n\n"
                        + "[Install]\n"
                        + "WantedBy=multi-user.target\n";
                // 写临时文件并上传
                Path serviceTmp = java.nio.file.Files.createTempFile("monitor-agent", ".service");
                java.nio.file.Files.writeString(serviceTmp, serviceContent);
                provider.upload(host, cred, "/etc/systemd/system/" + serviceName + ".service", serviceTmp);
                java.nio.file.Files.deleteIfExists(serviceTmp);
                // reload systemd
                provider.exec(host, cred, "systemctl daemon-reload", 10);
                // enable + start
                provider.exec(host, cred, "systemctl enable " + serviceName, 10);
                provider.exec(host, cred, "systemctl start " + serviceName, 10);
                log.info("[agent-deploy] systemd 服务 {} 安装成功", serviceName);
            }
        } catch (Exception e) {
            log.warn("[agent-deploy] 安装系统服务失败，回退到手动启动: {}", e.getMessage());
            // 回退：手动启动
            try {
                String startCmd = buildStartCmd(provider, dir, host);
                provider.exec(host, cred, startCmd, 30);
            } catch (Exception ex) {
                log.warn("[agent-deploy] 手动启动也失败: {}", ex.getMessage());
            }
        }
    }

    /**
    * 停止并卸载 agent 系统服务
    * @param provider 提供者，不允许为 null
    * @param host 主机，不允许为 null
    * @param cred 方法入参 cred
    */
    private void stopAgentService(HostRemoteProvider provider, MsServerHost host, SshCredential cred) {
        try {
            if (provider.shellDialect() == com.chua.starter.server.support.spi.HostShellDialect.POWERSHELL) {
                String serviceName = "MonitorAgent";
                provider.exec(host, cred, "Stop-Service -Name '" + serviceName + "' -ErrorAction SilentlyContinue", 10);
                provider.exec(host, cred, "sc.exe delete '" + serviceName + "' -ErrorAction SilentlyContinue", 10);
            } else {
                String serviceName = "monitor-agent";
                provider.exec(host, cred, "systemctl stop " + serviceName + " 2>/dev/null || true", 10);
                provider.exec(host, cred, "systemctl disable " + serviceName + " 2>/dev/null || true", 10);
                provider.exec(host, cred, "rm -f /etc/systemd/system/" + serviceName + ".service", 10);
                provider.exec(host, cred, "systemctl daemon-reload", 10);
            }
        } catch (Exception e) {
            log.warn("[agent-deploy] 停止系统服务失败: {}", e.getMessage());
        }
    }

    /**
     * 构建启动Cmd。
     *
     * @param provider 提供者，不允许为 null
     * @param dir 目录，不允许为 null
     * @param host 主机，不允许为 null
     * @return 结果字符串
     */
    private String buildStartCmd(HostRemoteProvider provider, String dir, MsServerHost host) {
        if (provider.shellDialect() == com.chua.starter.server.support.spi.HostShellDialect.POWERSHELL) {
            // Windows: 用 javaw 后台启动
            return "Start-Process javaw -ArgumentList '-jar', '" + dir + "\\agent.jar' -WorkingDirectory '" + dir + "' -WindowStyle Hidden";
        }
        // Linux: nohup 后台启动
        return "cd " + provider.quotePath(dir) + " && nohup java -jar agent.jar > /dev/null 2>&1 &";
    }

    /**
     * 获取Local地址。
     *
     * @return 结果字符串
     */
    private String getLocalAddress() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    /**
     * 推送。
     *
     * @param hostId 主机ID，不允许为 null
     * @param progress 方法入参 progress
     * @param message 消息，不允许为 null
     * @param status 状态，不允许为 null
     */
    private void push(Long hostId, int progress, String message, String status) {
        try {
            Map<String, Object> data = new LinkedHashMap<>(6);
            data.put("hostId", hostId);
            data.put("progress", progress);
            data.put("message", message);
            data.put("status", status);
            data.put("timestamp", System.currentTimeMillis());
            pushService.broadcast(EVENT_DEPLOY, data, hostId == null ? null : String.valueOf(hostId));
        } catch (Exception ignore) {
        }
    }

    /**
     * 测试 Agent 连接。
     *
     * @param id 目标主机记录 id
     * @return 连通性测试结果，主机不存在时返回错误信息
     */
    @Operation(summary = "测试Agent连接", description = "测试目标主机Agent是否可用")
    @GetMapping("/test/{id}")
    public ReturnResult<Map<String, Object>> testAgent(@PathVariable Long id) {
        MsServerHost host = hostMapper.selectById(id);
        if (host == null) {
            return ReturnResult.error("主机不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String url = "http://" + host.getServerHostAddress() + ":19876/health";
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            if (code == 200) {
                host.setServerHostAgentInstalled(1);
                hostMapper.updateById(host);
                result.put("available", true);
                result.put("msg", "Agent 连接成功");
                return ReturnResult.ok(result);
            }
        } catch (Exception e) {
            result.put("available", false);
            result.put("msg", "Agent 连接失败: " + e.getMessage());
            return ReturnResult.ok(result);
        }
        result.put("available", false);
        result.put("msg", "Agent 连接失败");
        return ReturnResult.ok(result);
    }
}

