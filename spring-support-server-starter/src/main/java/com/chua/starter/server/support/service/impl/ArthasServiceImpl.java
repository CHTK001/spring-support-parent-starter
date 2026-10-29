package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsServerProject;
import com.chua.starter.server.support.entity.MsServerRelease;
import com.chua.starter.server.support.entity.MsExecLog;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.mapper.MsServerProjectMapper;
import com.chua.starter.server.support.mapper.MsServerReleaseMapper;
import com.chua.starter.server.support.service.ArthasService;
import com.chua.starter.server.support.service.ExecService;
import com.chua.starter.server.support.spi.ProjectTypeProviderRegistry;
import com.chua.starter.server.support.util.HostPlatforms;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Arthas 诊断工具管理服务实现。
 *
 * <p>Arthas 支持两种注入方式：</p>
 * <ul>
 *   <li><b>启动前 javaagent</b>：在 jvmArgs 中挂载 {@code -javaagent:arthas-agent.jar}，
 *       与 SkyWalking 同一口径（见 ProjectsView.vue APM 注入 tab）。</li>
 *   <li><b>运行时 attach</b>：通过 SSH 执行 {@code java -jar arthas-boot.jar <pid>} 动态挂载，
 *       无需重启项目。本服务实现运行时 attach 的管理逻辑。</li>
 * </ul>
 *
 * <p>运行时 attach 通过 Arthas 的 telnet 端口（默认 3658）与目标 JVM 交互，
 * 支持命令执行（dashboard / thread / jvm 等）与终端输出查看。</p>
 *
 * @author CH
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ArthasServiceImpl implements ArthasService {

    private final MsServerProjectMapper projectMapper;
    private final MsServerReleaseMapper releaseMapper;
    private final MsServerHostMapper hostMapper;
    private final ExecService execService;

    /**
     * 项目类型路由器：Java 进程判定由类型 SPI 声明，不再比较类型字面量
     */
    private final ProjectTypeProviderRegistry projectTypeRouter;

    /** Arthas 默认 telnet 端口 */
    private static final int DEFAULT_TELNET_PORT = 3658;

    @Override
    public ReturnResult<List<Map<String, Object>>> listArthasProjects(Long releaseId) {
        List<MsServerProject> projects = projectMapper.selectList(
                new LambdaQueryWrapper<MsServerProject>()
                        .eq(MsServerProject::getReleaseId, releaseId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (MsServerProject p : projects) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("projectId", p.getId());
            item.put("projectName", p.getProjectName());
            item.put("projectType", p.getProjectType());
            item.put("mainEntry", p.getMainEntry());
            item.put("projectPath", p.getProjectPath());
            item.put("jvmArgs", p.getJvmArgs());
            // 仅 Java 进程类型项目支持 Arthas
            boolean javaProject = isJavaProject(p.getProjectType());
            item.put("javaProject", javaProject);
            item.put("arthasInjected", isArthasInJvmArgs(p.getJvmArgs()));
            result.add(item);
        }
        return ReturnResult.list(result);
    }

    @Override
    public ReturnResult<Map<String, Object>> getArthasStatus(Long projectId) {
        MsServerProject project = projectMapper.selectById(projectId);
        if (project == null) {
            return ReturnResult.error("项目不存在");
        }
        OperationContext ctx = prepareContext(project);
        if (ctx == null) {
            return ReturnResult.error("无法解析项目上下文");
        }

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("projectId", project.getId());
        status.put("projectName", project.getProjectName());
        status.put("projectType", project.getProjectType());
        status.put("javaProject", isJavaProject(project.getProjectType()));
        status.put("arthasInjected", isArthasInJvmArgs(project.getJvmArgs()));
        status.put("jvmArgs", project.getJvmArgs());

        // 查询运行时 Arthas 状态（通过 SSH 检查 telnet 端口是否监听）
        try {
            String checkCmd = buildPortCheckCommand(ctx, DEFAULT_TELNET_PORT);
            ReturnResult<List<MsExecLog>> logs = execService.runCommand(
                    Collections.singletonList(ctx.hostId), checkCmd, 10, null);
            if (logs.isOk() && logs.getData() != null && !logs.getData().isEmpty()) {
                String output = logs.getData().get(0).getMsExecLogOutput();
                // ss -tlnp | grep :3658 输出类似 "LISTEN 0 100 *:3658 *:* users:(("java",...))"，
                // 只要非空即表示端口在监听（grep 命中），退出码为 0
                boolean portOpen = output != null && !output.isBlank();
                status.put("arthasRunning", portOpen);
                status.put("telnetPort", DEFAULT_TELNET_PORT);
                status.put("httpPort", DEFAULT_TELNET_PORT + 1);
            } else {
                status.put("arthasRunning", false);
            }
        } catch (Exception e) {
            log.warn("查询 Arthas 状态失败: projectId={}", projectId, e);
            status.put("arthasRunning", false);
        }

        // 查询 JVM PID
        try {
            String pidCmd = buildPidQueryCommand(ctx);
            ReturnResult<List<MsExecLog>> pidLogs = execService.runCommand(
                    Collections.singletonList(ctx.hostId), pidCmd, 10, null);
            if (pidLogs.isOk() && pidLogs.getData() != null && !pidLogs.getData().isEmpty()) {
                String output = pidLogs.getData().get(0).getMsExecLogOutput();
                status.put("jvmPid", extractPid(output));
            }
        } catch (Exception e) {
            log.warn("查询 JVM PID 失败: projectId={}", projectId, e);
        }

        return ReturnResult.ok(status);
    }

    @Override
    public ReturnResult<String> attachArthas(Long projectId, String arthasJarPath, Integer telnetPort) {
        MsServerProject project = projectMapper.selectById(projectId);
        if (project == null) {
            return ReturnResult.error("项目不存在");
        }
        if (!isJavaProject(project.getProjectType())) {
            return ReturnResult.error("仅 SpringBoot / FatJar 类型项目支持 Arthas attach");
        }
        OperationContext ctx = prepareContext(project);
        if (ctx == null) {
            return ReturnResult.error("无法解析项目上下文");
        }

        int port = telnetPort != null ? telnetPort : DEFAULT_TELNET_PORT;
        // arthas jar 路径优先级：请求参数 > 项目路径推导 > 默认值
        String jarPath = arthasJarPath != null && !arthasJarPath.trim().isEmpty()
                ? arthasJarPath
                : (project.getProjectPath() != null && !project.getProjectPath().isEmpty()
                    ? project.getProjectPath() + "/lib/arthas/arthas-boot.jar"
                    : "/opt/arthas/arthas-boot.jar");

        // 先查 PID
        String pidCmd = buildPidQueryCommand(ctx);
        ReturnResult<List<MsExecLog>> pidLogs = execService.runCommand(
                Collections.singletonList(ctx.hostId), pidCmd, 10, null);
        if (!pidLogs.isOk() || pidLogs.getData() == null || pidLogs.getData().isEmpty()) {
            return ReturnResult.error("无法获取项目 JVM PID，请确认项目已启动");
        }
        String pid = extractPid(pidLogs.getData().get(0).getMsExecLogOutput());
        if (pid == null || pid.isEmpty()) {
            return ReturnResult.error("未找到运行中的 JVM PID，请确认项目已启动");
        }

        // 检查 arthas-boot.jar 是否存在
        String jarCheck = ctx.windows
                ? String.format("if exist \"%s\" (echo OK) else (echo MISSING)", jarPath)
                : String.format("if [ -f \"%s\" ]; then echo OK; else echo MISSING; fi", jarPath);
        ReturnResult<List<MsExecLog>> jarLogs = execService.runCommand(
                Collections.singletonList(ctx.hostId), jarCheck, 10, null);
        if (jarLogs.isOk() && jarLogs.getData() != null && !jarLogs.getData().isEmpty()) {
            String output = jarLogs.getData().get(0).getMsExecLogOutput();
            if (output != null && output.contains("MISSING")) {
                return ReturnResult.error("arthas-boot.jar 不存在: " + jarPath + "，请先在目标主机上传 Arthas 安装包");
            }
        }

        // 构建 attach 命令：java -jar arthas-boot.jar <pid> --telnet-port <port> --http-port <port+1>
        String attachCmd = buildAttachCommand(ctx, jarPath, pid, port);
        ReturnResult<List<MsExecLog>> attachLogs = execService.runCommand(
                Collections.singletonList(ctx.hostId), attachCmd, 30, null);
        // attach 命令用 nohup 后台运行，stdout 重定向到日志文件，exec 返回空输出。
        // 用单个 shell 命令内部轮询端口（最多 15 秒），避免 Java 端 sleep 时序问题。
        String waitCmd = buildPortWaitCommand(ctx, port, 15);
        ReturnResult<List<MsExecLog>> waitLogs = execService.runCommand(
                Collections.singletonList(ctx.hostId), waitCmd, 20, null);
        if (waitLogs.isOk() && waitLogs.getData() != null && !waitLogs.getData().isEmpty()) {
            String output = waitLogs.getData().get(0).getMsExecLogOutput();
            if (output != null && output.contains("PORT_OPEN")) {
                return ReturnResult.ok("Arthas attach 成功, PID=" + pid + ", telnet=" + port);
            }
        }
        // 端口未监听，尝试读取 arthas 日志判断原因
        String logFile = "/tmp/arthas-" + pid + ".log";
        String logCmd = ctx.windows
                ? String.format("if exist \"%s\" (type \"%s\") else (echo NO_LOG)", logFile, logFile)
                : String.format("if [ -f \"%s\" ]; then tail -5 \"%s\"; else echo NO_LOG; fi", logFile, logFile);
        ReturnResult<List<MsExecLog>> logResult = execService.runCommand(
                Collections.singletonList(ctx.hostId), logCmd, 10, null);
        String logOutput = logResult.getData() != null && !logResult.getData().isEmpty()
                ? logResult.getData().get(0).getMsExecLogOutput() : "无日志";
        return ReturnResult.error("Arthas attach 失败，telnet 端口未监听。日志: " + logOutput);
    }

    @Override
    public ReturnResult<String> detachArthas(Long projectId) {
        MsServerProject project = projectMapper.selectById(projectId);
        if (project == null) {
            return ReturnResult.error("项目不存在");
        }
        OperationContext ctx = prepareContext(project);
        if (ctx == null) {
            return ReturnResult.error("无法解析项目上下文");
        }

        // 通过 Arthas telnet 发送 shutdown 命令
        String shutdownCmd = buildShutdownCommand(ctx);
        ReturnResult<List<MsExecLog>> logs = execService.runCommand(
                Collections.singletonList(ctx.hostId), shutdownCmd, 15, null);
        if (logs.isOk()) {
            return ReturnResult.ok("Arthas 已卸载");
        }
        return ReturnResult.error("Arthas 卸载失败");
    }

    @Override
    public ReturnResult<String> execArthasCommand(Long projectId, String command) {
        MsServerProject project = projectMapper.selectById(projectId);
        if (project == null) {
            return ReturnResult.error("项目不存在");
        }
        OperationContext ctx = prepareContext(project);
        if (ctx == null) {
            return ReturnResult.error("无法解析项目上下文");
        }

        // 通过 Arthas HTTP API 或 telnet 执行命令
        String execCmd = buildExecCommand(ctx, command);
        ReturnResult<List<MsExecLog>> logs = execService.runCommand(
                Collections.singletonList(ctx.hostId), execCmd, 30, null);
        if (logs.isOk() && logs.getData() != null && !logs.getData().isEmpty()) {
            return ReturnResult.ok(logs.getData().get(0).getMsExecLogOutput());
        }
        return ReturnResult.error("命令执行失败");
    }

    @Override
    public ReturnResult<String> getArthasConsole(Long projectId, int lines) {
        MsServerProject project = projectMapper.selectById(projectId);
        if (project == null) {
            return ReturnResult.error("项目不存在");
        }
        OperationContext ctx = prepareContext(project);
        if (ctx == null) {
            return ReturnResult.error("无法解析项目上下文");
        }

        String consoleCmd = buildConsoleCommand(ctx, lines);
        ReturnResult<List<MsExecLog>> logs = execService.runCommand(
                Collections.singletonList(ctx.hostId), consoleCmd, 10, null);
        if (logs.isOk() && logs.getData() != null && !logs.getData().isEmpty()) {
            return ReturnResult.ok(logs.getData().get(0).getMsExecLogOutput());
        }
        return ReturnResult.error("获取终端输出失败");
    }

    // ==================== 内部工具 ====================

    private static class OperationContext {
        MsServerProject project;
        Long hostId;
        MsServerHost host;
        String osType;
        boolean windows;
    }

    private OperationContext prepareContext(MsServerProject project) {
        MsServerRelease release = releaseMapper.selectById(project.getReleaseId());
        if (release == null) return null;
        MsServerHost host = hostMapper.selectById(release.getHostId());
        if (host == null) return null;

        OperationContext ctx = new OperationContext();
        ctx.project = project;
        ctx.hostId = host.getId();
        ctx.host = host;
        ctx.osType = HostPlatforms.normalize(host.getServerHostOsType());
        ctx.windows = HostPlatforms.WINDOWS.equals(ctx.osType);
        return ctx;
    }

    /** 检查 jvmArgs 中是否包含 Arthas javaagent */
    private boolean isArthasInJvmArgs(String jvmArgsJson) {
        if (jvmArgsJson == null || jvmArgsJson.trim().isEmpty()) return false;
        return jvmArgsJson.contains("arthas");
    }

    /** 构建查询 telnet 端口是否监听的命令 */
    private String buildPortCheckCommand(OperationContext ctx, int port) {
        if (ctx.windows) {
            return String.format("netstat -ano | findstr :%d | findstr LISTENING", port);
        }
        return String.format("ss -tlnp | grep :%d", port);
    }

    /** 构建等待 telnet 端口监听的命令（shell 内部轮询，最多 maxSeconds 秒） */
    private String buildPortWaitCommand(OperationContext ctx, int port, int maxSeconds) {
        if (ctx.windows) {
            return String.format(
                    "for /L %%i in (1,1,%d) do (netstat -ano | findstr :%d | findstr LISTENING >nul 2>&1 && (echo PORT_OPEN & exit /b 0) & (timeout /t 1 >nul)) ; echo PORT_CLOSED & exit /b 1",
                    maxSeconds, port);
        }
        // Linux: 内部循环检查端口，每秒一次
        return String.format(
                "for i in $(seq 1 %d); do if ss -tlnp 2>/dev/null | grep -q ':%d'; then echo PORT_OPEN; exit 0; fi; sleep 1; done; echo PORT_CLOSED; exit 1",
                maxSeconds, port);
    }

    /** 构建查询项目 JVM PID 的命令。
     *  启动脚本会生成 {@code app.pid} 文件，优先读取；
     *  若 pid 文件不存在（如手动启动），回退到 {@code ps | grep} 方式。 */
    private String buildPidQueryCommand(OperationContext ctx) {
        String appName = ctx.project.getProjectName();
        String path = ctx.project.getProjectPath();
        String pidFile = (path != null && !path.isEmpty()) ? path + "/app.pid" : "app.pid";
        if (ctx.windows) {
            // Windows: 先读 pid 文件，不存在则用 wmic / PowerShell 查找
            return String.format(
                    "if exist \"%s\" (type \"%s\") else (powershell -Command \"Get-CimInstance Win32_Process -Filter \\\"Name='java.exe'\\\" | Where-Object { $_.CommandLine -like '*%s*' } | Select-Object -ExpandProperty ProcessId\")",
                    pidFile, pidFile, path != null ? path : appName);
        }
        // Linux: 先读 pid 文件，不存在则用 ps | grep 查找
        String search = (path != null && !path.isEmpty()) ? path : appName;
        return String.format(
                "if [ -f \"%s\" ]; then cat \"%s\"; else ps -eo pid,args | grep '[j]ava' | grep -i '%s' | awk '{print $1}' | head -1; fi",
                pidFile, pidFile, search);
    }

    /** 从命令输出中提取 PID */
    private String extractPid(String output) {
        if (output == null || output.trim().isEmpty()) return null;
        String[] lines = output.trim().split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            // 纯数字行
            if (trimmed.matches("\\d+")) return trimmed;
            // 含 PID 的行
            String[] parts = trimmed.split("\\s+");
            for (String part : parts) {
                if (part.matches("\\d{2,}")) return part;
            }
        }
        return null;
    }

    /** 构建 Arthas attach 命令 */
    private String buildAttachCommand(OperationContext ctx, String jarPath, String pid, int telnetPort) {
        int httpPort = telnetPort + 1;
        if (ctx.windows) {
            // Windows: java -jar arthas-boot.jar <pid> --telnet-port <port> --http-port <port>
            return String.format(
                    "java -jar \"%s\" %s --telnet-port %d --http-port %d",
                    jarPath, pid, telnetPort, httpPort);
        }
        // Linux: nohup java -jar arthas-boot.jar <pid> --telnet-port <port> --http-port <port> &
        return String.format(
                "nohup java -jar %s %s --telnet-port %d --http-port %d > /tmp/arthas-%s.log 2>&1 &",
                jarPath, pid, telnetPort, httpPort, pid);
    }

    /** 构建 Arthas shutdown 命令（通过 telnet 发送 stop） */
    private String buildShutdownCommand(OperationContext ctx) {
        return buildTelnetScriptCommand(ctx, "stop", 5);
    }

    /** 构建 Arthas 命令执行命令（通过 telnet 发送命令） */
    private String buildExecCommand(OperationContext ctx, String command) {
        return buildTelnetScriptCommand(ctx, command, 8);
    }

    /** 构建 Arthas 终端输出获取命令（通过 telnet 发送 history） */
    private String buildConsoleCommand(OperationContext ctx, int lines) {
        return buildTelnetScriptCommand(ctx, "history", 5);
    }

    /**
     * 构建通过 SSH 执行 Python telnet 脚本的命令。
     * 将 Python 脚本写入临时文件，然后执行。
     */
    private String buildTelnetScriptCommand(OperationContext ctx, String command, double readSeconds) {
        String safeCmd = shellQuote(command);
        return "cat > /tmp/arthas_telnet.py << 'ARTHAS_PY_EOF'\n"
                + PYTHON_TELNET_SCRIPT
                + "ARTHAS_PY_EOF\n"
                + "python3 /tmp/arthas_telnet.py 127.0.0.1 " + DEFAULT_TELNET_PORT + " " + safeCmd + " " + readSeconds;
    }

    /** Shell 单引号转义 */
    private String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /** Arthas telnet Python 脚本（raw socket 实现，无第三方依赖） */
    private static final String PYTHON_TELNET_SCRIPT =
            "#!/usr/bin/env python3\n" +
            "import socket, sys, time\n" +
            "host=sys.argv[1] if len(sys.argv)>1 else '127.0.0.1'\n" +
            "port=int(sys.argv[2]) if len(sys.argv)>2 else 3658\n" +
            "cmd=sys.argv[3] if len(sys.argv)>3 else 'dashboard'\n" +
            "read_sec=float(sys.argv[4]) if len(sys.argv)>4 else 5.0\n" +
            "IAC=0xFF; WILL=0xFB; WONT=0xFC; DO=0xFD; DONT=0xFE\n" +
            "def pr(data):\n" +
            "    text=bytearray(); resp=bytearray(); i=0\n" +
            "    while i<len(data):\n" +
            "        if data[i]==IAC and i+1<len(data):\n" +
            "            c=data[i+1]\n" +
            "            if c in (WILL,WONT,DO,DONT) and i+2<len(data):\n" +
            "                o=data[i+2]\n" +
            "                if c==WILL: resp+=bytes([IAC,DO,o])\n" +
            "                elif c==WONT: resp+=bytes([IAC,DONT,o])\n" +
            "                elif c==DO: resp+=bytes([IAC,WILL,o])\n" +
            "                elif c==DONT: resp+=bytes([IAC,WILL,o])\n" +
            "                i+=3\n" +
            "            elif c==IAC: text.append(IAC); i+=2\n" +
            "            else: i+=2\n" +
            "        else: text.append(data[i]); i+=1\n" +
            "    return bytes(text), bytes(resp)\n" +
            "s=socket.socket(); s.settimeout(10); s.connect((host,port))\n" +
            "buf=b''\n" +
            "deadline=time.time()+10\n" +
            "while time.time()<deadline:\n" +
            "    try:\n" +
            "        s.settimeout(3); ch=s.recv(4096)\n" +
            "        if not ch: break\n" +
            "        t,r=pr(ch); buf+=t\n" +
            "        if r: s.send(r)\n" +
            "        if b'[arthas@' in buf: break\n" +
            "    except socket.timeout:\n" +
            "        if b'[arthas@' in buf: break\n" +
            "        continue\n" +
            "    except: break\n" +
            "s.send(cmd.encode()+b'\\n')\n" +
            "time.sleep(read_sec)\n" +
            "out=b''\n" +
            "deadline=time.time()+10\n" +
            "while time.time()<deadline:\n" +
            "    try:\n" +
            "        s.settimeout(3); ch=s.recv(65536)\n" +
            "        if not ch: break\n" +
            "        t,r=pr(ch); out+=t\n" +
            "        if r: s.send(r)\n" +
            "        if b'[arthas@' in out: break\n" +
            "    except socket.timeout:\n" +
            "        if b'[arthas@' in out: break\n" +
            "        continue\n" +
            "    except: break\n" +
            "sys.stdout.write(out.decode('utf-8',errors='replace'))\n" +
            "s.close()\n";

    /**
     * 项目是否为 JVM 进程（Arthas 的准入条件）。
     *
     * <p>由项目类型 SPI 声明：新增 JVM 类型（如 Git 源码部署）无需改动本类。</p>
     *
     * @param projectType 项目类型
     * @return JVM 进程时为 {@code true}
     */
    private boolean isJavaProject(String projectType) {
        return projectTypeRouter.resolveOrGeneric(projectType).javaProcess();
    }
}
