package com.chua.starter.sync.data.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.sync.data.support.entity.MonitorSyncAlert;
import com.chua.starter.sync.data.support.service.AlertService;
import com.chua.starter.sync.data.support.service.SyncMonitorService;
import com.chua.starter.sync.data.support.service.SyncStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 同步监控统计 Controller
 *
 * @author System
 * @since 2026/03/09
 */
@RestController
@RequestMapping("/v1/sync/monitor")
@Tag(name = "同步监控统计")
@RequiredArgsConstructor
public class SyncMonitorController {

    private final SyncMonitorService monitorService;
    private final SyncStatisticsService statisticsService;
    private final AlertService alertService;

    /**
     * 获取实时监控数据
     */
    @GetMapping("/realtime/{taskId}")
    @Operation(summary = "获取实时监控数据")
    public ReturnResult<Map<String, Object>> getRealtimeData(
            @Parameter(description = "任务ID") @PathVariable Long taskId) {
        Map<String, Object> data = monitorService.collectRealtimeData(taskId);
        return ReturnResult.ok(data);
    }

    /**
     * 获取性能指标
     */
    @GetMapping("/metrics/{taskId}")
    @Operation(summary = "获取性能指标")
    public ReturnResult<Map<String, Object>> getMetrics(
            @Parameter(description = "任务ID") @PathVariable Long taskId,
            @Parameter(description = "开始时间") @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        Map<String, Object> metrics = monitorService.calculatePerformanceMetrics(taskId, startTime, endTime);
        return ReturnResult.ok(metrics);
    }

    /**
     * 获取统计趋势
     */
    @GetMapping("/trend/{taskId}")
    @Operation(summary = "获取统计趋势")
    public ReturnResult<Map<String, Object>> getTrend(
            @Parameter(description = "任务ID") @PathVariable Long taskId,
            @Parameter(description = "天数") @RequestParam(defaultValue = "7") int days) {
        Map<String, Object> trend = statisticsService.analyzeTrend(taskId, days);
        return ReturnResult.ok(trend);
    }

    /**
     * 获取告警列表
     */
    @GetMapping("/alerts")
    @Operation(summary = "获取告警列表")
    public ReturnResult<List<MonitorSyncAlert>> listAlerts(
            @Parameter(description = "任务ID（可选）") @RequestParam(required = false) Long taskId,
            @Parameter(description = "告警级别（可选）") @RequestParam(required = false) String level,
            @Parameter(description = "是否已解决（可选）") @RequestParam(required = false) Boolean resolved) {
        List<MonitorSyncAlert> alerts = alertService.listAlerts(taskId, level, resolved);
        return ReturnResult.ok(alerts);
    }

    /**
     * 确认告警
     */
    @PutMapping("/alerts/{alertId}/resolve")
    @Operation(summary = "确认告警")
    public ReturnResult<Boolean> resolveAlert(
            @Parameter(description = "告警ID") @PathVariable Long alertId) {
        alertService.resolveAlert(alertId);
        return ReturnResult.ok(true);
    }
}
