package com.chua.starter.sync.data.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.sync.data.support.entity.MonitorSyncConnection;
import com.chua.starter.sync.data.support.entity.MonitorSyncNode;
import com.chua.starter.sync.data.support.entity.MonitorSyncTask;
import com.chua.starter.sync.data.support.entity.MonitorSyncTaskLog;
import com.chua.starter.sync.data.support.service.sync.MonitorSyncTaskService;
import com.chua.starter.sync.data.support.service.sync.OutputTableService;
import com.chua.starter.sync.data.support.service.sync.SyncTableService;
import com.chua.starter.sync.data.support.sync.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 同步任务管理 Controller
 *
 * @author CH
 * @since 2024/12/19
 */
@RestController
@RequestMapping("/v1/sync/task")
@Tag(name = "同步任务管理")
@RequiredArgsConstructor
public class MonitorSyncTaskController {

    private final MonitorSyncTaskService taskService;
    private final SyncTableService tableService;
    private final OutputTableService outputTableService;

    /**
     * 分页查询任务列表
     */
    @GetMapping("/list")
    @Operation(summary = "分页查询任务列表")
    public ReturnResult<Page<MonitorSyncTask>> list(SyncTaskQuery query) {
        return taskService.pageList(query);
    }

    /**
     * 创建任务
     */
    @PostMapping("/create")
    @Operation(summary = "创建任务")
    public ReturnResult<MonitorSyncTask> create(@RequestBody MonitorSyncTask task) {
        return taskService.createTask(task);
    }

    /**
     * 更新任务
     */
    @PutMapping("/update")
    @Operation(summary = "更新任务")
    public ReturnResult<Boolean> update(@RequestBody MonitorSyncTask task) {
        return taskService.updateTask(task);
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/delete/{taskId}")
    @Operation(summary = "删除任务")
    public ReturnResult<Boolean> delete(
            @Parameter(description = "任务ID") @PathVariable Long taskId) {
        return taskService.deleteTask(taskId);
    }

    /**
     * 获取任务设计数据
     */
    @GetMapping("/design/{taskId}")
    @Operation(summary = "获取任务设计数据")
    public ReturnResult<SyncTaskDesign> getDesign(
            @Parameter(description = "任务ID") @PathVariable Long taskId) {
        return taskService.getTaskDesign(taskId);
    }

    /**
     * 保存任务设计
     */
    @PostMapping("/design/{taskId}")
    @Operation(summary = "保存任务设计")
    public ReturnResult<Boolean> saveDesign(
            @Parameter(description = "任务ID") @PathVariable Long taskId,
            @RequestBody SyncTaskDesign design) {
        return taskService.saveTaskDesign(taskId, design);
    }

    /**
     * 启动任务
     */
    @PostMapping("/start/{taskId}")
    @Operation(summary = "启动任务")
    public ReturnResult<Boolean> start(
            @Parameter(description = "任务ID") @PathVariable Long taskId) {
        return taskService.startTask(taskId);
    }

    /**
     * 停止任务
     */
    @PostMapping("/stop/{taskId}")
    @Operation(summary = "停止任务")
    public ReturnResult<Boolean> stop(
            @Parameter(description = "任务ID") @PathVariable Long taskId) {
        return taskService.stopTask(taskId);
    }

    /**
     * 手动执行一次
     */
    @PostMapping("/execute/{taskId}")
    @Operation(summary = "手动执行一次")
    public ReturnResult<Long> executeOnce(
            @Parameter(description = "任务ID") @PathVariable Long taskId) {
        return taskService.executeOnce(taskId);
    }

    /**
     * 获取任务执行日志
     */
    @GetMapping("/logs/{taskId}")
    @Operation(summary = "获取任务执行日志")
    public ReturnResult<Page<MonitorSyncTaskLog>> getLogs(
            @Parameter(description = "任务ID") @PathVariable Long taskId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer size) {
        return taskService.getTaskLogs(taskId, page, size);
    }

    /**
     * 获取日志详情
     */
    @GetMapping("/log/{logId}")
    @Operation(summary = "获取日志详情")
    public ReturnResult<MonitorSyncTaskLog> getLogDetail(
            @Parameter(description = "日志ID") @PathVariable Long logId) {
        return taskService.getLogDetail(logId);
    }

    /**
     * 验证任务设计
     */
    @PostMapping("/validate")
    @Operation(summary = "验证任务设计")
    public ReturnResult<Boolean> validateDesign(@RequestBody SyncTaskDesign design) {
        return taskService.validateDesign(design);
    }

    /**
     * 复制任务
     */
    @PostMapping("/copy/{taskId}")
    @Operation(summary = "复制任务")
    public ReturnResult<MonitorSyncTask> copyTask(
            @Parameter(description = "原任务ID") @PathVariable Long taskId,
            @Parameter(description = "新任务名称") @RequestParam String newName) {
        return taskService.copyTask(taskId, newName);
    }

    /**
     * 获取任务节点列表
     */
    @GetMapping("/nodes/{taskId}")
    @Operation(summary = "获取任务节点列表")
    public ReturnResult<List<MonitorSyncNode>> getNodes(
            @Parameter(description = "任务ID") @PathVariable Long taskId) {
        return taskService.getTaskNodes(taskId);
    }

    /**
     * 获取任务连线列表
     */
    @GetMapping("/connections/{taskId}")
    @Operation(summary = "获取任务连线列表")
    public ReturnResult<List<MonitorSyncConnection>> getConnections(
            @Parameter(description = "任务ID") @PathVariable Long taskId) {
        return taskService.getTaskConnections(taskId);
    }

    /**
     * 获取全局执行统计
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取全局执行统计")
    public ReturnResult<SyncTaskStatistics> getStatistics(
            @Parameter(description = "开始时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @Parameter(description = "统计粒度: hour/day") @RequestParam(required = false, defaultValue = "day") String granularity) {
        return taskService.getStatistics(startTime, endTime, granularity);
    }

    /**
     * 获取指定任务的执行统计
     */
    @GetMapping("/statistics/{taskId}")
    @Operation(summary = "获取指定任务的执行统计")
    public ReturnResult<SyncTaskStatistics> getTaskStatistics(
            @Parameter(description = "任务ID") @PathVariable Long taskId,
            @Parameter(description = "开始时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @Parameter(description = "统计粒度: hour/day") @RequestParam(required = false, defaultValue = "day") String granularity) {
        return taskService.getTaskStatistics(taskId, startTime, endTime, granularity);
    }

    // ==================== 表管理接口 ====================

    /**
     * 检查同步表状态
     */
    @GetMapping("/table/status")
    @Operation(summary = "检查同步表状态")
    public ReturnResult<SyncTableStatus> checkTableStatus() {
        return tableService.checkTableStatus();
    }

    /**
     * 初始化同步表
     * <p>
     * 业务功能：当表不存在时可通过此接口创建同步相关表
     * </p>
     */
    @PostMapping("/table/initialize")
    @Operation(summary = "初始化同步表")
    public ReturnResult<SyncTableStatus> initializeTables(
            @Parameter(description = "是否强制重建（会删除现有表和数据）") @RequestParam(defaultValue = "false") boolean force) {
        return tableService.initializeTables(force);
    }

    // ==================== 输出节点表管理接口 ====================

    /**
     * 检查输出目标表是否存在
     */
    @PostMapping("/output/table/exists")
    @Operation(summary = "检查输出目标表是否存在")
    public ReturnResult<Boolean> checkOutputTableExists(
            @Parameter(description = "节点配置JSON") @RequestBody String nodeConfig,
            @Parameter(description = "表名") @RequestParam String tableName) {
        return outputTableService.checkTableExists(nodeConfig, tableName);
    }

    /**
     * 创建输出目标表
     */
    @PostMapping("/output/table/create")
    @Operation(summary = "创建输出目标表")
    public ReturnResult<Boolean> createOutputTable(
            @Parameter(description = "节点配置JSON") @RequestParam String nodeConfig,
            @Parameter(description = "表名") @RequestParam String tableName,
            @RequestBody List<ColumnDefinition> columns) {
        return outputTableService.createTable(nodeConfig, tableName, columns);
    }

    /**
     * 获取输出目标表结构
     */
    @PostMapping("/output/table/structure")
    @Operation(summary = "获取输出目标表结构")
    public ReturnResult<List<ColumnDefinition>> getOutputTableStructure(
            @Parameter(description = "节点配置JSON") @RequestBody String nodeConfig,
            @Parameter(description = "表名") @RequestParam String tableName) {
        return outputTableService.getTableStructure(nodeConfig, tableName);
    }

    /**
     * 预览建表SQL
     */
    @PostMapping("/output/table/preview-sql")
    @Operation(summary = "预览建表SQL")
    public ReturnResult<String> previewCreateTableSql(
            @Parameter(description = "表名") @RequestParam String tableName,
            @Parameter(description = "数据库类型") @RequestParam(defaultValue = "mysql") String dbType,
            @RequestBody List<ColumnDefinition> columns) {
        return outputTableService.previewCreateTableSql(tableName, columns, dbType);
    }

    /**
     * 同步输出目标表结构
     * <p>
     * 根据列定义添加缺失的列
     * </p>
     */
    @PostMapping("/output/table/sync-structure")
    @Operation(summary = "同步输出目标表结构")
    public ReturnResult<Boolean> syncOutputTableStructure(
            @Parameter(description = "节点配置JSON") @RequestParam String nodeConfig,
            @Parameter(description = "表名") @RequestParam String tableName,
            @RequestBody List<ColumnDefinition> columns) {
        return outputTableService.syncTableStructure(nodeConfig, tableName, columns);
    }
}
