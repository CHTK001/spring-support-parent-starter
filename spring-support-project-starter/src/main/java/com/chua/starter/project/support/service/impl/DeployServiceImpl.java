package com.chua.starter.project.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.ssh.support.client.SftpClient;
import com.chua.starter.project.support.constants.ProjectConstants;
import com.chua.starter.project.support.entity.MsProject;
import com.chua.starter.project.support.entity.MsProjectDeployLog;
import com.chua.starter.project.support.mapper.MsProjectDeployLogMapper;
import com.chua.starter.project.support.mapper.MsProjectMapper;
import com.chua.starter.project.support.service.DeployService;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 发布操作服务实现（部署 / 升级 / 启停）
 * <p>
 * 依赖 spring-support-server-starter 提供的主机凭据解析、SSH 连接池与
 * SFTP 客户端，对目标服务器执行远程部署与生命周期管理。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeployServiceImpl implements DeployService {

    /** 项目 Mapper */
    private final MsProjectMapper projectMapper;

    /** 部署日志 Mapper */
    private final MsProjectDeployLogMapper deployLogMapper;

    /** 服务器主机 Mapper（来自 spring-support-server-starter） */
    private final MsServerHostMapper serverHostMapper;

    /** 服务器主机服务（凭据解析） */
    private final ServerHostService serverHostService;

    /** SSH 连接池 */
    private final ConnectionManager connectionManager;

    /** Jackson */
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<List<MsProjectDeployLog>> deploy(Long projectId, String action, MultipartFile file) {
        MsProject project = projectId == null ? null : projectMapper.selectById(projectId);
        if (project == null) {
            return ReturnResult.error("项目不存在: " + projectId);
        }
        if (file == null || file.isEmpty()) {
            return ReturnResult.error("部署包不能为空");
        }
        final String act = normalizeAction(action);
        final List<Long> hostIds = parseHostIds(project.getMsProjectHostIds());
        if (hostIds.isEmpty()) {
            return ReturnResult.error("项目未绑定目标服务器");
        }
        // 上传包落盘临时文件
        Path localFile = null;
        try {
            localFile = Files.createTempFile("ms-project-deploy-", "-" + safeFileName(file.getOriginalFilename()));
            try (var in = file.getInputStream()) {
                Files.copy(in, localFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            return ReturnResult.error("部署包保存失败: " + e.getMessage());
        }

        final String version = file.getOriginalFilename();
        final Path artifact = localFile;
        final MsProject deployProject = project;
        try {
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
                log.warn("[project] 并发部署中断 projectId={}: {}", projectId, e.getMessage());
            } finally {
                pool.shutdownNow();
            }

            boolean anySuccess = logs.stream().anyMatch(
                    l -> ProjectConstants.LOG_STATUS_SUCCESS.equals(l.getMsProjectDeployLogStatus()));
            if (anySuccess) {
                // 全量更新项目版本与运行状态
                MsProject update = new MsProject();
                update.setId(project.getId());
                update.setMsProjectCurrentVersion(version);
                update.setMsProjectRuntimeStatus(ProjectConstants.RUNTIME_UNKNOWN);
                projectMapper.updateById(update);
            }
            for (MsProjectDeployLog logItem : logs) {
                deployLogMapper.insert(logItem);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<List<MsProjectDeployLog>> start(Long projectId) {
        return lifecycle(projectId, ProjectConstants.ACTION_START, ProjectConstants.RUNTIME_RUNNING);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<List<MsProjectDeployLog>> stop(Long projectId) {
        return lifecycle(projectId, ProjectConstants.ACTION_STOP, ProjectConstants.RUNTIME_STOPPED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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

    /**
     * 生命周期操作（启动/停止/重启）
     *
     * @param projectId      项目 ID
     * @param action         操作类型
     * @param targetStatus   操作完成后项目运行状态
     * @return 每台主机的执行日志
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
        List<Long> hostIds = parseHostIds(project.getMsProjectHostIds());
        if (hostIds.isEmpty()) {
            return ReturnResult.error("项目未绑定目标服务器");
        }
        String command = renderScript(script, project.getMsProjectDeployDir());
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
        fillCost(item, startMs);
        return item;
    }

    /**
     * 单主机部署执行
     *
     * @param project   项目
     * @param host      主机
     * @param localFile 本地部署包
     * @param version   版本标识
     * @param action    操作类型
     * @return 部署日志
     */
    private MsProjectDeployLog deployToHost(MsProject project, Long hostId, Path localFile, String version, String action) {
        MsServerHost host = serverHostMapper.selectById(hostId);
        if (host == null) {
            return lifecycleLog(project, null, action, "目标服务器不存在: " + hostId, version);
        }
        long startMs = System.currentTimeMillis();
        MsProjectDeployLog item = newLog(project, host, action, version);
        item.setMsProjectDeployLogStartTime(LocalDateTime.now());
        ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(host.getId(), null);
        if (!resolved.isOk()) {
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
                execSsh(host, resolved.getData(), clearDirCommand(dir, linux), item);
            }
            boolean autoExtract = project.getMsProjectAutoExtract() != null
                    && project.getMsProjectAutoExtract() == 1;
            String fileName = safeFileName(version);
            if (autoExtract && isArchive(fileName)) {
                // 2. 压缩包：上传到临时目录后解压到部署目录
                String tempDir = tempDirFor(host, project.getId());
                String remoteArchive = tempDir + "/" + fileName;
                mkdirRemote(host, resolved.getData(), tempDir);
                uploadSftp(host, resolved.getData(), remoteArchive, localFile);
                execSsh(host, resolved.getData(), extractCommand(fileName, remoteArchive, dir, linux), item);
                execSsh(host, resolved.getData(), rmRemote(remoteArchive), item);
            } else {
                // 3. 单体文件（jar/war 等）：直接上传覆盖到部署目录
                mkdirRemote(host, resolved.getData(), dir);
                String remotePath = dir.endsWith("/") ? dir + fileName : dir + "/" + fileName;
                uploadSftp(host, resolved.getData(), remotePath, localFile);
            }
            item.setMsProjectDeployLogStatus(ProjectConstants.LOG_STATUS_SUCCESS);
        } catch (Exception e) {
            return fail(item, e.getMessage(), startMs);
        }
        fillCost(item, startMs);
        return item;
    }

    /**
     * 创建远程目录（递归）
     */
    private void mkdirRemote(MsServerHost host, SshCredential credential, String path) throws Exception {
        try (var client = buildConnectedSftp(host, credential)) {
            client.mkdir().path(path).recursive(true).exec();
        }
    }

    /**
     * SFTP 上传本地文件到远端路径
     */
    private void uploadSftp(MsServerHost host, SshCredential credential, String remotePath, Path localFile) throws Exception {
        try (var client = buildConnectedSftp(host, credential)) {
            client.upload().local(localFile.toString()).remote(remotePath).exec();
        }
    }

    /**
     * 构建并连接 SFTP 客户端（凭据装配后立即清理私钥临时文件）
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
     */
    private String clearDirCommand(String dir, boolean linux) {
        if (linux) {
            return "mkdir -p " + quote(dir) + " && rm -rf " + quote(dir) + "/*";
        }
        return "if exist " + quote(dir) + " ( rd /s /q " + quote(dir) + " ) & mkdir " + quote(dir);
    }

    /**
     * 解压命令
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
     */
    private String rmRemote(String path) {
        return "rm -f " + quote(path);
    }

    /**
     * 主机临时目录（用于中转压缩包）
     */
    private String tempDirFor(MsServerHost host, Long projectId) {
        return isLinux(host) ? "/tmp/ms-project-" + projectId : "C:\\ms-project-" + projectId;
    }

    /**
     * 是否 Linux 主机
     */
    private boolean isLinux(MsServerHost host) {
        return host.getServerHostOsType() != null
                && host.getServerHostOsType().trim().toUpperCase(Locale.ROOT).startsWith("LINUX");
    }

    /**
     * 是否为可解压压缩包
     */
    private boolean isArchive(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(".zip") || name.endsWith(".tar.gz")
                || name.endsWith(".tgz") || name.endsWith(".tar");
    }

    /**
     * 脚本模板渲染：{{dir}} 替换为部署目录
     */
    private String renderScript(String script, String dir) {
        if (script == null) {
            return "";
        }
        return script.replace("{{dir}}", dir == null ? "" : dir);
    }

    /**
     * 构建失败日志
     */
    private MsProjectDeployLog fail(MsProjectDeployLog item, String error, long startMs) {
        item.setMsProjectDeployLogStatus(ProjectConstants.LOG_STATUS_FAIL);
        item.setMsProjectDeployLogError(truncate(error));
        fillCost(item, startMs);
        return item;
    }

    /**
     * 构建生命周期失败日志
     */
    private MsProjectDeployLog lifecycleLog(MsProject project, MsServerHost host, String action, String error, String version) {
        long startMs = System.currentTimeMillis();
        MsProjectDeployLog item = newLog(project, host, action, version);
        item.setMsProjectDeployLogStartTime(LocalDateTime.now());
        return fail(item, error, startMs);
    }

    /**
     * 新建部署日志实体
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
     */
    private void fillCost(MsProjectDeployLog item, long startMs) {
        item.setMsProjectDeployLogEndTime(LocalDateTime.now());
        item.setMsProjectDeployLogCostMs(System.currentTimeMillis() - startMs);
    }

    /**
     * 解析 hostIds JSON 数组
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
     */
    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 20000 ? value.substring(0, 20000) : value;
    }

    /**
     * 文件名安全化（去除路径分隔符）
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
     */
    private String normalizeAction(String action) {
        if (ProjectConstants.ACTION_UPGRADE.equalsIgnoreCase(action)) {
            return ProjectConstants.ACTION_UPGRADE;
        }
        return ProjectConstants.ACTION_DEPLOY;
    }

    /**
     * shell 单引号包裹
     */
    private String quote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /**
     * 空串判断
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 非空判断
     */
    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}