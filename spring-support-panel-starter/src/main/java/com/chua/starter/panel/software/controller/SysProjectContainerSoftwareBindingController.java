package com.chua.starter.panel.software.controller;

import com.chua.common.support.utils.group.AddGroup;
import com.chua.common.support.utils.group.UpdateGroup;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.chua.starter.oauth.client.support.annotation.UserValue;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import com.chua.starter.panel.software.entity.SysProjectContainerSoftwareBinding;
import com.chua.starter.panel.software.model.ProjectContainerSoftwareOperationTicket;
import com.chua.starter.panel.software.pojo.ProjectContainerSoftwareImportPresetRequest;
import com.chua.starter.panel.software.pojo.ProjectContainerSoftwareOperationRequest;
import com.chua.starter.panel.software.pojo.ProjectContainerSoftwareReconfigureRequest;
import com.chua.starter.panel.software.pojo.ProjectContainerSoftwareTopologyOperationRequest;
import com.chua.starter.panel.software.pojo.ProjectContainerSoftwareBatchOperationRequest;
import com.chua.starter.panel.software.pojo.ProjectContainerSoftwareBatchTarget;
import com.chua.starter.panel.software.service.SysProjectContainerSoftwareBindingService;
import com.chua.starter.panel.software.service.SysProjectContainerSoftwareLogService;
import com.chua.starter.panel.software.service.SysProjectContainerSoftwareOperationService;
import com.chua.starter.panel.software.service.impl.DockerLogStreamService;
import com.chua.starter.panel.software.service.impl.DockerStatsStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Tag(name = "项目容器软件关联接口")
@RestController
@RequestMapping("/v2/project/container/software/binding")
public class SysProjectContainerSoftwareBindingController {

    private final SysProjectContainerSoftwareBindingService bindingService;
    private final SysProjectContainerSoftwareOperationService operationService;
    private final SysProjectContainerSoftwareLogService logService;
    private final DockerLogStreamService dockerLogStreamService;
    private final DockerStatsStreamService dockerStatsStreamService;

    public SysProjectContainerSoftwareBindingController(
            SysProjectContainerSoftwareBindingService bindingService,
            SysProjectContainerSoftwareOperationService operationService,
            SysProjectContainerSoftwareLogService logService,
            DockerLogStreamService dockerLogStreamService,
            DockerStatsStreamService dockerStatsStreamService) {
        this.bindingService = bindingService;
        this.operationService = operationService;
        this.logService = logService;
        this.dockerLogStreamService = dockerLogStreamService;
        this.dockerStatsStreamService = dockerStatsStreamService;
    }

    @Operation(summary = "项目容器软件关联分页")
    @GetMapping("/page")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnPageResult<SysProjectContainerSoftwareBinding> page(@ParameterObject Query<SysProjectContainerSoftwareBinding> query,
                                                                     @ParameterObject SysProjectContainerSoftwareBinding request,
                                                                     @Parameter(hidden = true) @UserValue("roles") Set<String> roles) {
        return PageResultUtils.ok(bindingService.pageFor(query, request, roles));
    }

    @Operation(summary = "按项目获取容器软件关联列表")
    @GetMapping("/project/list")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<List<SysProjectContainerSoftwareBinding>> listByProjectId(Integer sysProjectId) {
        if (null == sysProjectId) {
            return ReturnResult.illegal("项目ID不能为空");
        }
        return ReturnResult.ok(bindingService.listByProjectId(sysProjectId));
    }

    @Operation(summary = "从预设导入项目容器软件")
    @PostMapping("/importPreset")
    @Permission(value = "sys:project:update", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<SysProjectContainerSoftwareBinding> importPreset(@RequestBody ProjectContainerSoftwareImportPresetRequest request) {
        return bindingService.importFromPreset(request);
    }

    @Operation(summary = "新增自定义项目容器软件")
    @PostMapping("/save")
    @Permission(value = "sys:project:update", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<SysProjectContainerSoftwareBinding> save(@Parameter(hidden = true) @UserValue("roles") Set<String> roles,
                                                                 @Validated(AddGroup.class) @RequestBody SysProjectContainerSoftwareBinding request,
                                                                 BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return bindingService.save(request, roles);
    }

    @Operation(summary = "更新项目容器软件关联")
    @PutMapping("/update")
    @Permission(value = "sys:project:update", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Boolean> update(@Parameter(hidden = true) @UserValue("roles") Set<String> roles,
                                        @Validated(UpdateGroup.class) @RequestBody SysProjectContainerSoftwareBinding request,
                                        BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return bindingService.update(roles, request);
    }

    @Operation(summary = "删除项目容器软件关联")
    @DeleteMapping("/delete")
    @Transactional(rollbackFor = Exception.class)
    @Permission(value = "sys:project:update", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Boolean> delete(@RequestParam(value = "sysProjectContainerSoftwareBindingId", required = false) Integer id1,
                                        @RequestParam(value = "bindingId", required = false) Integer id2,
                                        @RequestParam(required = false) String connectionId) {
        Integer sysProjectContainerSoftwareBindingId = id1 != null ? id1 : id2;
        if (null == sysProjectContainerSoftwareBindingId) {
            return ReturnResult.illegal("项目容器软件关联不存在");
        }
        return bindingService.delete(sysProjectContainerSoftwareBindingId, connectionId);
    }

    @Operation(summary = "提交项目容器软件操作")
    @PostMapping("/operate")
    @Permission(value = "sys:project:update", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<ProjectContainerSoftwareOperationTicket> operate(@RequestBody Map<String, Object> request) {
        ProjectContainerSoftwareOperationRequest req = new ProjectContainerSoftwareOperationRequest();
        req.setSysProjectContainerSoftwareBindingId(getBindingId(request));
        req.setAction(getString(request, "action"));
        req.setParams(getString(request, "params"));
        req.setConnectionId(getString(request, "connectionId"));
        return operationService.submit(req);
    }

    @Operation(summary = "提交项目容器服务拓扑操作")
    @PostMapping("/topology/operate")
    @Permission(value = "sys:project:update", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<ProjectContainerSoftwareOperationTicket> operateTopology(@RequestBody ProjectContainerSoftwareTopologyOperationRequest request) {
        return operationService.submitTopology(request);
    }

    @Operation(summary = "重配置")
    @PostMapping("/reconfigure")
    @Permission(value = "sys:project:update", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Map<String, Object>> reconfigure(@RequestBody Map<String, Object> request) {
        Integer bindingId = getBindingId(request);
        @SuppressWarnings("unchecked")
        Map<String, Object> configValues = request.get("configValues") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : null;
        return bindingService.reconfigure(bindingId, configValues);
    }

    @Operation(summary = "检查安装条件")
    @PostMapping("/check-requirements")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Map<String, Object>> checkRequirements(@RequestBody Map<String, Object> request) {
        return bindingService.checkRequirements(request);
    }

    @Operation(summary = "AI 分析安装失败")
    @PostMapping("/operation/ai-analyze")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Map<String, Object>> aiAnalyzeFailure(@RequestBody Map<String, Object> request) {
        return operationService.aiAnalyzeFailure(request);
    }

    @Operation(summary = "检测服务器 Docker 可用性")
    @PostMapping("/check-docker")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Map<String, Object>> checkDocker(@RequestBody Map<String, Object> request) {
        return bindingService.checkDocker(request);
    }

    @Operation(summary = "获取运行日志（Docker 容器实时日志）")
    @GetMapping("/runtime/logs")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<List<String>> fetchRuntimeLogs(Integer bindingId, Integer tailLines,
                                                       @RequestParam(required = false) String connectionId) {
        return bindingService.fetchRuntimeLogs(bindingId, tailLines, connectionId);
    }

    @Operation(summary = "获取容器详情（端口、卷、环境变量）")
    @GetMapping("/container-info")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Map<String, Object>> getContainerInfo(Integer bindingId,
                                                               @RequestParam(required = false) String connectionId) {
        return bindingService.fetchContainerInfo(bindingId, connectionId);
    }

    @Operation(summary = "Docker 容器内执行命令")
    @PostMapping("/docker-exec")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Map<String, Object>> dockerExec(@RequestBody Map<String, Object> request) {
        Integer bindingId = getBindingId(request);
        String command = getString(request, "command");
        String connectionId = getString(request, "connectionId");
        return bindingService.dockerExec(bindingId, command, connectionId);
    }

    @Operation(summary = "获取容器实时统计信息（CPU/内存/网络）")
    @GetMapping("/stats")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Map<String, Object>> getContainerStats(Integer bindingId,
                                                                @RequestParam(required = false) String connectionId) {
        return bindingService.getContainerStats(bindingId, connectionId);
    }

    @Operation(summary = "开始实时推送容器运行日志")
    @PostMapping("/log-stream/start")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Void> startLogStream(@RequestBody Map<String, Object> request) {
        Integer bindingId = getBindingId(request);
        String connectionId = getString(request, "connectionId");
        String containerName = getString(request, "containerName");
        if (bindingId == null || connectionId == null || containerName == null) {
            return ReturnResult.illegal("参数不完整: 需要 bindingId, connectionId, containerName");
        }
        dockerLogStreamService.startStream(connectionId, containerName, bindingId);
        return ReturnResult.ok();
    }

    @Operation(summary = "停止实时推送容器运行日志")
    @PostMapping("/log-stream/stop")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Void> stopLogStream(@RequestBody Map<String, Object> request) {
        Integer bindingId = getBindingId(request);
        if (bindingId == null) {
            return ReturnResult.illegal("bindingId 不能为空");
        }
        dockerLogStreamService.stopStream(bindingId);
        return ReturnResult.ok();
    }

    @Operation(summary = "开始实时推送容器性能统计")
    @PostMapping("/stats/start")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Void> startStatsStream(@RequestBody Map<String, Object> request) {
        Integer bindingId = getBindingId(request);
        String connectionId = getString(request, "connectionId");
        String containerName = getString(request, "containerName");
        if (bindingId == null || connectionId == null || containerName == null) {
            return ReturnResult.illegal("参数不完整: 需要 bindingId, connectionId, containerName");
        }
        dockerStatsStreamService.startStream(connectionId, containerName, bindingId);
        return ReturnResult.ok();
    }

    @Operation(summary = "停止实时推送容器性能统计")
    @PostMapping("/stats/stop")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Void> stopStatsStream(@RequestBody Map<String, Object> request) {
        Integer bindingId = getBindingId(request);
        if (bindingId == null) {
            return ReturnResult.illegal("bindingId 不能为空");
        }
        dockerStatsStreamService.stopStream(bindingId);
        return ReturnResult.ok();
    }

    @Operation(summary = "获取操作日志")
    @GetMapping("/operation/logs")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<List<String>> getOperationLogs(Integer bindingId, String action, String operationId) {
        return logService.getLogs(bindingId, action, operationId);
    }

    @Operation(summary = "同步所有 Docker 容器状态（检查业务表 vs 实际容器）")
    @PostMapping("/sync-container-status")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<SysProjectContainerSoftwareBindingService.SyncSummary> syncContainerStatus(
            @RequestBody Map<String, Object> request) {
        String connectionId = getString(request, "connectionId");
        return bindingService.syncContainerStatus(connectionId);
    }

    @Operation(summary = "批量同步多台服务器的 Docker 容器状态")
    @PostMapping("/sync-container-status-multi")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<List<SysProjectContainerSoftwareBindingService.SyncSummary>> syncContainerStatusMulti(
            @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> connectionIds = request.get("connectionIds") instanceof List<?> list
                ? list.stream().map(Object::toString).toList() : List.of();
        return bindingService.syncContainerStatusMulti(connectionIds);
    }

    @Operation(summary = "批量操作 - 在多台服务器上安装/启动/停止同一软件")
    @PostMapping("/batch-operate")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<List<Map<String, Object>>> batchOperate(
            @RequestBody ProjectContainerSoftwareBatchOperationRequest request) {
        return bindingService.batchOperate(request);
    }

    @Operation(summary = "同步单个 Docker 容器状态")
    @PostMapping("/sync-container-status/{bindingId}")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Map<String, Object>> syncSingleContainerStatus(
            @PathVariable Integer bindingId,
            @RequestBody Map<String, Object> request) {
        String connectionId = getString(request, "connectionId");
        return bindingService.syncSingleContainerStatus(bindingId, connectionId);
    }

    // ─── Helper methods ─────────────────────────────────────────

    /**
     * 从 Map 中提取 bindingId，同时支持短名 "bindingId" 和长名 "sysProjectContainerSoftwareBindingId"
     */
    private static Integer getBindingId(Map<String, Object> map) {
        Integer id = getInt(map, "bindingId");
        if (id != null) return id;
        return getInt(map, "sysProjectContainerSoftwareBindingId");
    }

    private static Integer getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String s && !s.isBlank() ? s : null;
    }
}
