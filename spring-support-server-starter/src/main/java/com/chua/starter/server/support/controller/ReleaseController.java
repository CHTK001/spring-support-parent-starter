package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerProject;
import com.chua.starter.server.support.entity.MsServerRelease;
import com.chua.starter.server.support.entity.SoftwarePackage;
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
 */
@Tag(name = "发布管理")
@RestController
@RequestMapping("/ms/server/release")
@RequiredArgsConstructor
public class ReleaseController {

    private final ReleaseService releaseService;
    private final ProjectTaskService projectTaskService;

    // ==================== 发布管理 ====================

    @Operation(summary = "获取发布列表")
    @GetMapping("/list")
    public ReturnResult<List<MsServerRelease>> listReleases() {
        return releaseService.listReleases();
    }

    @Operation(summary = "创建发布")
    @PostMapping
    public ReturnResult<MsServerRelease> createRelease(@RequestBody MsServerRelease release) {
        return releaseService.createRelease(release);
    }

    @Operation(summary = "更新发布")
    @PutMapping("/{id}")
    public ReturnResult<MsServerRelease> updateRelease(@PathVariable Long id, @RequestBody MsServerRelease release) {
        return releaseService.updateRelease(id, release);
    }

    @Operation(summary = "删除发布")
    @DeleteMapping("/{id}")
    public ReturnResult<Void> deleteRelease(@PathVariable Long id) {
        return releaseService.deleteRelease(id);
    }

    // ==================== 项目管理 ====================

    @Operation(summary = "获取项目列表")
    @GetMapping("/{releaseId}/projects")
    public ReturnResult<List<MsServerProject>> listProjects(@PathVariable Long releaseId) {
        return releaseService.listProjects(releaseId);
    }

    @Operation(summary = "创建项目")
    @PostMapping("/project")
    public ReturnResult<MsServerProject> createProject(@RequestBody MsServerProject project) {
        return releaseService.createProject(project);
    }

    @Operation(summary = "更新项目")
    @PutMapping("/project/{id}")
    public ReturnResult<MsServerProject> updateProject(@PathVariable Long id, @RequestBody MsServerProject project) {
        return releaseService.updateProject(id, project);
    }

    @Operation(summary = "删除项目")
    @DeleteMapping("/project/{id}")
    public ReturnResult<Void> deleteProject(@PathVariable Long id) {
        return releaseService.deleteProject(id);
    }

    // ==================== 项目操作 ====================

    @Operation(summary = "启动项目（异步，返回taskId）")
    @PostMapping("/project/{id}/start")
    public ReturnResult<String> startProject(@PathVariable Long id) {
        return releaseService.startProject(id);
    }

    @Operation(summary = "停止项目（异步，返回taskId）")
    @PostMapping("/project/{id}/stop")
    public ReturnResult<String> stopProject(@PathVariable Long id) {
        return releaseService.stopProject(id);
    }

    @Operation(summary = "重启项目（异步，返回taskId）")
    @PostMapping("/project/{id}/restart")
    public ReturnResult<String> restartProject(@PathVariable Long id) {
        return releaseService.restartProject(id);
    }

    @Operation(summary = "升级项目（异步，返回taskId）")
    @PostMapping("/project/{id}/upgrade")
    public ReturnResult<String> upgradeProject(@PathVariable Long id) {
        return releaseService.upgradeProject(id);
    }

    @Operation(summary = "备份项目（异步，返回taskId）")
    @PostMapping("/project/{id}/backup")
    public ReturnResult<String> backupProject(@PathVariable Long id) {
        return releaseService.backupProject(id);
    }

    @Operation(summary = "回滚项目（异步，返回taskId）")
    @PostMapping("/project/{id}/rollback")
    public ReturnResult<String> rollbackProject(@PathVariable Long id) {
        return releaseService.rollbackProject(id);
    }

    @Operation(summary = "获取项目日志（一次性tail）")
    @GetMapping("/project/{id}/log")
    public ReturnResult<String> getProjectLog(@PathVariable Long id) {
        return releaseService.getProjectLog(id);
    }

    @Operation(summary = "开启实时日志（返回sessionId）")
    @PostMapping("/project/{id}/log/start")
    public ReturnResult<String> startProjectLog(@PathVariable Long id) {
        return releaseService.startProjectLog(id);
    }

    @Operation(summary = "停止实时日志")
    @PostMapping("/project/log/{sessionId}/stop")
    public ReturnResult<Void> stopProjectLog(@PathVariable String sessionId) {
        return releaseService.stopProjectLog(sessionId);
    }

    @Operation(summary = "查询任务快照（断点续传）")
    @GetMapping("/task/{taskId}")
    public ReturnResult<java.util.Map<String, Object>> getTaskSnapshot(@PathVariable String taskId) {
        return projectTaskService.getSnapshot(taskId);
    }

    // ==================== 软件仓库 ====================

    @Operation(summary = "搜索软件包")
    @GetMapping("/software/search")
    public ReturnResult<List<SoftwarePackage>> searchSoftware(
            @RequestParam Long hostId,
            @RequestParam String keyword) {
        return releaseService.searchSoftware(hostId, keyword);
    }

    @Operation(summary = "安装软件（异步，返回taskId）")
    @PostMapping("/software/install")
    public ReturnResult<String> installSoftware(
            @RequestParam Long hostId,
            @RequestParam String name) {
        return releaseService.installSoftware(hostId, name);
    }
}
