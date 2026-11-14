package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerProject;
import com.chua.starter.server.support.entity.MsServerRelease;
import com.chua.starter.server.support.entity.SoftwarePackage;
import com.chua.starter.server.support.model.vo.ProjectTaskSnapshotVO;
import com.chua.starter.server.support.model.vo.ProjectBackupVO;
import com.chua.starter.server.support.model.ApmGlobalConfig;
import com.chua.starter.server.support.push.ProjectTaskService;
import com.chua.starter.server.support.schedule.ReleaseScheduleService;
import com.chua.starter.server.support.service.ProjectOpLogService;
import com.chua.starter.server.support.service.ReleaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
@Slf4j
public class ReleaseController {

    private final ReleaseService releaseService;
    private final ProjectTaskService projectTaskService;
    private final ProjectOpLogService projectOpLogService;
    private final ObjectProvider<ReleaseScheduleService> releaseScheduleServiceProvider;

    /**
     * 同步项目的定时任务（宿主未引入 job-starter 时静默跳过）。
     * @param projectId 长整数
     * @return 返回值
    */
    private void syncSchedule(Long projectId) {
        ReleaseScheduleService service = releaseScheduleServiceProvider.getIfAvailable();
        if (service == null) {
            return;
        }
        try {
            service.syncProject(projectId);
        } catch (Exception e) {
            log.warn("[发布管理] 同步项目 {} 定时任务失败: {}", projectId, e.getMessage());
        }
    }

    /**
     * 移除项目的定时任务。
     * @param projectId 长整数
     * @return 返回值
    */
    private void removeSchedule(Long projectId) {
        ReleaseScheduleService service = releaseScheduleServiceProvider.getIfAvailable();
        if (service == null) {
            return;
        }
        try {
            service.removeProject(projectId);
        } catch (Exception e) {
            log.warn("[发布管理] 移除项目 {} 定时任务失败: {}", projectId, e.getMessage());
        }
    }

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
     * 读取全局 APM 兜底配置。
     *
     * @return 全局配置
     */
    @Operation(summary = "读取全局 APM 兜底配置")
    @GetMapping("/apm/global")
    public ReturnResult<ApmGlobalConfig> getGlobalApmConfig() {
        return releaseService.getGlobalApmConfig();
    }

    /**
     * 保存全局 APM 兜底配置，并同步到所有发布下的项目。
     *
     * @param config 全局配置
     * @return 保存后的配置
     */
    @Operation(summary = "保存全局 APM 兜底配置")
    @PutMapping("/apm/global")
    public ReturnResult<ApmGlobalConfig> saveGlobalApmConfig(@RequestBody ApmGlobalConfig config) {
        return releaseService.saveGlobalApmConfig(config);
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
        ReturnResult<MsServerProject> result = releaseService.createProject(project);
        if (result.isOk() && result.getData() != null) {
            syncSchedule(result.getData().getId());
        }
        return result;
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
        ReturnResult<MsServerProject> result = releaseService.updateProject(id, project);
        if (result.isOk()) {
            syncSchedule(id);
        }
        return result;
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
     * @param jdkHome     JDK 安装路径（可为空）
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
            @RequestParam(required = false) String cmdArgs,
            @RequestParam(required = false) String jdkHome) {
        return releaseService.generateScripts(hostId, projectType, mainEntry, projectPath,
                logPath, appName, envVars, jvmArgs, cmdArgs, jdkHome);
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
        ReturnResult<Void> result = releaseService.deleteProject(id);
        if (result.isOk()) {
            removeSchedule(id);
        }
        return result;
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
     * 在目标主机上检索项目日志（后端检索）。
     *
     * @param id        项目标识
     * @param keyword   关键字（字面匹配，非正则）
     * @param maxLines  最多返回命中行数，缺省 500
     * @param matchCase 是否区分大小写，缺省不区分
     * @return 命中行 + 主机侧耗时
     */
    @Operation(summary = "检索项目日志（后端检索）")
    @GetMapping("/project/{id}/log/search")
    public ReturnResult<com.chua.starter.server.support.model.vo.ProjectLogSearchVO> searchProjectLog(
            @PathVariable Long id,
            @RequestParam String keyword,
            @RequestParam(required = false) Integer maxLines,
            @RequestParam(required = false) Boolean matchCase) {
        return releaseService.searchProjectLog(id, keyword, maxLines, matchCase);
    }

    /**
     * 读取项目「日志读取行数」配置。
     *
     * @param id 项目标识
     * @return 生效行数
     */
    @Operation(summary = "读取日志读取行数")
    @GetMapping("/project/{id}/log/lines")
    public ReturnResult<Integer> getLogLines(@PathVariable Long id) {
        return releaseService.getLogLines(id);
    }

    /**
     * 保存项目「日志读取行数」配置。
     *
     * @param id    项目标识
     * @param lines 行数
     * @return 操作结果
     */
    @Operation(summary = "保存日志读取行数")
    @PostMapping("/project/{id}/log/lines")
    public ReturnResult<Void> updateLogLines(@PathVariable Long id,
                                             @RequestParam Integer lines) {
        return releaseService.updateLogLines(id, lines);
    }

    /**
     * 读取项目健康检测配置。
     *
     * @param id 项目标识
     * @return 已保存地址与建议地址
     */
    @Operation(summary = "读取项目健康检测配置")
    @GetMapping("/project/{id}/health")
    public ReturnResult<java.util.Map<String, Object>> getProjectHealthCheck(@PathVariable Long id) {
        return releaseService.getProjectHealthCheck(id);
    }

    /**
     * 保存项目健康检测地址与预热期判定开关。
     *
     * @param id          项目标识
     * @param url         检测地址（为空时保留原地址）
     * @param warmupCheck 启动预热期是否也按严格规则判定（为空时保留原值）
     * @return 操作结果
     */
    @Operation(summary = "保存项目健康检测地址")
    @PostMapping("/project/{id}/health")
    public ReturnResult<Void> saveProjectHealthCheck(@PathVariable Long id,
                                                     @RequestParam(required = false) String url,
                                                     @RequestParam(required = false) Boolean warmupCheck) {
        return releaseService.saveProjectHealthCheck(id, url, warmupCheck);
    }

    /**
     * 执行项目健康检测（仅 HTTP 200 判定通过）。
     *
     * @param id  项目标识
     * @param url 本次检测地址（可空，空则用已保存配置）
     * @return ok / statusCode / elapsedMs / checkedAt / message
     */
    @Operation(summary = "执行项目健康检测")
    @PostMapping("/project/{id}/health/check")
    public ReturnResult<java.util.Map<String, Object>> checkProjectHealth(
            @PathVariable Long id,
            @RequestParam(required = false) String url) {
        return releaseService.checkProjectHealth(id, url);
    }

    /**
     * 开启项目实时日志。
     *
     * @param id       项目标识
     * @param clientId 发布管理 SSE 客户端标识（实时日志按它定向推送）
     * @return 实时日志会话标识
     */
    @Operation(summary = "开启实时日志（返回sessionId）")
    @PostMapping("/project/{id}/log/start")
    public ReturnResult<String> startProjectLog(@PathVariable Long id,
                                                @RequestParam(required = false) String clientId) {
        return releaseService.startProjectLog(id, clientId);
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

    /**
     * 分页查询项目操作日志。
     *
     * @param projectId 项目标识（可空）
     * @param releaseId 发布标识（可空）
     * @param operation 操作类型（可空）
     * @param status    状态（可空，RUNNING/SUCCESS/FAILED）
     * @param keyword   项目名关键字（可空）
     * @param page      页码
     * @param pageSize  每页条数
     * @return 分页结果
     */
    @Operation(summary = "分页查询项目操作日志")
    @GetMapping("/op-log/page")
    public ReturnResult<com.baomidou.mybatisplus.core.metadata.IPage<
            com.chua.starter.server.support.entity.MsServerProjectOpLog>> opLogPage(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long releaseId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return projectOpLogService.page(projectId, releaseId, operation, status, keyword, page, pageSize);
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

    /**
     * 列出有可用更新的软件。
     *
     * <p>前端据此决定是否显示「升级」按钮：只有出现在该清单里的包才可能真的升级。</p>
     *
     * @param hostId 主机标识
     * @return 可升级条目（当前版本 → 候选版本）
     */
    @Operation(summary = "列出主机可升级软件")
    @GetMapping("/software/upgradable")
    public ReturnResult<List<com.chua.starter.server.support.model.vo.SoftwareUpgradeVO>> listUpgradableSoftware(
            @RequestParam Long hostId) {
        return releaseService.listUpgradableSoftware(hostId);
    }

    /**
     * 查询软件包依赖关系（详情面板按需调用）。
     *
     * @param hostId 主机标识
     * @param name 软件包名
     * @return 依赖包名列表
     */
    @Operation(summary = "查询软件包依赖关系")
    @GetMapping("/software/dependencies")
    public ReturnResult<List<String>> softwareDependencies(
            @RequestParam Long hostId,
            @RequestParam String name) {
        return releaseService.softwareDependencies(hostId, name);
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
     * @param projectId 项目ID；传了则附带该项目的默认升级配置
     * @return 全局、最近一次、项目默认三份配置
     */
    @Operation(summary = "读取升级配置（global 全局 + last 最近一次 + project 项目默认）")
    @GetMapping("/upgrade/config")
    public ReturnResult<java.util.Map<String, Object>> getUpgradeConfig(
            @RequestParam(required = false) Long projectId) {
        return releaseService.getUpgradeConfig(projectId);
    }

    /**
     * 保存升级配置。
     *
     * @param scope     配置作用域
     * @param projectId 项目ID（scope=project 时必填）
     * @param config    升级配置
     * @return 操作结果
     */
    @Operation(summary = "保存升级配置（scope=global/last/project）")
    @PutMapping("/upgrade/config")
    public ReturnResult<Void> saveUpgradeConfig(
            @RequestParam(defaultValue = "global") String scope,
            @RequestParam(required = false) Long projectId,
            @RequestBody java.util.Map<String, Object> config) {
        return releaseService.saveUpgradeConfig(scope, projectId, config);
    }

    /**
     * 集群批量升级：按同一份配置依次升级多个项目。
     *
     * @param body 请求体，含 projectIds 与 config
     * @return 每个项目的触发结果
     */
    @Operation(summary = "集群批量升级（同类型项目一次下发，异步）")
    @PostMapping("/project/batch-upgrade")
    public ReturnResult<java.util.List<java.util.Map<String, Object>>> batchUpgrade(
            @RequestBody java.util.Map<String, Object> body) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        Object raw = body == null ? null : body.get("projectIds");
        if (raw instanceof java.util.Collection<?> cols) {
            for (Object o : cols) {
                if (o != null) {
                    ids.add(Long.valueOf(String.valueOf(o)));
                }
            }
        }
        java.util.Map<String, Object> config = null;
        Object rawCfg = body == null ? null : body.get("config");
        if (rawCfg instanceof java.util.Map<?, ?> map) {
            java.util.Map<String, Object> parsed = new java.util.LinkedHashMap<>();
            map.forEach((k, v) -> parsed.put(String.valueOf(k), v));
            config = parsed;
        }
        return releaseService.batchUpgrade(ids, config);
    }

    /**
     * 集群批量回滚：按同一份配置依次回滚多个项目。
     *
     * @param body 请求体，含 projectIds 与可选的 backup
     * @return 每个项目的触发结果
     */
    @Operation(summary = "集群批量回滚（多项目一次下发，异步）")
    @PostMapping("/project/batch-rollback")
    public ReturnResult<java.util.List<java.util.Map<String, Object>>> batchRollback(
            @RequestBody java.util.Map<String, Object> body) {
        java.util.List<Long> ids = toLongList(body == null ? null : body.get("projectIds"));
        Object raw = body == null ? null : body.get("backup");
        String backup = raw == null ? null : String.valueOf(raw).trim();
        return releaseService.batchRollback(ids, backup == null || backup.isEmpty() ? null : backup);
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
     * 用主机文件里的产物升级：文件在运行本服务的这台机器上，后端负责推到目标主机。
     *
     * @param id 项目标识
     * @param body 主机文件 key 与升级配置
     * @return 异步任务标识
     */
    @Operation(summary = "从主机文件选产物升级（异步，返回taskId）")
    @PostMapping("/project/{id}/upgrade/from-host-file")
    public ReturnResult<String> upgradeFromHostFile(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Object> body) {
        Object key = body == null ? null : body.get("key");
        java.util.Map<String, Object> config = body == null
                ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(body);
        config.remove("key");
        return releaseService.upgradeFromHostFile(id, key == null ? null : String.valueOf(key), config);
    }

    /**
     * 把主机文件里的产物投放到目标主机的 uploads/（只投放，不升级）。
     *
     * @param id  项目标识
     * @param key 主机文件 key
     * @return 目标机最终包路径
     */
    @Operation(summary = "把主机文件产物投放到项目主机 uploads（不升级）")
    @PostMapping("/project/{id}/push/from-host-file")
    public ReturnResult<String> pushFromHostFile(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Object> body) {
        Object key = body == null ? null : body.get("key");
        return releaseService.pushHostFileToProject(id, key == null ? null : String.valueOf(key));
    }

    /**
     * SkyWalking OAP 可达性探测（右下角状态灯）。
     *
     * @param releaseId 发布标识
     * @return enabled / reachable / url / elapsedMs
     */
    @Operation(summary = "SkyWalking OAP 可达性探测")
    @GetMapping("/apm/skywalking/health")
    public ReturnResult<java.util.Map<String, Object>> skywalkingHealth(
            @RequestParam(name = "releaseId", required = false) Long releaseId) {
        return releaseService.skywalkingHealth(releaseId);
    }

    /**
     * 统计分离式项目 lib 目录中的 jar 文件数量。
     *
     * @param id 项目标识
     * @return jar 文件数量
     */
    @Operation(summary = "统计分离式项目依赖 jar 数量")
    @GetMapping("/project/{id}/lib/count")
    public ReturnResult<Integer> countLibFiles(@PathVariable Long id) {
        return releaseService.countLibFiles(id);
    }

    /**
     * Git 自动发布：拉取项目配置的 Git 仓库 → Maven 构建 → 上传 → 升级重启。
     *
     * @param id 项目标识
     * @return 异步任务标识
     */
    @Operation(summary = "Git 自动发布：拉取代码→构建→上传→升级（异步，返回taskId）")
    @PostMapping("/project/{id}/git/deploy")
    public ReturnResult<String> gitPullAndDeploy(@PathVariable Long id) {
        return releaseService.gitPullAndDeploy(id);
    }

    /**
     * 查看项目 Git 仓库（已拉取的本地工作区）。
     *
     * @param id 项目标识
     * @return 仓库概览：分支 / 提交 / 改动 / 分支列表
     */
    @Operation(summary = "查看项目 Git 仓库（分支/提交/工作区改动）")
    @GetMapping("/project/{id}/git/repository")
    public ReturnResult<java.util.Map<String, Object>> gitRepository(@PathVariable Long id) {
        return releaseService.gitRepository(id);
    }

    /**
     * 拉取项目 Git 仓库（只同步本地工作区，不构建、不发布）。
     *
     * @param id     项目标识
     * @param branch 目标分支（可空：用项目配置的分支）
     * @return 拉取结果
     */
    @Operation(summary = "拉取项目 Git 仓库（仅同步，不发布）")
    @PostMapping("/project/{id}/git/pull")
    public ReturnResult<java.util.Map<String, Object>> gitPull(
            @PathVariable Long id,
            @RequestParam(required = false) String branch) {
        return releaseService.gitPull(id, branch);
    }

    // ==================== 发布管理增强 ====================

    /**
     * 复制项目到其他服务器（可多选源项目与目标发布）。
     *
     * @param body 请求体：projectIds 源项目、releaseIds 目标发布
     * @return 复制结果
     */
    @Operation(summary = "复制项目到其他服务器（仅替换发布ID）")
    @PostMapping("/project/copy")
    public ReturnResult<java.util.Map<String, Object>> copyProjects(@RequestBody java.util.Map<String, Object> body) {
        java.util.List<Long> projectIds = toLongList(body == null ? null : body.get("projectIds"));
        java.util.List<Long> releaseIds = toLongList(body == null ? null : body.get("releaseIds"));
        ReturnResult<java.util.Map<String, Object>> result = releaseService.copyProjects(projectIds, releaseIds);
        if (result.isOk() && result.getData() != null) {
            Object copied = result.getData().get("copied");
            if (copied instanceof java.util.Collection<?> rows) {
                for (Object row : rows) {
                    if (row instanceof java.util.Map<?, ?> map && map.get("projectId") != null) {
                        syncSchedule(Long.valueOf(String.valueOf(map.get("projectId"))));
                    }
                }
            }
        }
        return result;
    }

    /**
     * 解析请求体中的 Long 列表（兼容数字与字符串）。
     * @param raw 对象
     * @return 返回值
    */
    private java.util.List<Long> toLongList(Object raw) {
        java.util.List<Long> out = new java.util.ArrayList<>();
        if (raw instanceof java.util.Collection<?> cols) {
            for (Object o : cols) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    out.add(Long.valueOf(String.valueOf(o)));
                }
            }
        }
        return out;
    }

    /**
     * 探测项目的 Swagger / Actuator 端点。
     *
     * @param id 项目标识
     * @return 探测结果（swagger/actuator 布尔值、访问地址、健康状态、指标数量）
     */
    @Operation(summary = "探测 Swagger / Actuator 端点")
    @GetMapping("/project/{id}/endpoints")
    public ReturnResult<java.util.Map<String, Object>> probeProjectEndpoints(@PathVariable Long id) {
        return releaseService.probeProjectEndpoints(id);
    }

    /**
     * 读取项目的 Actuator 定时采集配置（全局时间轮采集，经发布管理 SSE 推送）。
     *
     * @param id 项目标识
     * @return enabled / intervalSeconds
     */
    @Operation(summary = "读取 Actuator 定时采集配置")
    @GetMapping("/project/{id}/endpoints/monitor")
    public ReturnResult<java.util.Map<String, Object>> getActuatorMonitor(@PathVariable Long id) {
        return releaseService.getActuatorMonitor(id);
    }

    /**
     * 保存项目的 Actuator 定时采集配置（可关闭）。
     *
     * @param id     项目标识
     * @param config enabled / intervalSeconds
     * @return 保存结果
     */
    @Operation(summary = "保存 Actuator 定时采集配置")
    @PutMapping("/project/{id}/endpoints/monitor")
    public ReturnResult<Void> saveActuatorMonitor(@PathVariable Long id,
                                                  @RequestBody java.util.Map<String, Object> config) {
        return releaseService.saveActuatorMonitor(id, config);
    }

    /**
     * 读取项目的一个 Actuator 端点（在目标主机内请求）。
     *
     * @param id   项目标识
     * @param path 端点路径，如 metrics / metrics/jvm.memory.used / loggers
     * @return endpoint / url / status / body / elapsedMs
     */
    @Operation(summary = "读取 Actuator 端点")
    @GetMapping("/project/{id}/actuator")
    public ReturnResult<java.util.Map<String, Object>> actuatorFetch(
            @PathVariable Long id,
            @RequestParam(name = "path", required = false) String path) {
        return releaseService.actuatorFetch(id, path);
    }

    /**
     * 热更新日志级别（Actuator loggers，不用重启）。
     *
     * @param id    项目标识
     * @param body  name / level
     * @return ok / status / logger / level
     */
    @Operation(summary = "热更新日志级别")
    @PostMapping("/project/{id}/actuator/log-level")
    public ReturnResult<java.util.Map<String, Object>> actuatorSetLogLevel(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Object> body) {
        Object name = body == null ? null : body.get("name");
        Object level = body == null ? null : body.get("level");
        return releaseService.actuatorSetLogLevel(id,
                name == null ? null : String.valueOf(name),
                level == null ? null : String.valueOf(level));
    }

    /**
     * 手动触发一次 Git 轮询：有更新才发布。
     *
     * @param id 项目标识
     * @return changed / commit / taskId
     */
    @Operation(summary = "Git 轮询检查（有更新自动发布）")
    @PostMapping("/project/{id}/git/poll")
    public ReturnResult<java.util.Map<String, Object>> gitPollDeploy(@PathVariable Long id) {
        return releaseService.gitPollDeploy(id);
    }

    /**
     * 查询项目定时任务运行态（下一次/上一次触发时间）。
     *
     * @param id 项目标识
     * @return 运行态
     */
    @Operation(summary = "查询项目定时任务运行态")
    @GetMapping("/project/{id}/schedule")
    public ReturnResult<java.util.Map<String, Object>> scheduleStatus(@PathVariable Long id) {
        ReleaseScheduleService service = releaseScheduleServiceProvider.getIfAvailable();
        java.util.Map<String, Object> out = service == null
                ? new java.util.LinkedHashMap<>() : service.status(id);
        return ReturnResult.ok(out);
    }

    /**
     * 读取页面布局。
     *
     * @param pageKey 页面标识
     * @return 布局 JSON
     */
    @Operation(summary = "读取页面布局")
    @GetMapping("/layout/{pageKey}")
    public ReturnResult<java.util.Map<String, Object>> getPageLayout(@PathVariable String pageKey) {
        return releaseService.getPageLayout(pageKey);
    }

    /**
     * 保存页面布局。
     *
     * <p>布局是前端整棵树的原始 JSON 文本，这里直接读请求体原样落库：
     * 若用 {@code @RequestBody String}，{@code application/json} 会交给 Jackson
     * 反序列化成 String 而对 JSON 对象报错（C0400S0002）。</p>
     *
     * @param pageKey 页面标识
     * @param request 请求对象（取原始请求体）
     * @return 操作结果
     */
    @Operation(summary = "保存页面布局")
    @PutMapping("/layout/{pageKey}")
    public ReturnResult<Void> savePageLayout(@PathVariable String pageKey,
                                             HttpServletRequest request) throws IOException {
        String layout = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return releaseService.savePageLayout(pageKey, layout);
    }
}
