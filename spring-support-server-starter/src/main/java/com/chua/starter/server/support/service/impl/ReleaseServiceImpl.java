package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.MsServerProject;
import com.chua.starter.server.support.entity.MsServerRelease;
import com.chua.starter.server.support.entity.SoftwarePackage;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.mapper.MsServerProjectMapper;
import com.chua.starter.server.support.mapper.MsServerReleaseMapper;
import com.chua.starter.server.support.push.ProjectTaskService;
import com.chua.starter.server.support.push.ProjectTaskService.ProjectStep;
import com.chua.starter.server.support.service.ExecService;
import com.chua.starter.server.support.service.ReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
     * 操作上下文：项目 + 目标主机
     */
    private static class OperationContext {
        private MsServerProject project;
        private Long hostId;
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
        exists.setStartScript(project.getStartScript() == null ? exists.getStartScript() : project.getStartScript());
        exists.setStopScript(project.getStopScript() == null ? exists.getStopScript() : project.getStopScript());
        exists.setRestartScript(project.getRestartScript() == null ? exists.getRestartScript() : project.getRestartScript());
        exists.setProjectPath(project.getProjectPath() == null ? exists.getProjectPath() : project.getProjectPath());
        exists.setLogPath(project.getLogPath() == null ? exists.getLogPath() : project.getLogPath());
        exists.setDescription(project.getDescription() == null ? exists.getDescription() : project.getDescription());
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
        List<ProjectStep> steps = List.of(new ProjectStep("启动项目",
                buildCommand(project.getProjectPath(), project.getStartScript()), 1));
        String taskId = projectTaskService.startTask(prepared.getData().hostId, project.getId(),
                "start", steps, "running");
        return ReturnResult.ok(taskId);
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
                buildCommand(project.getProjectPath(), project.getStopScript()), 1));
        String taskId = projectTaskService.startTask(prepared.getData().hostId, project.getId(),
                "stop", steps, "stopped");
        return ReturnResult.ok(taskId);
    }

    @Override
    public ReturnResult<String> restartProject(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        String path = project.getProjectPath();
        List<ProjectStep> steps = new ArrayList<>();
        if (!isBlank(project.getRestartScript())) {
            steps.add(new ProjectStep("重启项目", buildCommand(path, project.getRestartScript()), 1));
        } else {
            if (isBlank(project.getStopScript()) || isBlank(project.getStartScript())) {
                return ReturnResult.error("重启脚本未配置（需停止+启动脚本或重启脚本）");
            }
            steps.add(new ProjectStep("停止服务", buildCommand(path, project.getStopScript()), 1));
            steps.add(new ProjectStep("启动服务", buildCommand(path, project.getStartScript()), 1));
        }
        String taskId = projectTaskService.startTask(prepared.getData().hostId, project.getId(),
                "restart", steps, "running");
        return ReturnResult.ok(taskId);
    }

    @Override
    public ReturnResult<String> upgradeProject(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        List<ProjectStep> steps = new ArrayList<>();
        if (!isBlank(project.getStopScript())) {
            steps.add(new ProjectStep("停止服务", buildCommand(project.getProjectPath(), project.getStopScript()), 1));
        }
        steps.add(new ProjectStep("备份当前版本", backupCommand(project.getProjectPath()), 2));
        if (!isBlank(project.getStartScript())) {
            steps.add(new ProjectStep("启动服务", buildCommand(project.getProjectPath(), project.getStartScript()), 1));
        }
        String taskId = projectTaskService.startTask(prepared.getData().hostId, project.getId(),
                "upgrade", steps, "running");
        return ReturnResult.ok(taskId);
    }

    @Override
    public ReturnResult<String> backupProject(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        List<ProjectStep> steps = List.of(new ProjectStep("备份项目",
                backupCommand(project.getProjectPath()), 1));
        String taskId = projectTaskService.startTask(prepared.getData().hostId, project.getId(),
                "backup", steps, null);
        return ReturnResult.ok(taskId);
    }

    @Override
    public ReturnResult<String> rollbackProject(Long id) {
        ReturnResult<OperationContext> prepared = prepare(id);
        if (!prepared.isOk()) {
            return ReturnResult.error(prepared.getMsg());
        }
        MsServerProject project = prepared.getData().project;
        List<ProjectStep> steps = new ArrayList<>();
        if (!isBlank(project.getStopScript())) {
            steps.add(new ProjectStep("停止服务", buildCommand(project.getProjectPath(), project.getStopScript()), 1));
        }
        steps.add(new ProjectStep("恢复最新备份", rollbackCommand(project.getProjectPath()), 2));
        if (!isBlank(project.getStartScript())) {
            steps.add(new ProjectStep("启动服务", buildCommand(project.getProjectPath(), project.getStartScript()), 1));
        }
        String taskId = projectTaskService.startTask(prepared.getData().hostId, project.getId(),
                "rollback", steps, "running");
        return ReturnResult.ok(taskId);
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
        String command = "tail -n 100 " + project.getLogPath();
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

        String osType = host.getServerHostOsType();
        String command;

        if ("WINDOWS".equalsIgnoreCase(osType)) {
            command = "winget search " + keyword;
        } else {
            command = "if command -v apt-cache >/dev/null 2>&1; then apt-cache search " + keyword +
                    "; elif command -v yum >/dev/null 2>&1; then yum search " + keyword +
                    "; elif command -v dnf >/dev/null 2>&1; then dnf search " + keyword +
                    "; else echo 'No supported package manager'; fi";
        }

        ReturnResult<List<com.chua.starter.server.support.entity.MsExecLog>> result =
                execService.runCommand(Collections.singletonList(hostId), command, 30, null);

        List<SoftwarePackage> packages = new ArrayList<>();
        if (result.isSuccess() && result.getData() != null && !result.getData().isEmpty()) {
            String output = result.getData().get(0).getMsExecLogOutput();
            packages = parsePackageOutput(output, keyword);
        }
        return ReturnResult.ok(packages);
    }

    /**
     * 安装软件：构建包管理器安装命令，转为异步任务，实时进度经 socket 推送。
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

        String osType = host.getServerHostOsType();
        String command;
        if ("WINDOWS".equalsIgnoreCase(osType)) {
            command = "winget install --id " + name + " -e --accept-package-agreements --accept-source-agreements";
        } else {
            command = "if command -v apt-get >/dev/null 2>&1; then sudo apt-get install -y " + name +
                    "; elif command -v yum >/dev/null 2>&1; then yum install -y " + name +
                    "; elif command -v dnf >/dev/null 2>&1; then dnf install -y " + name +
                    "; else echo 'No supported package manager'; exit 1; fi";
        }

        List<ProjectStep> steps = List.of(new ProjectStep("安装 " + name, command, 1));
        String taskId = projectTaskService.startTask(hostId, null, "install", steps, null);
        return ReturnResult.ok(taskId);
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
        OperationContext context = new OperationContext();
        context.project = project;
        context.hostId = release.getHostId();
        return ReturnResult.ok(context);
    }

    /**
     * 根据主机ID回填主机IP，保证发布卡片主机信息完整。
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

    /**
     * 构建执行命令
     */
    private String buildCommand(String projectPath, String script) {
        if (!isBlank(projectPath)) {
            return "cd " + projectPath + " && " + script;
        }
        return script;
    }

    /**
     * 构建备份命令（tar）
     */
    private String backupCommand(String projectPath) {
        String file = "backup_" + System.currentTimeMillis() + ".tar.gz";
        String prefix = isBlank(projectPath) ? "" : ("cd " + projectPath + " && ");
        return prefix + "tar -czf " + file + " .";
    }

    /**
     * 构建回滚命令（恢复最新备份）
     */
    private String rollbackCommand(String projectPath) {
        String prefix = isBlank(projectPath) ? "" : ("cd " + projectPath + " && ");
        return prefix + "latest=$(ls -t backup_*.tar.gz | head -1) && tar -xzf \"$latest\"";
    }

    /**
     * 字符串空白判断
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 解析包管理器搜索结果
     */
    private List<SoftwarePackage> parsePackageOutput(String output, String keyword) {
        List<SoftwarePackage> list = new ArrayList<>();
        if (output == null || output.isEmpty()) {
            return list;
        }

        String[] lines = output.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("=") || line.startsWith("-")
                    || line.toLowerCase().contains("matched")
                    || line.toLowerCase().contains("loading")
                    || line.toLowerCase().contains("warning")) {
                continue;
            }

            if (line.contains(" - ")) {
                int idx = line.indexOf(" - ");
                String name = line.substring(0, idx).trim();
                String summary = line.substring(idx + 3).trim();
                if (name.contains(" ")) {
                    name = name.split("\\s+")[0];
                }
                SoftwarePackage pkg = new SoftwarePackage();
                pkg.setName(name);
                pkg.setSummary(summary);
                list.add(pkg);
            } else if (line.contains(" ") && !line.startsWith("Name") && !line.startsWith("名称")) {
                String[] parts = line.split("\\s{2,}");
                if (parts.length >= 1) {
                    String name = parts[0].trim();
                    String prefix = keyword.toLowerCase().substring(0, Math.min(3, keyword.length()));
                    if (!name.isEmpty() && !name.equalsIgnoreCase("Name")
                            && !name.equalsIgnoreCase("名称")
                            && name.toLowerCase().contains(prefix)) {
                        SoftwarePackage pkg = new SoftwarePackage();
                        pkg.setName(name);
                        if (parts.length >= 2) {
                            pkg.setVersion(parts[1].trim());
                        }
                        if (parts.length >= 3) {
                            pkg.setSummary(parts[2].trim());
                        }
                        list.add(pkg);
                    }
                }
            }
        }
        return list;
    }
}
