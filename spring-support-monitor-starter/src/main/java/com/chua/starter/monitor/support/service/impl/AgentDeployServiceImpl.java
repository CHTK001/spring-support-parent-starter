package com.chua.starter.monitor.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.starter.monitor.support.config.AgentDeployProperties;
import com.chua.starter.monitor.support.model.AgentDeployProgressVO;
import com.chua.starter.monitor.support.model.AgentHealthVO;
import com.chua.starter.monitor.support.service.AgentDeployService;
import com.chua.starter.monitor.support.service.AgentHttpClient;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.enums.HostCollectType;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.spi.HostRemoteProvider;
import com.chua.starter.server.support.spi.HostRemoteProviderRegistry;
import com.chua.starter.server.support.spi.HostShellDialect;
import com.chua.starter.server.support.spi.RemoteExecResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Agent 部署生命周期实现
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentDeployServiceImpl implements AgentDeployService {

    /**
     * 部署进度事件名（前端 subscribe("monitor","agent-deploy") 接收）
    */
    private static final String EVENT_DEPLOY = "agent-deploy";

    /**
     * 远端 java 探测超时（秒）
    */
    private static final int PROBE_TIMEOUT_SECONDS = 15;

    /**
     * 目标机自取 agent 包超时（秒）
    */
    private static final int DOWNLOAD_TIMEOUT_SECONDS = 180;

    /**
     * 普通用户兜底启动时用的 JDK 主版本下限
    */
    private static final int MIN_JAVA_MAJOR = 8;

    /**
     * Windows java 定位脚本：机器级 PATH 里通常没有 java.exe，只查 PATH 会误判成「没装 Java」。
     * 按 JAVA_HOME → Get-Command → 各 JDK 厂商安装根目录兜底，取第一个存在的 java.exe。
     * 只在 Java 厂商目录内递归，避免扫全盘；不使用 -Depth（PowerShell 4.0 不支持）。
     */
    private static final String WINDOWS_JAVA_PROBE = "$f=@();"
            + "if ($env:JAVA_HOME) { $f += (Join-Path $env.JAVA_HOME 'bin\\java.exe') };"
            + "$g=(Get-Command java.exe -ErrorAction SilentlyContinue); if ($g) { $f += $g.Source };"
            + "foreach ($r in @('C:\\Program Files\\Java','C:\\Program Files (x86)\\Java',"
            + "'C:\\Program Files\\Eclipse Adoptium','C:\\Program Files\\Amazon Corretto',"
            + "'C:\\Program Files\\Zulu','C:\\Program Files\\BellSoft')) {"
            + " if (Test-Path $r) {"
            + " $f += (Get-ChildItem -Path $r -Filter java.exe -Recurse -ErrorAction SilentlyContinue"
            + " | Select-Object -First 3 -ExpandProperty FullName) } };"
            + "($f | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1)";

    /**
     * 主机 映射器
    */
    private final MsServerHostMapper hostMapper;

    /**
     * 主机服务（凭据解析）
    */
    private final ServerHostService serverHostService;

    /**
     * 远程执行能力注册表
    */
    private final HostRemoteProviderRegistry remoteRegistry;

    /**
     * socket 推送服务
    */
    private final MonitorPushService pushService;

    /**
     * agent HTTP 客户端
    */
    private final AgentHttpClient agentClient;

    /**
     * 模块配置
    */
    private final AgentDeployProperties properties;

    /**
     * 执行中的主机集合，防同一主机并发操作
    */
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    /**
     * 单线程序列足够：agent 操作是低频运维动作
    */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        AtomicInteger seq = new AtomicInteger();
        Thread thread = new Thread(runnable, "agent-deploy-" + seq.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public ReturnResult<String> submit(Long hostId, Action action) {
        if (hostId == null || action == null) {
            return ReturnResult.error("参数不完整：hostId/action 必填");
        }
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        if (!running.add(hostId)) {
            return ReturnResult.error("该主机已有 Agent 操作在执行中，请等待完成后再试");
        }
        try {
            executor.submit(() -> {
                try {
                    run(hostId, action);
                } finally {
                    running.remove(hostId);
                }
            });
        } catch (RuntimeException e) {
            running.remove(hostId);
            return ReturnResult.error("任务提交失败: " + e.getMessage());
        }
        return ReturnResult.ok(action.name(), "已提交 " + label(action) + " 任务");
    }

    @Override
    public boolean busy(Long hostId) {
        return hostId != null && running.contains(hostId);
    }

    @Override
    public Path packageFile() {
        return resolveArtifact();
    }

    /**
     * 执行一次部署动作。异常统一转成进度事件与主机台账上的失败原因。
     *
     * @param hostId 主机标识
     * @param action 动作
     */
    private void run(Long hostId, Action action) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return;
        }
        try {
            push(hostId, 2, "开始" + label(action), "info");
            Session session = open(host);
            switch (action) {
                case INSTALL -> install(session, false);
                case REINSTALL -> {
                    uninstall(session, false);
                    Session fresh = open(hostMapper.selectById(hostId));
                    install(fresh, true);
                }
                case UPGRADE -> upgrade(session);
                case UNINSTALL -> uninstall(session, true);
                default -> push(hostId, 0, "不支持的动作: " + action, "error");
            }
        } catch (Exception e) {
            log.warn("[agent-deploy] hostId={} {} 失败: {}", hostId, action, e.getMessage());
            markFailure(hostId, e.getMessage());
            push(hostId, 0, label(action) + "失败: " + e.getMessage(), "error");
        }
    }

    /**
     * 建立一次远程会话：凭据、执行通道与远端目录。
     *
     * @param host 主机
     * @return 会话
     */
    private Session open(MsServerHost host) throws Exception {
        if (host == null) {
            throw new IllegalStateException("主机不存在");
        }
        Long hostId = host.getId();
        push(hostId, 8, "解析主机凭据...", "info");
        ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, null);
        if (resolved == null || !resolved.isOk() || resolved.getData() == null) {
            throw new IllegalStateException("未找到可用的主机凭据，请先在主机资产里绑定 SSH/WinRM 凭据");
        }
        push(hostId, 14, "建立远程通道...", "info");
        HostRemoteProvider provider = remoteRegistry.resolve(host).orElse(null);
        if (provider == null) {
            throw new IllegalStateException("不支持的操作系统类型: " + host.getServerHostOsType());
        }
        boolean windows = provider.shellDialect() == HostShellDialect.POWERSHELL;
        String dir = provider.join(provider.tempDir(), properties.getRemoteDirName());
        push(hostId, 20, "创建远端目录 " + dir, "info");
        provider.mkdirs(host, resolved.getData(), dir);
        return new Session(host, resolved.getData(), provider, windows, dir);
    }

    /**
     * 安装：探测 java → 上传包与配置 → 注册系统服务 → 健康校验。
     *
     * @param session 会话
     * @param reinstalled 是否为重装后的安装（用于进度文案）
     */
    private void install(Session session, boolean reinstalled) throws Exception {
        Long hostId = session.host.getId();
        push(hostId, 26, reinstalled ? "重装：检查远端 Java..." : "检查远端 Java...", "info");
        String javaBin = requireJava(session);

        Path artifact = resolveArtifact();
        push(hostId, 32, "Agent 包版本 " + nullToEmpty(readArtifactVersion(artifact)), "info");
        push(hostId, 42, "投递 agent.jar（" + (artifact.toFile().length() / 1024 / 1024 + 1) + "MB）...", "info");
        deliver(session, artifact, jarPath(session));

        push(hostId, 58, "写入 agent.yml", "info");
        writeConfig(session);

        push(hostId, 70, "注册并启动 monitor-agent 服务...", "info");
        startService(session, javaBin);

        verifyHealth(session, "安装");
    }

    /**
     * 升级：新包先落到 .new，停服务后备份在跑版本再换装，健康校验不过就回滚。
     *
     * @param session 会话
     */
    private void upgrade(Session session) throws Exception {
        Long hostId = session.host.getId();
        String javaBin = requireJava(session);
        Path artifact = resolveArtifact();
        String targetVersion = readArtifactVersion(artifact);

        AgentHealthVO before = agentClient.health(session.host);
        String currentVersion = before == null ? "" : nullToEmpty(before.version());
        push(hostId, 12, "当前版本 " + (currentVersion.isEmpty() ? "未知/不可达" : currentVersion)
                + " → 目标 " + nullToEmpty(targetVersion), "info");
        if (!currentVersion.isEmpty() && currentVersion.equals(targetVersion)) {
            push(hostId, 40, "目标版本与当前一致，仍按指令重装该版本", "info");
        }

        push(hostId, 46, "投递新包 agent.jar.new", "info");
        deliver(session, artifact, jarNewPath(session));

        push(hostId, 56, "停止服务", "info");
        stopService(session);
        session.exec(session.windows ? "Copy-Item -Force '" + jarPath(session) + "' '" + jarBakPath(session) + "'"
                : "cp -f " + jarPath(session) + " " + jarBakPath(session) + " 2>/dev/null || true", PROBE_TIMEOUT_SECONDS);
        session.exec(session.windows ? "Move-Item -Force '" + jarNewPath(session) + "' '" + jarPath(session) + "'"
                : "mv -f " + jarNewPath(session) + " " + jarPath(session), PROBE_TIMEOUT_SECONDS);

        push(hostId, 70, "启动服务", "info");
        startService(session, javaBin);

        try {
            verifyHealth(session, "升级", targetVersion);
        } catch (Exception e) {
            push(hostId, 88, "新版本未就绪，回滚到旧包: " + e.getMessage(), "warn");
            session.exec(session.windows ? "Move-Item -Force '" + jarBakPath(session) + "' '" + jarPath(session) + "'"
                    : "mv -f " + jarBakPath(session) + " " + jarPath(session), PROBE_TIMEOUT_SECONDS);
            stopService(session);
            startService(session, javaBin);
            agentClient.awaitHealthy(session.host, properties.getHealthWaitSeconds(), null);
            markFailure(hostId, "升级失败已回滚: " + e.getMessage());
            push(hostId, 0, "升级失败，已回滚到旧版本", "error");
            return;
        }
        session.tryExec(session.windows ? "Remove-Item -Force '" + jarBakPath(session) + "' -ErrorAction SilentlyContinue"
                : "rm -f " + jarBakPath(session) + " 2>/dev/null || true", PROBE_TIMEOUT_SECONDS);
    }

    /**
     * 卸载：停服务、去自启、删单元文件与目录，并把主机台账回写为未安装。
     *
     * @param session 会话
     * @param pushFinal 是否推送最终完成事件（重装的中途清理阶段不推）
     */
    private void uninstall(Session session, boolean pushFinal) throws Exception {
        Long hostId = session.host.getId();
        push(hostId, 34, "停止 monitor-agent 服务", "info");
        stopService(session);
        push(hostId, 56, "清理远端文件", "info");
        String dir = session.dir;
        if (session.windows) {
            // 删除计划任务定义（stopService 已停运行实例，这里彻底注销）
            unregisterWindowsTask(session);
            session.exec("Remove-Item -Recurse -Force '" + dir + "' -ErrorAction SilentlyContinue", PROBE_TIMEOUT_SECONDS);
        } else {
            session.exec(sudo(session) + "rm -rf " + dir, PROBE_TIMEOUT_SECONDS);
        }
        MsServerHost host = session.host;
        host.setServerHostAgentInstalled(0);
        hostMapper.updateById(host);
        // 反向确认：/health 还在应答说明服务并没真停
        for (int i = 0; i < 6; i++) {
            if (agentClient.health(session.host) == null) {
                break;
            }
            Thread.sleep(1000L);
        }
        if (agentClient.health(session.host) != null) {
            markFailure(hostId, "卸载未完成：远端 agent 仍在响应 /health，请检查服务是否被守护拉起");
            throw new IllegalStateException("卸载未完成：agent 仍在响应 /health");
        }
        // updateById 的字段策略会跳过 null，这三列必须显式置空，
        // 否则卸载后台账仍写着「已装 vX.Y 且刚采集过」，画像页会一直显示过期状态
        hostMapper.update(null, new LambdaUpdateWrapper<MsServerHost>()
                .eq(MsServerHost::getId, host.getId())
                .set(MsServerHost::getServerHostAgentVersion, null)
                .set(MsServerHost::getServerHostAgentTime, null)
                .set(MsServerHost::getServerHostAgentError, null));
        if (pushFinal) {
            push(hostId, 100, "Agent 卸载完成", "success");
            log.info("[agent-deploy] hostId={} uninstall 成功", hostId);
        }
    }

    /**
     * 健康校验：只有 agent 自己应答了才算装成功，并顺带把端口/版本写回台账。
     *
     * @param session 会话
     * @param label 动作文案
     */
    private void verifyHealth(Session session, String label) throws Exception {
        verifyHealth(session, label, null);
    }

    /**
     * 健康校验（可指定期望版本）。
     *
     * @param session 会话
     * @param label 动作文案
     * @param expectVersion 期望版本，空则只要求可达
     */
    private void verifyHealth(Session session, String label, String expectVersion) {
        Long hostId = session.host.getId();
        push(hostId, 84, "等待 agent 就绪（/health）...", "info");
        AgentHealthVO health = agentClient.awaitHealthy(session.host, properties.getHealthWaitSeconds(), expectVersion);
        if (health == null) {
            markFailure(hostId, label + "失败：agent 启动后 " + properties.getHealthWaitSeconds() + "s 内未响应 /health");
            throw new IllegalStateException(label + "失败：agent 未响应 /health（端口 "
                    + agentClient.portOf(session.host) + "），请检查远端 Java 与服务日志");
        }
        if (expectVersion != null && !expectVersion.isBlank() && !expectVersion.equals(nullToEmpty(health.version()))) {
            markFailure(hostId, label + "失败：版本仍为 " + nullToEmpty(health.version()));
            throw new IllegalStateException(label + "失败：期望版本 " + expectVersion
                    + "，实际 " + nullToEmpty(health.version()));
        }
        MsServerHost host = session.host;
        host.setServerHostAgentInstalled(1);
        host.setServerHostAgentEnabled(1);
        host.setServerHostAgentPort(agentClient.portOf(host));
        host.setServerHostAgentVersion(nullToEmpty(health.version()));
        host.setServerHostAgentTime(LocalDateTime.now());
        host.setServerHostCollectMode(HostCollectType.AGENT.getCode());
        hostMapper.updateById(host);
        // 成功后清掉历史失败原因（updateById 跳过 null，必须显式置空）
        hostMapper.update(null, new LambdaUpdateWrapper<MsServerHost>()
                .eq(MsServerHost::getId, host.getId())
                .set(MsServerHost::getServerHostAgentError, null));
        push(hostId, 100, "Agent " + label + "完成，版本 " + nullToEmpty(host.getServerHostAgentVersion()), "success");
        log.info("[agent-deploy] hostId={} {} 成功，agent 版本 {}", hostId, label, host.getServerHostAgentVersion());
    }

    /**
     * 探测远端 java，拿不到或版本过低直接失败，避免装出一个起不来的服务。
     *
     * @param session 会话
     * @return 可用于 ExecStart 的 java 路径或命令名
     */
    private String requireJava(Session session) {
        String configured = properties.getJavaCommand();
        List<String> candidates = new ArrayList<>();
        if (configured != null && !configured.isBlank()) {
            candidates.add(configured.trim());
        } else if (session.windows) {
            String found = firstLine(trimOut(session.tryExec(WINDOWS_JAVA_PROBE, PROBE_TIMEOUT_SECONDS)));
            if (!found.isEmpty()) {
                candidates.add(found);
            }
            candidates.add("java");
        } else {
            candidates.add("command -v java 2>/dev/null || which java 2>/dev/null");
        }
        for (String candidate : candidates) {
            String resolved = candidate;
            if (!session.windows && candidate.startsWith("command -v")) {
                String path = trimOut(session.tryExec(candidate, PROBE_TIMEOUT_SECONDS));
                if (path.isEmpty()) {
                    continue;
                }
                resolved = path;
            }
            RemoteExecResult version = session.tryExec(versionProbe(resolved, session.windows), PROBE_TIMEOUT_SECONDS);
            if (!version.success()) {
                continue;
            }
            String output = trimOut(version);
            if (output.isEmpty()) {
                continue;
            }
            int major = javaMajor(output);
            if (major > 0 && major < MIN_JAVA_MAJOR) {
                throw new IllegalStateException("远端 Java 版本过低（" + major + "），agent 需要 Java "
                        + MIN_JAVA_MAJOR + "+：" + firstLine(output));
            }
            push(session.host.getId(), 30, "远端 Java 就绪：" + firstLine(output), "info");
            return resolved;
        }
        throw new IllegalStateException("目标主机未找到可用的 java（agent 需要 Java " + MIN_JAVA_MAJOR + "+），"
                + "可先安装 JDK 或在 monitor.agent.java-command 指定绝对路径后重试");
    }

    /**
     * 组装 {@code java -version} 探测命令
     * <p>
     * 两个 Windows 坑：路径常含空格（C:\Program Files\...）；且 java 把版本信息写在 stderr，
     * 在 PowerShell 里用 {@code &} 调用再 {@code 2>&1} 合并会生成 NativeCommandError 错误记录，
     * 使 powershell.exe 以退出码 1 结束，版本明明正常也会被判成探测失败。
     * 故交给 {@code cmd /c} 做重定向合并——cmd 原生把 stderr 并进 stdout，
     * PowerShell 侧只见 stdout，退出码保持真实值。
     * </p>
     *
     * @param javaBin java 路径或命令名
     * @param windows 是否 Windows 方言
     * @return 可下发的探测命令
     */
    private String versionProbe(String javaBin, boolean windows) {
        if (!windows) {
            return javaBin + " -version 2>&1";
        }
        String quoted = javaBin.replace("`", "");
        return "cmd /c \"`\"" + quoted + "`\" -version 2>&1\"";
    }

    /**
     * 投递 agent 包到远端路径。
     * <p>
     * 优先让目标机按 {@code monitor.agent.jar-url} 自行 HTTP 下载：WinRM 单条命令行只有约 8KB 可用，
     * Base64 分块搬运 6MB+ 的包需要数千次往返，实测直接报「命令行太长」。
     * 未配置下载地址、下载失败或字节数不符时回退通道上传（Linux 走 SFTP 不受影响）。
     * </p>
     *
     * @param session 会话
     * @param artifact 本地包
     * @param remote 远端路径
     */
    private void deliver(Session session, Path artifact, String remote) {
        Long hostId = session.host.getId();
        long expectSize = artifact.toFile().length();
        String url = properties.getJarUrl();
        if (url != null && !url.isBlank()) {
            String failure;
            try {
                push(hostId, 44, "目标机下载 agent 包...", "info");
                downloadOnRemote(session, url.trim(), remote);
                long size = session.provider.fileSize(session.host, session.credential, remote);
                if (size == expectSize) {
                    push(hostId, 55, "agent 包就绪（" + size + " 字节）", "info");
                    return;
                }
                failure = "下载字节数不符（远端 " + size + " / 本地 " + expectSize + "）";
            } catch (Exception e) {
                failure = firstLine(e.getMessage());
            }
            log.warn("[agent-deploy] hostId={} 远端下载未成功，回退通道上传: {}", hostId, failure);
            push(hostId, 44, "远端下载失败（" + failure + "），回退通道上传", "warn");
        }
        session.put(artifact, remote);
    }

    /**
     * 生成目标机自取包的下载命令。
     *
     * @param session 会话
     * @param url 包地址
     * @param remote 远端路径
     */
    private void downloadOnRemote(Session session, String url, String remote) {
        String script;
        if (session.windows) {
            // PS4 的 Invoke-WebRequest 依赖 IE 引擎配置，-UseBasicParsing 必须带；
            // 2012R2 默认只启 SSL3/TLS1.0，https 地址需显式补上 TLS1.2
            script = "$ProgressPreference='SilentlyContinue';"
                    + "try { [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor 3072 } catch {};"
                    + "Invoke-WebRequest -Uri '" + url + "' -OutFile '" + remote + "' -UseBasicParsing -TimeoutSec "
                    + DOWNLOAD_TIMEOUT_SECONDS;
        } else {
            script = "curl -fsSL --retry 2 -o '" + remote + "' '" + url + "' "
                    + "|| wget -q -O '" + remote + "' '" + url + "'";
        }
        session.exec(script, DOWNLOAD_TIMEOUT_SECONDS);
    }

    /**
     * 写入远端 agent.yml：端口与令牌都取平台配置，避免主机侧与平台侧各写一套。
     *
     * @param session 会话
     */
    private void writeConfig(Session session) throws IOException {
        int port = session.host.getServerHostAgentPort() == null || session.host.getServerHostAgentPort() <= 0
                ? properties.getPort() : session.host.getServerHostAgentPort();
        StringBuilder sb = new StringBuilder();
        if (properties.getReportUrl() != null && !properties.getReportUrl().isBlank()) {
            sb.append("server:\n  url: ").append(properties.getReportUrl().trim()).append('\n');
        }
        sb.append("agent:\n")
                .append("  port: ").append(port).append('\n')
                .append("  bind: 0.0.0.0\n")
                .append("  agentKey: ").append(nullToEmpty(session.host.getServerHostAgentKey())).append('\n');
        if (properties.getToken() != null && !properties.getToken().isBlank()) {
            sb.append("  token: ").append(properties.getToken().trim()).append('\n');
        }
        Path tmp = Files.createTempFile("agent", ".yml");
        try {
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
            session.put(tmp, configPath(session));
        } finally {
            Files.deleteIfExists(tmp);
        }
        MsServerHost host = session.host;
        host.setServerHostAgentPort(port);
        hostMapper.updateById(host);
    }

    /**
     * 注册并启动服务。系统级服务不可用时退化为普通用户常驻进程，并把真实方式回报到进度里。
     *
     * @param session 会话
     * @param javaBin java 可执行路径
     */
    private void startService(Session session, String javaBin) {
        if (session.windows) {
            startWindowsService(session, javaBin);
        } else {
            startLinuxService(session, javaBin);
        }
    }

    /**
     * Linux：systemd 单元 + enable + restart。
     *
     * @param session 会话
     * @param javaBin java 路径
     */
    private void startLinuxService(Session session, String javaBin) {
        Long hostId = session.host.getId();
        String service = properties.getLinuxServiceName();
        String unit = "[Unit]\n"
                + "Description=Monitor Agent\n"
                + "After=network.target\n\n"
                + "[Service]\n"
                + "Type=simple\n"
                + "WorkingDirectory=" + session.dir + "\n"
                + "ExecStart=" + javaBin + " -cp " + jarPath(session) + " com.chua.agent.AgentMain\n"
                + "Restart=always\n"
                + "RestartSec=5\n\n"
                + "[Install]\n"
                + "WantedBy=multi-user.target\n";
        String prefix = sudo(session);
        try {
            Path tmp = Files.createTempFile("monitor-agent", ".service");
            try {
                Files.writeString(tmp, unit, StandardCharsets.UTF_8);
                session.put(tmp, session.join(session.dir, service + ".service"));
            } finally {
                Files.deleteIfExists(tmp);
            }
            session.exec(prefix + "cp -f " + session.join(session.dir, service + ".service")
                    + " /etc/systemd/system/" + service + ".service", PROBE_TIMEOUT_SECONDS);
            session.exec(prefix + "systemctl daemon-reload", PROBE_TIMEOUT_SECONDS);
            session.exec(prefix + "systemctl enable " + service, PROBE_TIMEOUT_SECONDS);
            session.exec(prefix + "systemctl restart " + service, PROBE_TIMEOUT_SECONDS);
            push(hostId, 78, "systemd 服务已启动", "info");
            return;
        } catch (Exception e) {
            log.warn("[agent-deploy] hostId={} systemd 安装失败，回退普通用户启动: {}", hostId, e.getMessage());
            push(hostId, 74, "systemd 不可用（" + firstLine(e.getMessage()) + "），改用普通用户常驻进程", "warn");
        }
        startNoopProcess(session, javaBin);
    }

    /**
     * Windows：用「计划任务」注册为开机启动、SYSTEM 身份、崩溃自动重启的常驻服务。
     * <p>
     * 不再依赖 nssm（目标机默认没有，现场下载/放行不可控）。计划任务是 Windows 原生能力，
     * Server 2012 R2（PowerShell 4.0）即可用：-AtStartup 开机拉起、RestartCount/Interval
     * 实现进程退出后自动重启，效果与 nssm 服务等价且无需任何第三方可执行文件。
     *
     * @param session 会话
     * @param javaBin java 路径
     */
    private void startWindowsService(Session session, String javaBin) {
        Long hostId = session.host.getId();
        String task = properties.getWindowsServiceName();
        String jar = jarPath(session);
        String dir = session.dir;
        // java 路径作为 Execute、其余作为 Argument，规避「路径含空格 + 命令行引号」被任务计划错误拆分
        String arguments = "-cp \"" + jar + "\" com.chua.agent.AgentMain";
        String script = "$ErrorActionPreference='Stop';"
                + "$task='" + psEscape(task) + "';"
                + "$action=New-ScheduledTaskAction -Execute '" + psEscape(javaBin) + "' -Argument '"
                + psEscape(arguments) + "' -WorkingDirectory '" + psEscape(dir) + "';"
                + "$trigger=New-ScheduledTaskTrigger -AtStartup;"
                + "$principal=New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest;"
                + "$settings=New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries"
                + " -StartWhenAvailable -DontStopOnIdleEnd"
                + " -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1)"
                + " -ExecutionTimeLimit ([TimeSpan]::Zero);"
                + "Register-ScheduledTask -TaskName $task -Action $action -Trigger $trigger"
                + " -Principal $principal -Settings $settings -Force | Out-Null;"
                + "Start-ScheduledTask -TaskName $task;";
        session.exec(script, PROBE_TIMEOUT_SECONDS);
        push(hostId, 78, "Windows 计划任务已注册并启动（开机自启 + 崩溃重启）", "info");
    }

    /**
     * 停止 Windows 计划任务的运行实例（保留任务定义，升级/重装后由 startWindowsService 重新注册并启动）。
     *
     * @param session 会话
     */
    private void stopWindowsTask(Session session) {
        String task = properties.getWindowsServiceName();
        String script = "$ErrorActionPreference='SilentlyContinue';"
                + "Stop-ScheduledTask -TaskName '" + psEscape(task) + "';";
        session.tryExec(script, PROBE_TIMEOUT_SECONDS);
    }

    /**
     * 注销并删除 Windows 计划任务（卸载时调用）。
     *
     * @param session 会话
     */
    private void unregisterWindowsTask(Session session) {
        String task = properties.getWindowsServiceName();
        String script = "$ErrorActionPreference='SilentlyContinue';"
                + "Stop-ScheduledTask -TaskName '" + psEscape(task) + "';"
                + "Unregister-ScheduledTask -TaskName '" + psEscape(task) + "' -Confirm:$false;";
        session.tryExec(script, PROBE_TIMEOUT_SECONDS);
    }

    /**
     * 普通用户常驻启动：用子 shell 包裹，避免后台进程占住 SSH 通道导致命令迟迟不返回。
     *
     * @param session 会话
     * @param javaBin java 路径
     */
    private void startNoopProcess(Session session, String javaBin) {
        session.exec("cd " + session.dir + " && ( nohup " + javaBin + " -cp " + jarPath(session)
                + " com.chua.agent.AgentMain > " + session.join(session.dir, "agent.out")
                + " 2>&1 < /dev/null & )", PROBE_TIMEOUT_SECONDS);
    }

    /**
     * 停止服务（含普通用户进程兜底）。
     *
     * @param session 会话
     */
    private void stopService(Session session) {
        String service = session.windows ? properties.getWindowsServiceName() : properties.getLinuxServiceName();
        if (session.windows) {
            // 先停计划任务运行实例（任务定义保留，升级/重装后重新注册）
            stopWindowsTask(session);
            // 按 agent.pid 精确停，绝不按进程名批量杀：同机其它 java 服务会被连带打断
            String pidFile = session.join(session.dir, "agent.pid").replace("'", "''");
            String agentJar = jarPath(session).replace("'", "''");
            session.tryExec("$p=(Get-Content '" + pidFile + "' -ErrorAction SilentlyContinue | Select-Object -First 1);"
                    + "if ($p) { $proc=(Get-CimInstance Win32_Process -Filter \"ProcessId=$p\" -ErrorAction SilentlyContinue);"
                    + "if ($proc -and $proc.CommandLine -like '*" + agentJar + "*') { Stop-Process -Id $p -Force -ErrorAction SilentlyContinue } }",
                    PROBE_TIMEOUT_SECONDS);
            return;
        }
        String prefix = sudo(session);
        session.tryExec(prefix + "systemctl stop " + service + " 2>/dev/null; true", PROBE_TIMEOUT_SECONDS);
        session.tryExec(prefix + "systemctl disable " + service + " 2>/dev/null; true", PROBE_TIMEOUT_SECONDS);
        session.tryExec(prefix + "rm -f /etc/systemd/system/" + service + ".service", PROBE_TIMEOUT_SECONDS);
        session.tryExec(prefix + "systemctl daemon-reload 2>/dev/null; true", PROBE_TIMEOUT_SECONDS);
        // 单元文件不在位时（普通用户常驻模式）靠 pid 文件精确停，避免 pkill 误杀同机其它 java 进程。
        // 这是兜底探测：agent 由 systemd 接管时可能压根没有该文件，失败也不能中断卸载。
        session.tryExec("kill $(cat " + session.join(session.dir, "agent.pid") + ") 2>/dev/null; true", PROBE_TIMEOUT_SECONDS);
    }

    /**
     * 该会话是否需要 sudo：先探一次，root 或无 sudo 时返回空串。
     *
     * @param session 会话
     * @return sudo 前缀
     */
    private String sudo(Session session) {
        if (session.windows) {
            return "";
        }
        if (session.sudoChecked) {
            return session.sudoPrefix;
        }
        session.sudoChecked = true;
        RemoteExecResult root = session.tryExec("id -u", PROBE_TIMEOUT_SECONDS);
        if ("0".equals(trimOut(root))) {
            session.sudoPrefix = "";
        } else if (session.tryExec("sudo -n true", PROBE_TIMEOUT_SECONDS).success()) {
            session.sudoPrefix = "sudo -n ";
        } else {
            session.sudoPrefix = "";
        }
        return session.sudoPrefix;
    }

    /**
     * 定位本地 agent.jar：显式配置优先，其次若干候选路径。
     *
     * @return 存在的 jar 路径
     */
    private Path resolveArtifact() {
        List<Path> candidates = new ArrayList<>();
        String configured = properties.getJarPath();
        if (configured != null && !configured.isBlank()) {
            candidates.add(Paths.get(configured.trim()));
        }
        String base = System.getProperty("user.dir");
        candidates.add(Paths.get(base, "monitor-agent", "agent.jar"));
        candidates.add(Paths.get(base, "spring-support-monitor-starter", "monitor-agent", "agent.jar"));
        candidates.add(Paths.get(base, "spring-support-parent-starter", "spring-support-monitor-starter",
                "monitor-agent", "agent.jar"));
        for (Path candidate : candidates) {
            if (Files.isReadable(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("未找到 agent.jar，已尝试: " + Arrays.toString(candidates.stream()
                .map(Path::toString).toArray()) + "；可通过 monitor.agent.jar-path 指定");
    }

    /**
     * 读取 jar 内 agent.properties 的版本号，用于升级后校验是否真的换成了目标版本。
     *
     * @param artifact jar 路径
     * @return 版本号，读不到时返回空串
     */
    private String readArtifactVersion(Path artifact) {
        try (ZipFile zip = new ZipFile(artifact.toFile())) {
            ZipEntry entry = zip.getEntry("agent.properties");
            if (entry == null) {
                return "";
            }
            String body = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            for (String line : body.split("\\R")) {
                int idx = line.indexOf('=');
                if (idx > 0 && "version".equals(line.substring(0, idx).trim())) {
                    return line.substring(idx + 1).trim();
                }
            }
        } catch (Exception e) {
            log.debug("[agent-deploy] 读取 agent 版本失败: {}", e.getMessage());
        }
        return "";
    }

    /**
     * 从 java -version 输出里取主版本号。
     *
     * @param output 原始输出
     * @return 主版本号，解析不出时返回 -1
     */
    private int javaMajor(String output) {
        String text = output == null ? "" : output;
        int quote = text.indexOf('"');
        String tail = quote >= 0 ? text.substring(quote + 1) : text;
        String[] parts = tail.split("[._-]");
        if (parts.length == 0) {
            return -1;
        }
        try {
            int major = Integer.parseInt(parts[0].trim());
            // 形如 1.8.0_xx：主版本在第二段
            if (major == 1 && parts.length > 1) {
                return Integer.parseInt(parts[1].trim());
            }
            return major;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 记录失败原因到主机台账，画像页据此解释「为什么没数据」。
     *
     * @param hostId 主机标识
     * @param message 失败原因
     */
    private void markFailure(Long hostId, String message) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return;
        }
        host.setServerHostAgentError(cut(message));
        hostMapper.updateById(host);
    }

    /**
     * 推送进度事件。
     *
     * @param hostId 主机标识
     * @param progress 百分比
     * @param message 文案
     * @param status 状态（info/success/warn/error）
     */
    private void push(Long hostId, int progress, String message, String status) {
        try {
            pushService.broadcast(EVENT_DEPLOY,
                    new AgentDeployProgressVO(hostId, progress, message, status, System.currentTimeMillis()),
                    hostId == null ? null : String.valueOf(hostId));
        } catch (Exception ignore) {
            // socket 未启用时静默，不影响部署本身
        }
    }

    /**
     * 动作中文名。
     *
     * @param action 动作
     * @return 文案
     */
    private String label(Action action) {
        return switch (action) {
            case INSTALL -> "安装";
            case REINSTALL -> "重装";
            case UPGRADE -> "升级";
            case UNINSTALL -> "卸载";
            default -> action.name();
        };
    }

    /**
     * 远端 jar 路径。
     *
     * @param session 会话
     * @return 路径
     */
    private String jarPath(Session session) {
        return session.join(session.dir, "agent.jar");
    }

    /**
     * 远端升级暂存路径。
     *
     * @param session 会话
     * @return 路径
     */
    private String jarNewPath(Session session) {
        return session.join(session.dir, "agent.jar.new");
    }

    /**
     * 远端回滚备份路径。
     *
     * @param session 会话
     * @return 路径
     */
    private String jarBakPath(Session session) {
        return session.join(session.dir, "agent.jar.bak");
    }

    /**
     * 远端配置文件路径。
     *
     * @param session 会话
     * @return 路径
     */
    private String configPath(Session session) {
        return session.join(session.dir, "agent.yml");
    }

    /**
     * 取执行结果首行文本。
     *
     * @param result 执行结果
     * @return 文本
     */
    private String trimOut(RemoteExecResult result) {
        if (result == null) {
            return "";
        }
        String text = result.stdout() == null || result.stdout().isBlank() ? result.stderr() : result.stdout();
        return text == null ? "" : text.trim();
    }

    /**
     * 取首行。
     *
     * @param text 文本
     * @return 首行
     */
    private String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int idx = text.indexOf('\n');
        return (idx > 0 ? text.substring(0, idx) : text).trim();
    }

    /**
     * 空值转空串。
     *
     * @param value 原值
     * @return 文本
     */
    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 转义 PowerShell 单引号字符串字面量：单引号翻倍（' → ''）。
     *
     * @param value 原值
     * @return 可安全嵌入 PS 单引号字面量的文本
     */
    private String psEscape(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    /**
     * 截断过长文本。
     *
     * @param text 文本
     * @return 结果
     */
    private String cut(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    /**
     * 一次远程会话：主机、凭据、执行通道与远端目录。
     */
    private final class Session {

        /**
         * 主机
        */
        private final MsServerHost host;

        /**
         * 凭据
        */
        private final SshCredential credential;

        /**
         * 执行通道
        */
        private final HostRemoteProvider provider;

        /**
         * 是否 Windows
        */
        private final boolean windows;

        /**
         * 远端安装目录
        */
        private final String dir;

        /**
         * sudo 探测结果缓存
        */
        private boolean sudoChecked;

        /**
         * sudo 前缀
        */
        private String sudoPrefix = "";

        /**
         * 构造会话。
         *
         * @param host 主机
         * @param credential 凭据
         * @param provider 执行通道
         * @param windows 是否 Windows
         * @param dir 远端目录
         */
        private Session(MsServerHost host, SshCredential credential, HostRemoteProvider provider,
                        boolean windows, String dir) {
            this.host = host;
            this.credential = credential;
            this.provider = provider;
            this.windows = windows;
            this.dir = dir;
        }

        /**
         * 按目标系统规则拼路径。
         *
         * @param parts 片段
         * @return 路径
         */
        private String join(String... parts) {
            return provider.join(parts);
        }

        /**
         * 执行远端命令，失败即抛异常并带上原因。
         *
         * @param command 命令
         * @param timeoutSeconds 超时秒
         * @return 执行结果
         */
        private RemoteExecResult exec(String command, int timeoutSeconds) {
            try {
                RemoteExecResult result = provider.exec(host, credential, command, timeoutSeconds);
                if (result != null && !result.success()) {
                    String detail = result.failureMessage();
                    throw new IllegalStateException("远端命令失败: " + firstLine(command) + " -> "
                            + (detail == null || detail.isBlank() ? "退出码 " + result.exitCode() : detail));
                }
                return result;
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("远端命令执行异常: " + firstLine(command) + " -> " + e.getMessage(), e);
            }
        }

        /**
         * 执行探测类命令：非零退出与异常都折算成结果，不抛出。
         *
         * @param command 命令
         * @param timeoutSeconds 超时秒
         * @return 执行结果（失败时 exitCode 为 -1）
         */
        private RemoteExecResult tryExec(String command, int timeoutSeconds) {
            try {
                RemoteExecResult result = provider.exec(host, credential, command, timeoutSeconds);
                return result == null ? RemoteExecResult.ok("") : result;
            } catch (Exception e) {
                log.debug("[agent-deploy] 探测命令未成功 hostId={} cmd={}: {}", host.getId(), command, e.getMessage());
                return new RemoteExecResult(-1, "", e.getMessage() == null ? "探测失败" : e.getMessage());
            }
        }

        /**
         * 上传本地文件到远端路径。
         *
         * @param local 本地文件
         * @param remote 远端路径
         */
        private void put(Path local, String remote) {
            try {
                provider.upload(host, credential, remote, local);
            } catch (Exception e) {
                throw new IllegalStateException("上传 " + remote + " 失败: " + e.getMessage(), e);
            }
        }
    }
}
