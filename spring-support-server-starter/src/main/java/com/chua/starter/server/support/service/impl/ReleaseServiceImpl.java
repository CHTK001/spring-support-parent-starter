package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.placeholder.MapMixSystemPlaceholderResolver;
import com.chua.common.support.lang.placeholder.PlaceholderSupport;
import com.chua.common.support.lang.placeholder.StringValuePropertyResolver;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsServerConfig;
import com.chua.starter.server.support.entity.MsServerProject;
import com.chua.starter.server.support.entity.MsServerRelease;
import com.chua.starter.server.support.entity.SoftwarePackage;
import com.chua.starter.server.support.mapper.MsServerConfigMapper;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.mapper.MsServerProjectMapper;
import com.chua.starter.server.support.mapper.MsServerReleaseMapper;
import com.chua.starter.server.support.model.vo.ProjectBackupVO;
import com.chua.starter.server.support.push.ProjectTaskService;
import com.chua.starter.server.support.push.ProjectTaskService.ProjectStep;
import com.chua.starter.server.support.service.ExecService;
import com.chua.starter.server.support.service.ReleaseService;
import com.chua.starter.server.support.service.SftpService;
import com.chua.starter.server.support.spi.PackageManagerProvider;
import com.chua.starter.server.support.spi.PackageManagerRouter;
import com.chua.starter.server.support.util.ProjectScripts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 发布管理服务实现
 *
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class ReleaseServiceImpl implements ReleaseService {

    private final MsServerReleaseMapper releaseMapper;
    private final MsServerProjectMapper projectMapper;
    private final MsServerHostMapper hostMapper;
    private final ExecService execService;
    private final ProjectTaskService projectTaskService;

    /**
     * 备份保留数量：每次备份后只保留最新 N 个，清理更旧的，防止备份无限增长撑满磁盘
     */
    private static final int MAX_BACKUPS = 10;
    /**
     * SFTP 服务（拖拽升级上传安装包，Linux）
     */
    private final SftpService sftpService;
    /**
     * 软件仓库包管理器路由器（按操作系统/实际包管理器选择 SPI 实现）
     */
    private final PackageManagerRouter packageManagerRouter;

    /**
     * 系统配置 Mapper（读写升级全局/最近配置的原始 JSON）
     */
    private final MsServerConfigMapper serverConfigMapper;

    /**
     * JSON 序列化（升级配置解析）
     */
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private static final String CFG_GLOBAL_KEY = "upgrade.global";
    private static final String CFG_LAST_KEY = "upgrade.last";

    /**
     * 操作上下文：项目 + 目标主机
     */
    private static class OperationContext {
        private MsServerProject project;
        private Long hostId;
        /** 目标主机是否为 Windows（决定命令按 PowerShell 还是 bash 构造） */
        private boolean windows;
    }

    // ==================== 发布 CRUD ====================

    @Override
    public ReturnResult<List<MsServerRelease>> listReleases() {
        List<MsServerRelease> releases = releaseMapper.selectList(
                new LambdaQueryWrapper<MsServerRelease>().orderByDesc(MsServerRelease::getId));
        // 回填主机IP，保证发布卡片主机信息完整
        releases.forEach(this::fillHostInfo);
        return ReturnResult.list(releases);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<MsServerRelease> createRelease(MsServerRelease release) {
        fillHostInfo(release);
        release.setCreateTime(LocalDateTime.now());
        release.setUpdateTime(LocalDateTime.now());
        releaseMapper.insert(release);
        return ReturnResult.ok(release);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<MsServerRelease> updateRelease(Long id, MsServerRelease release) {
        MsServerRelease exists = releaseMapper.selectById(id);
        if (exists == null) {
            return ReturnResult.error("发布不存在: " + id);
        }
        exists.setReleaseName(release.getReleaseName() == null ? exists.getReleaseName() : release.getReleaseName());
        exists.setHostId(release.getHostId() == null ? exists.getHostId() : release.getHostId());
        exists.setHostIp(release.getHostIp() == null ? exists.getHostIp() : release.getHostIp());
        exists.setDescription(release.getDescription() == null ? exists.getDescription() : release.getDescription());
        exists.setBackgroundImage(release.getBackgroundImage() == null ? exists.getBackgroundImage() : release.getBackgroundImage());
        fillHostInfo(exists);
        exists.setUpdateTime(LocalDateTime.now());
        releaseMapper.updateById(exists);
        return ReturnResult.ok(exists);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Void> deleteRelease(Long id) {
        // 删除发布下的所有项目
        projectMapper.delete(new LambdaQueryWrapper<MsServerProject>()
                .eq(MsServerProject::getReleaseId, id));
        releaseMapper.deleteById(id);
        return ReturnResult.success();
    }

    // ==================== 项目 CRUD ====================

    @Override
    public ReturnResult<List<MsServerProject>> listProjects(Long releaseId) {
        List<MsServerProject> projects = projectMapper.selectList(
                new LambdaQueryWrapper<MsServerProject>()
                        .eq(MsServerProject::getReleaseId, releaseId)
                        .orderByDesc(MsServerProject::getEnabled)
                        .orderByDesc(MsServerProject::getId));
        return ReturnResult.list(projects);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<MsServerProject> createProject(MsServerProject project) {
        project.setCreateTime(LocalDateTime.now());
        project.setUpdateTime(LocalDateTime.now());
        if (isBlank(project.getStatus())) {
            project.setStatus("stopped");
        }
        if (project.getEnabled() == null) {
            project.setEnabled(true);
        }
        // 四个脚本留空时用内置通用模板（含占位符）补全，用户无需手写
        applyDefaultScripts(project);
        projectMapper.insert(project);
        return ReturnResult.ok(project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<MsServerProject> updateProject(Long id, MsServerProject project) {
        MsServerProject exists = projectMapper.selectById(id);
        if (exists == null) {
            return ReturnResult.error("项目不存在: " + id);
        }
        exists.setProjectName(project.getProjectName() == null ? exists.getProjectName() : project.getProjectName());
        exists.setProjectType(project.getProjectType() == null ? exists.getProjectType() : project.getProjectType());
        exists.setMainEntry(project.getMainEntry() == null ? exists.getMainEntry() : project.getMainEntry());
        exists.setStartScript(project.getStartScript() == null ? exists.getStartScript() : project.getStartScript());
        exists.setStopScript(project.getStopScript() == null ? exists.getStopScript() : project.getStopScript());
        exists.setRestartScript(project.getRestartScript() == null ? exists.getRestartScript() : project.getRestartScript());
        exists.setProjectPath(project.getProjectPath() == null ? exists.getProjectPath() : project.getProjectPath());
        exists.setLogPath(project.getLogPath() == null ? exists.getLogPath() : project.getLogPath());
        exists.setLogMode(project.getLogMode() == null ? exists.getLogMode() : project.getLogMode());
        exists.setDescription(project.getDescription() == null ? exists.getDescription() : project.getDescription());
                exists.setIcon(project.getIcon() == null ? exists.getIcon() : project.getIcon());
        exists.setEnvVars(project.getEnvVars() == null ? exists.getEnvVars() : project.getEnvVars());
        exists.setCmdArgs(project.getCmdArgs() == null ? exists.getCmdArgs() : project.getCmdArgs());
        exists.setJvmArgs(project.getJvmArgs() == null ? exists.getJvmArgs() : project.getJvmArgs());
        exists.setStatusScript(project.getStatusScript() == null ? exists.getStatusScript() : project.getStatusScript());
        exists.setServiceName(project.getServiceName() == null ? exists.getServiceName() : project.getServiceName());
        exists.setAutostart(project.getAutostart() == null ? exists.getAutostart() : project.getAutostart());
        exists.setGuardian(project.getGuardian() == null ? exists.getGuardian() : project.getGuardian());
        exists.setEnabled(project.getEnabled() == null ? exists.getEnabled() : project.getEnabled());
        exists.setPid(project.getPid() == null ? exists.getPid() : project.getPid());
        exists.setAppConfig(project.getAppConfig() == null ? exists.getAppConfig() : project.getAppConfig());
        exists.setConfigDir(project.getConfigDir() == null ? exists.getConfigDir() : project.getConfigDir());
        exists.setLibDir(project.getLibDir() == null ? exists.getLibDir() : project.getLibDir());
        exists.setNginxDir(project.getNginxDir() == null ? exists.getNginxDir() : project.getNginxDir());
        // 四个脚本为空时用内置通用模板补全
        applyDefaultScripts(exists);
        exists.setUpdateTime(LocalDateTime.now());
        projectMapper.updateById(exists);
        return ReturnResult.ok(exists);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Void> deleteProject(Long id) {
        projectMapper.deleteById(id);
        return ReturnResult.success();
    }

    // ==================== 项目操作（异步任务） ====================

    @Override
    public ReturnResult<String> startProject(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        if (isBlank(project.getStartScript())) {
            return ReturnResult.error("启动脚本未配置");
        }
        // 幂等：已在运行则不再重复启动（避免重复拉起、覆盖 app.pid、产生孤儿进程）
        if (!isBlank(project.getStatusScript())) {
            ReturnResult<String> cur = statusProject(id);
            if (cur.isOk() && cur.getData() != null
                    && cur.getData().toLowerCase().contains("running")) {
                return ReturnResult.error("项目已在运行，无需重复启动");
            }
        }
        boolean win = prepared.getData().windows;
        String path = project.getProjectPath();
        List<ProjectStep> steps = new ArrayList<>();
        ProjectStep cfg = springConfigStep(win, project);
        if (cfg != null) {
            steps.add(cfg);
        }
        steps.add(new ProjectStep("启动项目", buildCommand(win, path, project.getStartScript()), 3));
        steps.add(pidStep(win, path));
        String taskId = projectTaskService.startTask(prepared.getData().hostId, project.getId(),
                "start", steps, "running");
        return task(taskId);
    }

    @Override
    public ReturnResult<String> stopProject(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        if (isBlank(project.getStopScript())) {
            return ReturnResult.error("停止脚本未配置");
        }
        List<ProjectStep> steps = List.of(new ProjectStep("停止项目",
                buildCommand(prepared.getData().windows, project.getProjectPath(), project.getStopScript()), 1));
        String taskId = projectTaskService.startTask(prepared.getData().hostId, project.getId(),
                "stop", steps, "stopped");
        return task(taskId);
    }

    @Override
    public ReturnResult<String> restartProject(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        String path = project.getProjectPath();
        boolean win = prepared.getData().windows;
        List<ProjectStep> steps = new ArrayList<>();
        ProjectStep cfg = springConfigStep(win, project);
        if (cfg != null) {
            steps.add(cfg);
        }
        if (!isBlank(project.getRestartScript())) {
            steps.add(new ProjectStep("重启项目", buildCommand(win, path, project.getRestartScript()), 3));
        } else {
            if (isBlank(project.getStopScript()) || isBlank(project.getStartScript())) {
                return ReturnResult.error("重启脚本未配置（需停止+启动脚本或重启脚本）");
            }
            steps.add(new ProjectStep("停止服务", buildCommand(win, path, project.getStopScript()), 2));
            steps.add(new ProjectStep("启动服务", buildCommand(win, path, project.getStartScript()), 3));
        }
        steps.add(pidStep(win, path));
        String taskId = projectTaskService.startTask(prepared.getData().hostId, project.getId(),
                "restart", steps, "running");
        return task(taskId);
    }

    @Override
    public ReturnResult<String> upgradeProject(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        boolean win = prepared.getData().windows;
        String path = project.getProjectPath();
        List<ProjectStep> steps = new ArrayList<>();
        ProjectStep cfg = springConfigStep(win, project);
        if (cfg != null) {
            steps.add(cfg);
        }
        if (!isBlank(project.getStopScript())) {
            steps.add(new ProjectStep("停止服务", buildCommand(win, path, project.getStopScript()), 1));
        }
        steps.add(new ProjectStep("备份当前版本", backupCommand(win, path), 2));
        if (!isBlank(project.getStartScript())) {
            steps.add(new ProjectStep("启动服务", buildCommand(win, path, project.getStartScript()), 3));
        }
        steps.add(pidStep(win, path));
        String taskId = projectTaskService.startTask(prepared.getData().hostId, project.getId(),
                "upgrade", steps, "running");
        return task(taskId);
    }

    @Override
    public ReturnResult<String> backupProject(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        List<ProjectStep> steps = List.of(new ProjectStep("备份项目",
                backupCommand(prepared.getData().windows, project.getProjectPath()), 1));
        String taskId = projectTaskService.startTask(prepared.getData().hostId, project.getId(),
                "backup", steps, null);
        return task(taskId);
    }

    @Override
    public ReturnResult<String> rollbackProject(Long id, String backupName) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        boolean win = prepared.getData().windows;
        List<ProjectStep> steps = new ArrayList<>();
        if (!isBlank(project.getStopScript())) {
            steps.add(new ProjectStep("停止服务", buildCommand(win, project.getProjectPath(), project.getStopScript()), 1));
        }
        String stepLabel = isBlank(backupName) ? "恢复最新备份" : ("恢复备份 " + backupName);
        steps.add(new ProjectStep(stepLabel, rollbackCommand(win, project.getProjectPath(), backupName), 2));
        if (!isBlank(project.getStartScript())) {
            steps.add(new ProjectStep("启动服务", buildCommand(win, project.getProjectPath(), project.getStartScript()), 1));
        }
        String taskId = projectTaskService.startTask(prepared.getData().hostId, project.getId(),
                "rollback", steps, "running");
        return task(taskId);
    }

    @Override
    public ReturnResult<List<ProjectBackupVO>> listBackups(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        OperationContext ctx = prepared.getData();
        String command = backupListCommand(ctx.windows, ctx.project.getProjectPath());
        ReturnResult<List<com.chua.starter.server.support.entity.MsExecLog>> result =
                execService.runCommand(Collections.singletonList(ctx.hostId), command, 30, null);
        List<ProjectBackupVO> rows = new ArrayList<>();
        if (result.isSuccess() && result.getData() != null && !result.getData().isEmpty()) {
            String output = result.getData().get(0).getMsExecLogOutput();
            if (output != null) {
                for (String raw : output.split("\n")) {
                    String line = raw.trim();
                    if (!line.startsWith("backup_")) {
                        continue;
                    }
                    // name|epochSeconds|size|kind
                    String[] parts = line.split("\\|", -1);
                    ProjectBackupVO vo = new ProjectBackupVO();
                    vo.setName(parts[0]);
                    vo.setTime(parseEpochSeconds(parts.length > 1 ? parts[1] : null));
                    vo.setSize(parseLongOrNull(parts.length > 2 ? parts[1 + 1] : null));
                    vo.setKind(parts.length > 3 && !parts[3].isBlank() ? parts[3]
                            : (parts[0].endsWith(".tar.gz") ? "file" : "dir"));
                    rows.add(vo);
                }
            }
        }
        // 最新备份在前
        rows.sort((x, y) -> Long.compare(
                y.getTime() == null ? 0L : y.getTime(),
                x.getTime() == null ? 0L : x.getTime()));
        return ReturnResult.ok(rows);
    }

    /** epoch 秒字符串 -> 毫秒；非法返回 null */
    private Long parseEpochSeconds(String raw) {
        try {
            return Long.parseLong(raw.trim()) * 1000L;
        } catch (Exception e) {
            return null;
        }
    }

    /** 数字字符串 -> Long；空或非法返回 null */
    private Long parseLongOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 构造「列出 backups/ 备份记录」命令，输出每行 name|epochSeconds|size|kind
     */
    private String backupListCommand(boolean windows, String projectPath) {
        if (windows) {
            StringBuilder ps = new StringBuilder();
            if (!isBlank(projectPath)) {
                ps.append("Set-Location -LiteralPath '").append(projectPath.replace("'", "''")).append("'\n");
            }
            ps.append("if (Test-Path 'backups') {\n");
            ps.append("  Get-ChildItem 'backups' -Filter 'backup_*' | ForEach-Object {\n");
            ps.append("    $kind = 'file'; $size = $_.Length\n");
            ps.append("    if ($_.PSIsContainer) { $kind = 'dir'; $size = '' }\n");
            ps.append("    $epoch = [int][double]::Parse((Get-Date $_.LastWriteTime -UFormat %s))\n");
            ps.append("    $_.Name + '|' + $epoch + '|' + $size + '|' + $kind\n");
            ps.append("  }\n");
            ps.append("}");
            return ps.toString();
        }
        StringBuilder sh = new StringBuilder();
        if (!isBlank(projectPath)) {
            sh.append("cd '").append(projectPath.replace("'", "'\\''")).append("' && ");
        }
        sh.append("if [ -d backups ]; then find backups -maxdepth 1 -name 'backup_*' | while read p; do ")
          .append("f=$(basename \"$p\"); ")
          .append("if [ -d \"$p\" ]; then kind=dir; size=; else kind=file; size=$(stat -c %s \"$p\"); fi; ")
          .append("epoch=$(stat -c %Y \"$p\"); ")
          .append("echo \"$f|$epoch|$size|$kind\"; done; fi");
        return sh.toString();
    }

    // ==================== 日志 ====================

    @Override
    public ReturnResult<String> getProjectLog(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        if (isBlank(project.getLogPath())) {
            return ReturnResult.ok("未配置日志路径");
        }
        // 按操作系统生成读取命令：Windows 无 tail，用 PowerShell Get-Content -Tail
        String logPath = project.getLogPath();
        String command = prepared.getData().windows
                ? "if (Test-Path '" + logPath + "') { Get-Content -Path '" + logPath + "' -Tail 100 }"
                : "tail -n 100 " + logPath;
        ReturnResult<List<com.chua.starter.server.support.entity.MsExecLog>> result =
                execService.runCommand(Collections.singletonList(prepared.getData().hostId), command, 30, null);

        if (result.isSuccess() && result.getData() != null && !result.getData().isEmpty()) {
            return ReturnResult.ok(result.getData().get(0).getMsExecLogOutput());
        }
        return ReturnResult.ok("暂无日志内容");
    }

    @Override
    public ReturnResult<String> startProjectLog(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        if (isBlank(project.getLogPath())) {
            return ReturnResult.error("日志路径未配置");
        }
        String sessionId = projectTaskService.startLogTail(prepared.getData().hostId,
                project.getId(), project.getLogPath());
        return ReturnResult.ok(sessionId);
    }

    @Override
    public ReturnResult<Void> stopProjectLog(String sessionId) {
        projectTaskService.stopLogTail(sessionId);
        return ReturnResult.success();
    }

    // ==================== 软件仓库 ====================

    @Override
    public ReturnResult<List<SoftwarePackage>> searchSoftware(Long hostId, String keyword) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }

        PackageManagerProvider provider;
        try {
            provider = packageManagerRouter.route(host);
        } catch (IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        }

        String command = provider.searchCommand(keyword);
        ReturnResult<List<com.chua.starter.server.support.entity.MsExecLog>> result =
                execService.runCommand(Collections.singletonList(hostId), command, 30, null);

        List<SoftwarePackage> packages = new ArrayList<>();
        if (result.isSuccess() && result.getData() != null && !result.getData().isEmpty()) {
            String output = result.getData().get(0).getMsExecLogOutput();
            packages = provider.parse(output, keyword);
        }
        return ReturnResult.ok(packages);
    }

    /**
     * 安装软件：构建包管理器安装命令，转为异步任务，实时进度经 socket 推送
     */
    @Override
    public ReturnResult<String> installSoftware(Long hostId, String name) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        if (isBlank(name)) {
            return ReturnResult.error("软件包名不能为空");
        }

        PackageManagerProvider provider;
        try {
            provider = packageManagerRouter.route(host);
        } catch (IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        }
        String command = provider.installCommand(name);

        List<ProjectStep> steps = List.of(new ProjectStep("安装 " + name, command, 1));
        String taskId = projectTaskService.startTask(hostId, null, "install", steps, null);
        return task(taskId);
    }

    // ==================== 状态检测 ====================

    @Override
    public ReturnResult<String> statusProject(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        OperationContext ctx = prepared.getData();
        MsServerProject project = ctx.project;
        if (isBlank(project.getStatusScript())) {
            return ReturnResult.error("状态脚本未配置");
        }
        String command = buildCommand(ctx.windows, project.getProjectPath(), project.getStatusScript());
        ReturnResult<List<com.chua.starter.server.support.entity.MsExecLog>> result =
                execService.runCommand(Collections.singletonList(ctx.hostId), command, 20, null);
        if (result.isSuccess() && result.getData() != null && !result.getData().isEmpty()) {
            return ReturnResult.ok(result.getData().get(0).getMsExecLogOutput());
        }
        return ReturnResult.ok("unknown");
    }

    // ==================== 系统服务挂载 ====================

    @Override
    public ReturnResult<String> installService(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        OperationContext ctx = prepared.getData();
        MsServerProject project = ctx.project;
        if (isBlank(project.getProjectPath())) {
            return ReturnResult.error("项目地址未配置，无法挂载服务");
        }
        String svc = isBlank(project.getServiceName()) ? serviceKey(project) : project.getServiceName();
        String command = ctx.windows
                ? installServiceCommandWin(project, svc)
                : installServiceCommand(project, svc);
        List<ProjectStep> steps = List.of(new ProjectStep("挂载系统服务", command, 1));
        String taskId = projectTaskService.startTask(ctx.hostId, project.getId(),
                "install-service", steps, null);
        project.setServiceName(svc);
        projectMapper.updateById(project);
        return task(taskId);
    }

    @Override
    public ReturnResult<String> uninstallService(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        OperationContext ctx = prepared.getData();
        MsServerProject project = ctx.project;
        String svc = isBlank(project.getServiceName()) ? serviceKey(project) : project.getServiceName();
        String command = ctx.windows
                ? uninstallServiceCommandWin(project, svc)
                : uninstallServiceCommand(project, svc);
        List<ProjectStep> steps = List.of(new ProjectStep("卸载系统服务", command, 1));
        String taskId = projectTaskService.startTask(ctx.hostId, project.getId(),
                "uninstall-service", steps, null);
        project.setServiceName(null);
        projectMapper.updateById(project);
        return task(taskId);
    }

    // ==================== 升级配置 ====================

    @Override
    public ReturnResult<Map<String, Object>> getUpgradeConfig() {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("global", readCfg(CFG_GLOBAL_KEY, defaultGlobalConfig()));
        out.put("last", readCfg(CFG_LAST_KEY, null));
        return ReturnResult.ok(out);
    }

    @Override
    public ReturnResult<Void> saveUpgradeConfig(String scope, Map<String, Object> config) {
        String key;
        String desc;
        if ("global".equalsIgnoreCase(scope)) {
            key = CFG_GLOBAL_KEY;
            desc = "升级全局默认配置";
        } else if ("last".equalsIgnoreCase(scope)) {
            key = CFG_LAST_KEY;
            desc = "最近一次手动升级配置";
        } else {
            return ReturnResult.error("非法 scope: " + scope);
        }
        if (config == null) {
            return ReturnResult.error("配置不能为空");
        }
        String value;
        try {
            value = JSON.writeValueAsString(config);
        } catch (Exception e) {
            return ReturnResult.error("配置序列化失败");
        }
        MsServerConfig exists = serverConfigMapper.selectOne(
                new LambdaQueryWrapper<MsServerConfig>().eq(MsServerConfig::getConfigKey, key));
        if (exists == null) {
            MsServerConfig cfg = new MsServerConfig();
            cfg.setConfigKey(key);
            cfg.setConfigValue(value);
            cfg.setConfigDesc(desc);
            serverConfigMapper.insert(cfg);
        } else {
            exists.setConfigValue(value);
            serverConfigMapper.updateById(exists);
        }
        return ReturnResult.success();
    }

    // ==================== 按配置升级 ====================

    @Override
    public ReturnResult<String> runUpgrade(Long id, Map<String, Object> config) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        OperationContext ctx = prepared.getData();
        MsServerProject project = ctx.project;
        boolean win = ctx.windows;
        String path = project.getProjectPath();
        Map<String, Object> cfg = config == null ? new java.util.LinkedHashMap<>() : config;
        boolean autoBackup = boolCfg(cfg, "autoBackup", true);
        boolean autoExtract = boolCfg(cfg, "autoExtract", true);
        boolean autoRestart = boolCfg(cfg, "autoRestart", true);
        boolean autoRollback = boolCfg(cfg, "autoRollback", false);
        String packagePath = strCfg(cfg, "packagePath", "");

        List<ProjectStep> steps = new ArrayList<>();
        ProjectStep cfgStep = springConfigStep(win, project);
        if (cfgStep != null) {
            steps.add(cfgStep);
        }
        // 先停服：运行中的进程会占用目标文件（尤其 Windows 上 jar 被 JVM 锁），不先停则备份/投放/解压必失败
        if (!isBlank(project.getStopScript())) {
            steps.add(new ProjectStep("停止服务", buildCommand(win, path, project.getStopScript()), 1));
        }
        if (autoBackup) {
            steps.add(new ProjectStep("备份当前版本", backupCommand(win, path), 2));
        }
        if (!isBlank(packagePath)) {
            if (autoExtract && isArchive(packagePath)) {
                steps.add(new ProjectStep("解压新版本包", extractCommand(win, path, packagePath), 2));
            } else {
                steps.add(new ProjectStep("投放新版本文件",
                        placeCommand(win, path, packagePath, project.getMainEntry()), 2));
            }
        }
        if (autoRollback && !isBlank(project.getStatusScript())) {
            steps.add(new ProjectStep("重启并健康检测（失败自动回滚）",
                    guardedRestartCommand(win, path, project, autoRestart), 3));
        } else if (autoRestart) {
            if (!isBlank(project.getRestartScript())) {
                steps.add(new ProjectStep("重启服务",
                        buildCommand(win, path, project.getRestartScript()), 3));
            } else if (!isBlank(project.getStopScript()) && !isBlank(project.getStartScript())) {
                steps.add(new ProjectStep("停止服务",
                        buildCommand(win, path, project.getStopScript()), 2));
                steps.add(new ProjectStep("启动服务",
                        buildCommand(win, path, project.getStartScript()), 3));
            }
        }
        steps.add(pidStep(win, path));
        String taskId = projectTaskService.startTask(ctx.hostId, project.getId(),
                "upgrade", steps, "running");
        return task(taskId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ReturnResult<String> uploadAndUpgrade(Long id, MultipartFile file) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        OperationContext ctx = prepared.getData();
        MsServerProject project = ctx.project;
        if (isBlank(project.getProjectPath())) {
            return ReturnResult.error("项目地址未配置，无法上传升级");
        }
        if (file == null || file.isEmpty()) {
            return ReturnResult.error("上传文件不能为空");
        }
        String uploadDir = project.getProjectPath() + "/uploads";
        ReturnResult<Void> mk = sftpService.mkdir(ctx.hostId, null, uploadDir);
        if (!mk.isOk()) {
            return ReturnResult.error("创建上传目录失败: " + mk.getMsg());
        }
        ReturnResult<String> up = sftpService.upload(ctx.hostId, null, uploadDir, file);
        if (!up.isOk()) {
            return ReturnResult.error(up.getMsg());
        }
        String remotePackage = up.getData();
        Object global = readCfg(CFG_GLOBAL_KEY, defaultGlobalConfig());
        Map<String, Object> cfg = global instanceof Map
                ? new java.util.LinkedHashMap<>((Map<String, Object>) global)
                : defaultGlobalConfig();
        cfg.put("packagePath", remotePackage);
        return runUpgrade(id, cfg);
    }

    // ==================== 辅助 ====================

    /**
     * 加载并校验项目、发布、目标主机
     *
     * @param id 项目ID
     * @return 操作上下文
     */
    private ReturnResult<OperationContext> prepare(Long id) {
        MsServerProject project = projectMapper.selectById(id);
        if (project == null) {
            return ReturnResult.error("项目不存在: " + id);
        }
        MsServerRelease release = releaseMapper.selectById(project.getReleaseId());
        if (release == null || release.getHostId() == null) {
            return ReturnResult.error("发布或主机不存在");
        }
        MsServerHost targetHost = hostMapper.selectById(release.getHostId());
        if (targetHost == null) {
            return ReturnResult.error("目标主机不存在: " + release.getHostId());
        }
        // 发布模块基于 OpenSSH 执行：主机须开启 SSH（Linux/macOS 默认开，Windows 默认关）
        Boolean sshOn = resolveSshEnabled(targetHost);
        if (!sshOn) {
            return ReturnResult.error("目标主机「" + targetHost.getServerHostName()
                    + "」未开启 SSH（Windows 需启用 OpenSSH 并打开「开启 SSH」开关），无法执行发布操作");
        }
        // 执行前就地展开脚本占位符（__ENTRY__/__LOG__/__PATH__/__APPNAME__），
        // 使内置模板保持通用、按项目字段即时生效
        expandProjectScripts(project);
        OperationContext context = new OperationContext();
        context.project = project;
        context.hostId = release.getHostId();
        context.windows = "WINDOWS".equalsIgnoreCase(targetHost.getServerHostOsType());
        return ReturnResult.ok(context);
    }

    /**
     * 补齐项目四个脚本的默认模板：仅当对应脚本为空时用内置模板（占位符形式）填充
     *
     * @param project 项目
     */
    private void applyDefaultScripts(MsServerProject project) {
        MsServerRelease release = project.getReleaseId() == null ? null : releaseMapper.selectById(project.getReleaseId());
        MsServerHost host = (release != null && release.getHostId() != null)
                ? hostMapper.selectById(release.getHostId()) : null;
        boolean windows = host != null && "WINDOWS".equalsIgnoreCase(host.getServerHostOsType());
        ProjectScripts.Scripts def = ProjectScripts.builtin(windows, project.getProjectType(), project.getMainEntry());
        if (isBlank(project.getStartScript())) {
            project.setStartScript(def.start());
        }
        if (isBlank(project.getStopScript())) {
            project.setStopScript(def.stop());
        }
        if (isBlank(project.getRestartScript())) {
            project.setRestartScript(def.restart());
        }
        if (isBlank(project.getStatusScript())) {
            project.setStatusScript(def.status());
        }
    }

    /**
     * 就地展开项目四个脚本中的占位符（不落库，仅执行期生效）
     *
     * @param project 项目
     */
    private void expandProjectScripts(MsServerProject project) {
        boolean windows = isWindowsProject(project);
        // 结构化变量（envVars/jvmArgs/cmdArgs）注入 VARS 块 + 展开 __JVMARGS__/__ARGS__，
        // 使「API 创建 / 内置模板」的项目也能生效（此前仅前端保存时注入）
        String start = materializeVars(project.getStartScript(), project, windows);
        String restart = materializeVars(project.getRestartScript(), project, windows);
        project.setStartScript(ProjectScripts.apply(start,
                project.getMainEntry(), project.getProjectPath(), project.getLogPath(), project.getProjectName()));
        project.setStopScript(ProjectScripts.apply(project.getStopScript(),
                project.getMainEntry(), project.getProjectPath(), project.getLogPath(), project.getProjectName()));
        project.setRestartScript(ProjectScripts.apply(restart,
                project.getMainEntry(), project.getProjectPath(), project.getLogPath(), project.getProjectName()));
        project.setStatusScript(ProjectScripts.apply(project.getStatusScript(),
                project.getMainEntry(), project.getProjectPath(), project.getLogPath(), project.getProjectName()));
    }

    /**
     * 判断项目目标主机是否 Windows（按所属发布绑定的主机）
     */
    private boolean isWindowsProject(MsServerProject project) {
        MsServerRelease release = project.getReleaseId() == null ? null : releaseMapper.selectById(project.getReleaseId());
        MsServerHost host = (release != null && release.getHostId() != null)
                ? hostMapper.selectById(release.getHostId()) : null;
        return host != null && "WINDOWS".equalsIgnoreCase(host.getServerHostOsType());
    }

    /**
     * 将 envVars/jvmArgs/cmdArgs 注入脚本：构建 VARS 托管块（幂等）并展开参数占位符
     *
     * @param script  脚本（可为空）
     * @param project 项目（读取 envVars/jvmArgs/cmdArgs）
     * @param windows 是否 Windows
     * @return 注入后的脚本
     */
    private String materializeVars(String script, MsServerProject project, boolean windows) {
        if (script == null || script.isEmpty()) {
            return script;
        }
        List<EnvVar> envs = readEnvList(project.getEnvVars());
        List<String> jvms = readStringList(project.getJvmArgs());
        List<String> args = readStringList(project.getCmdArgs());
        String out = script;
        if (!envs.isEmpty() || !jvms.isEmpty() || !args.isEmpty()) {
            StringBuilder b = new StringBuilder(VARS_BEGIN).append('\n');
            if (windows) {
                for (EnvVar e : envs) {
                    if (!isBlank(e.key)) {
                        b.append("$env:").append(e.key.trim()).append(" = '")
                                .append(e.value == null ? "" : e.value.replace("'", "''")).append("'\n");
                    }
                }
                b.append("$JvmArgs = @(").append(csv(jvms, true)).append(")\n");
                b.append("$AppArgs = @(").append(csv(args, true)).append(")\n");
            } else {
                for (EnvVar e : envs) {
                    if (!isBlank(e.key)) {
                        b.append("export ").append(e.key.trim()).append("='")
                                .append(e.value == null ? "" : e.value.replace("'", "'\\''")).append("'\n");
                    }
                }
                b.append("JVM_ARGS='").append(csv(jvms, false)).append("'\n");
                b.append("APP_ARGS='").append(csv(args, false)).append("'\n");
            }
            b.append(VARS_END).append('\n');
            out = injectVarsBlock(out, b.toString());
        }
        out = out.replace("__JVMARGS__", windows ? "$JvmArgs" : quotedArgs(jvms))
                 .replace("__ARGS__", windows ? "$AppArgs" : quotedArgs(args));
        return out;
    }

    /**
     * 把参数列表展开为<b>逐个单引号包裹</b>的 shell 字面量。
     *
     * <p>不能改成 {@code "$APP_ARGS"} 形式依赖 shell 的单词拆分：bash 会把未加引号的参数展开按
     * 空白拆成多个参数，而 <b>zsh 默认不拆分</b>（非交互式 zsh 需显式 {@code setopt shwordsplit}
     * 或 {@code ${=VAR}}），Kali 等默认 shell 为 zsh 的主机上整串参数会被当成<b>一个</b>参数传入，
     * 表现为 Spring 报 {@code Invalid value '8112 --monitor.xxx=...' for configuration property
     * 'server.port'}。生成期逐个加引号可让 bash / zsh / ash 行为一致。</p>
     *
     * @param items 参数列表
     * @return 可直接内联进命令行的字符串（空列表返回空串）
     */
    private String quotedArgs(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String it : items) {
            if (isBlank(it)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append('\'').append(it.trim().replace("'", "'\\''")).append('\'');
        }
        return sb.toString();
    }

    /**
     * 拼接参数数组/串：Windows 为 PS 数组元素（单引号，逗号分隔）；Linux 为空格分隔
     */
    private String csv(List<String> items, boolean windows) {
        List<String> parts = new ArrayList<>();
        for (String it : items) {
            if (isBlank(it)) {
                continue;
            }
            parts.add(windows ? ("'" + it.replace("'", "''") + "'") : it.replace("'", "'\\''"));
        }
        return windows ? String.join(",", parts) : String.join(" ", parts);
    }

    /**
     * 幂等注入 VARS 托管块：已存在则替换，不存在则前插
     */
    private String injectVarsBlock(String script, String block) {
        Matcher m = VARS_BLOCK_RE.matcher(script);
        if (m.find()) {
            return m.replaceFirst(Matcher.quoteReplacement(block));
        }
        return block + script;
    }

    /**
     * 根据主机ID回填主机IP，保证发布卡片主机信息完整
     */
    private void fillHostInfo(MsServerRelease release) {
        if (release == null || release.getHostId() == null) {
            return;
        }
        MsServerHost host = hostMapper.selectById(release.getHostId());
        if (host != null) {
            release.setHostIp(host.getServerHostAddress());
        }
    }

    /** 变量托管块标记（与前端 ProjectsView 保持一致） */
    private static final String VARS_BEGIN = "# >>> VARS (auto-managed) >>>";
    private static final String VARS_END = "# <<< VARS (auto-managed) <<<";
    /** 变量托管块匹配（幂等替换用） */
    private static final Pattern VARS_BLOCK_RE =
            Pattern.compile("# >>> VARS \\(auto-managed\\) >>>[\\s\\S]*?# <<< VARS \\(auto-managed\\) <<<\\n?");
    /** 托管块内环境变量行：Linux `export NAME='VALUE'`、Windows `$env:NAME = 'VALUE'` */
    private static final Pattern ENV_NIX = Pattern.compile("^export\\s+([A-Za-z_][A-Za-z0-9_]*)='(.*)'$");
    private static final Pattern ENV_WIN = Pattern.compile("^\\$env:([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*'(.*)'$");

    /**
     * 执行前解析变量托管块中的占位符，支持：
     * ${key:default} 默认值、${now(pattern)} 日期格式化、${uuid}、${random(n)}、${upper/lower/length} 等。
     * 仅处理托管块，避免误改脚本中正常的 shell / PowerShell 语法；
     * 无法解析的占位符保留原样（交给 shell），不中断执行。
     */
    private String resolveScriptPlaceholders(String script, boolean windows) {
        if (script == null) {
            return null;
        }
        int b = script.indexOf(VARS_BEGIN);
        if (b < 0) {
            return script;
        }
        int e = script.indexOf(VARS_END, b);
        if (e < 0) {
            return script;
        }
        int eEnd = e + VARS_END.length();
        String block = script.substring(b, eEnd);

        // 收集块内已定义的环境变量作为占位符上下文（KEY -> 原始值）
        Map<String, Object> ctx = new LinkedHashMap<>();
        Pattern pat = windows ? ENV_WIN : ENV_NIX;
        for (String line : block.split("\n")) {
            Matcher m = pat.matcher(line.trim());
            if (m.find()) {
                ctx.put(m.group(1), m.group(2));
            }
        }
        PlaceholderSupport support = new PlaceholderSupport();
        support.setResolver(new MapMixSystemPlaceholderResolver(ctx));
        support.setIgnoreUnresolvablePlaceholders(true);
        String resolved = new StringValuePropertyResolver(support).resolvePlaceholders(block);
        return script.substring(0, b) + resolved + script.substring(eEnd);
    }

    /**
     * 构建执行命令。
     * Linux：bash（cd path && script）；Windows：PowerShell（Set-Location 后执行，PS4.0 不支持 &&）。
     */
    private String buildCommand(boolean windows, String projectPath, String script) {
        script = resolveScriptPlaceholders(script, windows);
        if (windows) {
            if (isBlank(projectPath)) {
                return script;
            }
            String p = projectPath.replace("'", "''");
            return "if (-not (Test-Path -LiteralPath '" + p + "')) { New-Item -ItemType Directory -Force -Path '"
                    + p + "' | Out-Null }\nSet-Location -LiteralPath '" + p + "'\n" + script;
        }
        if (!isBlank(projectPath)) {
            return "mkdir -p '" + sq(projectPath) + "' && cd '" + sq(projectPath) + "' && " + script;
        }
        return script;
    }

    /**
     * 构造「生成 SpringBoot 专属外部配置」步骤：
     * 写入项目目录下 config/application.properties，Spring Boot 会自动加载
     * （file:./config/ 的优先级高于 jar 内 application.properties，实现覆盖）。
     *
     * @param windows 是否 Windows
     * @param project 项目
     * @return 步骤；无配置项时返回 null
     */
    private ProjectStep springConfigStep(boolean windows, MsServerProject project) {
        List<EnvVar> entries = readEnvList(project.getAppConfig());
        if (entries.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (windows) {
            sb.append("New-Item -ItemType Directory -Force -Path 'config' | Out-Null\r\n");
            // 逐行写入，避免 PowerShell 数组字面量的尾随逗号导致 "Missing expression after ','"
            sb.append("[IO.File]::WriteAllLines((Get-Location).Path + '\\config\\application.properties', @(\r\n");
            List<String> cfgLines = new ArrayList<>();
            for (EnvVar e : entries) {
                if (e.key != null && !e.key.isBlank()) {
                    cfgLines.add("  '" + winPsEscape(e.key) + "=" + winPsEscape(e.value) + "'");
                }
            }
            sb.append(String.join(",\r\n", cfgLines)).append("\r\n");
            sb.append("))\r\n");
        } else {
            sb.append("mkdir -p config\n");
            sb.append("cat > config/application.properties <<'MS_CFG_EOF'\n");
            for (EnvVar e : entries) {
                if (e.key != null && !e.key.isBlank()) {
                    sb.append(e.key).append('=').append(e.value == null ? "" : e.value).append('\n');
                }
            }
            sb.append("MS_CFG_EOF\n");
        }
        // 必须在项目目录下执行：否则 config/application.properties 会写到 SSH 默认工作目录
        return new ProjectStep("生成项目配置", buildCommand(windows, project.getProjectPath(), sb.toString()), 1);
    }

    /**
     * 构造「读取 PID」步骤（启动 / 重启的最后一步，其输出仅为 PID，供回写卡片）。
     * 经 buildCommand 包装后在项目目录下读取 app.pid，避免在 WinRM 默认工作目录读到残留文件。
     *
     * @param windows     是否 Windows
     * @param projectPath 项目地址
     * @return 步骤
     */
    private ProjectStep pidStep(boolean windows, String projectPath) {
        String cmd = windows
                ? "if (Test-Path 'app.pid') { Get-Content 'app.pid' }"
                : "cat app.pid 2>/dev/null || true";
        return new ProjectStep("记录PID", buildCommand(windows, projectPath, cmd), 1);
    }

    /**
     * PowerShell 单引号字符串转义（单引号翻倍）。
     */
    private String winPsEscape(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    /**
     * 构建备份命令。
     * Linux：tar.gz 写入 backups/ 并排除自身；Windows：robocopy 镜像到 backups\backup_<毫秒时间戳>
     * （robocopy 为 Win2012R2 自带，PS4.0 无 Compress-Archive；返回码 0-7 均为成功）。
     */
    private String backupCommand(boolean windows, String projectPath) {
        if (windows) {
            StringBuilder ps = new StringBuilder();
            if (!isBlank(projectPath)) {
                ps.append("Set-Location -LiteralPath '").append(projectPath).append("'\n");
            }
            ps.append("if (-not (Test-Path 'backups')) { New-Item -ItemType Directory -Path 'backups' | Out-Null }\n");
            // 用 Get-Date 生成固定长度(yyyyMMddHHmmssfff)时间戳；ToUnixTimeMilliseconds 需 .NET4.6+，Win2012R2(.NET4.5.2) 不支持
            ps.append("$stamp = Get-Date -Format 'yyyyMMddHHmmssfff'\n");
            ps.append("$dest = Join-Path 'backups' ('backup_' + $stamp)\n");
            ps.append("New-Item -ItemType Directory -Force -Path $dest | Out-Null\n");
            // /XF app.pid：排除运行态 PID 文件，避免备份/还原把过期 PID 带回来导致状态误判
            ps.append("robocopy '.' $dest /E /NFL /NDL /NJH /NJS /NP /XD backups /XF app.pid | Out-Null\n");
            ps.append("if ($LASTEXITCODE -gt 7) { throw 'robocopy backup failed' }\n");
            // 保留策略：只保留最新 MAX_BACKUPS 个，清理更旧的，避免备份无限增长撑满磁盘
            ps.append("Get-ChildItem -Directory -Path 'backups' -Filter 'backup_*' | ")
              .append("Sort-Object Name -Descending | Select-Object -Skip ").append(MAX_BACKUPS)
              .append(" | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue\n");
            ps.append("Write-Output ('backup created: ' + $dest)");
            return ps.toString();
        }
        String file = "backup_" + System.currentTimeMillis() + ".tar.gz";
        String prefix = isBlank(projectPath) ? "" : ("cd " + projectPath + " && ");
        // 备份统一写入 backups/ 子目录并排除该目录，避免 tar 把正在写入的备份打包进自身；
        // 保留最新 MAX_BACKUPS 个，清理更旧的防止磁盘被备份撑满
        return prefix + "mkdir -p backups && tar -czf backups/" + file + " --exclude='./backups' . ; "
                + "ls -1dt backups/backup_* 2>/dev/null | tail -n +" + (MAX_BACKUPS + 1) + " | xargs -r rm -rf";
    }

    /**
     * 构建回滚命令（恢复最新备份）。
     * Linux：解最新 tar.gz；Windows：robocopy /MIR 从最新 backups\backup_* 目录恢复。
     */
    private String rollbackCommand(boolean windows, String projectPath, String backupName) {
        boolean named = !isBlank(backupName);
        if (windows) {
            StringBuilder ps = new StringBuilder();
            if (!isBlank(projectPath)) {
                ps.append("Set-Location -LiteralPath '").append(projectPath).append("'\n");
            }
            if (named) {
                String safe = backupName.replace("'", "''");
                ps.append("$latest = Get-Item -LiteralPath (Join-Path 'backups' '").append(safe).append("')\n");
            } else {
                ps.append("$latest = Get-ChildItem -Directory -Path 'backups' -Filter 'backup_*' | ")
                  .append("Sort-Object Name -Descending | Select-Object -First 1\n");
            }
            ps.append("if (-not $latest) { throw 'no backup found' }\n");
            // /E 覆盖恢复备份内容；/XD backups 排除备份目录（不能用 /MIR，否则会尝试删除正在读取的 backups 源目录而自引用失败）
            ps.append("robocopy $latest.FullName '.' /E /XD backups /XF app.pid /NFL /NDL /NJH /NJS /NP | Out-Null\n");
            ps.append("if ($LASTEXITCODE -gt 7) { throw 'robocopy rollback failed' }\n");
            ps.append("Write-Output 'rollback done'");
            return ps.toString();
        }
        String prefix = isBlank(projectPath) ? "" : ("cd " + projectPath + " && ");
        if (named) {
            String safe = backupName.replace("'", "'\\''");
            return prefix + "latest='backups/" + safe + "' && [ -f \"$latest\" ] && tar -xzf \"$latest\"";
        }
        return prefix + "latest=$(ls -t backups/backup_*.tar.gz | head -1) && tar -xzf \"$latest\"";
    }

    /**
     * 包装项目任务提交结果：项目已有操作在跑（startTask 返回 空）时给出明确提示，
     * 避免并发「启动/停止/重启/升级」互相竞争、覆盖 app.pid 或产生孤儿进程。
     *
     * @param taskId 任务 标识（可能为 空）
     * @return 成功返回 taskId；项目占用中返回错误
     */
    private ReturnResult<String> task(String taskId) {
        return taskId == null
                ? ReturnResult.error("该项目有操作正在进行，请稍后再试")
                : ReturnResult.ok(taskId);
    }

    /**
     * 字符串空白判断
     */
    /**
     * 解析主机是否开启 SSH：显式设置取该值；未设置时非 Windows 视为开启、Windows 视为关闭
     *
     * @param host 主机
     * @return 是否开启
     */
    private boolean resolveSshEnabled(MsServerHost host) {
        Integer v = host.getServerHostSshEnabled();
        if (v != null) {
            return v == 1;
        }
        return !"WINDOWS".equalsIgnoreCase(host.getServerHostOsType());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // ---------- 升级配置辅助 ----------

    private Map<String, Object> defaultGlobalConfig() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("autoBackup", true);
        m.put("autoExtract", true);
        m.put("autoRestart", true);
        m.put("autoRollback", false);
        m.put("packagePath", "");
        return m;
    }

    private Object readCfg(String key, Object defValue) {
        MsServerConfig cfg = serverConfigMapper.selectOne(
                new LambdaQueryWrapper<MsServerConfig>().eq(MsServerConfig::getConfigKey, key));
        if (cfg == null || isBlank(cfg.getConfigValue())) {
            return defValue;
        }
        try {
            return JSON.readValue(cfg.getConfigValue(), Object.class);
        } catch (Exception e) {
            return defValue;
        }
    }

    private boolean boolCfg(Map<String, Object> cfg, String key, boolean defValue) {
        Object v = cfg.get(key);
        if (v == null) {
            return defValue;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private String strCfg(Map<String, Object> cfg, String key, String defValue) {
        Object v = cfg.get(key);
        return v == null ? defValue : String.valueOf(v);
    }

    // ---------- 升级包处理 ----------

    private boolean isArchive(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".tar.gz") || n.endsWith(".tgz") || n.endsWith(".tar")
                || n.endsWith(".zip") || n.endsWith(".gz");
    }

    private String extractCommand(boolean windows, String projectPath, String packagePath) {
        String n = packagePath.toLowerCase();
        if (windows) {
            String pkg = packagePath.replace("'", "''");
            String dst = projectPath.replace("'", "''");
            if (n.endsWith(".zip")) {
                // 逐项解压并覆盖同名文件：ExtractToDirectory 遇已存在文件会抛异常，无法用于重复升级
                return "Add-Type -AssemblyName System.IO.Compression.FileSystem\n"
                        + "$zip = [IO.Compression.ZipFile]::OpenRead('" + pkg + "')\n"
                        + "try {\n"
                        + "  foreach ($e in $zip.Entries) {\n"
                        + "    $dest = Join-Path '" + dst + "' $e.FullName\n"
                        + "    if ([string]::IsNullOrEmpty($e.Name)) { New-Item -ItemType Directory -Force -Path $dest | Out-Null }\n"
                        + "    else { $dir = Split-Path $dest -Parent; if ($dir) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }; [IO.Compression.ZipFileExtensions]::ExtractToFile($e, $dest, $true) }\n"
                        + "  }\n"
                        + "} finally { $zip.Dispose() }";
            }
            // tar / tar.gz / tgz / gz：Windows 10 1803+ 自带 tar.exe；旧系统无内置工具，给出明确提示
            String tarFlag = n.endsWith(".tar") ? "-xf" : "-xzf";
            return "if (-not (Get-Command tar.exe -ErrorAction SilentlyContinue)) { throw 'Windows 未内置 tar，无法解压 ' + n + '，请改用 zip 包' }\n"
                    + "tar.exe " + tarFlag + " '" + pkg + "' -C '" + dst + "'";
        }
        String prefix = isBlank(projectPath) ? "" : ("cd " + projectPath + " && ");
        if (n.endsWith(".zip")) {
            return prefix + "unzip -o '" + packagePath + "'";
        }
        if (n.endsWith(".gz") && !n.endsWith(".tar.gz")) {
            return prefix + "gunzip -f '" + packagePath + "'";
        }
        return prefix + "tar -xzf '" + packagePath + "'";
    }

    private String placeCommand(boolean windows, String projectPath, String packagePath, String mainEntry) {
        String target = isBlank(mainEntry)
                ? baseName(packagePath)
                : mainEntry;
        if (windows) {
            return "Copy-Item -LiteralPath '" + packagePath + "' -Destination '"
                    + projectPath + "\\" + target + "' -Force";
        }
        return "cp -f '" + packagePath + "' '" + projectPath + "/" + target + "'";
    }

    private String baseName(String path) {
        String n = path.replace('\\', '/');
        int idx = n.lastIndexOf('/');
        return idx >= 0 ? n.substring(idx + 1) : n;
    }

    /**
     * 重启后健康检测，失败则回滚（Linux）。
     */
    private String guardedRestartCommand(boolean windows, String projectPath,
                                         MsServerProject project, boolean restart) {
        if (windows) {
            String restartCmd = restart && !isBlank(project.getRestartScript())
                    ? buildCommand(true, projectPath, project.getRestartScript())
                    : (buildCommand(true, projectPath, project.getStopScript()) + "\n"
                       + buildCommand(true, projectPath, project.getStartScript()));
            String statusCmd = buildCommand(true, projectPath, project.getStatusScript());
            String rollbackCmd = buildCommand(true, projectPath, rollbackCommand(true, projectPath, null));
            String startCmd = buildCommand(true, projectPath, project.getStartScript());
            StringBuilder ps = new StringBuilder();
            ps.append(restartCmd).append('\n');
            ps.append("Start-Sleep -Seconds 4\n");
            ps.append("$out = $(").append(statusCmd).append(") -join ' '\n");
            ps.append("if (\"$out\" -match 'running') { 'health ok' }\n");
            ps.append("else {\n")
              .append("  'health failed, rolling back...'\n  ")
              .append(rollbackCmd).append('\n')
              // 回滚文件后重启旧版本，再做二次健康检查
              .append("  ").append(startCmd.replace("\n", "\n  ")).append('\n')
              .append("  Start-Sleep -Seconds 4\n")
              .append("  $out2 = $(").append(statusCmd).append(") -join ' '\n")
              .append("  if (\"$out2\" -match 'running') { 'rollback ok' } else { 'rollback failed'; exit 1 }\n")
              .append("}");
            return ps.toString();
        }
        StringBuilder sb = new StringBuilder();
        if (restart) {
            String rc = isBlank(project.getRestartScript())
                    ? (buildCommand(false, projectPath, project.getStopScript()) + "\n"
                       + buildCommand(false, projectPath, project.getStartScript()))
                    : buildCommand(false, projectPath, project.getRestartScript());
            sb.append(rc).append('\n');
        }
        sb.append("sleep 4\n");
        sb.append("out=$(").append(buildCommand(false, projectPath, project.getStatusScript())).append(")\n");
        sb.append("if echo \"$out\" | grep -qi running; then\n  echo 'health ok'\nelse\n")
          .append("  echo 'health failed, rolling back...'\n  ")
          .append(buildCommand(false, projectPath, rollbackCommand(false, projectPath, null))).append('\n')
          .append("  ").append(buildCommand(false, projectPath, project.getStartScript()).replace("\n", "\n  ")).append('\n')
          .append("  sleep 4\n")
          .append("  out2=$(").append(buildCommand(false, projectPath, project.getStatusScript())).append(")\n")
          .append("  if echo \"$out2\" | grep -qi running; then echo 'rollback ok'; else echo 'rollback failed'; exit 1; fi\n")
          .append("fi");
        return sb.toString();
    }

    // ---------- 系统服务（Linux systemd）----------

    /**
     * 生成确定性服务名：ms-<名称>-<项目ID>
     */
    private String serviceKey(MsServerProject p) {
        String base = isBlank(p.getProjectName()) ? "project" : p.getProjectName();
        String s = base.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (s.isEmpty()) {
            s = "project";
        }
        return "ms-" + s + "-" + p.getId();
    }

    private String sq(String value) {
        return value == null ? "" : value.replace("'", "'\\''");
    }

    private List<String> readStringList(String json) {
        if (isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return JSON.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<EnvVar> readEnvList(String json) {
        if (isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> raw = JSON.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() { });
            List<EnvVar> out = new ArrayList<>();
            raw.forEach(m -> out.add(new EnvVar(
                    String.valueOf(m.get("key")),
                    m.get("value") == null ? "" : String.valueOf(m.get("value")))));
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 前台启动器（systemd ExecStart，exec 接管进程，stdout 入 journal）。
     */
    private String buildLauncherBash(MsServerProject p) {
        StringBuilder sb = new StringBuilder("#!/bin/bash\n");
        sb.append("cd '").append(sq(p.getProjectPath())).append("'\n");
        for (EnvVar e : readEnvList(p.getEnvVars())) {
            sb.append("export ").append(e.key).append("='").append(sq(e.value)).append("'\n");
        }
        sb.append("JVM_ARGS='").append(sq(String.join(" ", readStringList(p.getJvmArgs())))).append("'\n");
        sb.append("APP_ARGS='").append(sq(String.join(" ", readStringList(p.getCmdArgs())))).append("'\n");
        sb.append("exec ").append(foregroundBash(p)).append("\n");
        return sb.toString();
    }

    private String foregroundBash(MsServerProject p) {
        String t = p.getProjectType() == null ? "" : p.getProjectType();
        switch (t) {
            case "springboot":
            case "fatjar":
                return "java $JVM_ARGS -jar '" + sq(p.getMainEntry()) + "' $APP_ARGS";
            case "separated":
                return "'./" + sq(p.getMainEntry()) + "' $JVM_ARGS $APP_ARGS";
            case "nginx":
            case "static":
                return "nginx -g 'daemon off;'";
            default:
                return "'./" + sq(p.getMainEntry()) + "' $APP_ARGS";
        }
    }

    private String buildUnit(MsServerProject p, String svc, String launcher, boolean guardian) {
        StringBuilder sb = new StringBuilder("[Unit]\n");
        sb.append("Description=").append(isBlank(p.getProjectName()) ? svc : p.getProjectName()).append("\n");
        sb.append("After=network.target\n\n[Service]\nType=simple\n");
        sb.append("WorkingDirectory=").append(p.getProjectPath()).append("\n");
        sb.append("ExecStart=").append(launcher).append("\n");
        sb.append("Restart=").append(guardian ? "always" : "no").append("\nRestartSec=5\n\n");
        sb.append("[Install]\nWantedBy=multi-user.target\n");
        return sb.toString();
    }

    private String installServiceCommand(MsServerProject p, String svc) {
        String dir = p.getProjectPath() + "/.ms";
        String launcher = dir + "/" + svc + ".run.sh";
        String launchContent = buildLauncherBash(p);
        String unitContent = buildUnit(p, svc, launcher, Boolean.TRUE.equals(p.getGuardian()));
        StringBuilder sb = new StringBuilder("set -e\n");
        sb.append("mkdir -p '").append(sq(dir)).append("'\n");
        sb.append("cat > '").append(sq(launcher)).append("' <<'MS_LAUNCHER_EOF'\n")
          .append(launchContent).append("MS_LAUNCHER_EOF\n");
        sb.append("chmod +x '").append(sq(launcher)).append("'\n");
        sb.append("sudo tee '/etc/systemd/system/").append(svc).append(".service' > /dev/null <<'MS_UNIT_EOF'\n")
          .append(unitContent).append("MS_UNIT_EOF\n");
        sb.append("sudo systemctl daemon-reload\n");
        if (Boolean.TRUE.equals(p.getAutostart())) {
            sb.append("sudo systemctl enable '").append(svc).append("'\n");
        } else {
            sb.append("sudo systemctl disable '").append(svc).append("' || true\n");
        }
        sb.append("sudo systemctl restart '").append(svc).append("'\n");
        sb.append("systemctl is-active '").append(svc).append("'\n");
        return sb.toString();
    }

    private String uninstallServiceCommand(MsServerProject p, String svc) {
        StringBuilder sb = new StringBuilder();
        sb.append("sudo systemctl stop '").append(svc).append("' || true\n");
        sb.append("sudo systemctl disable '").append(svc).append("' || true\n");
        sb.append("sudo rm -f '/etc/systemd/system/").append(svc).append(".service'\n");
        sb.append("sudo systemctl daemon-reload\n");
        if (!isBlank(p.getProjectPath())) {
            sb.append("rm -f '").append(sq(p.getProjectPath())).append("/.ms/").append(svc).append(".run.sh'\n");
        }
        return sb.toString();
    }

    // ---------- 系统服务（Windows 计划任务：开机自启 + 守护）----------

    private String installServiceCommandWin(MsServerProject p, String svc) {
        String dir = p.getProjectPath() + "\\.ms";
        String autoPath = dir + "\\" + svc + ".autostart.ps1";
        String watchPath = dir + "\\" + svc + ".watchdog.ps1";
        StringBuilder ps = new StringBuilder();
        ps.append("New-Item -ItemType Directory -Force -Path '").append(dir).append("' | Out-Null\n");
        // 开机自启脚本：定位到项目目录后执行启动脚本
        ps.append("$auto = @'\nSet-Location -LiteralPath '").append(p.getProjectPath()).append("'\n")
          .append(p.getStartScript() == null ? "" : p.getStartScript()).append("\n'@\n");
        ps.append("[IO.File]::WriteAllText('").append(autoPath).append("', $auto)\n");
        // 以 SYSTEM 账户 + ONSTART 创建：不依赖交互登录（SSH/无登录的服务器上 ONLOGON/交互令牌任务不会执行）
        ps.append("schtasks /Create /TN '").append(svc).append("-autostart' /SC ONSTART /RU SYSTEM ")
          .append("/TR \"powershell -ExecutionPolicy BYPASS -File '").append(autoPath).append("'\" /F\n");
        if (Boolean.TRUE.equals(p.getGuardian()) && !isBlank(p.getStatusScript())) {
            // 守护脚本：状态不含 running 则执行启动脚本
            ps.append("$wd = @'\nSet-Location -LiteralPath '").append(p.getProjectPath()).append("'\n")
              .append("$s = ").append(p.getStatusScript()).append("\n")
              .append("if ($s -notmatch 'running') {\n").append(p.getStartScript() == null ? "" : p.getStartScript())
              .append("\n}\n'@\n");
            ps.append("[IO.File]::WriteAllText('").append(watchPath).append("', $wd)\n");
ps.append("schtasks /Create /TN '").append(svc).append("-guard' /SC MINUTE /MO 1 /RU SYSTEM ")
          .append("/TR \"powershell -ExecutionPolicy BYPASS -File '").append(watchPath).append("'\" /F\n");
        }
        return ps.toString();
    }

    private String uninstallServiceCommandWin(MsServerProject p, String svc) {
        // 容忍任务不存在：逐个删除并抑制错误，最后强制成功（未挂守护时 -guard 任务本就不存在）
        return "schtasks /Delete /TN '" + svc + "-autostart' /F 2>$null\n"
                + "schtasks /Delete /TN '" + svc + "-guard' /F 2>$null\n"
                + "exit 0";
    }

    /** 环境变量键值（仅服务启动器内部使用） */
    private static final class EnvVar {
        private final String key;
        private final String value;

        private EnvVar(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
