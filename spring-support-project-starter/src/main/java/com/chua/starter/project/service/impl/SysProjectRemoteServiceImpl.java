package com.chua.starter.project.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.io.file.FileInfo;
import com.chua.common.support.network.protocol.client.FileClient;
import com.chua.starter.entity.project.SysProject;
import com.chua.starter.project.model.remote.ProjectRemoteBackup;
import com.chua.starter.project.model.remote.ProjectRemoteFileContent;
import com.chua.starter.project.model.remote.ProjectRemoteFileEntry;
import com.chua.starter.project.model.remote.ProjectRemoteFileOperationResult;
import com.chua.starter.project.model.remote.ProjectRemoteOperationResult;
import com.chua.starter.project.model.remote.ProjectRemoteProject;
import com.chua.starter.project.model.remote.ProjectRemoteUploadResult;
import com.chua.starter.project.service.SysProjectRemoteService;
import com.chua.starter.project.service.SysProjectService;
import com.chua.starter.project.service.runtime.ProjectRuntimeCommandContext;
import com.chua.starter.project.service.runtime.ProjectRuntimeCommandStrategy;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.service.impl.ServerHostCommandExecutor;
import com.chua.starter.server.support.spi.ServerHostProtocolManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.common.support.lang.json.Json;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 基于 sys_project.endpoint 的远程项目管理服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysProjectRemoteServiceImpl implements SysProjectRemoteService {

    private static final String PROJECTS_DIR = "~/.panel/projects";
    private static final String BACKUPS_DIR = "~/.panel/backups";
    private static final String DEPLOY_LOGS_DIR = "~/.panel/deploy-logs";
    private static final String PID_FILE_NAME = ".app.pid";
    private static final String FILE_UPLOAD_PROGRESS_TOPIC = "service:file:upload:progress@0";
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_READ_LIMIT = 262_144;
    private static final Pattern STATUS_PGREP_PATTERN = Pattern.compile("pgrep\\s+-f\\s+['\\\"]([^'\\\"]+)['\\\"]");

    private final SysProjectService sysProjectService;
    private final ServerHostCommandExecutor commandExecutor;
    private final ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ServerHostProtocolManager protocolManager = new ServerHostProtocolManager();
    private final ServiceProvider<ProjectRuntimeCommandStrategy> runtimeCommandStrategies =
            ServiceProvider.of(ProjectRuntimeCommandStrategy.class);

    @Override
    public Integer resolveProjectIdByEndpoint(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return null;
        }
        String normalizedInput = normalizeEndpointIdentity(endpoint);
        List<SysProject> projects = sysProjectService.list(
                Wrappers.<SysProject>lambdaQuery()
                        .isNotNull(SysProject::getSysProjectEndpoint)
        );
        for (SysProject project : projects) {
            if (!StringUtils.hasText(project.getSysProjectEndpoint())) {
                continue;
            }
            if (Objects.equals(endpoint.trim(), project.getSysProjectEndpoint().trim())) {
                return project.getSysProjectId();
            }
            if (Objects.equals(normalizedInput, normalizeEndpointIdentity(project.getSysProjectEndpoint()))) {
                return project.getSysProjectId();
            }
        }
        return null;
    }

    @Override
    public List<ProjectRemoteFileEntry> listFiles(Integer sysProjectId, String path) throws Exception {
        ServerHost host = resolveHost(sysProjectId);
        FileClient client = createFileClient(host);
        try {
            client.connect();
            String targetPath = resolveRemotePath(host, path);
            return client.listFiles(targetPath)
                    .stream()
                    .sorted(Comparator
                            .comparing((FileInfo item) -> !item.isDirectory())
                            .thenComparing(item -> safeLower(item.getName())))
                    .map(this::toFileEntry)
                    .toList();
        } finally {
            client.closeQuietly();
        }
    }

    @Override
    public ProjectRemoteFileContent readFileContent(Integer sysProjectId, String path, Integer maxBytes) throws Exception {
        int limit = maxBytes == null || maxBytes <= 0 ? DEFAULT_READ_LIMIT : maxBytes;
        ServerHost host = resolveHost(sysProjectId);
        String targetPath = resolveRemotePath(host, path);
        byte[] fullBytes = downloadRemoteBytes(host, targetPath);
        boolean truncated = fullBytes.length > limit;
        byte[] bytes = truncated
                ? Arrays.copyOfRange(fullBytes, fullBytes.length - limit, fullBytes.length)
                : fullBytes;
        return ProjectRemoteFileContent.builder()
                .path(targetPath)
                .content(new String(bytes, StandardCharsets.UTF_8))
                .size(bytes.length)
                .truncated(truncated)
                .language(resolveLanguage(targetPath))
                .build();
    }

    @Override
    public byte[] downloadFile(Integer sysProjectId, String path) throws Exception {
        ServerHost host = resolveHost(sysProjectId);
        return downloadRemoteBytes(host, resolveRemotePath(host, path));
    }

    @Override
    public ProjectRemoteUploadResult uploadFile(Integer sysProjectId, String directory, String fileName, InputStream inputStream) throws Exception {
        ServerHost host = resolveHost(sysProjectId);
        FileClient client = createFileClient(host);
        String targetDir = resolveRemotePath(host, directory);
        String normalizedName = StringUtils.hasText(fileName) ? fileName.trim() : "upload.bin";
        String targetPath = joinRemotePath(targetDir, normalizedName, protocolManager.isWindows(host));
        String taskId = "upload-" + System.currentTimeMillis();
        try {
            pushUploadProgress(taskId, normalizedName, 10, "uploading", "开始上传");
            client.connect();
            client.createDirectory(targetDir, true);
            client.uploadFile(inputStream, targetPath);
            pushUploadProgress(taskId, normalizedName, 100, "completed", "上传完成");
            return ProjectRemoteUploadResult.builder()
                    .success(true)
                    .message("上传成功")
                    .uploadedPath(targetPath)
                    .build();
        } finally {
            client.closeQuietly();
        }
    }

    @Override
    public ProjectRemoteFileOperationResult writeFileContent(Integer sysProjectId, String path, String content) throws Exception {
        ServerHost host = resolveHost(sysProjectId);
        String targetPath = resolveRemotePath(host, path);
        FileClient client = createFileClient(host);
        try {
            client.connect();
            String parent = parentRemotePath(targetPath, protocolManager.isWindows(host));
            if (StringUtils.hasText(parent)) {
                client.createDirectory(parent, true);
            }
            byte[] bytes = String.valueOf(content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
            client.uploadFile(new ByteArrayInputStream(bytes), targetPath);
            return ProjectRemoteFileOperationResult.builder()
                    .success(true)
                    .message("文件已更新")
                    .path(targetPath)
                    .build();
        } finally {
            client.closeQuietly();
        }
    }

    @Override
    public ProjectRemoteFileOperationResult createDirectory(Integer sysProjectId, String path) throws Exception {
        ServerHost host = resolveHost(sysProjectId);
        String targetPath = resolveRemotePath(host, path);
        FileClient client = createFileClient(host);
        try {
            client.connect();
            client.createDirectory(targetPath, true);
            return ProjectRemoteFileOperationResult.builder()
                    .success(true)
                    .message("目录已创建")
                    .path(targetPath)
                    .build();
        } finally {
            client.closeQuietly();
        }
    }

    @Override
    public ProjectRemoteFileOperationResult renameFile(Integer sysProjectId, String path, String targetPath) throws Exception {
        ServerHost host = resolveHost(sysProjectId);
        String source = resolveRemotePath(host, path);
        String target = resolveRemotePath(host, targetPath);
        FileClient client = createFileClient(host);
        try {
            client.connect();
            client.rename(source, target);
            return ProjectRemoteFileOperationResult.builder()
                    .success(true)
                    .message("重命名成功")
                    .path(source)
                    .targetPath(target)
                    .build();
        } finally {
            client.closeQuietly();
        }
    }

    @Override
    public ProjectRemoteFileOperationResult deleteFile(Integer sysProjectId, String path, boolean recursive) throws Exception {
        ServerHost host = resolveHost(sysProjectId);
        String targetPath = resolveRemotePath(host, path);
        FileClient client = createFileClient(host);
        try {
            client.connect();
            if (client.isDirectory(targetPath)) {
                client.deleteDirectory(targetPath, recursive);
            } else {
                client.deleteFile(targetPath);
            }
            return ProjectRemoteFileOperationResult.builder()
                    .success(true)
                    .message("删除成功")
                    .path(targetPath)
                    .build();
        } finally {
            client.closeQuietly();
        }
    }

    @Override
    public List<ProjectRemoteProject> listProjects(Integer sysProjectId) {
        ServerHost host = resolveHost(sysProjectId);
        ensureDir(host, PROJECTS_DIR);
        String out = exec(host, "ls " + PROJECTS_DIR + "/*.json 2>/dev/null");
        List<ProjectRemoteProject> list = new ArrayList<>();
        if (!StringUtils.hasText(out) || out.startsWith("ERROR") || out.contains("No such")) {
            return list;
        }
        for (String line : out.split("\n")) {
            String path = line.trim();
            if (!StringUtils.hasText(path)) {
                continue;
            }
            try {
                String json = readRemoteText(host, path);
                ProjectRemoteProject project = objectMapper.readValue(json, ProjectRemoteProject.class);
                backfillRuntimeMetaIfNeeded(host, project);
                applyRuntimeSnapshot(host, project, false);
                list.add(project);
            } catch (Exception e) {
                log.warn("[ProjectRemote] 跳过不可解析的项目: {} ({})", path, e.getMessage());
            }
        }
        return list;
    }

    @Override
    public ProjectRemoteProject getProject(Integer sysProjectId, String projectId) {
        ServerHost host = resolveHost(sysProjectId);
        try {
            String json = readRemoteText(host, projectFilePath(projectId));
            ProjectRemoteProject project = objectMapper.readValue(json, ProjectRemoteProject.class);
            backfillRuntimeMetaIfNeeded(host, project);
            applyRuntimeSnapshot(host, project, false);
            return project;
        } catch (Exception e) {
            throw new IllegalStateException("项目不存在: " + projectId, e);
        }
    }

    @Override
    public ProjectRemoteProject createProject(Integer sysProjectId, ProjectRemoteProject project) {
        ServerHost host = resolveHost(sysProjectId);
        ensureDir(host, PROJECTS_DIR);
        project.setProjectId(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        project.setRuntimeStatus("unknown");
        project.setPid(null);
        refreshMainArtifactMd5(host, project);
        applyRuntimeMetaDefaults(project);
        saveMeta(host, project);
        return project;
    }

    @Override
    public ProjectRemoteProject updateProject(Integer sysProjectId, String projectId, ProjectRemoteProject project) {
        ServerHost host = resolveHost(sysProjectId);
        project.setProjectId(projectId);
        refreshMainArtifactMd5(host, project);
        applyRuntimeMetaDefaults(project);
        saveMeta(host, project);
        return project;
    }

    @Override
    public void deleteProject(Integer sysProjectId, String projectId) {
        exec(resolveHost(sysProjectId), "rm -f " + projectFilePath(projectId));
    }

    @Override
    public ProjectRemoteOperationResult startProject(Integer sysProjectId, String projectId) {
        ServerHost host = resolveHost(sysProjectId);
        ProjectRemoteProject project = getProject(sysProjectId, projectId);
        String out = exec(host, buildStartScript(project));
        boolean ok = !hasCommandError(out);
        RuntimeSnapshot snapshot = detectRuntimeSnapshotAfterStart(host, project);
        String runtimeStatus = ok ? normalizeRuntimeStatus(snapshot.runtimeStatus(), "running") : "error";
        updateMeta(host, project, runtimeStatus, out, snapshot.pid());
        return ProjectRemoteOperationResult.builder()
                .success(ok)
                .message(ok ? "启动成功" : "启动失败")
                .output(out)
                .runtimeStatus(runtimeStatus)
                .pid(snapshot.pid())
                .build();
    }

    @Override
    public ProjectRemoteOperationResult stopProject(Integer sysProjectId, String projectId) {
        ServerHost host = resolveHost(sysProjectId);
        ProjectRemoteProject project = getProject(sysProjectId, projectId);
        String out = exec(host, buildStopScript(project));
        boolean commandOk = !hasCommandError(out);
        RuntimeSnapshot snapshot = detectRuntimeSnapshot(host, project);
        boolean stopped = "stopped".equals(snapshot.runtimeStatus());
        boolean ok = commandOk && stopped;
        String runtimeStatus = ok
                ? "stopped"
                : normalizeRuntimeStatus(snapshot.runtimeStatus(), "error");
        Long pid = "running".equals(runtimeStatus) ? snapshot.pid() : null;
        String message = ok
                ? "停止成功"
                : (commandOk ? "停止失败：进程仍在运行" : "停止失败");
        updateMeta(host, project, runtimeStatus, out, pid);
        return ProjectRemoteOperationResult.builder()
                .success(ok)
                .message(message)
                .output(out)
                .runtimeStatus(runtimeStatus)
                .pid(pid)
                .build();
    }

    @Override
    public ProjectRemoteOperationResult restartProject(Integer sysProjectId, String projectId) {
        ServerHost host = resolveHost(sysProjectId);
        ProjectRemoteProject project = getProject(sysProjectId, projectId);
        String out = exec(host, buildRestartScript(project));
        boolean ok = !hasCommandError(out);
        RuntimeSnapshot snapshot = detectRuntimeSnapshotAfterStart(host, project);
        String runtimeStatus = ok ? normalizeRuntimeStatus(snapshot.runtimeStatus(), "running") : "error";
        updateMeta(host, project, runtimeStatus, out, snapshot.pid());
        return ProjectRemoteOperationResult.builder()
                .success(ok)
                .message(ok ? "重启成功" : "重启失败")
                .output(out)
                .runtimeStatus(runtimeStatus)
                .pid(snapshot.pid())
                .build();
    }

    @Override
    public ProjectRemoteOperationResult getProjectStatus(Integer sysProjectId, String projectId) {
        ServerHost host = resolveHost(sysProjectId);
        ProjectRemoteProject project = getProject(sysProjectId, projectId);
        RuntimeSnapshot snapshot = detectRuntimeSnapshot(host, project);
        updateMeta(host, project, snapshot.runtimeStatus(), project.getLastOperationMessage(), snapshot.pid(), false);
        return ProjectRemoteOperationResult.builder()
                .success(true)
                .runtimeStatus(snapshot.runtimeStatus())
                .pid(snapshot.pid())
                .build();
    }

    @Override
    public String getProjectLogs(Integer sysProjectId, String projectId, int lines) {
        ServerHost host = resolveHost(sysProjectId);
        ProjectRemoteProject project = getProject(sysProjectId, projectId);
        String startupLog = StringUtils.hasText(project.getStartupLogFile())
                ? project.getStartupLogFile().trim()
                : "";
        if (StringUtils.hasText(startupLog)) {
            return exec(host, "tail -n " + lines + " " + shQuote(startupLog) + " 2>&1");
        }
        String logDir = StringUtils.hasText(project.getLogDirectory())
                ? project.getLogDirectory()
                : project.getRootDirectory() + "/logs";
        String latestLog = exec(host, "ls -t " + shQuote(logDir) + "/*.log 2>/dev/null | head -1").trim();
        if (!StringUtils.hasText(latestLog) || latestLog.startsWith("ERROR")) {
            latestLog = project.getRootDirectory() + "/nohup.out";
        }
        return exec(host, "tail -n " + lines + " " + shQuote(latestLog) + " 2>&1");
    }

    @Override
    public String getProjectDeployLogs(Integer sysProjectId, String projectId, int lines) {
        ServerHost host = resolveHost(sysProjectId);
        String logPath = resolveHomePath(host, DEPLOY_LOGS_DIR + "/" + projectId + ".log");
        return exec(host,
                "if [ -f " + shQuote(logPath) + " ]; then "
                        + "tail -n " + lines + " " + shQuote(logPath) + "; "
                        + "else echo \"暂无升级日志\"; fi 2>&1");
    }

    @Override
    public ProjectRemoteOperationResult backupProject(Integer sysProjectId, String projectId, String note) {
        ServerHost host = resolveHost(sysProjectId);
        ProjectRemoteProject project = getProject(sysProjectId, projectId);
        ensureDir(host, BACKUPS_DIR + "/" + projectId);
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupPath = BACKUPS_DIR + "/" + projectId + "/" + ts + ".tar.gz";
        String backupRoot = resolveBackupRootDirectory(project);
        String out = exec(host, "tar -czf " + backupPath + " -C " + backupRoot + " . 2>&1");
        boolean ok = !hasCommandError(out);
        if (ok) {
            ProjectRemoteBackup backup = new ProjectRemoteBackup();
            backup.setBackupId(ts);
            backup.setBackupPath(backupPath);
            backup.setBackupTime(LocalDateTime.now().format(DT_FMT));
            backup.setNote(note);
            String sizeOut = exec(host, "du -b " + backupPath + " 2>/dev/null | awk '{print $1}'").trim();
            try {
                backup.setSize(Long.parseLong(sizeOut));
            } catch (Exception ignored) {
                // ignore
            }
            saveBackupMeta(host, projectId, backup);
        }
        return ProjectRemoteOperationResult.builder()
                .success(ok)
                .message(ok ? "备份成功" : "备份失败")
                .output(out)
                .backupPath(ok ? backupPath : null)
                .build();
    }

    @Override
    public List<ProjectRemoteBackup> listProjectBackups(Integer sysProjectId, String projectId) {
        ServerHost host = resolveHost(sysProjectId);
        try {
            String json = readRemoteText(host, BACKUPS_DIR + "/" + projectId + "/meta.json");
            return objectMapper.readValue(json, new TypeReference<List<ProjectRemoteBackup>>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public ProjectRemoteOperationResult rollbackProject(Integer sysProjectId, String projectId, String backupId) {
        ServerHost host = resolveHost(sysProjectId);
        List<ProjectRemoteBackup> backups = listProjectBackups(sysProjectId, projectId);
        ProjectRemoteBackup backup = backups.stream()
                .filter(item -> Objects.equals(backupId, item.getBackupId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("备份不存在: " + backupId));
        ProjectRemoteProject project = getProject(sysProjectId, projectId);
        exec(host, buildStopScript(project));
        String rollbackRoot = resolveBackupRootDirectory(project);
        String out = exec(host, "rm -rf " + rollbackRoot + "/* 2>&1 && "
                + "tar -xzf " + backup.getBackupPath() + " -C " + rollbackRoot + " 2>&1");
        out += "\n" + exec(host, buildStartScript(project));
        boolean ok = !hasCommandError(out);
        return ProjectRemoteOperationResult.builder()
                .success(ok)
                .message(ok ? "回滚成功" : "回滚失败")
                .output(out)
                .build();
    }

    @Override
    public ProjectRemoteOperationResult deployProject(Integer sysProjectId,
                                                      String projectId,
                                                      InputStream fileStream,
                                                      String fileName,
                                                      boolean stopBefore,
                                                      boolean startAfter,
                                                      boolean backupBefore,
                                                      boolean clearBefore,
                                                      boolean autoExtract,
                                                      boolean deleteArchive,
                                                      boolean configCheckBefore,
                                                      boolean dependencyCheckBefore) {
        ServerHost host = resolveHost(sysProjectId);
        ProjectRemoteProject project = null;
        LocalDateTime startedAt = LocalDateTime.now();
        String taskId = startedAt.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        StringBuilder log = new StringBuilder();
        boolean success = false;
        String message = "部署失败";

        log.append("[任务ID] ").append(taskId).append("\n");
        log.append("[开始时间] ").append(startedAt.format(DT_FMT)).append("\n");
        log.append("[项目ID] ").append(projectId).append("\n");
        log.append("[文件] ").append(fileName == null ? "-" : fileName).append("\n");
        log.append("[策略] stopBefore=").append(stopBefore)
                .append(", startAfter=").append(startAfter)
                .append(", backupBefore=").append(backupBefore)
                .append(", clearBefore=").append(clearBefore)
                .append(", autoExtract=").append(autoExtract)
                .append(", deleteArchive=").append(deleteArchive)
                .append(", configCheckBefore=").append(configCheckBefore)
                .append(", dependencyCheckBefore=").append(dependencyCheckBefore)
                .append("\n");
        try {
            project = getProject(sysProjectId, projectId);
            String preflight = runDeployPreflightChecks(host, project, configCheckBefore, dependencyCheckBefore);
            if (StringUtils.hasText(preflight)) {
                log.append(preflight).append("\n");
                message = "部署前核对失败";
            } else {
                if (backupBefore) {
                    ProjectRemoteOperationResult backupResult = backupProject(sysProjectId, projectId, "deploy-before");
                    log.append("[备份] ").append(backupResult.getMessage()).append("\n");
                    if (StringUtils.hasText(backupResult.getBackupPath())) {
                        log.append("[备份路径] ").append(backupResult.getBackupPath()).append("\n");
                    }
                    if (!backupResult.isSuccess()) {
                        throw new IllegalStateException("部署前备份失败");
                    }
                }
                if (stopBefore) {
                    String stopOut = exec(host, buildStopScript(project)).trim();
                    log.append("[停止] ").append(stopOut).append("\n");
                    if (hasCommandError(stopOut)) {
                        throw new IllegalStateException("部署前停止失败: " + firstNonBlankLine(stopOut));
                    }
                }

                String tmpPath = "/tmp/" + (StringUtils.hasText(fileName) ? fileName : "deploy-upload.bin");
                pushUploadProgress(taskId, fileName, 12, "uploading", "开始上传部署包");
                uploadViaFileClient(host, tmpPath, fileStream);
                log.append("[上传] ").append(fileName).append(" -> ").append(tmpPath).append("\n");
                pushUploadProgress(taskId, fileName, 48, "uploading", "部署包上传完成");

                String rootDir = project.getRootDirectory();
                if (StringUtils.hasText(rootDir)) {
                    ensureDir(host, rootDir);
                    log.append("[目录] 确保目标目录: ").append(rootDir).append("\n");
                }

                if (clearBefore) {
                    String clearOut = exec(host, "rm -rf " + project.getRootDirectory() + "/* 2>&1");
                    log.append("[清空] ").append(project.getRootDirectory()).append("\n");
                    if (hasCommandError(clearOut)) {
                        throw new IllegalStateException("清空目标目录失败: " + firstNonBlankLine(clearOut));
                    }
                }

                if (autoExtract && isArchiveFile(fileName)) {
                    pushUploadProgress(taskId, fileName, 72, "merging", "正在解压部署包");
                    String cmd = fileName != null && fileName.endsWith(".zip")
                            ? "unzip -o " + tmpPath + " -d " + project.getRootDirectory() + " 2>&1"
                            : "tar -xzf " + tmpPath + " -C " + project.getRootDirectory() + " 2>&1";
                    String extractOut = exec(host, cmd).trim();
                    log.append("[解压] ").append(extractOut).append("\n");
                    if (hasCommandError(extractOut)) {
                        throw new IllegalStateException("解压部署包失败: " + firstNonBlankLine(extractOut));
                    }
                    if (deleteArchive) {
                        exec(host, "rm -f " + tmpPath);
                    }
                } else {
                    String targetFileName = resolveDeployTargetFileName(project, fileName);
                    String moveOut = exec(host, "mv " + tmpPath + " " + project.getRootDirectory() + "/" + targetFileName + " 2>&1").trim();
                    log.append("[投递] ").append(fileName).append(" -> ").append(targetFileName).append("\n");
                    if (hasCommandError(moveOut)) {
                        throw new IllegalStateException("投递部署包失败: " + firstNonBlankLine(moveOut));
                    }
                    String targetPath = project.getRootDirectory() + "/" + targetFileName;
                    String verifyOut = exec(host, "test -f " + shQuote(targetPath) + " && echo OK || echo MISSING").trim();
                    if (!"OK".equals(verifyOut)) {
                        throw new IllegalStateException("投递部署包失败: 文件不存在于 " + targetPath + "，mv输出=" + firstNonBlankLine(moveOut));
                    }
                    if (!StringUtils.hasText(project.getMainArtifact())) {
                        project.setMainArtifact(targetFileName);
                    }
                }

                refreshMainArtifactMd5(host, project);
                refreshSpringProjectSettings(host, project);
                pushUploadProgress(taskId, fileName, 92, "merging", "部署包处理完成，正在收尾");

                if (startAfter) {
                    String startOut = exec(host, buildStartScript(project)).trim();
                    log.append("[启动] ").append(startOut).append("\n");
                    if (hasCommandError(startOut)) {
                        throw new IllegalStateException("部署后启动失败: " + firstNonBlankLine(startOut));
                    }
                }

                success = true;
                message = "部署成功";
                pushUploadProgress(taskId, fileName, 100, "completed", "部署完成");
                RuntimeSnapshot snapshot = startAfter
                        ? detectRuntimeSnapshotAfterStart(host, project)
                        : detectRuntimeSnapshot(host, project);
                String runtimeStatus = startAfter
                        ? normalizeRuntimeStatus(snapshot.runtimeStatus(), "running")
                        : "stopped";
                Long pid = "running".equals(runtimeStatus) ? snapshot.pid() : null;
                updateMeta(host, project, runtimeStatus, "部署完成", pid);
            }
        } catch (Exception e) {
            success = false;
            message = StringUtils.hasText(e.getMessage()) ? e.getMessage() : "部署失败";
            pushUploadProgress(taskId, fileName, 100, "error", message);
            log.append("[异常] ").append(message).append("\n");
            if (project != null) {
                RuntimeSnapshot snapshot = detectRuntimeSnapshot(host, project);
                updateMeta(host, project, "error", "部署失败: " + message, snapshot.pid());
            }
        } finally {
            log.append("[结束时间] ").append(LocalDateTime.now().format(DT_FMT)).append("\n");
            log.append("[结果] ").append(success ? "SUCCESS" : "FAILED").append(" - ").append(message).append("\n");
            appendDeployLog(host, projectId, log.toString());
        }

        return ProjectRemoteOperationResult.builder()
                .success(success)
                .message(message)
                .output(log.toString())
                .runtimeStatus(success ? (startAfter ? "running" : "stopped") : "error")
                .build();
    }

    private ServerHost resolveHost(Integer sysProjectId) {
        if (sysProjectId == null) {
            throw new IllegalStateException("项目ID不能为空");
        }
        SysProject sysProject = sysProjectService.getOne(sysProjectId);
        if (sysProject == null || sysProject.getSysProjectDelete() != null && sysProject.getSysProjectDelete() == 1) {
            throw new IllegalStateException("项目不存在: " + sysProjectId);
        }
        return buildHostFromProject(sysProject);
    }

    private ServerHost buildHostFromProject(SysProject sysProject) {
        String rawEndpoint = firstNonBlank(sysProject.getSysProjectEndpoint());
        if (!StringUtils.hasText(rawEndpoint)) {
            throw new IllegalStateException("项目未配置 sys_project_endpoint: " + sysProject.getSysProjectId());
        }
        String endpoint = rawEndpoint.trim();
        URI uri = parseEndpointUri(endpoint);
        Map<String, String> query = uri == null ? Collections.emptyMap() : parseQuery(uri.getRawQuery());

        String serverType = normalizeServerType(firstNonBlank(
                query.get("serverType"),
                query.get("type"),
                detectServerType(uri, endpoint)
        ));
        String host = firstNonBlank(
                query.get("host"),
                uri == null ? null : uri.getHost(),
                extractHostWithoutScheme(endpoint)
        );
        Integer port = parsePort(firstNonBlank(
                query.get("port"),
                uri == null || uri.getPort() <= 0 ? null : String.valueOf(uri.getPort())
        ), defaultPort(serverType));
        String osType = firstNonBlank(query.get("osType"), defaultOsType(serverType));

        String username = firstNonBlank(
                query.get("username"),
                extractUsername(uri),
                sysProject.getSysProjectAppId(),
                "root"
        );
        String password = firstNonBlank(
                query.get("password"),
                extractPassword(uri),
                sysProject.getSysProjectAppSecret()
        );
        String privateKey = firstNonBlank(
                query.get("privateKey"),
                decodeBase64(query.get("privateKeyBase64"))
        );
        String baseDirectory = firstNonBlank(
                query.get("baseDirectory"),
                query.get("basePath"),
                defaultBaseDirectory(serverType, username)
        );

        if (!StringUtils.hasText(host) && !"LOCAL".equalsIgnoreCase(serverType)) {
            throw new IllegalStateException("无法从 endpoint 解析主机地址: " + endpoint);
        }

        ServerHost hostEntity = new ServerHost();
        hostEntity.setServerType(serverType);
        hostEntity.setHost("LOCAL".equalsIgnoreCase(serverType)
                ? firstNonBlank(host, "127.0.0.1")
                : host);
        hostEntity.setPort(port);
        hostEntity.setUsername(username);
        hostEntity.setPassword(password);
        hostEntity.setPrivateKey(privateKey);
        hostEntity.setOsType(osType);
        hostEntity.setBaseDirectory(baseDirectory);
        return hostEntity;
    }

    private URI parseEndpointUri(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return null;
        }
        String text = endpoint.trim();
        try {
            if (text.contains("://")) {
                return URI.create(text);
            }
            if ("local".equalsIgnoreCase(text)) {
                return URI.create("local://localhost");
            }
            return URI.create("ssh://" + text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, String> parseQuery(String rawQuery) {
        if (!StringUtils.hasText(rawQuery)) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        String[] parts = rawQuery.split("&");
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            int index = part.indexOf('=');
            if (index < 0) {
                result.put(decodeUrl(part), "");
                continue;
            }
            String key = decodeUrl(part.substring(0, index));
            String value = decodeUrl(part.substring(index + 1));
            result.put(key, value);
        }
        return result;
    }

    private String decodeUrl(String value) {
        if (value == null) {
            return null;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return value;
        }
    }

    private String decodeBase64(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return value;
        }
    }

    private String extractUsername(URI uri) {
        if (uri == null || !StringUtils.hasText(uri.getUserInfo())) {
            return null;
        }
        String[] parts = uri.getUserInfo().split(":", 2);
        return decodeUrl(parts[0]);
    }

    private String extractPassword(URI uri) {
        if (uri == null || !StringUtils.hasText(uri.getUserInfo())) {
            return null;
        }
        String[] parts = uri.getUserInfo().split(":", 2);
        return parts.length > 1 ? decodeUrl(parts[1]) : null;
    }

    private String detectServerType(URI uri, String endpoint) {
        String scheme = uri == null ? null : uri.getScheme();
        if (!StringUtils.hasText(scheme) && StringUtils.hasText(endpoint)) {
            if ("local".equalsIgnoreCase(endpoint.trim())) {
                return "LOCAL";
            }
            return "SSH";
        }
        if (scheme == null) {
            return "SSH";
        }
        return switch (scheme.toLowerCase(Locale.ROOT)) {
            case "local", "file" -> "LOCAL";
            case "winrm", "http", "https" -> "WINRM";
            default -> "SSH";
        };
    }

    private String normalizeServerType(String type) {
        if (!StringUtils.hasText(type)) {
            return "SSH";
        }
        String value = type.trim().toUpperCase(Locale.ROOT);
        if ("HTTP".equals(value) || "HTTPS".equals(value)) {
            return "WINRM";
        }
        return value;
    }

    private Integer parsePort(String value, Integer fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Integer defaultPort(String serverType) {
        if ("LOCAL".equalsIgnoreCase(serverType)) {
            return 0;
        }
        if ("WINRM".equalsIgnoreCase(serverType)) {
            return 5985;
        }
        return 22;
    }

    private String defaultOsType(String serverType) {
        return "WINRM".equalsIgnoreCase(serverType) ? "windows" : "linux";
    }

    private String defaultBaseDirectory(String serverType, String username) {
        if ("LOCAL".equalsIgnoreCase(serverType)) {
            return System.getProperty("user.home");
        }
        if ("WINRM".equalsIgnoreCase(serverType)) {
            return "C:\\";
        }
        String user = StringUtils.hasText(username) ? username.trim() : "root";
        return "root".equalsIgnoreCase(user) ? "/root" : "/home/" + user;
    }

    private String extractHostWithoutScheme(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return null;
        }
        String text = endpoint.trim();
        if (text.contains("://")) {
            return null;
        }
        int at = text.lastIndexOf('@');
        if (at >= 0 && at + 1 < text.length()) {
            text = text.substring(at + 1);
        }
        if (!StringUtils.hasText(text)) {
            return null;
        }
        int colon = text.lastIndexOf(':');
        if (colon > 0 && colon < text.length() - 1 && text.indexOf(':') == colon) {
            String maybePort = text.substring(colon + 1);
            if (maybePort.chars().allMatch(Character::isDigit)) {
                return text.substring(0, colon);
            }
        }
        return text;
    }

    private String normalizeEndpointIdentity(String endpoint) {
        URI uri = parseEndpointUri(endpoint);
        String type = normalizeServerType(detectServerType(uri, endpoint));
        String host = firstNonBlank(uri == null ? null : uri.getHost(), extractHostWithoutScheme(endpoint), "localhost");
        int port = parsePort(uri == null || uri.getPort() <= 0 ? null : String.valueOf(uri.getPort()), defaultPort(type));
        return type + "|" + host.toLowerCase(Locale.ROOT) + "|" + port;
    }

    private String resolveRemotePath(ServerHost host, String requestedPath) {
        boolean windows = protocolManager.isWindows(host);
        String root = normalizeRemotePath(firstNonBlank(host.getBaseDirectory(), protocolManager.resolveBaseDirectory(host)), windows);
        if (!StringUtils.hasText(requestedPath)) {
            return root;
        }
        String value = requestedPath.trim();
        if (value.startsWith("~")) {
            return resolveHomePath(host, value);
        }
        String resolved = isAbsoluteRemotePath(value, windows)
                ? normalizeRemotePath(value, windows)
                : normalizeRemotePath(joinRemotePath(root, value, windows), windows);
        return resolved;
    }

    private boolean isAbsoluteRemotePath(String value, boolean windows) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        if (windows) {
            return value.matches("^[A-Za-z]:.*") || value.startsWith("\\\\");
        }
        return value.startsWith("/");
    }

    private String normalizeRemotePath(String path, boolean windows) {
        if (!StringUtils.hasText(path)) {
            return windows ? "C:\\" : "/";
        }
        String normalized = windows ? path.trim().replace('/', '\\') : path.trim().replace('\\', '/');
        normalized = windows
                ? normalized.replaceAll("\\\\{2,}", "\\\\")
                : normalized.replaceAll("/{2,}", "/");
        if (windows && normalized.matches("^[A-Za-z]:$")) {
            normalized = normalized + "\\";
        }
        if (!windows && !normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        String separator = windows ? "\\" : "/";
        while (normalized.length() > (windows ? 3 : 1) && normalized.endsWith(separator)) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String joinRemotePath(String parent, String child, boolean windows) {
        String separator = windows ? "\\" : "/";
        String normalizedParent = parent.endsWith(separator) ? parent.substring(0, parent.length() - 1) : parent;
        String normalizedChild = child.startsWith(separator) ? child.substring(1) : child;
        return normalizedParent + separator + normalizedChild;
    }

    private String parentRemotePath(String path, boolean windows) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String separator = windows ? "\\" : "/";
        int index = path.lastIndexOf(separator);
        if (index <= 0) {
            return null;
        }
        return path.substring(0, index);
    }

    private boolean isWithinRemoteRoot(String root, String path, boolean windows) {
        String normalizedRoot = normalizeRemotePath(root, windows);
        String normalizedPath = normalizeRemotePath(path, windows);
        if (windows) {
            normalizedRoot = normalizedRoot.toLowerCase(Locale.ROOT);
            normalizedPath = normalizedPath.toLowerCase(Locale.ROOT);
        }
        if ("/".equals(normalizedRoot)) {
            return normalizedPath.startsWith("/");
        }
        String separator = windows ? "\\" : "/";
        return normalizedPath.equals(normalizedRoot)
                || normalizedPath.startsWith(normalizedRoot + separator);
    }

    private FileClient createFileClient(ServerHost host) {
        FileClient client = protocolManager.createFileClient(host);
        if (client == null) {
            throw new IllegalStateException("当前协议不支持远程文件客户端: " + host.getServerType());
        }
        return client;
    }

    private byte[] downloadRemoteBytes(ServerHost host, String path) throws Exception {
        FileClient client = createFileClient(host);
        try {
            client.connect();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            client.downloadFile(path, outputStream);
            return outputStream.toByteArray();
        } finally {
            client.closeQuietly();
        }
    }

    private ProjectRemoteFileEntry toFileEntry(FileInfo info) {
        String name = StringUtils.hasText(info.getName()) ? info.getName() : info.getPath();
        return ProjectRemoteFileEntry.builder()
                .name(name)
                .path(info.getPath())
                .directory(info.isDirectory())
                .file(info.isFile() || !info.isDirectory())
                .hidden(info.isHidden())
                .size(info.getSize())
                .lastModified(info.getLastModified())
                .extension(resolveExtension(name))
                .build();
    }

    private String resolveLanguage(String path) {
        String extension = resolveExtension(path);
        return switch (extension) {
            case "json" -> "json";
            case "xml" -> "xml";
            case "yaml", "yml" -> "yaml";
            case "properties" -> "properties";
            case "sh" -> "shell";
            case "bat" -> "bat";
            case "ps1" -> "powershell";
            case "sql" -> "sql";
            case "java" -> "java";
            case "js" -> "javascript";
            case "ts" -> "typescript";
            case "vue" -> "vue";
            case "html" -> "html";
            case "css", "scss" -> "css";
            default -> "text";
        };
    }

    private String resolveExtension(String name) {
        if (!StringUtils.hasText(name) || !name.contains(".")) {
            return "";
        }
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String safeLower(String value) {
        return StringUtils.hasText(value) ? value.toLowerCase(Locale.ROOT) : "";
    }

    private String exec(ServerHost host, String command) {
        try {
            var result = commandExecutor.execute(host, command);
            return result.output() != null ? result.output() : "";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private void pushUploadProgress(String taskId,
                                    String fileName,
                                    int progress,
                                    String status,
                                    String message) {
        SocketSessionTemplate socketSessionTemplate = socketSessionTemplateProvider.getIfAvailable();
        if (socketSessionTemplate == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        payload.put("fileName", fileName);
        payload.put("progress", progress);
        payload.put("status", status);
        payload.put("message", message);
        payload.put("timestamp", System.currentTimeMillis());
        socketSessionTemplate.send(FILE_UPLOAD_PROGRESS_TOPIC, Json.toJson(payload));
    }

    private String readRemoteText(ServerHost host, String path) {
        String realPath = resolveHomePath(host, path);
        String out = exec(host, "cat " + shQuote(realPath) + " 2>&1");
        if (out.startsWith("ERROR") || out.contains("No such file")) {
            throw new IllegalStateException("文件不存在: " + path);
        }
        return out;
    }

    private void writeRemoteText(ServerHost host, String path, String content) {
        String realPath = resolveHomePath(host, path);
        String encoded = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        exec(host, "echo '" + encoded + "' | base64 -d > " + shQuote(realPath));
    }

    private void appendRemoteText(ServerHost host, String path, String content) {
        String realPath = resolveHomePath(host, path);
        String encoded = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        exec(host, "echo '" + encoded + "' | base64 -d >> " + shQuote(realPath));
    }

    private String resolveHomePath(ServerHost host, String path) {
        if (!StringUtils.hasText(path)) {
            return path;
        }
        if (!path.startsWith("~")) {
            return path;
        }
        return exec(host, "echo " + path.replace("'", "")).trim();
    }

    private void appendDeployLog(ServerHost host, String projectId, String content) {
        ensureDir(host, DEPLOY_LOGS_DIR);
        String header = "==== DEPLOY " + LocalDateTime.now().format(DT_FMT) + " projectId=" + projectId + " ====\n";
        String body = (content == null ? "" : content.trim()) + "\n\n";
        appendRemoteText(host, DEPLOY_LOGS_DIR + "/" + projectId + ".log", header + body);
    }

    private void uploadViaFileClient(ServerHost host, String remotePath, InputStream stream) throws Exception {
        FileClient client = createFileClient(host);
        File tmp = File.createTempFile("deploy_", ".bin");
        try {
            try (FileOutputStream outputStream = new FileOutputStream(tmp)) {
                stream.transferTo(outputStream);
            }
            client.connect();
            client.uploadFile(tmp.getAbsolutePath(), remotePath);
        } finally {
            client.closeQuietly();
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private boolean hasCommandError(String output) {
        if (!StringUtils.hasText(output)) {
            return false;
        }
        // 去除明确的警告/通知行，避免误判
        String cleaned = output.toLowerCase(Locale.ROOT)
                .replaceAll("(?m)^.*\\[(warn|notice)\\].*$", "")
                .replaceAll("(?m)^\\s*warning:.*$", "")
                .trim();
        if (!StringUtils.hasText(cleaned)) {
            return false;
        }
        return cleaned.contains("[emerg]")
                || cleaned.contains("permission denied")
                || cleaned.contains("fatal")
                || cleaned.contains("cannot ")
                || cleaned.contains("no such file")
                || cleaned.contains("not found")
                || cleaned.contains("command not found")
                || (cleaned.contains("failed") && !cleaned.contains("already") && !cleaned.contains("syntax is ok"))
                || (cleaned.contains("error") && !cleaned.contains("error_log") && !cleaned.contains("error_page"));
    }

    private String firstNonBlankLine(String output) {
        if (!StringUtils.hasText(output)) {
            return "";
        }
        return Arrays.stream(output.split("\n"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private void ensureDir(ServerHost host, String dir) {
        exec(host, "mkdir -p " + dir);
    }

    private String projectFilePath(String projectId) {
        return PROJECTS_DIR + "/" + projectId + ".json";
    }

    private void saveMeta(ServerHost host, ProjectRemoteProject project) {
        try {
            applyRuntimeMetaDefaults(project);
            String json = objectMapper.writeValueAsString(project);
            writeRemoteText(host, projectFilePath(project.getProjectId()), json);
        } catch (Exception e) {
            throw new IllegalStateException("保存项目元数据失败", e);
        }
    }

    private void backfillRuntimeMetaIfNeeded(ServerHost host, ProjectRemoteProject project) {
        if (applyRuntimeMetaDefaults(project)) {
            saveMeta(host, project);
        }
    }

    private boolean applyRuntimeMetaDefaults(ProjectRemoteProject project) {
        boolean changed = false;
        if (syncLifecycleScripts(project)) {
            changed = true;
        }
        String startupLogFile = String.valueOf(project.getStartupLogFile() == null ? "" : project.getStartupLogFile()).trim();
        if (!StringUtils.hasText(startupLogFile) && StringUtils.hasText(project.getRootDirectory())) {
            String defaultStartupLogFile = resolveRuntimeStartupLogFile(project);
            if (StringUtils.hasText(defaultStartupLogFile)) {
                project.setStartupLogFile(defaultStartupLogFile);
                changed = true;
            }
        } else if (!Objects.equals(project.getStartupLogFile(), startupLogFile)) {
            project.setStartupLogFile(startupLogFile);
            changed = true;
        }
        String logDirectory = String.valueOf(project.getLogDirectory() == null ? "" : project.getLogDirectory()).trim();
        if (!Objects.equals(project.getLogDirectory(), logDirectory)) {
            project.setLogDirectory(logDirectory);
            changed = true;
        }
        return changed;
    }

    private boolean syncLifecycleScripts(ProjectRemoteProject project) {
        if (isCustomProjectType(project)) {
            return false;
        }
        boolean changed = false;
        String startScript = buildStartScript(project);
        if (!Objects.equals(project.getStartScript(), startScript)) {
            project.setStartScript(startScript);
            changed = true;
        }
        String stopScript = buildStopScript(project);
        if (!Objects.equals(project.getStopScript(), stopScript)) {
            project.setStopScript(stopScript);
            changed = true;
        }
        String restartScript = buildRestartScript(project);
        if (!Objects.equals(project.getRestartScript(), restartScript)) {
            project.setRestartScript(restartScript);
            changed = true;
        }
        String statusScript = buildGeneratedStatusScript(project);
        if (!Objects.equals(project.getStatusScript(), statusScript)) {
            project.setStatusScript(statusScript);
            changed = true;
        }
        return changed;
    }

    private String buildGeneratedStatusScript(ProjectRemoteProject project) {
        String pidScript = buildProjectPidScript(project);
        if (!StringUtils.hasText(pidScript) || "true".equals(pidScript.trim())) {
            return "";
        }
        return pidScript;
    }

    private void updateMeta(ServerHost host, ProjectRemoteProject project, String status, String msg, Long pid) {
        updateMeta(host, project, status, msg, pid, true);
    }

    private void updateMeta(ServerHost host,
                            ProjectRemoteProject project,
                            String status,
                            String msg,
                            Long pid,
                            boolean updateOperationTime) {
        project.setRuntimeStatus(normalizeRuntimeStatus(status, "unknown"));
        project.setPid(pid);
        if (msg != null) {
            project.setLastOperationMessage(msg);
        }
        if (updateOperationTime) {
            project.setLastOperationTime(LocalDateTime.now().format(DT_FMT));
        }
        saveMeta(host, project);
    }

    private void applyRuntimeSnapshot(ServerHost host, ProjectRemoteProject project, boolean persist) {
        RuntimeSnapshot snapshot = detectRuntimeSnapshot(host, project);
        boolean changed = !Objects.equals(project.getRuntimeStatus(), snapshot.runtimeStatus())
                || !Objects.equals(project.getPid(), snapshot.pid());
        project.setRuntimeStatus(snapshot.runtimeStatus());
        project.setPid(snapshot.pid());
        if (persist && changed) {
            saveMeta(host, project);
        }
    }

    private void saveBackupMeta(ServerHost host, String projectId, ProjectRemoteBackup backup) {
        List<ProjectRemoteBackup> list = new ArrayList<>(listProjectBackupsInternal(host, projectId));
        list.add(0, backup);
        try {
            String json = objectMapper.writeValueAsString(list);
            writeRemoteText(host, BACKUPS_DIR + "/" + projectId + "/meta.json", json);
        } catch (Exception ignored) {
            // ignore
        }
    }

    private List<ProjectRemoteBackup> listProjectBackupsInternal(ServerHost host, String projectId) {
        try {
            String json = readRemoteText(host, BACKUPS_DIR + "/" + projectId + "/meta.json");
            return objectMapper.readValue(json, new TypeReference<List<ProjectRemoteBackup>>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private RuntimeSnapshot detectRuntimeSnapshot(ServerHost host, ProjectRemoteProject project) {
        if ("static".equals(normalizeProjectTypeToken(project.getProjectType()))) {
            return new RuntimeSnapshot("stopped", null);
        }
        if (isCustomProjectType(project) && StringUtils.hasText(project.getStatusScript())) {
            String out = exec(host, project.getStatusScript());
            String lower = out.toLowerCase(Locale.ROOT);
            String runtimeStatus = lower.contains("running") || lower.contains("active")
                    ? "running"
                    : "stopped";
            Long pid = "running".equals(runtimeStatus) ? resolveProjectPid(host, project) : null;
            return new RuntimeSnapshot(runtimeStatus, pid);
        }
        Long pid = resolveProjectPid(host, project);
        return new RuntimeSnapshot(pid == null ? "stopped" : "running", pid);
    }

    /**
     * 启动后带延迟的状态检测，给进程足够的启动时间。
     * 首次等待 2 秒检测，若未运行则再等待 2 秒重试一次。
     */
    private RuntimeSnapshot detectRuntimeSnapshotAfterStart(ServerHost host, ProjectRemoteProject project) {
        try { Thread.sleep(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        RuntimeSnapshot snapshot = detectRuntimeSnapshot(host, project);
        if ("running".equals(snapshot.runtimeStatus())) {
            return snapshot;
        }
        try { Thread.sleep(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        return detectRuntimeSnapshot(host, project);
    }

    private String normalizeRuntimeStatus(String status, String fallback) {
        String normalized = StringUtils.hasText(status) ? status.trim().toLowerCase(Locale.ROOT) : "";
        return switch (normalized) {
            case "running", "stopped", "error", "unknown" -> normalized;
            default -> fallback;
        };
    }

    private Long resolveProjectPid(ServerHost host, ProjectRemoteProject project) {
        String pidFile = resolvePidFilePath(project);
        String fromPidFile = exec(host,
                "pid=\"\"; "
                        + "if [ -f " + shQuote(pidFile) + " ]; then "
                        + "pid=$(cat " + shQuote(pidFile) + " | tr -d '\\r\\n '); "
                        + "if [ -n \"$pid\" ] && kill -0 \"$pid\" 2>/dev/null; then echo \"$pid\"; exit 0; fi; "
                        + "fi");
        Long pid = parseFirstPid(fromPidFile);
        if (pid != null) {
            return pid;
        }
        String statusPidScript = buildStatusPidScript(project);
        if (StringUtils.hasText(statusPidScript)) {
            pid = parseFirstPid(exec(host, statusPidScript + " | head -1"));
            if (pid != null) {
                return pid;
            }
        }
        String pidScript = buildProjectPidScript(project);
        return parseFirstPid(exec(host, pidScript + " | head -1"));
    }

    private String resolveRootDirectory(ProjectRemoteProject project) {
        String rootDirectory = StringUtils.hasText(project.getRootDirectory())
                ? project.getRootDirectory().trim()
                : ".";
        return StringUtils.hasText(rootDirectory) ? rootDirectory : ".";
    }

    private String resolveRuntimeLogDirectory(ProjectRemoteProject project) {
        if ("tomcat".equals(normalizeProjectTypeToken(project.getProjectType()))) {
            if (StringUtils.hasText(project.getLogDirectory())) {
                return project.getLogDirectory().trim();
            }
            return resolveTomcatHome(project) + "/logs";
        }
        if ("static".equals(normalizeProjectTypeToken(project.getProjectType()))
                || "frontend".equals(normalizeProjectTypeToken(project.getProjectType()))) {
            return StringUtils.hasText(project.getRootDirectory())
                    ? project.getRootDirectory().trim()
                    : ".";
        }
        String rootDirectory = resolveRootDirectory(project);
        if (StringUtils.hasText(project.getLogDirectory())) {
            return project.getLogDirectory().trim();
        }
        return rootDirectory + "/logs";
    }

    private String resolveRuntimeStartupLogFile(ProjectRemoteProject project) {
        if ("tomcat".equals(normalizeProjectTypeToken(project.getProjectType()))) {
            if (StringUtils.hasText(project.getStartupLogFile())) {
                return project.getStartupLogFile().trim();
            }
            return resolveTomcatHome(project) + "/logs/catalina.out";
        }
        if ("static".equals(normalizeProjectTypeToken(project.getProjectType()))) {
            return "";
        }
        if ("frontend".equals(normalizeProjectTypeToken(project.getProjectType()))) {
            if (StringUtils.hasText(project.getStartupLogFile())) {
                return project.getStartupLogFile().trim();
            }
            return resolveRuntimeLogDirectory(project) + "/console.log";
        }
        if (StringUtils.hasText(project.getStartupLogFile())) {
            return project.getStartupLogFile().trim();
        }
        return resolveRuntimeLogDirectory(project) + "/console.log";
    }

    private String resolvePidFilePath(ProjectRemoteProject project) {
        if ("tomcat".equals(normalizeProjectTypeToken(project.getProjectType()))) {
            return resolveTomcatHome(project) + "/" + PID_FILE_NAME;
        }
        return resolveRootDirectory(project) + "/" + PID_FILE_NAME;
    }

    private String resolveMainArtifactPath(ProjectRemoteProject project) {
        String artifact = StringUtils.hasText(project.getMainArtifact())
                ? project.getMainArtifact().trim()
                : "";
        if (!StringUtils.hasText(artifact)) {
            return "";
        }
        if (artifact.startsWith("/") || artifact.matches("^[A-Za-z]:[\\\\/].*")) {
            return artifact;
        }
        String rootDirectory = resolveRootDirectory(project);
        if (!StringUtils.hasText(rootDirectory)) {
            return artifact;
        }
        String normalizedRoot = rootDirectory.replaceAll("[\\\\/]+$", "");
        String normalizedArtifact = artifact.replaceAll("^[\\\\/]+", "");
        String separator = normalizedRoot.contains("\\") ? "\\" : "/";
        return normalizedRoot + separator + normalizedArtifact;
    }

    private void refreshMainArtifactMd5(ServerHost host, ProjectRemoteProject project) {
        String artifactPath = resolveMainArtifactPath(project);
        if (!StringUtils.hasText(artifactPath)) {
            project.setMainArtifactMd5(null);
            return;
        }
        String out = exec(host,
                "if [ -f " + shQuote(artifactPath) + " ]; then "
                        + "md5sum " + shQuote(artifactPath) + " | awk '{print $1}'; "
                        + "fi");
        String md5 = firstNonBlankLine(out).trim().toLowerCase(Locale.ROOT);
        if (!md5.matches("^[a-f0-9]{32}$")) {
            project.setMainArtifactMd5(null);
            return;
        }
        project.setMainArtifactMd5(md5);
    }

    private void refreshSpringProjectSettings(ServerHost host, ProjectRemoteProject project) {
        if (!isSpringBootProjectType(project)) {
            return;
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.putAll(readJarSpringConfigValues(host, project));
        values.putAll(readExternalSpringConfigValues(host, project));
        if (values.isEmpty()) {
            return;
        }
        project.setSpringArgs(mergeSpringArgs(project.getSpringArgs(), values));
        String port = values.get("server.port");
        if (StringUtils.hasText(port)) {
            project.setHealthCheckUrl("http://127.0.0.1:" + port + "/actuator/health");
        } else if (!StringUtils.hasText(project.getHealthCheckUrl())) {
            project.setHealthCheckUrl("http://127.0.0.1:8080/actuator/health");
        }
    }

    private Map<String, String> readExternalSpringConfigValues(ServerHost host, ProjectRemoteProject project) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        LinkedHashMap<String, Boolean> candidates = new LinkedHashMap<>();
        addSpringConfigCandidates(candidates, resolveRootDirectory(project));
        addSpringConfigCandidates(candidates, StringUtils.hasText(project.getConfigDirectory()) ? project.getConfigDirectory().trim() : "");
        for (String path : candidates.keySet()) {
            String out = readRemoteIfExists(host, path);
            if (!StringUtils.hasText(out)) {
                continue;
            }
            values.putAll(extractSpringConfigValues(out));
        }
        return values;
    }

    private void addSpringConfigCandidates(Map<String, Boolean> candidates, String baseDirectory) {
        if (!StringUtils.hasText(baseDirectory)) {
            return;
        }
        String base = baseDirectory.replaceAll("[\\\\/]+$", "");
        String[] names = {
                "application.properties",
                "application.yml",
                "application.yaml",
                "application-dev.properties",
                "application-dev.yml",
                "application-dev.yaml",
                "bootstrap.properties",
                "bootstrap.yml",
                "bootstrap.yaml"
        };
        for (String name : names) {
            candidates.put(base + "/" + name, Boolean.TRUE);
        }
    }

    private Map<String, String> readJarSpringConfigValues(ServerHost host, ProjectRemoteProject project) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        String jarPath = resolveMainArtifactPath(project);
        if (!StringUtils.hasText(jarPath) || !jarPath.endsWith(".jar")) {
            return values;
        }
        String entries = exec(host,
                "jar tf " + shQuote(jarPath)
                        + " 2>/dev/null | grep -E '^BOOT-INF/classes/(application|bootstrap)(-[^/]+)?\\.(properties|ya?ml)$' || true");
        for (String line : entries.split("\n")) {
            String entry = line.trim();
            if (!StringUtils.hasText(entry)) {
                continue;
            }
            String content = exec(host,
                    "unzip -p " + shQuote(jarPath) + " " + shQuote(entry) + " 2>/dev/null || true");
            if (!StringUtils.hasText(content)) {
                continue;
            }
            values.putAll(extractSpringConfigValues(content));
        }
        return values;
    }

    private String readRemoteIfExists(ServerHost host, String path) {
        String out = exec(host,
                "if [ -f " + shQuote(path) + " ]; then cat " + shQuote(path) + "; fi 2>/dev/null");
        return out.startsWith("ERROR:") ? "" : out;
    }

    private Map<String, String> extractSpringConfigValues(String content) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        List<String[]> scopes = new ArrayList<>();
        for (String rawLine : content.split("\n")) {
            String line = rawLine.replace("\r", "");
            if (!StringUtils.hasText(line)) {
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed) || trimmed.startsWith("#")) {
                continue;
            }
            while (!scopes.isEmpty() && indent <= Integer.parseInt(scopes.get(scopes.size() - 1)[0])) {
                scopes.remove(scopes.size() - 1);
            }
            if (trimmed.endsWith(":") && !trimmed.contains(": ")) {
                scopes.add(new String[]{String.valueOf(indent), trimmed.substring(0, trimmed.length() - 1).trim()});
                continue;
            }
            int separator = trimmed.indexOf(':');
            int equalIndex = trimmed.indexOf('=');
            if (separator < 0 || (equalIndex >= 0 && equalIndex < separator)) {
                separator = equalIndex;
            }
            if (separator <= 0) {
                continue;
            }
            String key = trimmed.substring(0, separator).trim();
            String value = sanitizeSpringConfigValue(trimmed.substring(separator + 1));
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String fullKey = buildSpringConfigKey(scopes, key);
            if (isTrackedSpringConfigKey(fullKey)) {
                values.put(fullKey, value);
            }
        }
        return values;
    }

    private String buildSpringConfigKey(List<String[]> scopes, String key) {
        if (scopes.isEmpty()) {
            return key;
        }
        StringBuilder builder = new StringBuilder();
        for (String[] scope : scopes) {
            if (builder.length() > 0) {
                builder.append('.');
            }
            builder.append(scope[1]);
        }
        if (builder.length() > 0) {
            builder.append('.');
        }
        builder.append(key);
        return builder.toString();
    }

    private String sanitizeSpringConfigValue(String value) {
        String normalized = String.valueOf(value == null ? "" : value).trim();
        int commentIndex = normalized.indexOf(" #");
        if (commentIndex >= 0) {
            normalized = normalized.substring(0, commentIndex).trim();
        }
        if ((normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("'") && normalized.endsWith("'"))) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean isTrackedSpringConfigKey(String key) {
        return "server.port".equals(key)
                || "spring.profiles.active".equals(key)
                || "spring.application.name".equals(key)
                || "spring.config.location".equals(key)
                || "logging.level.root".equals(key)
                || "management.endpoints.web.exposure.include".equals(key);
    }

    private String mergeSpringArgs(String currentArgs, Map<String, String> values) {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        List<String> customTokens = new ArrayList<>();
        for (String token : String.valueOf(currentArgs == null ? "" : currentArgs).split("\\s+")) {
            String normalized = token.trim();
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            String withoutPrefix = normalized.startsWith("--") ? normalized.substring(2) : normalized;
            int index = withoutPrefix.indexOf('=');
            if (index <= 0) {
                customTokens.add(normalized);
                continue;
            }
            String key = withoutPrefix.substring(0, index).trim();
            String value = withoutPrefix.substring(index + 1).trim();
            if (isTrackedSpringConfigKey(key)) {
                merged.put(key, value);
                continue;
            }
            customTokens.add(normalized);
        }
        values.forEach(merged::put);
        List<String> tokens = new ArrayList<>();
        merged.forEach((key, value) -> {
            if (StringUtils.hasText(value)) {
                tokens.add("--" + key + "=" + value);
            }
        });
        tokens.addAll(customTokens);
        return String.join(" ", tokens).trim();
    }

    private boolean isSpringBootProjectType(ProjectRemoteProject project) {
        String type = normalizeProjectTypeToken(project.getProjectType());
        return "springboot".equals(type)
                || "springboot_resource_isolation".equals(type)
                || "springboot_resource_split".equals(type)
                || "springboot_resource_separation".equals(type);
    }

    private String buildStartScript(ProjectRemoteProject project) {
        String rootDirectory = resolveRootDirectory(project);
        if (isCustomProjectType(project) && StringUtils.hasText(project.getStartScript())) {
            return "cd " + shQuote(rootDirectory) + " && bash -c '" + project.getStartScript().replace("'", "'\\''") + "' 2>&1";
        }
        return resolveRuntimeCommandStrategy(project).buildStart(buildRuntimeCommandContext(project));
    }

    private String buildStopScript(ProjectRemoteProject project) {
        String rootDirectory = resolveRootDirectory(project);
        if (isCustomProjectType(project) && StringUtils.hasText(project.getStopScript())) {
            return "cd " + shQuote(rootDirectory) + " && bash -c '" + project.getStopScript().replace("'", "'\\''") + "' 2>&1";
        }
        return resolveRuntimeCommandStrategy(project).buildStop(buildRuntimeCommandContext(project));
    }

    private String buildRestartScript(ProjectRemoteProject project) {
        if (isCustomProjectType(project) && StringUtils.hasText(project.getRestartScript())) {
            return "cd " + shQuote(resolveRootDirectory(project)) + " && bash -c '" + project.getRestartScript().replace("'", "'\\''") + "' 2>&1";
        }
        if (isCustomProjectType(project)) {
            return buildStopScript(project) + " && sleep 2 && " + buildStartScript(project);
        }
        return resolveRuntimeCommandStrategy(project).buildRestart(buildRuntimeCommandContext(project));
    }

    private ProjectRuntimeCommandContext buildRuntimeCommandContext(ProjectRemoteProject project) {
        return new ProjectRuntimeCommandContext(
                project,
                resolveRootDirectory(project),
                resolveRuntimeLogDirectory(project),
                resolveRuntimeStartupLogFile(project),
                resolvePidFilePath(project),
                buildStatusPidScript(project),
                buildProjectPidScript(project)
        );
    }

    private ProjectRuntimeCommandStrategy resolveRuntimeCommandStrategy(ProjectRemoteProject project) {
        String type = normalizeProjectTypeToken(project.getProjectType());
        ProjectRuntimeCommandStrategy strategy = runtimeCommandStrategies.getExtension(type);
        if (strategy != null) {
            return strategy;
        }
        return runtimeCommandStrategies.getExtension("default");
    }

    private String resolveTomcatHome(ProjectRemoteProject project) {
        if (StringUtils.hasText(project.getTomcatHome())) {
            return project.getTomcatHome().trim();
        }
        return resolveRootDirectory(project);
    }

    private String resolveBackupRootDirectory(ProjectRemoteProject project) {
        if ("tomcat".equals(normalizeProjectTypeToken(project.getProjectType()))) {
            return resolveTomcatHome(project);
        }
        return resolveRootDirectory(project);
    }

    private String runDeployPreflightChecks(ServerHost host,
                                            ProjectRemoteProject project,
                                            boolean configCheckBefore,
                                            boolean dependencyCheckBefore) {
        List<String> errors = new ArrayList<>();
        if (configCheckBefore) {
            if (!StringUtils.hasText(project.getConfigDirectory())) {
                errors.add("[配置核对] 未配置 configDirectory");
            } else {
                String out = exec(host, "[ -d " + shQuote(project.getConfigDirectory()) + " ] && echo OK || echo MISSING");
                if (!out.contains("OK")) {
                    errors.add("[配置核对] 目录不存在: " + project.getConfigDirectory());
                }
            }
        }
        if (dependencyCheckBefore) {
            if (!StringUtils.hasText(project.getLibDirectory())) {
                errors.add("[依赖核对] 未配置 libDirectory");
            } else {
                String out = exec(host, "[ -d " + shQuote(project.getLibDirectory()) + " ] && echo OK || echo MISSING");
                if (!out.contains("OK")) {
                    errors.add("[依赖核对] 目录不存在: " + project.getLibDirectory());
                }
            }
        }
        return String.join("\n", errors);
    }

    private boolean isArchiveFile(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz");
    }

    private String resolveDeployTargetFileName(ProjectRemoteProject project, String uploadedFileName) {
        if ((!StringUtils.hasText(project.getProjectType())
                || "springboot".equals(project.getProjectType())
                || "springboot_resource_split".equals(project.getProjectType())
                || "springboot_resource_isolation".equals(project.getProjectType())
                || "springboot_resource_separation".equals(project.getProjectType())
                || "java".equals(project.getProjectType())
                || "tomcat".equals(project.getProjectType())
                || "frontend".equals(project.getProjectType()))
                && StringUtils.hasText(project.getMainArtifact())) {
            return project.getMainArtifact();
        }
        return uploadedFileName;
    }

    private String buildStatusPidScript(ProjectRemoteProject project) {
        String statusKeyword = extractStatusPgrepKeyword(project);
        if (!StringUtils.hasText(statusKeyword)) {
            return "";
        }
        String kw = shQuote(statusKeyword);
        return "for pid in $(pgrep -f -- " + kw + " 2>/dev/null); do "
                + "cmd=$(ps -p \"$pid\" -o args= 2>/dev/null); "
                + "echo \"$cmd\" | grep -E 'pgrep -f|grep -F|grep -E' >/dev/null && continue; "
                + "echo \"$pid\"; "
                + "done";
    }

    private String extractStatusPgrepKeyword(ProjectRemoteProject project) {
        if (!isCustomProjectType(project)) {
            return null;
        }
        if (!StringUtils.hasText(project.getStatusScript())) {
            return null;
        }
        Matcher matcher = STATUS_PGREP_PATTERN.matcher(project.getStatusScript());
        if (!matcher.find()) {
            return null;
        }
        String keyword = matcher.group(1);
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    private String buildProjectPidScript(ProjectRemoteProject project) {
        String type = normalizeProjectTypeToken(project.getProjectType());
        if ("static".equals(type)) {
            return "true";
        }
        if ("frontend".equals(type)) {
            // frontend PID 文件存储引擎标记：nginx:<port> 或数字 PID
            String pidFilePath = resolvePidFilePath(project);
            String rootDirectory = resolveRootDirectory(project);
            return "pid_file=" + shQuote(pidFilePath) + "; "
                    + "if [ -f \"$pid_file\" ]; then "
                    + "  _engine=$(cat \"$pid_file\" | tr -d '\\r\\n '); "
                    + "  if echo \"$_engine\" | grep -q '^nginx:'; then "
                    + "    pgrep -x nginx 2>/dev/null || true; "
                    + "  else "
                    + "    _pid=$(echo \"$_engine\" | grep -o '^[0-9]\\+'); "
                    + "    if [ -n \"$_pid\" ] && kill -0 \"$_pid\" 2>/dev/null; then echo \"$_pid\"; fi; "
                    + "  fi; "
                    + "else "
                    // 兜底：查找 http-server 或 serve 进程（cwd 匹配）
                    + "  root_dir=" + shQuote(rootDirectory) + "; "
                    + "  for _kw in 'http-server' 'serve'; do "
                    + "    pgrep -f -- \"$_kw\" 2>/dev/null | while read _p; do "
                    + "      if [ -L /proc/\"$_p\"/cwd ]"
                    + "        && [ \"$(readlink /proc/\"$_p\"/cwd)\" = \"$root_dir\" ]; then echo \"$_p\"; fi; "
                    + "    done; "
                    + "  done; "
                    + "fi";
        }
        if ("tomcat".equals(type)) {
            String tomcatHome = resolveTomcatHome(project);
            return "pgrep -f -- " + shQuote(tomcatHome) + " 2>/dev/null || true";
        }
        String keyword = firstNonBlank(
                StringUtils.hasText(project.getMainArtifact()) ? project.getMainArtifact().trim() : null,
                "nodejs".equals(type) ? "app.js" : null,
                "python".equals(type) ? "app.py" : null,
                StringUtils.hasText(project.getProjectName()) ? project.getProjectName().trim() : null
        );
        if (!StringUtils.hasText(keyword)) {
            return "true";
        }
        String rootDirectory = StringUtils.hasText(project.getRootDirectory()) ? project.getRootDirectory().trim() : "";
        String kw = shQuote(keyword);
        return "root_dir=" + shQuote(rootDirectory) + "; "
                + "for pid in $(pgrep -f -- " + kw + " 2>/dev/null); do "
                + "cmd=$(ps -p \"$pid\" -o args= 2>/dev/null); "
                + "echo \"$cmd\" | grep -E 'pgrep -f|grep -F|grep -E' >/dev/null && continue; "
                + "if [ -n \"$root_dir\" ]; then "
                + "cwd=$(readlink -f \"/proc/$pid/cwd\" 2>/dev/null); "
                + "if [ -n \"$cwd\" ]; then "
                + "if [ \"$cwd\" != \"$root_dir\" ]; then "
                + "echo \"$cmd\" | grep -F -- \"$root_dir\" >/dev/null || continue; "
                + "fi; "
                + "else "
                + "echo \"$cmd\" | grep -F -- \"$root_dir\" >/dev/null || continue; "
                + "fi; "
                + "fi; "
                + "echo \"$pid\"; "
                + "done";
    }

    private Long parseFirstPid(String output) {
        String firstLine = firstNonBlankLine(output).trim();
        if (!firstLine.matches("^\\d+$")) {
            return null;
        }
        try {
            return Long.parseLong(firstLine);
        } catch (Exception ignored) {
            return null;
        }
    }

    private record RuntimeSnapshot(String runtimeStatus, Long pid) {
    }

    private String shQuote(String value) {
        return "'" + String.valueOf(value).replace("'", "'\"'\"'") + "'";
    }

    private String normalizeProjectTypeToken(String type) {
        if (!StringUtils.hasText(type)) {
            return "custom";
        }
        return type.trim().toLowerCase(Locale.ROOT).replace("-", "_");
    }

    private boolean isCustomProjectType(ProjectRemoteProject project) {
        return "custom".equals(normalizeProjectTypeToken(project.getProjectType()));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}


