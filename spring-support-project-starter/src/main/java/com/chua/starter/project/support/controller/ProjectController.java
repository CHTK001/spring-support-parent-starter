package com.chua.starter.project.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.project.support.constants.ProjectConstants;
import com.chua.starter.project.support.entity.MsProject;
import com.chua.starter.project.support.entity.MsProjectDeployLog;
import com.chua.starter.project.support.entity.MsProjectUploadArchive;
import com.chua.starter.project.support.service.DeployService;
import com.chua.starter.project.support.service.ProjectAiScriptService;
import com.chua.starter.project.support.service.ProjectMetricService;
import com.chua.starter.project.support.service.ProjectService;
import com.chua.starter.project.support.service.WatchdogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
* 发布项目管理接口
* <p>
* 提供部署项目台账 CRUD、可选服务器查询与部署/升级/启停操作。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Tag(name = "发布项目管理", description = "部署项目台账、部署/升级/启停与部署日志")
@RestController
@RequestMapping(ProjectConstants.API_PREFIX)
@RequiredArgsConstructor
public class ProjectController {

    /** 项目服务 */
    private final ProjectService projectService;

    /** 部署操作服务 */
    private final DeployService deployService;

    /** AI 脚本生成服务 */
    private final ProjectAiScriptService aiScriptService;

    /** 守护进程服务 */
    private final WatchdogService watchdogService;

    /** 指标采集服务 */
    private final ProjectMetricService metricService;

    /**
    * 新增项目
    *
    * @param project 项目
    * @return 结果
     */
    @Operation(summary = "新增项目")
    @PostMapping
    public ReturnResult<MsProject> create(@RequestBody MsProject project) {
        return projectService.create(project);
    }

    /**
    * 更新项目
    *
    * @param id      主键
    * @param project 项目
    * @return 结果
     */
    @Operation(summary = "更新项目")
    @PutMapping("/{id}")
    public ReturnResult<MsProject> update(@PathVariable Long id, @RequestBody MsProject project) {
        return projectService.update(id, project);
    }

    /**
    * 删除项目
    *
    * @param id 主键
    * @return 结果
     */
    @Operation(summary = "删除项目")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable Long id) {
        return projectService.delete(id);
    }

    /**
    * 项目详情
    *
    * @param id 主键
    * @return 结果
     */
    @Operation(summary = "项目详情")
    @GetMapping("/{id}")
    public ReturnResult<MsProject> get(@PathVariable Long id) {
        return projectService.get(id);
    }

    /**
    * 项目列表（卡片展示）
    *
    * @param keyword 关键字（名称/编码）
    * @param type    项目类型
    * @return 结果
    * @param false false
     */
    @Operation(summary = "项目列表")
    @GetMapping
    public ReturnResult<List<MsProject>> list(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String type) {
        return projectService.query(keyword, type);
    }

    /**
    * 项目列表（POST 兼容）
    *
    * @param body keyword / 类型
    * @return 结果
    * @param false false
     */
    @Operation(summary = "项目列表(条件查询)")
    @PostMapping("/query")
    public ReturnResult<List<MsProject>> query(@RequestBody(required = false) Map<String, Object> body) {
        String keyword = body == null ? null : asString(body.get("keyword"));
        String type = body == null ? null : asString(body.get("type"));
        return projectService.query(keyword, type);
    }

    /**
    * 更新启用状态
    *
    * @param id      主键
    * @param enabled 是否启用
    * @return 结果
     */
    @Operation(summary = "更新启用状态")
    @PutMapping("/{id}/status")
    public ReturnResult<Boolean> updateStatus(@PathVariable Long id,
                                             @RequestParam(defaultValue = "true") Boolean enabled) {
        return projectService.updateEnabled(id, enabled);
    }

    /**
    * 可选服务器列表（按 OS 过滤）
    *
    * @param os 操作系统类型 Linux/窗口
    * @return 结果
    * @param false false
     */
    @Operation(summary = "可选服务器列表")
    @GetMapping("/host-options")
    public ReturnResult<List<Map<String, Object>>> hostOptions(@RequestParam(required = false) String os) {
        return projectService.hostOptions(os);
    }

    /**
    * 部署/升级项目
    *
    * @param id     项目 标识
    * @param action 操作类型 DEPLOY/UPGRADE
    * @param file   部署包
    * @return 每台主机的部署日志
     */
    @Operation(summary = "部署/升级项目")
    @PostMapping("/{id}/deploy")
    public ReturnResult<List<MsProjectDeployLog>> deploy(@PathVariable Long id,
                                                         @RequestParam(defaultValue = "DEPLOY") String action,
                                                         @RequestParam("file") MultipartFile file) {
        return deployService.deploy(id, action, file);
    }

    /**
    * 启动项目
    *
    * @param id 项目 标识
    * @return 每台主机的执行日志
     */
    @Operation(summary = "启动项目")
    @PostMapping("/{id}/start")
    public ReturnResult<List<MsProjectDeployLog>> start(@PathVariable Long id) {
        return deployService.start(id);
    }

    /**
    * 停止项目
    *
    * @param id 项目 标识
    * @return 每台主机的执行日志
     */
    @Operation(summary = "停止项目")
    @PostMapping("/{id}/stop")
    public ReturnResult<List<MsProjectDeployLog>> stop(@PathVariable Long id) {
        return deployService.stop(id);
    }

    /**
    * 重启项目
    *
    * @param id 项目 标识
    * @return 每台主机的执行日志
     */
    @Operation(summary = "重启项目")
    @PostMapping("/{id}/restart")
    public ReturnResult<List<MsProjectDeployLog>> restart(@PathVariable Long id) {
        return deployService.restart(id);
    }

    /**
    * 部署日志列表
    *
    * @param id     项目 标识
    * @param action 操作类型过滤
    * @return 日志列表
     */
    @Operation(summary = "部署日志列表")
    @GetMapping("/{id}/logs")
    public ReturnResult<List<MsProjectDeployLog>> logs(@PathVariable Long id,
                                                       @RequestParam(required = false) String action) {
        return deployService.listLogs(id, action);
    }

    /**
    * 部署包归档列表（资产维度历史版本）
    *
    * @param id     项目 标识
    * @param hostId 主机 标识，可为空
    * @return 归档列表
     */
    @Operation(summary = "部署包归档列表")
    @GetMapping("/{id}/archives")
    public ReturnResult<List<MsProjectUploadArchive>> archives(@PathVariable Long id,
                                                               @RequestParam(required = false) Long hostId) {
        return deployService.listArchives(id, hostId);
    }

    /**
    * 回滚到指定历史版本
    *
    * @param id        项目 标识
    * @param archiveId 归档记录 标识
    * @return 每台主机的部署日志
     */
    @Operation(summary = "回滚到历史版本")
    @PostMapping("/{id}/rollback")
    public ReturnResult<List<MsProjectDeployLog>> rollback(@PathVariable Long id,
                                                           @RequestParam Long archiveId) {
        return deployService.rollback(id, archiveId);
    }

    /**
     * 实时查看项目日志（tail）
     *
     * @param id     项目 标识
     * @param hostId 目标主机 标识
     * @param cursor 上次读取字节偏移，可为空
     * @param lines  拉取行数，默认 500
     * @return 日志内容
     */
    @Operation(summary = "实时查看项目日志")
    @GetMapping("/{id}/log")
    public ReturnResult<Map<String, Object>> tailLog(@PathVariable Long id,
                                                     @RequestParam Long hostId,
                                                     @RequestParam(required = false) Long cursor,
                                                     @RequestParam(required = false) Integer lines) {
        return deployService.tailLog(id, hostId, cursor, lines);
    }

    /**
     * AI 生成运维脚本（启动/停止/重启/状态检测）
     * <p>
     * 依据项目上下文（类型/目录/OS/健康目标）调用 LLM 生成脚本，
     * 供前端表单一键填充；宿主未装配 AI 时返回降级提示。
     * </p>
     *
     * @param id   项目 标识（传 0 表示用请求体内项目上下文）
     * @param kind 脚本类型 START/STOP/RESTART/CHECK
     * @param body 项目上下文（表单未保存时直接传字段）
     * @return 生成的脚本
     */
    @Operation(summary = "AI 生成运维脚本")
    @PostMapping("/{id}/ai-gen-script")
    public ReturnResult<String> aiGenScript(@PathVariable Long id,
                                            @RequestParam(defaultValue = "START") String kind,
                                            @RequestBody(required = false) MsProject body) {
        MsProject context = body;
        if (context == null || (context.getMsProjectType() == null && context.getMsProjectDeployDir() == null)) {
            context = id == null || id == 0L ? new MsProject() : projectService.get(id).getData();
        }
        if (context == null) {
            return ReturnResult.error("项目上下文不能为空: " + id);
        }
        return aiScriptService.generate(context, kind);
    }

    /**
     * 守护进程状态（连续失败计数/最近检测时间/累计拉起次数）
     *
     * @param id 项目 标识
     * @return 守护状态快照
     */
    @Operation(summary = "守护进程状态")
    @GetMapping("/{id}/watchdog/status")
    public ReturnResult<Map<String, Object>> watchdogStatus(@PathVariable Long id) {
        return ReturnResult.ok(watchdogService.statusOf(id));
    }

    /**
     * 项目指标时间序列（历史查询，供前端图表渲染）
     *
     * @param id         项目 标识
     * @param metricName 指标名，可为空查全部
     * @param limit      上限，默认 200
     * @return 指标点列表（新→旧）
     */
    @Operation(summary = "项目指标时间序列")
    @GetMapping("/{id}/metrics")
    public ReturnResult<List<Map<String, Object>>> metrics(
            @PathVariable Long id,
            @RequestParam(required = false) String metricName,
            @RequestParam(defaultValue = "200") int limit) {
        return ReturnResult.ok(metricService.queryHistory(id, metricName, limit));
    }

    /**
    * 安全转换字符串
    *
    * @param value 原始值
    * @return 字符串或 空
     */
    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }
}