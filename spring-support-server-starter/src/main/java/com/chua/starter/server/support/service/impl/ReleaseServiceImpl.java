package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.placeholder.MapMixSystemPlaceholderResolver;
import com.chua.common.support.lang.placeholder.PlaceholderSupport;
import com.chua.common.support.lang.placeholder.StringValuePropertyResolver;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsServerConfig;
import com.chua.starter.server.support.entity.MsExecLog;
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
import com.chua.starter.server.support.spi.ProjectPlatformProvider;
import com.chua.starter.server.support.spi.ProjectPlatformProviderRegistry;
import com.chua.starter.server.support.spi.SystemServiceContext;
import com.chua.starter.server.support.spi.SystemServiceProvider;
import com.chua.starter.server.support.spi.SystemServiceProviderRegistry;
import com.chua.starter.server.support.util.HostPlatforms;
import com.chua.starter.server.support.util.ProjectScripts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
     * 启动参数里的业务端口：{@code --server.port=8112}
     */
    private static final Pattern SERVER_PORT_ARG = Pattern.compile("--server\\.port=(\\d{1,5})");

    /**
     * 外部配置里的业务端口：{@code server.port=8112}
     */
    private static final Pattern SERVER_PORT_PROP = Pattern.compile("server\\.port\\s*=\\s*(\\d{1,5})");
    /**
     * SFTP 服务（拖拽升级上传安装包，Linux）
     */
    private final SftpService sftpService;
    /**
     * 软件仓库包管理器路由器（按操作系统/实际包管理器选择 SPI 实现）
     */
    private final PackageManagerRouter packageManagerRouter;

    /**
     * 系统服务挂载路由器（按操作系统选择 SPI 实现：systemd / launchd / 计划任务）
     */
    private final SystemServiceProviderRegistry systemServiceRouter;

    /**
     * 平台命令路由器（按操作系统选择 SPI 实现：POSIX / Windows）。
     * 备份/回滚/执行包装/变量块/外部配置/解压投放等平台命令一律经它取得，
     * 业务层不再出现 tar/robocopy/powershell 等平台字面量与 windows 命令二选一。
     */
    private final ProjectPlatformProviderRegistry projectPlatformRouter;

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
        /** 目标主机（挂载服务时取运行账户，避免 unit 以 root 常驻） */
        private MsServerHost host;
        /** 归一后的系统类型（HostPlatforms 取值域），系统服务挂载按此路由 SPI 实现 */
        private String osType;
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

    /**
     * 生成内置四段脚本：与执行期（prepare→expandProjectScripts）走完全相同的模板与展开路径，
     * 保证前端预览 / 一键生成与真正下发的脚本逐字一致。
     *
     * @param hostId      目标主机标识
     * @param projectType 项目类型
     * @param mainEntry   主入口
     * @param projectPath 项目地址
     * @param logPath     日志地址
     * @param appName     应用名
     * @param envVars     环境变量 JSON
     * @param jvmArgs     JVM 参数 JSON
     * @param cmdArgs     命令参数 JSON
     * @return startScript/stopScript/restartScript/statusScript
     */
    @Override
    public ReturnResult<Map<String, String>> generateScripts(Long hostId, String projectType, String mainEntry,
            String projectPath, String logPath, String appName,
            String envVars, String jvmArgs, String cmdArgs) {
        MsServerHost host = hostId == null ? null : hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        String osType = HostPlatforms.normalize(host.getServerHostOsType());
        boolean windows = HostPlatforms.WINDOWS.equals(osType);
        ProjectScripts.Scripts def = ProjectScripts.builtin(windows, projectType, mainEntry);
        // 用一个只承载上下文的临时项目走 materializeVars，使 envVars/jvmArgs/cmdArgs 的
        // VARS 块与参数展开与执行期一致（前端拿到的即可直接执行，无需二次加工）。
        MsServerProject stub = new MsServerProject();
        stub.setProjectType(projectType);
        stub.setMainEntry(mainEntry);
        stub.setProjectPath(projectPath);
        stub.setLogPath(logPath);
        stub.setProjectName(appName);
        stub.setEnvVars(envVars);
        stub.setJvmArgs(jvmArgs);
        stub.setCmdArgs(cmdArgs);
        ProjectPlatformProvider pf = projectPlatformRouter.resolveOrPosix(osType);
        String start = materializeVars(def.start(), stub, pf);
        String restart = materializeVars(def.restart(), stub, pf);
        Map<String, String> out = new LinkedHashMap<>();
        out.put("startScript", ProjectScripts.apply(start, mainEntry, projectPath, logPath, appName));
        out.put("stopScript", ProjectScripts.apply(def.stop(), mainEntry, projectPath, logPath, appName));
        out.put("restartScript", ProjectScripts.apply(restart, mainEntry, projectPath, logPath, appName));
        out.put("statusScript", ProjectScripts.apply(def.status(), mainEntry, projectPath, logPath, appName));
        return ReturnResult.ok(out);
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
        ProjectPlatformProvider win = projectPlatformRouter.resolveOrPosix(prepared.getData().osType);
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
                buildCommand(projectPlatformRouter.resolveOrPosix(prepared.getData().osType), project.getProjectPath(), project.getStopScript()), 1));
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
        ProjectPlatformProvider win = projectPlatformRouter.resolveOrPosix(prepared.getData().osType);
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
        ProjectPlatformProvider win = projectPlatformRouter.resolveOrPosix(prepared.getData().osType);
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
                backupCommand(projectPlatformRouter.resolveOrPosix(prepared.getData().osType), project.getProjectPath()), 1));
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
        ProjectPlatformProvider win = projectPlatformRouter.resolveOrPosix(prepared.getData().osType);
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
        String command = backupListCommand(projectPlatformRouter.resolveOrPosix(ctx.osType), ctx.project.getProjectPath());
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
    private String backupListCommand(ProjectPlatformProvider pf, String projectPath) {
        return pf.listBackups(projectPath);
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
        // 按操作系统生成读取命令（由平台 SPI 提供：POSIX tail / Windows Get-Content -Tail）
        String logPath = project.getLogPath();
        String command = projectPlatformRouter.resolveOrPosix(prepared.getData().osType).tailLog(logPath, 100);
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
        String command = buildCommand(projectPlatformRouter.resolveOrPosix(ctx.osType), project.getProjectPath(), project.getStatusScript());
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
        var provider = systemServiceRouter.resolve(ctx.osType);
        if (provider.isEmpty()) {
            return ReturnResult.error("目标主机系统类型「" + ctx.osType + "」暂不支持挂载系统服务");
        }
        var serviceContext = serviceContext(ctx);
        String svc = isBlank(project.getServiceName())
                ? provider.get().defaultServiceName(serviceContext)
                : project.getServiceName();
        String command = provider.get().install(serviceContext, svc);
        List<ProjectStep> steps = List.of(new ProjectStep("挂载系统服务", command, 1));
        // 服务名只在任务真正成功后才回写：旧实现在提交任务时就写库，
        // 安装失败（如守护进程未拉起）会留下「库里有服务名、机器上没有服务」的假状态。
        String taskId = projectTaskService.startTask(ctx.hostId, project.getId(),
                "install-service", steps, null, new ProjectTaskService.ServiceNameWrite(svc));
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
        var provider = systemServiceRouter.resolve(ctx.osType);
        if (provider.isEmpty()) {
            return ReturnResult.error("目标主机系统类型「" + ctx.osType + "」暂不支持卸载系统服务");
        }
        var serviceContext = serviceContext(ctx);
        String svc = isBlank(project.getServiceName())
                ? provider.get().defaultServiceName(serviceContext)
                : project.getServiceName();
        String command = provider.get().uninstall(serviceContext, svc);
        List<ProjectStep> steps = List.of(new ProjectStep("卸载系统服务", command, 1));
        // 卸载失败（例如「自启脚本仍存在」）时不能清空库中服务名：那会让 UI 谎报未挂载。
        String taskId = projectTaskService.startTask(ctx.hostId, project.getId(),
                "uninstall-service", steps, null, new ProjectTaskService.ServiceNameWrite(null));
        return task(taskId);
    }

    // ==================== 升级配置 ====================

    /**
     * 装配系统服务挂载上下文：把项目实体与结构化变量转换为 SPI 输入
     *
     * @param ctx 操作上下文
     * @return 挂载上下文
     */
    private SystemServiceContext serviceContext(OperationContext ctx) {
        var env = new LinkedHashMap<String, String>();
        for (EnvVar e : readEnvList(ctx.project.getEnvVars())) {
            env.put(e.key, e.value);
        }
        return new SystemServiceContext(
                ctx.project.getProjectName(),
                ctx.project.getId(),
                ctx.project.getProjectType(),
                ctx.project.getMainEntry(),
                ctx.project.getProjectPath(),
                ctx.project.getLogPath(),
                ctx.project.getStartScript(),
                ctx.project.getStopScript(),
                ctx.project.getStatusScript(),
                env,
                readStringList(ctx.project.getJvmArgs()),
                readStringList(ctx.project.getCmdArgs()),
                ctx.host == null ? "" : ctx.host.getServerHostAccount(),
                Boolean.TRUE.equals(ctx.project.getAutostart()),
                Boolean.TRUE.equals(ctx.project.getGuardian()));
    }

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
        ProjectPlatformProvider win = projectPlatformRouter.resolveOrPosix(ctx.osType);
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
                // SDK 优先：文件投放用 SFTP rename（同文件系统内即移动），SDK 不可用才回退平台命令
                String sdkTarget = (path.endsWith("/") ? path : path + "/") + project.getMainEntry();
                steps.add(new ProjectStep("投放新版本文件", "", 2, () -> {
                    String src = packagePath.replace('\\', '/');
                    String dst = sdkTarget.replace('\\', '/');
                    ReturnResult<Void> mv = sftpService.rename(ctx.hostId, null, src, dst);
                    if (!mv.isOk()) {
                        // 多数 SFTP 实现不支持覆盖式 rename（目标已存在的升级场景必然失败）：
                        // 先删目标再 rename，仍走 SDK；只有删除后仍失败才回退命令。
                        sftpService.delete(ctx.hostId, null, dst, false);
                        mv = sftpService.rename(ctx.hostId, null, src, dst);
                    }
                    if (mv.isOk()) {
                        return java.util.List.of("sftp rename " + src + " -> " + dst);
                    }
                    // 跨设备等场景：回退到平台命令（cp -f / Copy-Item）
                    String sdkWhy = mv.getMsg();
                    // 必须检查命令投放的结果：此前 runCommand 的返回值被直接丢弃，
                    // 于是「SFTP 重命名失败 + 命令投放也失败」时步骤依然报成功，
                    // 编排器继续往下走去重启 —— 而目标文件此时已被 delete 掉，
                    // 项目一个可用 jar 都不剩，最后表现为「健康检查超时、自动回滚也没跑」。
                    ReturnResult<java.util.List<MsExecLog>> placed =
                            execService.runCommand(java.util.Collections.singletonList(ctx.hostId),
                                    placeCommand(win, path, packagePath, project.getMainEntry()), 60, null);
                    if (!placed.isOk() || anyExecFailed(placed.getData())) {
                        // 止损：投放失败且旧文件已被删除，立刻用最新备份还原并把服务重新拉起。
                        // 只还原文件是不够的 —— 流程在第 3 步中止，后面的「重启并健康检测」
                        // 不会执行，项目会停留在「已停止」状态；升级失败却把服务弄停，
                        // 比升级失败本身更严重。
                        String restoreNote = restoreAndRestart(win, ctx, path, project);
                        throw new IllegalStateException("投放新版本文件失败且旧文件已删除；" + restoreNote
                                + "SDK=" + sdkWhy + "；命令投放=" + (placed.isOk() ? "退出码非 0" : placed.getMsg()));
                    }
                    return java.util.List.of("SFTP rename 不可用，已回退命令投放: " + sdkWhy);
                }));
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
        context.osType = HostPlatforms.normalize(targetHost.getServerHostOsType());
        context.host = targetHost;
        context.windows = HostPlatforms.WINDOWS.equals(context.osType);
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
        ProjectPlatformProvider pf = projectPlatformRouter.resolveOrPosix(osTypeOfProject(project));
        // 结构化变量（envVars/jvmArgs/cmdArgs）注入 VARS 块 + 展开 __JVMARGS__/__ARGS__，
        // 使「API 创建 / 内置模板」的项目也能生效（此前仅前端保存时注入）
        String start = materializeVars(project.getStartScript(), project, pf);
        String restart = materializeVars(project.getRestartScript(), project, pf);
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
    /**
     * 取项目目标主机归一后的系统类型（供平台 SPI 路由）。
     *
     * @param project 项目
     * @return 系统类型（HostPlatforms 取值域）；主机缺失返回空串
     */
    private String osTypeOfProject(MsServerProject project) {
        MsServerRelease release = project.getReleaseId() == null ? null : releaseMapper.selectById(project.getReleaseId());
        MsServerHost host = (release != null && release.getHostId() != null)
                ? hostMapper.selectById(release.getHostId()) : null;
        return host == null ? "" : HostPlatforms.normalize(host.getServerHostOsType());
    }

    /**
     * 判断项目目标主机是否 Windows（按所属发布绑定的主机）
     *
     * @param project 项目
     * @return 是否 Windows
     */
    private boolean isWindowsProject(MsServerProject project) {
        return HostPlatforms.WINDOWS.equals(osTypeOfProject(project));
    }

    /**
     * 将 envVars/jvmArgs/cmdArgs 注入脚本：构建 VARS 托管块（幂等）并展开参数占位符
     *
     * @param script  脚本（可为空）
     * @param project 项目（读取 envVars/jvmArgs/cmdArgs）
     * @param pf      平台命令提供者（变量块与参数展开的平台差异由其负责）
     * @return 注入后的脚本
     */
    private String materializeVars(String script, MsServerProject project, ProjectPlatformProvider pf) {
        if (script == null || script.isEmpty()) {
            return script;
        }
        List<EnvVar> envs = readEnvList(project.getEnvVars());
        List<String> jvms = readStringList(project.getJvmArgs());
        List<String> args = readStringList(project.getCmdArgs());
        String out = script;
        if (!envs.isEmpty() || !jvms.isEmpty() || !args.isEmpty()) {
            List<String> names = new ArrayList<>();
            List<String> values = new ArrayList<>();
            for (EnvVar e : envs) {
                if (!isBlank(e.key)) {
                    names.add(e.key.trim());
                    values.add(e.value == null ? "" : e.value);
                }
            }
            StringBuilder b = new StringBuilder(VARS_BEGIN).append('\n');
            b.append(pf.varsBlock(names, values, jvms, args));
            b.append(VARS_END).append('\n');
            out = injectVarsBlock(out, b.toString());
        }
        out = out.replace("__JVMARGS__", pf.jvmArgsLiteral(jvms))
                .replace("__ARGS__", pf.appArgsLiteral(args));
        return out;
    }

    /**
     * 幂等注入 VARS 托管块：已存在则替换，不存在则前插
     */
    /**
     * 幂等注入 VARS 托管块：已存在则替换，不存在则前置
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

    /**
     * 执行前解析变量托管块中的占位符，支持：
     * ${key:default} 默认值、${now(pattern)} 日期格式化、${uuid}、${random(n)}、${upper/lower/length} 等。
     * 仅处理托管块，避免误改脚本中正常的 shell / PowerShell 语法；
     * 无法解析的占位符保留原样（交给 shell），不中断执行。
     */
    private String resolveScriptPlaceholders(String script, ProjectPlatformProvider pf) {
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
        Pattern pat = Pattern.compile(pf.envPattern());
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
    private String buildCommand(ProjectPlatformProvider pf, String projectPath, String script) {
        return pf.wrap(projectPath, resolveScriptPlaceholders(script, pf));
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
    private ProjectStep springConfigStep(ProjectPlatformProvider pf, MsServerProject project) {
        List<EnvVar> entries = readEnvList(project.getAppConfig());
        if (entries.isEmpty()) {
            return null;
        }
        List<String> keys = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (EnvVar e : entries) {
            if (e.key != null && !e.key.isBlank()) {
                keys.add(e.key);
                values.add(e.value == null ? "" : e.value);
            }
        }
        // 必须在项目目录下执行：否则 config/application.properties 会写到 SSH 默认工作目录
        return new ProjectStep("生成项目配置",
                pf.wrap(project.getProjectPath(), pf.springConfigScript(keys, values)), 1);
    }

    /**
     * 构造「读取 PID」步骤（启动 / 重启的最后一步，其输出仅为 PID，供回写卡片）。
     * 经 buildCommand 包装后在项目目录下读取 app.pid，避免在 WinRM 默认工作目录读到残留文件。
     *
     * @param windows     是否 Windows
     * @param projectPath 项目地址
     * @return 步骤
     */
    private ProjectStep pidStep(ProjectPlatformProvider pf, String projectPath) {
        return new ProjectStep("记录PID", pf.wrap(projectPath, pf.readPid()), 1);
    }

    /**
     * 构建备份命令（平台实现由 {@link ProjectPlatformProvider} 提供）。
     *
     * @param pf          平台命令提供者
     * @param projectPath 项目地址
     * @return 备份命令
     */
    private String backupCommand(ProjectPlatformProvider pf, String projectPath) {
        return pf.backup(projectPath, MAX_BACKUPS);
    }

    /**
     * 构建回滚命令（恢复指定/最新备份），平台实现由 {@link ProjectPlatformProvider} 提供。
     *
     * @param pf          平台命令提供者
     * @param projectPath 项目地址
     * @param backupName  备份名；为空表示最新
     * @return 回滚命令
     */
    private String rollbackCommand(ProjectPlatformProvider pf, String projectPath, String backupName) {
        return pf.rollback(projectPath, backupName);
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

    private String extractCommand(ProjectPlatformProvider pf, String projectPath, String packagePath) {
        return pf.extract(projectPath, packagePath);
    }

    /**
     * 判断一批执行日志里是否存在失败项。
     *
     * <p>不能只看外层 {@code ReturnResult}：命令投放这类操作外层常常是成功的，
     * 真正的失败只体现在每台主机的退出码上（真实踩过：SFTP 重命名失败后回退命令投放，
     * 外层 ok 但退出码非 0，步骤却报了「成功」）。</p>
     *
     * @param logs 执行日志
     * @return 有任一主机失败时为 {@code true}；日志为空按失败处理（无法确认成功）
     */
    private static boolean anyExecFailed(java.util.List<MsExecLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return true;
        }
        for (MsExecLog log : logs) {
            if (log == null || log.getMsExecLogExitCode() == null || log.getMsExecLogExitCode() != 0) {
                return true;
            }
        }
        return false;
    }

    /**
 * 投放失败后的止损：用最新备份还原文件并把服务重新拉起。
 *
 * <p>为什么必须连服务一起拉起：升级流程是「停服 → 备份 → 投放 → 重启并健康检查」，
 * 投放这步失败后整单中止，第 4 步不会执行。此时文件已还原但进程没起来，
 * 项目停在停止状态 —— 一次失败的升级把在跑的服务弄停，比升级失败本身更严重。</p>
 *
 * @param pf      平台
 * @param ctx     上下文（取 hostId）
 * @param path    项目地址
 * @param project 项目
 * @return 止损执行结果描述，用于拼进异常信息
 */
    private String restoreAndRestart(ProjectPlatformProvider pf, OperationContext ctx, String path,
                                     MsServerProject project) {
        StringBuilder sb = new StringBuilder();
        try {
            execService.runCommand(java.util.Collections.singletonList(ctx.hostId),
                    rollbackCommand(pf, path, null), 120, null);
            sb.append("已用最新备份还原；");
        } catch (Exception e) {
            sb.append("还原备份失败(").append(e.getMessage()).append(")；");
        }
        if (!isBlank(project.getStartScript())) {
            try {
                ReturnResult<java.util.List<MsExecLog>> started = execService.runCommand(
                        java.util.Collections.singletonList(ctx.hostId),
                        buildCommand(pf, path, project.getStartScript()), 180, null);
                sb.append(started.isOk() && !anyExecFailed(started.getData())
                        ? "已重新拉起服务" : "重新拉起服务未成功");
            } catch (Exception e) {
                sb.append("重新拉起服务异常(").append(e.getMessage()).append(")");
            }
        }
        return sb.toString();
    }

    private String placeCommand(ProjectPlatformProvider pf, String projectPath, String packagePath, String mainEntry) {
        return pf.place(projectPath, packagePath, mainEntry);
    }

    /**
     * 重启后健康检测，失败则回滚（平台实现由 {@link ProjectPlatformProvider} 提供）。
     *
     * @param pf          平台命令提供者
     * @param projectPath 项目地址
     * @param project     项目
     * @param restart     是否重启（false 时仅健康检查）
     * @return 命令
     */
    private String guardedRestartCommand(ProjectPlatformProvider pf, String projectPath,
                                         MsServerProject project, boolean restart) {
        return pf.guardedRestart(projectPath, project.getRestartScript(), project.getStopScript(),
                project.getStartScript(), project.getStatusScript(), restart, resolveHealthPort(project));
    }

    /**
     * 从项目配置里解析业务端口，供健康检查做端口探活。
     *
     * <p>解析顺序与「实际生效」一致：显式启动参数（{@code --server.port=}）优先于
     * 外部配置文件里的 {@code server.port=}，两者都没有则返回 0（不做端口探活）。
     * 解析失败一律返回 0 而不是抛错——端口只是增强判据，不能因为它让升级流程中断。</p>
     *
     * @param project 项目配置
     * @return 端口号；无法确定时为 0
     */
    private int resolveHealthPort(MsServerProject project) {
        int port = firstPort(SERVER_PORT_ARG, project.getCmdArgs());
        if (port <= 0) {
            port = firstPort(SERVER_PORT_ARG, project.getJvmArgs());
        }
        if (port <= 0) {
            port = firstPort(SERVER_PORT_PROP, project.getAppConfig());
        }
        return port;
    }

    /**
     * 取文本中第一个匹配到的合法端口号。
     *
     * @param pattern 端口正则（首个捕获组为端口）
     * @param text    待解析文本，可为空
     * @return 合法端口号；未匹配或越界时为 0
     */
    private int firstPort(Pattern pattern, String text) {
        if (isBlank(text)) {
            return 0;
        }
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            try {
                int v = Integer.parseInt(m.group(1));
                if (v > 0 && v <= 65535) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
                // 超出 int 范围的数字串直接跳过，继续找下一个候选
            }
        }
        return 0;
    }

    // ---------- 系统服务（Linux systemd）----------

    private List<String> readStringList(String json) {
        if (isBlank(json)) {
            return Collections.emptyList();
        }
        String text = json.trim();
        // 正常来源是前端 JSON.stringify(string[])，即 JSON 数组；但手工写入或历史数据
        // 往往是「--server.port=8112 --spring.profiles.active=prod」这样的裸串。
        // 原实现遇到裸串时 JSON 解析抛错并静默返回空列表 —— 用户填了参数却毫无提示地被丢弃，
        // 表现为「端口/参数没生效」，很难排查。此处改为：非 JSON 数组一律按空白切分。
        if (!text.startsWith("[")) {
            return splitArgs(text);
        }
        try {
            return JSON.readValue(text, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { });
        } catch (Exception e) {
            // JSON 数组解析失败同样不能静默丢参：剥掉括号与引号后按空白切分，并留下告警
            log.warn("[release] 参数不是合法 JSON 数组，已按空白切分兜底: {}", text);
            return splitArgs(text.replaceAll("[\\[\\]\"]", " "));
        }
    }

    /**
     * 把裸参数串按空白切分为列表。
     *
     * @param text 参数串（如 {@code --server.port=8112 --spring.profiles.active=prod}）
     * @return 非空参数项列表
     */
    private List<String> splitArgs(String text) {
        if (isBlank(text)) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String part : text.trim().split("\\s+")) {
            if (!part.isBlank()) {
                out.add(part);
            }
        }
        return out;
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
