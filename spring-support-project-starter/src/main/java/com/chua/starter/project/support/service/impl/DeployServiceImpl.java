package com.chua.starter.project.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.ssh.support.client.SftpClient;
import com.chua.starter.project.support.constants.ProjectConstants;
import com.chua.starter.project.support.entity.MsProject;
import com.chua.starter.project.support.entity.MsProjectDeployLog;
import com.chua.starter.project.support.entity.MsProjectUploadArchive;
import com.chua.starter.project.support.mapper.MsProjectDeployLogMapper;
import com.chua.starter.project.support.mapper.MsProjectMapper;
import com.chua.starter.project.support.mapper.MsProjectUploadArchiveMapper;
import com.chua.starter.project.support.service.DeployService;
import com.chua.starter.project.support.service.ProjectPushService;
import com.chua.starter.server.support.connection.ConnectionManager;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.util.SshClients;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
* 发布操作服务实现（部署 / 升级 / 回滚 / 启停 / 日志）
* <p>
* 依赖 spring-支持-服务端-启动 提供的主机凭据解析、SSH 连接池与
* SFTP 客户端；部署包归档到本地存储支撑版本回溯与按版本回滚；
* 进度经 {@link ProjectPushService} 实时广播。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeployServiceImpl implements DeployService {

    /** 项目 映射器 */
    private final MsProjectMapper projectMapper;

    /** 部署日志 映射器 */
    private final MsProjectDeployLogMapper deployLogMapper;

    /** 部署包归档 映射器 */
    private final MsProjectUploadArchiveMapper archiveMapper;

    /** 服务器主机 映射器（来自 spring-支持-服务端-启动） */
    private final MsServerHostMapper serverHostMapper;

    /** 服务器主机服务（凭据解析） */
    private final ServerHostService serverHostService;

    /** SSH 连接池 */
    private final ConnectionManager connectionManager;

    /** 实时推送 */
    private final ProjectPushService pushService;

    /** 模块配置 */
    private final com.chua.starter.project.support.config.ProjectProperties projectProperties;

    /** Jackson */
    private final ObjectMapper objectMapper;

    /** 项目部署互斥锁（防止同项目并发部署互相清目录） */
    private final java.util.concurrent.ConcurrentHashMap<Long, java.util.concurrent.atomic.AtomicBoolean>
            projectDeployLocks = new java.util.concurrent.ConcurrentHashMap<>();

    /** 命令执行超时控制用线程池（单线程串行，仅用于 Future.get 超时） */
    private final java.util.concurrent.ExecutorService execCommandPool =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "project-exec-cmd");
                t.setDaemon(true);
                return t;
            });

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
    * deploy。
    * @param projectId projectid
    * @param action 动作
    * @param file 文件
    * @return deploy的结果
    */
    public ReturnResult<List<MsProjectDeployLog>> deploy(Long projectId, String action, MultipartFile file) {
        MsProject project = projectId == null ? null : projectMapper.selectById(projectId);
        if (project == null) {
            return ReturnResult.error("项目不存在: " + projectId);
        }
        if (file == null || file.isEmpty()) {
            return ReturnResult.error("部署包不能为空");
        }
        // 生产级：上传包校验（大小 + 扩展名白名单）
        ReturnResult<String> fileCheck = validateUpload(file);
        if (fileCheck != null) {
            return ReturnResult.error(fileCheck.getMsg());
        }
        // 生产级：同项目并发部署互斥
        java.util.concurrent.atomic.AtomicBoolean lock =
                projectDeployLocks.computeIfAbsent(projectId, k -> new java.util.concurrent.atomic.AtomicBoolean(false));
        if (!lock.compareAndSet(false, true)) {
            return ReturnResult.error("该项目正在部署中，请勿并发操作");
        }
        try {
            return doDeploy(project, action, file);
        } finally {
            lock.set(false);
            projectDeployLocks.remove(projectId);
        }
    }

    /**
    * 生产级：上传包校验
    *
    * @param file 上传文件
    * @return 校验失败的错误结果，合法返回 null
    */
    private ReturnResult<String> validateUpload(MultipartFile file) {
        if (projectProperties.getMaxUploadBytes() > 0 && file.getSize() > projectProperties.getMaxUploadBytes()) {
            return ReturnResult.error("部署包超过大小限制 " + (projectProperties.getMaxUploadBytes() / 1024 / 1024) + "MB");
        }
        String name = safeFileName(file.getOriginalFilename());
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : projectProperties.getAllowedExtensions()) {
            if (lower.endsWith(ext)) {
                return null;
            }
        }
        return ReturnResult.error("不支持的文件类型: " + name + "（允许 " + String.join(",", projectProperties.getAllowedExtensions()) + "）");
    }

    /**
    * 部署内部执行（已被并发锁保护）
    * @param project 方法入参 project
    * @param action 方法入参 action
    * @param file 文件，不允许为 null
    * @return Return结果 对象
    */
    private ReturnResult<List<MsProjectDeployLog>> doDeploy(MsProject project, String action, MultipartFile file) {
        Long projectId = project.getId();
        final String act = normalizeAction(action);
        final List<Long> hostIds = parseHostIds(project.getMsProjectHostIds());
        if (hostIds.isEmpty()) {
            return ReturnResult.error("项目未绑定目标服务器");
        }
        final String version = file.getOriginalFilename();
        pushService.pushProgress(projectId, null, act, "VALIDATE", "RUNNING", "校验部署包", version);

        // 部署包落盘临时文件并归档
        Path localFile = null;
        Path archived = null;
        try {
            localFile = Files.createTempFile("ms-project-deploy-", "-" + safeFileName(version));
            /**
            * 尝试。
            * @param file.getInputStream( 文件.获取输入流(
            */
            try (var in = file.getInputStream()) {
                Files.copy(in, localFile, StandardCopyOption.REPLACE_EXISTING);
            }
            archived = archivePackage(projectId, version, localFile);
        } catch (IOException e) {
            return ReturnResult.error("部署包保存失败: " + e.getMessage());
        }

        final Path artifact = localFile;
        final MsProject deployProject = project;
        try {
            List<MsProjectDeployLog> logs = new ArrayList<>();
            // 部署前置门禁：开启时逐台校验主机在线状态与延迟，不可达主机直接拦截（区分网络故障 vs 部署失败）
            List<Long> gateHostIds = hostIds;
            if (isGateEnabled(project)) {
                gateHostIds = new ArrayList<>();
                for (Long hostId : hostIds) {
                    MsServerHost host = serverHostMapper.selectById(hostId);
                    if (host == null) {
                        logs.add(lifecycleLog(project, null, act, "目标服务器不存在: " + hostId, version));
                        continue;
                    }
                    String reject = preflightCheck(host);
                    if (reject == null) {
                        gateHostIds.add(hostId);
                    } else {
                        logs.add(lifecycleLog(project, host, act, reject, version));
                        pushService.pushProgress(projectId, hostId, act, "GATE", "FAIL", reject, version);
                    }
                }
                if (gateHostIds.isEmpty()) {
                    for (MsProjectDeployLog g : logs) {
                        deployLogMapper.insert(g);
                    }
                    pushService.pushProgress(projectId, null, act, "FINISH", "FAIL",
                            "全部目标主机未通过前置门禁（网络不可达/已停用），部署取消", version);
                    return ReturnResult.ok(logs);
                }
            }
            int size = Math.max(1, Math.min(gateHostIds.size(), 8));
            ExecutorService pool = newPool(size);
            try {
                List<java.util.concurrent.Future<MsProjectDeployLog>> futures = new ArrayList<>();
                for (Long hostId : gateHostIds) {
                    futures.add(pool.submit(() -> deployToHost(deployProject, hostId, artifact, version, act)));
                }
                for (java.util.concurrent.Future<MsProjectDeployLog> future : futures) {
                    logs.add(future.get());
                }
            } catch (Exception e) {
                log.warn("[project] 并发部署中断 projectId={}: {}", projectId, e.getMessage());
            } finally {
                pool.shutdownNow();
            }

            boolean anySuccess = logs.stream().anyMatch(
                    l -> ProjectConstants.LOG_STATUS_SUCCESS.equals(l.getMsProjectDeployLogStatus()));
            // 生产级语义：全部主机成功才视为本次版本生效；部分成功不更新版本号，状态置 UNKNOWN
            boolean allSuccess = logs.stream().allMatch(
                    l -> ProjectConstants.LOG_STATUS_SUCCESS.equals(l.getMsProjectDeployLogStatus()));
            if (allSuccess) {
                saveArchives(deployProject, logs, version, archived);
                MsProject update = new MsProject();
                update.setId(project.getId());
                update.setMsProjectCurrentVersion(version);
                update.setMsProjectRuntimeStatus(ProjectConstants.RUNTIME_UNKNOWN);
                projectMapper.updateById(update);
            } else if (anySuccess) {
                // 部分成功：保留旧版本号，仅记录状态
                MsProject update = new MsProject();
                update.setId(project.getId());
                update.setMsProjectRuntimeStatus(ProjectConstants.RUNTIME_UNKNOWN);
                projectMapper.updateById(update);
                log.warn("[project] 项目 {} 部分主机部署成功({}/{})，版本号保持 {}",
                        project.getMsProjectName(),
                        logs.stream().filter(l -> ProjectConstants.LOG_STATUS_SUCCESS.equals(l.getMsProjectDeployLogStatus())).count(),
                        logs.size(), project.getMsProjectCurrentVersion());
            }
            for (MsProjectDeployLog logItem : logs) {
                deployLogMapper.insert(logItem);
            }
            pushService.pushProgress(projectId, null, act, "FINISH",
                    allSuccess ? "SUCCESS" : "FAIL",
                    allSuccess ? "部署完成" : (anySuccess ? "部分主机部署失败" : "部署失败"), version);
            /**
            * 之后deploy。
            * @param project project
            * @param anySuccess 任意成功
            */
            afterDeploy(project, allSuccess);

            // 存在失败主机时：按配置自动回滚到上一版本
            boolean anyFail = logs.stream().anyMatch(
                    l -> ProjectConstants.LOG_STATUS_FAIL.equals(l.getMsProjectDeployLogStatus()));
            if (anyFail) {
                autoRollbackOnFail(project, version, act);
            }
            return ReturnResult.ok(logs);
        } finally {
            if (localFile != null) {
                try {
                    Files.deleteIfExists(localFile);
                } catch (IOException ignored) {
                    // 临时文件清理失败交由系统临时目录兜底
                }
            }
        }
    }

    /**
    * 部署前置门禁：校验目标主机在线状态与延迟
    * <p>
    * 依据主机台账 {@code serverHostOnline} / {@code serverHostDelayMs}（由
    * server-starter 的 HostOnlineScheduler 周期探测写回）判定：
    * 停用、探测离线、延迟不可达（-1）任一命中即拦截，返回拦截原因；
    * 全部通过返回 空。
    * </p>
    *
    * @param host 主机
    * @return 拦截原因，通过返回 空
    */
    private String preflightCheck(MsServerHost host) {
        if (host.getServerHostStatus() != null && host.getServerHostStatus() != 1) {
            return "前置门禁：主机 " + host.getServerHostName() + " 已停用";
        }
        // 优先用 HostOnlineScheduler 填的探测结果
        if (host.getServerHostOnline() != null && host.getServerHostOnline() == 0) {
            return "前置门禁：主机 " + host.getServerHostName() + " 最近探测离线（网络不可达）";
        }
        if (host.getServerHostDelayMs() != null && host.getServerHostDelayMs() < 0) {
            return "前置门禁：主机 " + host.getServerHostName() + " 最近延迟探测不可达";
        }
        // HostOnlineScheduler 未填充（delay 探测未开）时，做一次实时 TCP 探测兜底
        if (host.getServerHostDelayMs() == null) {
            boolean reachable = isTcpReachable(host);
            if (!reachable) {
                return "前置门禁：主机 " + host.getServerHostName() + " 实时 TCP 探测不可达（"
                        + (host.getServerHostAddress() == null ? "?" : host.getServerHostAddress())
                        + ":" + (host.getServerHostPort() != null ? host.getServerHostPort() : 22) + "）";
            }
        }
        return null;
    }

    /**
    * TCP 连通探测（异步，超时 3 秒）
    *
    * @param host 主机
    * @return 是否可达
    */
    private boolean isTcpReachable(MsServerHost host) {
        if (host.getServerHostAddress() == null || host.getServerHostAddress().isBlank()) {
            return false;
        }
        int port = host.getServerHostPort() != null && host.getServerHostPort() > 0
                ? host.getServerHostPort() : 22;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean reachable = new java.util.concurrent.atomic.AtomicBoolean(false);
        try {
            // 用新线程做 3 秒 TCP connect
            Thread probe = new Thread(() -> {
                try (java.net.Socket s = new java.net.Socket()) {
                    s.connect(new java.net.InetSocketAddress(host.getServerHostAddress(), port), 3000);
                    reachable.set(true);
                } catch (Exception ignored) {
                    // 连接失败 = 不可达
                } finally {
                    latch.countDown();
                }
            }, "gate-tcp-probe-" + host.getId());
            probe.setDaemon(true);
            probe.start();
            latch.await(4, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return reachable.get();
    }

    /**
    * 前置门禁是否开启
    *
    * @param project 项目
    * @return 是否开启
    */
    private boolean isGateEnabled(MsProject project) {
        return project.getMsProjectPreflightGate() != null && project.getMsProjectPreflightGate() == 1;
    }

    /**
    * 部署失败自动回滚：项目开启 autoRollback 时，若存在失败主机，回滚到归档中最近的历史版本
    *
    * @param project 项目
    * @param failedVersion 本次失败版本
    * @param action 操作类型
    */
    private void autoRollbackOnFail(MsProject project, String failedVersion, String action) {
        if (project.getMsProjectAutoRollback() == null || project.getMsProjectAutoRollback() != 1) {
            return;
        }
        // 查找归档中最近一次成功的、非当前失败版本的版本
        List<MsProjectUploadArchive> latest = archiveMapper.selectList(
                new LambdaQueryWrapper<MsProjectUploadArchive>()
                        .eq(MsProjectUploadArchive::getMsProjectUploadArchiveProjectId, project.getId())
                        .eq(MsProjectUploadArchive::getMsProjectUploadArchiveStatus, ProjectConstants.LOG_STATUS_SUCCESS)
                        .ne(MsProjectUploadArchive::getMsProjectUploadArchiveVersion, failedVersion)
                        .orderByDesc(MsProjectUploadArchive::getMsProjectUploadArchiveDeployTime)
                        .last("LIMIT 1"));
        if (latest.isEmpty()) {
            log.warn("[project] 项目 {} 开启自动回滚但无历史版本可回滚", project.getMsProjectName());
            return;
        }
        MsProjectUploadArchive target = latest.getFirst();
        Path archived = Path.of(target.getMsProjectUploadArchiveUploadPath());
        if (!Files.exists(archived)) {
            log.warn("[project] 自动回滚失败：归档文件缺失 {}", target.getMsProjectUploadArchiveUploadPath());
            return;
        }
        log.info("[project] 项目 {} 部署失败，自动回滚到版本 {}", project.getMsProjectName(), target.getMsProjectUploadArchiveVersion());
        pushService.pushProgress(project.getId(), null, ProjectConstants.ACTION_ROLLBACK, "ROLLBACK",
                "RUNNING", "部署失败，自动回滚到 " + target.getMsProjectUploadArchiveVersion(), target.getMsProjectUploadArchiveVersion());
        rollbackInternal(project, target, archived, action);
    }

    /**
    * 回滚内部逻辑（供自动回滚与手动回滚复用）
    *
    * @param project  项目
    * @param archive  归档记录
    * @param archived 归档文件路径
    * @param action   原操作类型
    */
    private ReturnResult<List<MsProjectDeployLog>> rollbackInternal(MsProject project, MsProjectUploadArchive archive,
                                                                    Path archived, String action) {
        final List<Long> hostIds = parseHostIds(project.getMsProjectHostIds());
        if (hostIds.isEmpty()) {
            return ReturnResult.error("项目未绑定目标服务器");
        }
        final String version = archive.getMsProjectUploadArchiveVersion();
        pushService.pushProgress(project.getId(), null, ProjectConstants.ACTION_ROLLBACK, "VALIDATE",
                "RUNNING", "开始回滚到版本 " + version, version);

        final MsProject deployProject = project;
        final Path artifact = archived;
        final String act = ProjectConstants.ACTION_ROLLBACK;
        List<MsProjectDeployLog> logs = new ArrayList<>();
        int size = Math.max(1, Math.min(hostIds.size(), 8));
        ExecutorService pool = newPool(size);
        try {
            List<java.util.concurrent.Future<MsProjectDeployLog>> futures = new ArrayList<>();
            for (Long hostId : hostIds) {
                futures.add(pool.submit(() -> deployToHost(deployProject, hostId, artifact, version, act)));
            }
            for (java.util.concurrent.Future<MsProjectDeployLog> future : futures) {
                logs.add(future.get());
            }
        } catch (Exception e) {
            log.warn("[project] 并发回滚中断 projectId={}: {}", project.getId(), e.getMessage());
        } finally {
            pool.shutdownNow();
        }
        boolean anySuccess = logs.stream().anyMatch(
                l -> ProjectConstants.LOG_STATUS_SUCCESS.equals(l.getMsProjectDeployLogStatus()));
        if (anySuccess) {
            saveArchives(deployProject, logs, version, archived);
            MsProject update = new MsProject();
            update.setId(project.getId());
            update.setMsProjectCurrentVersion(version);
            update.setMsProjectRuntimeStatus(ProjectConstants.RUNTIME_UNKNOWN);
            projectMapper.updateById(update);
        }
        for (MsProjectDeployLog logItem : logs) {
            deployLogMapper.insert(logItem);
        }
        pushService.pushProgress(project.getId(), null, act, "FINISH",
                anySuccess ? "SUCCESS" : "FAIL", anySuccess ? "回滚完成" : "回滚失败", version);
        afterDeploy(project, anySuccess);
        return ReturnResult.ok(logs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
    * 启动。
    * @param projectId projectid
    * @return 启动的结果
    */
    public ReturnResult<List<MsProjectDeployLog>> start(Long projectId) {
        return lifecycle(projectId, ProjectConstants.ACTION_START, ProjectConstants.RUNTIME_RUNNING);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
    * 停止。
    * @param projectId projectid
    * @return 停止的结果
    */
    public ReturnResult<List<MsProjectDeployLog>> stop(Long projectId) {
        return lifecycle(projectId, ProjectConstants.ACTION_STOP, ProjectConstants.RUNTIME_STOPPED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
    * restart。
    * @param projectId projectid
    * @return restart的结果
    */
    public ReturnResult<List<MsProjectDeployLog>> restart(Long projectId) {
        return lifecycle(projectId, ProjectConstants.ACTION_RESTART, ProjectConstants.RUNTIME_RUNNING);
    }

    @Override
    public ReturnResult<List<MsProjectDeployLog>> listLogs(Long projectId, String action) {
        LambdaQueryWrapper<MsProjectDeployLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, MsProjectDeployLog::getMsProjectDeployLogProjectId, projectId);
        wrapper.eq(action != null && !action.isBlank(), MsProjectDeployLog::getMsProjectDeployLogAction, action);
        wrapper.orderByDesc(MsProjectDeployLog::getMsProjectDeployLogStartTime)
                .last("LIMIT 200");
        return ReturnResult.ok(deployLogMapper.selectList(wrapper));
    }

    @Override
    public ReturnResult<List<MsProjectUploadArchive>> listArchives(Long projectId, Long hostId) {
        LambdaQueryWrapper<MsProjectUploadArchive> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, MsProjectUploadArchive::getMsProjectUploadArchiveProjectId, projectId);
        wrapper.eq(hostId != null, MsProjectUploadArchive::getMsProjectUploadArchiveHostId, hostId);
        wrapper.eq(MsProjectUploadArchive::getMsProjectUploadArchiveStatus, ProjectConstants.LOG_STATUS_SUCCESS);
        wrapper.orderByDesc(MsProjectUploadArchive::getMsProjectUploadArchiveDeployTime)
                .last("LIMIT 200");
        return ReturnResult.ok(archiveMapper.selectList(wrapper));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
    * rollback。
    * @param projectId projectid
    * @param archiveId Arch Linux Linux Linuxid
    * @return rollback的结果
    */
    public ReturnResult<List<MsProjectDeployLog>> rollback(Long projectId, Long archiveId) {
        MsProject project = projectId == null ? null : projectMapper.selectById(projectId);
        if (project == null) {
            return ReturnResult.error("项目不存在: " + projectId);
        }
        MsProjectUploadArchive archive = archiveId == null ? null : archiveMapper.selectById(archiveId);
        if (archive == null || archive.getMsProjectUploadArchiveUploadPath() == null) {
            return ReturnResult.error("归档记录不存在或已失效: " + archiveId);
        }
        Path archived = Path.of(archive.getMsProjectUploadArchiveUploadPath());
        if (!Files.exists(archived)) {
            return ReturnResult.error("归档包文件缺失: " + archive.getMsProjectUploadArchiveVersion());
        }
        return rollbackInternal(project, archive, archived, archive.getMsProjectUploadArchiveAction());
    }

    @Override
    public ReturnResult<Map<String, Object>> tailLog(Long projectId, Long hostId, Long cursor, Integer lines) {
        MsProject project = projectId == null ? null : projectMapper.selectById(projectId);
        if (project == null) {
            return ReturnResult.error("项目不存在: " + projectId);
        }
        if (hostId == null) {
            return ReturnResult.error("请指定目标主机");
        }
        if (isBlank(project.getMsProjectLogDir()) || isBlank(project.getMsProjectLogFile())) {
            return ReturnResult.error("项目未配置日志目录/日志文件");
        }
        MsServerHost host = serverHostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("目标服务器不存在: " + hostId);
        }
        ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, null);
        if (!resolved.isOk()) {
            return ReturnResult.error(resolved.getMsg());
        }
        String file = project.getMsProjectLogDir().endsWith("/")
                ? project.getMsProjectLogDir() + project.getMsProjectLogFile()
                : project.getMsProjectLogDir() + "/" + project.getMsProjectLogFile();
        int tailLines = lines == null || lines <= 0 ? 500 : Math.min(lines, 2000);
        String command = buildTailCommand(file, cursor, tailLines, isLinux(host));
        try {
            String output = execOutput(host, resolved.getData(), command);
            long nextCursor = cursor == null ? output.length() : cursor + output.length();
            Map<String, Object> data = new HashMap<>(4);
            data.put("content", output);
            data.put("cursor", nextCursor);
            data.put("eof", output.isEmpty());
            return ReturnResult.ok(data);
        } catch (Exception e) {
            return ReturnResult.error("日志读取失败: " + e.getMessage());
        }
    }

    /**
    * 构建 tail 命令
    * @param file 文件
    * @param cursor Cursor
    * @param lines 线
    * @param linux Linux
    * @return 构建tail命令的结果
    */
    private String buildTailCommand(String file, Long cursor, int lines, boolean linux) {
        if (linux) {
            if (cursor != null && cursor > 0) {
                // 增量：从上次字节偏移 + 1 起读取（tail -c +N 从第 N 字节开始）
                return "tail -c +" + (cursor + 1) + " " + quote(file) + " 2>/dev/null";
            }
            return "tail -n " + lines + " " + quote(file) + " 2>/dev/null";
        }
        if (cursor != null && cursor > 0) {
            return "powershell -NoProfile -Command \"$c=[System.IO.File]::Open(\'" + file.replace("'", "''")
                    + "\','Open','Read','ReadWrite'); $c.Position=" + cursor + "; $r=$c.ReadToEnd(); $c.Close(); Write-Output $r\"";
        }
        return "powershell -NoProfile -Command \"Get-Content -Path '" + file.replace("'", "''")
                + "' -Tail " + lines + "\"";
    }

    /**
    * SSH 执行命令并返回标准输出
    * @param host 主机
    * @param credential credential
    * @param command 命令
    * @return 执行输出的结果
    */
    private String execOutput(MsServerHost host, SshCredential credential, String command) throws Exception {
        SshClients.Handle handle = null;
        try {
            handle = connectionManager.borrow(host, credential);
            var result = handle.client().exec().command(command).execute();
            return result.stdout();
        } finally {
            if (handle != null) {
                connectionManager.release(host.getId());
            }
        }
    }

    /**
    * 执行命令并返回是否成功（退出码 0）
    *
    * @param host       主机
    * @param credential 凭据
    * @param command    命令
    * @param timeout    超时（秒）
    * @return 退出码是否为 0
    * @throws Exception 连接失败时抛出
    */
    private boolean execCommand(MsServerHost host, SshCredential credential, String command, int timeout) throws Exception {
        SshClients.Handle handle = null;
        try {
            handle = connectionManager.borrow(host, credential);
            // 生产级：Future 超时控制，避免远端命令挂起导致整个部署卡死
            final SshClients.Handle borrowed = handle;
            java.util.concurrent.Future<Boolean> future = execCommandPool.submit(() -> {
                var result = borrowed.client().exec().command(command).execute();
                return result.exitCode() == 0;
            });
            int t = timeout > 0 ? timeout : projectProperties.getScriptTimeoutSeconds();
            try {
                return future.get(t, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException te) {
                future.cancel(true);
                borrowed.client().close();
                throw new IllegalStateException("命令执行超时(>" + t + "s): " + command);
            }
        } finally {
            if (handle != null) {
                connectionManager.release(host.getId());
            }
        }
    }

    /**
    * 部署/回滚成功后处理：自动启动 → 健康检查
    * @param project project
    * @param anySuccess 任意成功
    */
    private void afterDeploy(MsProject project, boolean anySuccess) {
        if (!anySuccess) {
            return;
        }
        // 自动启动
        if (project.getMsProjectAutoStart() != null && project.getMsProjectAutoStart() == 1
                && !isBlank(project.getMsProjectStartScript())) {
            pushService.pushProgress(project.getId(), null, ProjectConstants.ACTION_START, "EXEC",
                    "RUNNING", "自动执行启动脚本", project.getMsProjectCurrentVersion());
            lifecycle(project.getId(), ProjectConstants.ACTION_START, ProjectConstants.RUNTIME_RUNNING);
        }
        // 健康检查（启动后探测）
        if (project.getMsProjectHealthEnabled() != null && project.getMsProjectHealthEnabled() == 1
                && !isBlank(project.getMsProjectHealthTarget())) {
            pushService.pushProgress(project.getId(), null, ProjectConstants.ACTION_START, "HEALTH",
                    "RUNNING", "健康检查中", project.getMsProjectCurrentVersion());
            boolean healthy = healthCheck(project);
            MsProject update = new MsProject();
            update.setId(project.getId());
            update.setMsProjectRuntimeStatus(healthy
                    ? ProjectConstants.RUNTIME_HEALTHY
                    : ProjectConstants.RUNTIME_UNHEALTHY);
            projectMapper.updateById(update);
            pushService.pushProgress(project.getId(), null, ProjectConstants.ACTION_START, "HEALTH",
                    healthy ? "SUCCESS" : "FAIL",
                    healthy ? "健康检查通过" : "健康检查失败，服务未就绪", project.getMsProjectCurrentVersion());
        }
    }

    /**
    * 健康检查：对所有绑定主机执行探测
    * @param project 方法入参 project
    * @return 是否成功（true 表示成功）
    */
    private boolean healthCheck(MsProject project) {
        List<Long> hostIds = parseHostIds(project.getMsProjectHostIds());
        int timeout = project.getMsProjectHealthTimeout() != null ? project.getMsProjectHealthTimeout() : 30;
        for (Long hostId : hostIds) {
            MsServerHost host = serverHostMapper.selectById(hostId);
            if (host == null) {
                return false;
            }
            try {
                ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, null);
                if (!resolved.isOk()) {
                    return false;
                }
                boolean pass = doHealthCheck(host, resolved.getData(), project, timeout);
                if (!pass) {
                    return false;
                }
            } catch (Exception e) {
                log.warn("[project] 健康检查异常 hostId={}: {}", hostId, e.getMessage());
                return false;
            }
        }
        return true;
    }

    /**
    * 单主机健康检查
    */
    private boolean doHealthCheck(MsServerHost host, SshCredential credential,
                                   MsProject project, int timeoutSec) throws Exception {
        String target = project.getMsProjectHealthTarget();
        boolean linux = isLinux(host);
        String cmd = switch (project.getMsProjectHealthType()) {
            case ProjectConstants.HEALTH_TYPE_PORT ->
                    linux ? "ss -tlnp 2>/dev/null | grep -q ':" + target + "'" :
                            "netstat -ano 2>nul | findstr :" + target + " >nul 2>&1";
            case ProjectConstants.HEALTH_TYPE_HTTP ->
                    linux ? "curl -sI --max-time " + Math.min(timeoutSec, 10) + " http://" + target + " >/dev/null 2>&1" :
                            "powershell -NoProfile -Command \"try { Invoke-WebRequest -Uri 'http://" + target + "' -UseBasicParsing -TimeoutSec 5 | Out-Null } catch { exit 1 }\"";
            case ProjectConstants.HEALTH_TYPE_PROCESS ->
                    linux ? "pgrep -f '" + target + "'" :
                            "tasklist 2>nul | findstr /i " + target;
            default -> "echo unknown_type";
        };
        return execCommand(host, credential, cmd, Math.max(timeoutSec, 10));
    }

    /**
    * 生命周期操作（启动/停止/重启）
    * @param projectId projectid
    * @param action 动作
    * @param targetStatus Target状态
    * @return lifecycle的结果
    */
    private ReturnResult<List<MsProjectDeployLog>> lifecycle(Long projectId, String action, String targetStatus) {
        MsProject project = projectId == null ? null : projectMapper.selectById(projectId);
        if (project == null) {
            return ReturnResult.error("项目不存在: " + projectId);
        }
        String script = switch (action) {
            case ProjectConstants.ACTION_START -> project.getMsProjectStartScript();
            case ProjectConstants.ACTION_STOP -> project.getMsProjectStopScript();
            case ProjectConstants.ACTION_RESTART -> project.getMsProjectRestartScript();
            default -> null;
        };
        if (isBlank(script)) {
            return ReturnResult.error("项目未配置" + action + "脚本");
        }
        // 项目级互斥锁：防止同项目 start/stop/restart 并发操作互相踩目录
        java.util.concurrent.atomic.AtomicBoolean lock =
                projectDeployLocks.computeIfAbsent(projectId, k -> new java.util.concurrent.atomic.AtomicBoolean(false));
        if (!lock.compareAndSet(false, true)) {
            return ReturnResult.error("该项目正在操作中（" + action + "），请勿并发操作");
        }
        try {
            return lifecycleInternal(project, action, targetStatus);
        } finally {
            lock.set(false);
            projectDeployLocks.remove(projectId);
        }
    }

    /**
     * lifecycleInternal。
     *
     * @param project 方法入参 project
     * @param action 方法入参 action
     * @param targetStatus 目标状态，不允许为 null
     * @return Return结果 对象
     */
    private ReturnResult<List<MsProjectDeployLog>> lifecycleInternal(MsProject project, String action, String targetStatus) {
        String script = switch (action) {
            case ProjectConstants.ACTION_START -> project.getMsProjectStartScript();
            case ProjectConstants.ACTION_STOP -> project.getMsProjectStopScript();
            case ProjectConstants.ACTION_RESTART -> project.getMsProjectRestartScript();
            default -> null;
        };
        if (isBlank(script)) {
            return ReturnResult.error("项目未配置" + action + "脚本");
        }
        List<Long> hostIds = parseHostIds(project.getMsProjectHostIds());
        if (hostIds.isEmpty()) {
            return ReturnResult.error("项目未绑定目标服务器");
        }
        String command = renderScript(script, project.getMsProjectDeployDir(), isLinuxHostOs(project.getMsProjectHostOs()));
        List<MsProjectDeployLog> logs = new ArrayList<>();
        for (Long hostId : hostIds) {
            MsServerHost host = serverHostMapper.selectById(hostId);
            logs.add(host == null ? lifecycleLog(project, null, action, "目标服务器不存在: " + hostId, null)
                    : execLifecycle(project, host, action, command));
        }
        boolean anySuccess = logs.stream().anyMatch(
                l -> ProjectConstants.LOG_STATUS_SUCCESS.equals(l.getMsProjectDeployLogStatus()));
        if (anySuccess) {
            MsProject update = new MsProject();
            update.setId(project.getId());
            update.setMsProjectRuntimeStatus(targetStatus);
            projectMapper.updateById(update);
        }
        for (MsProjectDeployLog logItem : logs) {
            deployLogMapper.insert(logItem);
        }
        return ReturnResult.ok(logs);
    }

    /**
    * 单主机生命周期执行
    * @param project project
    * @param host 主机
    * @param action 动作
    * @param command 命令
    * @return 执行lifecycle的结果
    */
    private MsProjectDeployLog execLifecycle(MsProject project, MsServerHost host, String action, String command) {
        long startMs = System.currentTimeMillis();
        MsProjectDeployLog item = newLog(project, host, action, null);
        item.setMsProjectDeployLogStartTime(LocalDateTime.now());
        ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(host.getId(), null);
        if (!resolved.isOk()) {
            return fail(item, resolved.getMsg(), startMs);
        }
        SshClients.Handle handle = null;
        try {
            handle = connectionManager.borrow(host, resolved.getData());
            var result = handle.client().exec().command(command).execute();
            item.setMsProjectDeployLogOutput(truncate(result.stdout()));
            if (result.exitCode() != 0) {
                String error = isBlank(result.stderr()) ? "退出码非0: " + result.exitCode() : result.stderr();
                return fail(item, error, startMs);
            }
            item.setMsProjectDeployLogStatus(ProjectConstants.LOG_STATUS_SUCCESS);
        } catch (Exception e) {
            return fail(item, e.getMessage(), startMs);
        } finally {
            if (handle != null) {
                connectionManager.release(host.getId());
            }
        }
        /**
        * fillcost。
        * @param item item
        * @param startMs 启动ms
        */
        fillCost(item, startMs);
        return item;
    }

    /**
    * 单主机部署执行
    *
    * @param project   项目
    * @param hostId    主机 标识
    * @param localFile 本地部署包
    * @param version   版本标识
    * @param action    操作类型 DEPLOY/UPGRADE/ROLLBACK
    * @return 部署日志
    */
    private MsProjectDeployLog deployToHost(MsProject project, Long hostId, Path localFile, String version, String action) {
        int retryTimes = project.getMsProjectDeployRetry() != null ? project.getMsProjectDeployRetry() : 0;
        MsProjectDeployLog last = deployOnce(project, hostId, localFile, version, action);
        if (ProjectConstants.LOG_STATUS_SUCCESS.equals(last.getMsProjectDeployLogStatus()) || retryTimes <= 0) {
            return last;
        }
        MsServerHost host = serverHostMapper.selectById(hostId);
        String hostName = host != null ? host.getServerHostName() : String.valueOf(hostId);
        for (int attempt = 1; attempt <= retryTimes; attempt++) {
            log.warn("[project] 主机 {} 部署失败，第 {} 次重试", hostName, attempt);
            pushService.pushProgress(project.getId(), hostId, action, "RETRY", "RUNNING",
                    "部署失败，第 " + attempt + " 次重试: " + hostName, version);
            try {
                Thread.sleep(3000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return last;
            }
            MsProjectDeployLog retried = deployOnce(project, hostId, localFile, version, action);
            if (ProjectConstants.LOG_STATUS_SUCCESS.equals(retried.getMsProjectDeployLogStatus())) {
                return retried;
            }
            last = retried;
        }
        return last;
    }

    /**
    * 单主机单次部署执行（不含重试）
    *
    * @param project   项目
    * @param hostId    主机 ID
    * @param localFile 本地部署包
    * @param version   版本标识
    * @param action    操作类型 DEPLOY/UPGRADE/ROLLBACK
    * @return 部署日志
    */
    private MsProjectDeployLog deployOnce(MsProject project, Long hostId, Path localFile, String version, String action) {
        MsServerHost host = serverHostMapper.selectById(hostId);
        if (host == null) {
            return lifecycleLog(project, null, action, "目标服务器不存在: " + hostId, version);
        }
        long startMs = System.currentTimeMillis();
        MsProjectDeployLog item = newLog(project, host, action, version);
        item.setMsProjectDeployLogStartTime(LocalDateTime.now());
        pushService.pushProgress(project.getId(), hostId, action, "UPLOAD", "RUNNING",
                "开始部署到 " + host.getServerHostName(), version);
        ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(host.getId(), null);
        if (!resolved.isOk()) {
            pushService.pushProgress(project.getId(), hostId, action, "UPLOAD", "FAIL",
                    resolved.getMsg(), version);
            return fail(item, resolved.getMsg(), startMs);
        }
        boolean linux = isLinux(host);
        String dir = project.getMsProjectDeployDir();
        try {
            boolean shouldClear = project.getMsProjectDeleteBefore() != null
                    && project.getMsProjectDeleteBefore() == 1
                    || ProjectConstants.OVERWRITE_MODE_REPLACE.equals(project.getMsProjectOverwriteMode());
            // 1. 按策略清空部署目录
            if (shouldClear) {
                pushService.pushProgress(project.getId(), hostId, action, "CLEAN", "RUNNING",
                        "清空部署目录", version);
                /**
                * 执行ssh。
                * @param host 主机
                * @param resolved.getData( resolved.获取数据(
                */
                execSsh(host, resolved.getData(), clearDirCommand(dir, linux), item);
            }
            boolean autoExtract = project.getMsProjectAutoExtract() != null
                    && project.getMsProjectAutoExtract() == 1;
            String fileName = safeFileName(version);
            if (autoExtract && isArchive(fileName)) {
                // 2. 压缩包：上传到临时目录后解压到部署目录
                String tempDir = tempDirFor(host, project.getId());
                String remoteArchive = tempDir + "/" + fileName;
                /**
                * mkdir远程。
                * @param host 主机
                * @param resolved.getData( resolved.获取数据(
                */
                mkdirRemote(host, resolved.getData(), tempDir);
                /**
                * uploadsftp。
                * @param host 主机
                * @param resolved.getData( resolved.获取数据(
                */
                uploadSftp(host, resolved.getData(), remoteArchive, localFile);
                pushService.pushProgress(project.getId(), hostId, action, "EXTRACT", "RUNNING",
                        "解压到部署目录", version);
                /**
                * 执行ssh。
                * @param host 主机
                * @param resolved.getData( resolved.获取数据(
                */
                execSsh(host, resolved.getData(), extractCommand(fileName, remoteArchive, dir, linux), item);
                /**
                * 执行ssh。
                * @param host 主机
                * @param resolved.getData( resolved.获取数据(
                */
                execSsh(host, resolved.getData(), rmRemote(remoteArchive), item);
            } else {
                // 3. 单体文件（jar/war 等）：直接上传覆盖到部署目录
                /**
                * mkdir远程。
                * @param host 主机
                * @param resolved.getData( resolved.获取数据(
                */
                mkdirRemote(host, resolved.getData(), dir);
                String remotePath = dir.endsWith("/") ? dir + fileName : dir + "/" + fileName;
                /**
                * uploadsftp。
                * @param host 主机
                * @param resolved.getData( resolved.获取数据(
                */
                uploadSftp(host, resolved.getData(), remotePath, localFile);
            }
            item.setMsProjectDeployLogStatus(ProjectConstants.LOG_STATUS_SUCCESS);
            pushService.pushProgress(project.getId(), hostId, action, "FINISH", "SUCCESS",
                    "部署成功: " + host.getServerHostName(), version);
        } catch (Exception e) {
            pushService.pushProgress(project.getId(), hostId, action, "FINISH", "FAIL",
                    e.getMessage(), version);
            return fail(item, e.getMessage(), startMs);
        }
        /**
        * fillcost。
        * @param item item
        * @param startMs 启动ms
        */
        fillCost(item, startMs);
        return item;
    }

    /**
    * 部署包归档：复制到本地归档目录并计算 MD5
    *
    * @param projectId 项目 标识
    * @param version   版本标识
    * @param localFile 临时部署包
    * @return 归档路径
    * @throws IOException 归档失败时抛出
    */
    private Path archivePackage(Long projectId, String version, Path localFile) throws IOException {
        Path archiveDir = Path.of(projectProperties.getArchiveDir(), String.valueOf(projectId));
        Files.createDirectories(archiveDir);
        String fileName = safeFileName(version);
        Path target = archiveDir.resolve(fileName);
        Files.copy(localFile, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    /**
    * 保存归档记录（按成功主机逐条）
    * @param project project
    * @param logs 日志
    * @param version 版本
    * @param archived archived
    */
    private void saveArchives(MsProject project, List<MsProjectDeployLog> logs, String version, Path archived) {
        for (MsProjectDeployLog logItem : logs) {
            if (!ProjectConstants.LOG_STATUS_SUCCESS.equals(logItem.getMsProjectDeployLogStatus())) {
                continue;
            }
            MsProjectUploadArchive archive = new MsProjectUploadArchive();
            archive.setMsProjectUploadArchiveProjectId(project.getId());
            archive.setMsProjectUploadArchiveProjectName(project.getMsProjectName());
            archive.setMsProjectUploadArchiveHostId(logItem.getMsProjectDeployLogHostId());
            archive.setMsProjectUploadArchiveHostName(logItem.getMsProjectDeployLogHostName());
            archive.setMsProjectUploadArchiveAction(logItem.getMsProjectDeployLogAction());
            archive.setMsProjectUploadArchiveVersion(version);
            archive.setMsProjectUploadArchiveType(isArchive(safeFileName(version)) ? "COMPRESSED" : "SINGLE_FILE");
            archive.setMsProjectUploadArchiveUploadPath(archived == null ? null : archived.toString());
            archive.setMsProjectUploadArchiveDeployDir(project.getMsProjectDeployDir());
            archive.setMsProjectUploadArchiveStatus(ProjectConstants.LOG_STATUS_SUCCESS);
            archive.setMsProjectUploadArchiveDeployTime(logItem.getMsProjectDeployLogEndTime());
            try {
                if (archived != null && Files.exists(archived)) {
                    archive.setMsProjectUploadArchiveFileSize(Files.size(archived));
                    archive.setMsProjectUploadArchiveMd5(md5(archived));
                }
            } catch (IOException ignored) {
                // 归档元信息读取失败不影响主流程
            }
            archiveMapper.insert(archive);
        }
        // 归档保留策略：超出数量自动清理最旧版本
        cleanArchives(project);
    }

    /**
    * 归档保留策略：保留最近 N 个版本，超出清理最旧（归档表 + 本地文件同步删除）
    *
    * @param project 项目
    */
    private void cleanArchives(MsProject project) {
        Integer keep = project.getMsProjectArchiveKeep();
        if (keep == null || keep <= 0) {
            return;
        }
        List<MsProjectUploadArchive> all = archiveMapper.selectList(
                new LambdaQueryWrapper<MsProjectUploadArchive>()
                        .eq(MsProjectUploadArchive::getMsProjectUploadArchiveProjectId, project.getId())
                        .orderByDesc(MsProjectUploadArchive::getMsProjectUploadArchiveDeployTime));
        if (all.size() <= keep) {
            return;
        }
        for (int i = keep; i < all.size(); i++) {
            MsProjectUploadArchive old = all.get(i);
            try {
                if (old.getMsProjectUploadArchiveUploadPath() != null) {
                    Files.deleteIfExists(Path.of(old.getMsProjectUploadArchiveUploadPath()));
                }
            } catch (Exception ignored) {
                // 归档文件清理失败不影响主流程
            }
            archiveMapper.deleteById(old.getId());
            log.info("[project] 归档保留策略清理: {}@{}", project.getMsProjectName(),
                    old.getMsProjectUploadArchiveVersion());
        }
    }

    /**
    * 计算文件 MD5
    * @param file 文件
    * @return md5的结果
    */
    private String md5(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (Exception e) {
            return null;
        }
        /**
        * 尝试。
        * @param Files.newInputStream(file 文件.新输入流(文件
        */
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
    * 创建远程目录（递归）
    * @param host 主机
    * @param credential credential
    * @param path 路径
    */
    private void mkdirRemote(MsServerHost host, SshCredential credential, String path) throws Exception {
        /**
        * 尝试。
        * @param credential credential
        */
        try (var client = buildConnectedSftp(host, credential)) {
            client.mkdir().path(path).recursive(true).exec();
        }
    }

    /**
    * SFTP 上传本地文件到远端路径
    * @param host 主机
    * @param credential credential
    * @param remotePath 远程路径
    * @param localFile 本地文件
    */
    private void uploadSftp(MsServerHost host, SshCredential credential, String remotePath, Path localFile) throws Exception {
        /**
        * 尝试。
        * @param credential credential
        */
        try (var client = buildConnectedSftp(host, credential)) {
            client.upload().local(localFile.toString()).remote(remotePath).exec();
        }
    }

    /**
    * 构建并连接 SFTP 客户端（凭据装配后立即清理私钥临时文件）
    * @param host 主机
    * @param credential credential
    * @return 构建连接sftp的结果
    */
    private SftpClient buildConnectedSftp(MsServerHost host, SshCredential credential) throws IOException {
        var builder = SftpClient.builder()
                .host(host.getServerHostAddress())
                .port(host.getServerHostPort() == null ? 22 : host.getServerHostPort())
                .username(credential.username())
                .connectTimeout(8);
        Path tempKey = null;
        try {
            if (credential.hasKey()) {
                tempKey = Files.createTempFile("ms-project-key-", ".pem");
                Files.writeString(tempKey, credential.privateKey());
                builder.privateKey(tempKey.toString());
                if (notBlank(credential.password())) {
                    builder.password(credential.password());
                }
            } else {
                builder.password(credential.password() == null ? "" : credential.password());
            }
            var client = builder.build();
            client.connect();
            return client;
        } finally {
            if (tempKey != null) {
                try {
                    Files.deleteIfExists(tempKey);
                } catch (IOException ignored) {
                    // 临时私钥清理失败不影响主流程
                }
            }
        }
    }

    /**
    * SSH 执行命令并填充日志输出，退出码非 0 抛出异常
    * @param host 主机
    * @param credential credential
    * @param command 命令
    * @param item item
    */
    private void execSsh(MsServerHost host, SshCredential credential, String command, MsProjectDeployLog item) throws Exception {
        SshClients.Handle handle = null;
        try {
            handle = connectionManager.borrow(host, credential);
            var result = handle.client().exec().command(command).execute();
            if (item.getMsProjectDeployLogOutput() == null) {
                item.setMsProjectDeployLogOutput(truncate(result.stdout()));
            } else {
                item.setMsProjectDeployLogOutput(truncate(item.getMsProjectDeployLogOutput() + "\n" + result.stdout()));
            }
            if (result.exitCode() != 0) {
                String error = isBlank(result.stderr()) ? "退出码非0: " + result.exitCode() : result.stderr();
                /**
                * illegal状态异常。
                * @param error 错误
                * @return illegal状态异常的结果
                */
                throw new IllegalStateException(error);
            }
        } finally {
            if (handle != null) {
                connectionManager.release(host.getId());
            }
        }
    }

    /**
    * 清空部署目录命令
    * @param dir dir
    * @param linux Linux
    * @return cleardir命令的结果
    */
    private String clearDirCommand(String dir, boolean linux) {
        if (linux) {
            return "mkdir -p " + quote(dir) + " && rm -rf " + quote(dir) + "/*";
        }
        return "if exist " + quote(dir) + " ( rd /s /q " + quote(dir) + " ) & mkdir " + quote(dir);
    }

    /**
    * 解压命令
    * @param fileName 文件名称
    * @param archivePath Arch Linux Linux Linux路径
    * @param dir dir
    * @param linux Linux
    * @return extract命令的结果
    */
    private String extractCommand(String fileName, String archivePath, String dir, boolean linux) {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (linux) {
            if (name.endsWith(".zip")) {
                return "mkdir -p " + quote(dir) + " && unzip -o " + quote(archivePath) + " -d " + quote(dir);
            }
            if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
                return "mkdir -p " + quote(dir) + " && tar -xzf " + quote(archivePath) + " -C " + quote(dir);
            }
            if (name.endsWith(".tar")) {
                return "mkdir -p " + quote(dir) + " && tar -xf " + quote(archivePath) + " -C " + quote(dir);
            }
        } else {
            if (name.endsWith(".zip")) {
                return "mkdir " + quote(dir) + " 2>nul & tar -xf " + quote(archivePath) + " -C " + quote(dir);
            }
            if (name.endsWith(".tar.gz") || name.endsWith(".tgz") || name.endsWith(".tar")) {
                return "mkdir " + quote(dir) + " 2>nul & tar -xf " + quote(archivePath) + " -C " + quote(dir);
            }
        }
        return "echo " + quote(fileName) + ": 非压缩包，跳过解压";
    }

    /**
    * 删除远端文件命令
    * @param path 路径
    * @return rm远程的结果
    */
    private String rmRemote(String path) {
        return "rm -f " + quote(path);
    }

    /**
    * 主机临时目录（用于中转压缩包）
    * @param host 主机
    * @param projectId projectid
    * @return tempDirFor的结果
    */
    private String tempDirFor(MsServerHost host, Long projectId) {
        return isLinux(host) ? "/tmp/ms-project-" + projectId : "C:\\ms-project-" + projectId;
    }

    /**
    * 是否 Linux 主机
    * @param host 主机
    * @return 是否Linux的结果
    */
    private boolean isLinux(MsServerHost host) {
        return host.getServerHostOsType() != null
                && host.getServerHostOsType().trim().toUpperCase(Locale.ROOT).startsWith("LINUX");
    }

    /**
    * 是否为可解压压缩包
    * @param fileName 文件名称
    * @return 是否Arch Linux的结果
    */
    private boolean isArchive(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(".zip") || name.endsWith(".tar.gz")
                || name.endsWith(".tgz") || name.endsWith(".tar");
    }

    /**
    * 脚本模板渲染：{{dir}} 替换为部署目录
    * @param script script
    * @param dir dir
    * @return renderScript的结果
    * @param linux linux（布尔开关）
    */
    private String renderScript(String script, String dir, boolean linux) {
        if (script == null) {
            return "";
        }
        // 生产级：{{dir}} 按目标 OS 转义，防止目录含空格/特殊字符导致命令注入
        String safeDir;
        if (dir == null || dir.isBlank()) {
            safeDir = "";
        } else if (linux) {
            safeDir = quote(dir);
        } else {
            safeDir = "\"" + dir.replace("\"", "\\\"") + "\"";
        }
        return script.replace("{{dir}}", safeDir);
    }

    /**
    * 根据项目配置的主机 OS 判断是否为 Linux
    * @param os 方法入参 os
    * @return 是否成功（true 表示成功）
    */
    private boolean isLinuxHostOs(String os) {
        return os != null && os.trim().toUpperCase(Locale.ROOT).startsWith("LINUX");
    }

    /**
    * 构建失败日志
    * @param item item
    * @param error 错误
    * @param startMs 启动ms
    * @return 失败的结果
    */
    private MsProjectDeployLog fail(MsProjectDeployLog item, String error, long startMs) {
        item.setMsProjectDeployLogStatus(ProjectConstants.LOG_STATUS_FAIL);
        item.setMsProjectDeployLogError(truncate(error));
        /**
        * fillcost。
        * @param item item
        * @param startMs 启动ms
        */
        fillCost(item, startMs);
        return item;
    }

    /**
    * 构建生命周期失败日志
    * @param project project
    * @param host 主机
    * @param action 动作
    * @param error 错误
    * @param version 版本
    * @return lifecycle日志的结果
    */
    private MsProjectDeployLog lifecycleLog(MsProject project, MsServerHost host, String action, String error, String version) {
        long startMs = System.currentTimeMillis();
        MsProjectDeployLog item = newLog(project, host, action, version);
        item.setMsProjectDeployLogStartTime(LocalDateTime.now());
        return fail(item, error, startMs);
    }

    /**
    * 新建部署日志实体
    * @param project project
    * @param host 主机
    * @param action 动作
    * @param version 版本
    * @return 新日志的结果
    */
    private MsProjectDeployLog newLog(MsProject project, MsServerHost host, String action, String version) {
        MsProjectDeployLog item = new MsProjectDeployLog();
        item.setMsProjectDeployLogProjectId(project.getId());
        item.setMsProjectDeployLogProjectName(project.getMsProjectName());
        if (host != null) {
            item.setMsProjectDeployLogHostId(host.getId());
            item.setMsProjectDeployLogHostName(host.getServerHostName());
        }
        item.setMsProjectDeployLogAction(action);
        item.setMsProjectDeployLogVersion(version);
        item.setMsProjectDeployLogStatus(ProjectConstants.LOG_STATUS_RUNNING);
        return item;
    }

    /**
    * 填充耗时
    * @param item item
    * @param startMs 启动ms
    */
    private void fillCost(MsProjectDeployLog item, long startMs) {
        item.setMsProjectDeployLogEndTime(LocalDateTime.now());
        item.setMsProjectDeployLogCostMs(System.currentTimeMillis() - startMs);
    }

    /**
    * 解析 主机标识 JSON 数组
    * @param json json
    * @return 解析主机标识的结果
    */
    private List<Long> parseHostIds(String json) {
        if (isBlank(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (IOException e) {
            log.warn("[project] hostIds 解析失败: {}", json);
            return List.of();
        }
    }

    /**
    * 创建带命名线程的固定线程池
    * @param size 大小
    * @return 新游泳池的结果
    */
    private ExecutorService newPool(int size) {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "project-deploy-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(size, size, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64), factory, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
    * 输出截断（上限 20000 字符）
    * @param value 值
    * @return truncate的结果
    */
    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 20000 ? value.substring(0, 20000) : value;
    }

    /**
    * 文件名安全化（去除路径分隔符）
    * @param name 名称
    * @return safe文件名称的结果
    */
    private String safeFileName(String name) {
        if (isBlank(name)) {
            return "deploy.bin";
        }
        String base = name.replace('\\', '/');
        int idx = base.lastIndexOf('/');
        return idx >= 0 ? base.substring(idx + 1) : base;
    }

    /**
    * 归一化操作类型（DEPLOY 或 UPGRADE）
    * @param action 动作
    * @return normalize动作的结果
    */
    private String normalizeAction(String action) {
        if (ProjectConstants.ACTION_UPGRADE.equalsIgnoreCase(action)) {
            return ProjectConstants.ACTION_UPGRADE;
        }
        return ProjectConstants.ACTION_DEPLOY;
    }

    /**
    * Shell 单引号包裹
    * @param value 值
    * @return 引述的结果
    */
    private String quote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /**
    * 空串判断
    * @param value 值
    * @return 是否blank的结果
    */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
    * 非空判断
    * @param value 值
    * @return notBlank的结果
    */
    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
