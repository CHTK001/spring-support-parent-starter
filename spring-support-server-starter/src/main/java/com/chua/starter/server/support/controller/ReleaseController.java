package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerProject;
import com.chua.starter.server.support.entity.MsServerRelease;
import com.chua.starter.server.support.entity.SoftwarePackage;
import com.chua.starter.server.support.model.vo.ProjectTaskSnapshotVO;
import com.chua.starter.server.support.model.vo.ProjectBackupVO;
import com.chua.starter.server.support.push.ProjectTaskService;
import com.chua.starter.server.support.service.ReleaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 发布管理控制器
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "发布管理")
@RestController
@RequestMapping("/ms/server/release")
@RequiredArgsConstructor
public class ReleaseController {

    private final ReleaseService releaseService;
    private final ProjectTaskService projectTaskService;

    // ==================== 发布管理 ====================

    /**
     * 获取发布列表。
     *
     * @return 发布列表
     */
    @Operation(summary = "获取发布列表")
    @GetMapping("/list")
    public ReturnResult<List<MsServerRelease>> listReleases() {
        return releaseService.listReleases();
    }

    /**
     * 创建发布。
     *
     * @param release 发布信息
     * @return 创建后的发布
     */
    @Operation(summary = "创建发布")
    @PostMapping
    public ReturnResult<MsServerRelease> createRelease(@RequestBody MsServerRelease release) {
        return releaseService.createRelease(release);
    }

    /**
     * 更新发布。
     *
     * @param id 发布标识
     * @param release 发布信息
     * @return 更新后的发布
     */
    @Operation(summary = "更新发布")
    @PutMapping("/{id}")
    public ReturnResult<MsServerRelease> updateRelease(@PathVariable Long id, @RequestBody MsServerRelease release) {
        return releaseService.updateRelease(id, release);
    }

    /**
     * 删除发布。
     *
     * @param id 发布标识
     * @return 操作结果
     */
    @Operation(summary = "删除发布")
    @DeleteMapping("/{id}")
    public ReturnResult<Void> deleteRelease(@PathVariable Long id) {
        return releaseService.deleteRelease(id);
    }

    // ==================== 项目管理 ====================

    /**
     * 获取项目列表。
     *
     * @param releaseId 发布标识
     * @return 项目列表
     */
    @Operation(summary = "获取项目列表")
    @GetMapping("/{releaseId}/projects")
    public ReturnResult<List<MsServerProject>> listProjects(@PathVariable Long releaseId) {
        return releaseService.listProjects(releaseId);
    }

    /**
     * 创建项目。
     *
     * @param project 项目信息
     * @return 创建后的项目
     */
    @Operation(summary = "创建项目")
    @PostMapping("/project")
    public ReturnResult<MsServerProject> createProject(@RequestBody MsServerProject project) {
        return releaseService.createProject(project);
    }

    /**
     * 更新项目。
     *
     * @param id 项目标识
     * @param project 项目信息
     * @return 更新后的项目
     */
    @Operation(summary = "更新项目")
    @PutMapping("/project/{id}")
    public ReturnResult<MsServerProject> updateProject(@PathVariable Long id, @RequestBody MsServerProject project) {
        return releaseService.updateProject(id, project);
    }

    /**
     * 生成内置四段脚本（前端一键生成/预览的唯一权威来源，与执行期一致）。
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
     * @return 四段脚本
     */
    @Operation(summary = "生成内置脚本（启动/停止/重启/状态，与执行期一致）")
    @GetMapping("/script/template")
    public ReturnResult<java.util.Map<String, String>> scriptTemplate(
            @RequestParam Long hostId,
            @RequestParam String projectType,
            @RequestParam(required = false) String mainEntry,
            @RequestParam(required = false) String projectPath,
            @RequestParam(required = false) String logPath,
            @RequestParam(required = false) String appName,
            @RequestParam(required = false) String envVars,
            @RequestParam(required = false) String jvmArgs,
            @RequestParam(required = false) String cmdArgs) {
        return releaseService.generateScripts(hostId, projectType, mainEntry, projectPath,
                logPath, appName, envVars, jvmArgs, cmdArgs);
    }

    /**
     * 删除项目。
     *
     * @param id 项目标识
     * @return 操作结果
     */
    @Operation(summary = "删除项目")
    @DeleteMapping("/project/{id}")
    public ReturnResult<Void> deleteProject(@PathVariable Long id) {
        return releaseService.deleteProject(id);
    }

    // ==================== 项目操作 ====================

    /**
     * 启动项目。
     *
     * @param id 项目标识
     * @return 异步任务标识
     */
    @Operation(summary = "启动项目（异步，返回taskId）")
    @PostMapping("/project/{id}/start")
    public ReturnResult<String> startProject(@PathVariable Long id) {
        return releaseService.startProject(id);
    }

    /**
     * 停止项目。
     *
     * @param id 项目标识
     * @return 异步任务标识
     */
    @Operation(summary = "停止项目（异步，返回taskId）")
    @PostMapping("/project/{id}/stop")
    public ReturnResult<String> stopProject(@PathVariable Long id) {
        return releaseService.stopProject(id);
    }

    /**
     * 重启项目。
     *
     * @param id 项目标识
     * @return 异步任务标识
     */
    @Operation(summary = "重启项目（异步，返回taskId）")
    @PostMapping("/project/{id}/restart")
    public ReturnResult<String> restartProject(@PathVariable Long id) {
        return releaseService.restartProject(id);
    }

    /**
     * 升级项目。
     *
     * @param id 项目标识
     * @return 异步任务标识
     */
    @Operation(summary = "升级项目（异步，返回taskId）")
    @PostMapping("/project/{id}/upgrade")
    public ReturnResult<String> upgradeProject(@PathVariable Long id) {
        return releaseService.upgradeProject(id);
    }

    /**
     * 备份项目。
     *
     * @param id 项目标识
     * @return 异步任务标识
     */
    @Operation(summary = "备份项目（异步，返回taskId）")
    @PostMapping("/project/{id}/backup")
    public ReturnResult<String> backupProject(@PathVariable Long id) {
        return releaseService.backupProject(id);
    }

    /**
     * 回滚项目。
     *
     * @param id 项目标识
     * @param backup 备份标识，为空时恢复最新备份
     * @return 异步任务标识
     */
    @Operation(summary = "回滚项目（异步，返回taskId）。backup 为空恢复最新备份")
    @PostMapping("/project/{id}/rollback")
    public ReturnResult<String> rollbackProject(@PathVariable Long id,
                                                 @RequestParam(required = false) String backup) {
        return releaseService.rollbackProject(id, backup);
    }

    /**
     * 列出项目备份记录。
     *
     * @param id 项目标识
     * @return 备份记录列表
     */
    @Operation(summary = "列出项目备份记录")
    @GetMapping("/project/{id}/backups")
    public ReturnResult<List<ProjectBackupVO>> listBackups(@PathVariable Long id) {
        return releaseService.listBackups(id);
    }

    /**
     * 获取项目日志。
     *
     * @param id 项目标识
     * @return 一次性日志内容
     */
    @Operation(summary = "获取项目日志（一次性tail）")
    @GetMapping("/project/{id}/log")
    public ReturnResult<String> getProjectLog(@PathVariable Long id) {
        return releaseService.getProjectLog(id);
    }

    /**
     * 开启项目实时日志。
     *
     * @param id 项目标识
     * @return 实时日志会话标识
     */
    @Operation(summary = "开启实时日志（返回sessionId）")
    @PostMapping("/project/{id}/log/start")
    public ReturnResult<String> startProjectLog(@PathVariable Long id) {
        return releaseService.startProjectLog(id);
    }

    /**
     * 停止项目实时日志。
     *
     * @param sessionId 日志会话标识
     * @return 操作结果
     */
    @Operation(summary = "停止实时日志")
    @PostMapping("/project/log/{sessionId}/stop")
    public ReturnResult<Void> stopProjectLog(@PathVariable String sessionId) {
        return releaseService.stopProjectLog(sessionId);
    }

    /**
     * 查询任务快照。
     *
     * @param taskId 任务标识
     * @return 任务快照
     */
    @Operation(summary = "查询任务快照（断点续传）")
    @GetMapping("/task/{taskId}")
    public ReturnResult<ProjectTaskSnapshotVO> getTaskSnapshot(@PathVariable String taskId) {
        return projectTaskService.getSnapshot(taskId);
    }

    // ==================== 软件仓库 ====================

    /**
     * 搜索软件包。
     *
     * @param hostId 主机标识
     * @param keyword 搜索关键词
     * @return 软件包列表
     */
    @Operation(summary = "搜索软件包")
    @GetMapping("/software/search")
    public ReturnResult<List<SoftwarePackage>> searchSoftware(
            @RequestParam Long hostId,
            @RequestParam String keyword) {
        return releaseService.searchSoftware(hostId, keyword);
    }

    /**
     * 安装软件。
     *
     * @param hostId 主机标识
     * @param name 软件名称
     * @return 异步任务标识
     */
    @Operation(summary = "安装软件（异步，返回taskId）")
    @PostMapping("/software/install")
    public ReturnResult<String> installSoftware(
            @RequestParam Long hostId,
            @RequestParam String name) {
        return releaseService.installSoftware(hostId, name);
    }

    /**
     * 列出已安装软件。
     *
     * @param hostId 主机标识
     * @return 已安装软件包列表
     */
    @Operation(summary = "列出主机已安装软件")
    @GetMapping("/software/installed")
    public ReturnResult<List<SoftwarePackage>> listInstalledSoftware(@RequestParam Long hostId) {
        return releaseService.listInstalledSoftware(hostId);
    }

    /**
     * 卸载软件。
     *
     * @param hostId 主机标识
     * @param name 软件名称
     * @return 异步任务标识
     */
    @Operation(summary = "卸载软件（异步，返回taskId）")
    @PostMapping("/software/uninstall")
    public ReturnResult<String> uninstallSoftware(
            @RequestParam Long hostId,
            @RequestParam String name) {
        return releaseService.uninstallSoftware(hostId, name);
    }

    /**
     * 升级软件到最新版。
     *
     * @param hostId 主机标识
     * @param name 软件名称
     * @return 异步任务标识
     */
    @Operation(summary = "升级软件到最新版（异步，返回taskId）")
    @PostMapping("/software/upgrade")
    public ReturnResult<String> upgradeSoftware(
            @RequestParam Long hostId,
            @RequestParam String name) {
        return releaseService.upgradeSoftware(hostId, name);
    }

    // ==================== 状态检测 ====================

    /**
     * 查询项目运行状态。
     *
     * @param id 项目标识
     * @return 项目状态
     */
    @Operation(summary = "查询项目运行状态")
    @GetMapping("/project/{id}/status")
    public ReturnResult<String> statusProject(@PathVariable Long id) {
        return releaseService.statusProject(id);
    }

    // ==================== 系统服务挂载 ====================

    /**
     * 挂载项目为系统服务。
     *
     * @param id 项目标识
     * @return 异步任务标识
     */
    @Operation(summary = "挂载为系统服务（开机自启/守护，异步，返回taskId）")
    @PostMapping("/project/{id}/service/install")
    public ReturnResult<String> installService(@PathVariable Long id) {
        return releaseService.installService(id);
    }

    /**
     * 卸载项目系统服务。
     *
     * @param id 项目标识
     * @return 异步任务标识
     */
    @Operation(summary = "卸载系统服务（异步，返回taskId）")
    @PostMapping("/project/{id}/service/uninstall")
    public ReturnResult<String> uninstallService(@PathVariable Long id) {
        return releaseService.uninstallService(id);
    }

    // ==================== 升级配置 ====================

    /**
     * 读取升级配置。
     *
     * @return 全局和最近一次升级配置
     */
    @Operation(summary = "读取升级配置（global 全局 + last 最近一次）")
    @GetMapping("/upgrade/config")
    public ReturnResult<java.util.Map<String, Object>> getUpgradeConfig() {
        return releaseService.getUpgradeConfig();
    }

    /**
     * 保存升级配置。
     *
     * @param scope 配置作用域
     * @param config 升级配置
     * @return 操作结果
     */
    @Operation(summary = "保存升级配置（scope=global/last）")
    @PutMapping("/upgrade/config")
    public ReturnResult<Void> saveUpgradeConfig(
            @RequestParam(defaultValue = "global") String scope,
            @RequestBody java.util.Map<String, Object> config) {
        return releaseService.saveUpgradeConfig(scope, config);
    }

    /**
     * 按配置执行升级。
     *
     * @param id 项目标识
     * @param config 升级配置，可为空
     * @return 异步任务标识
     */
    @Operation(summary = "按配置执行升级（异步，返回taskId）")
    @PostMapping("/project/{id}/upgrade/run")
    public ReturnResult<String> runUpgrade(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, Object> config) {
        return releaseService.runUpgrade(id, config);
    }

    /**
     * 上传安装包并执行拖拽升级。
     *
     * @param id 项目标识
     * @param file 安装包文件
     * @return 异步任务标识
     */
    @Operation(summary = "拖拽升级：上传安装包并按全局配置升级（异步，返回taskId）")
    @PostMapping("/project/{id}/upload")
    public ReturnResult<String> uploadAndUpgrade(
            @PathVariable Long id,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return releaseService.uploadAndUpgrade(id, file);
    }
}
