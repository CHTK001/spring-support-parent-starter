package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.model.ServerMetricsDetail;
import com.chua.starter.server.support.model.ServerMetricsSnapshot;
import com.chua.starter.server.support.model.ServerMetricsTaskSettings;
import com.chua.starter.server.support.model.ServerMetricsTaskSettingsRequest;
import com.chua.starter.server.support.service.ServerMetricsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 为前端与历史验收路径提供独立的指标访问入口，避免必须经过 /server/hosts 前缀。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/server/metrics")
public class ServerMetricsController {

    private final ServerMetricsService serverMetricsService;

    /**
     * 查询所有服务器当前的最新指标快照。
     */
    @GetMapping
    public ReturnResult<List<ServerMetricsSnapshot>> listMetrics() {
        return ReturnResult.ok(serverMetricsService.listSnapshots());
    }

    /**
     * 手动触发一次全量指标采集。
     */
    @PostMapping("/refresh")
    public ReturnResult<List<ServerMetricsSnapshot>> refreshMetrics() {
        return ReturnResult.ok(serverMetricsService.refreshMetrics());
    }

    /**
     * 读取指标采集任务配置与调度状态。
     */
    @GetMapping("/task-settings")
    public ReturnResult<ServerMetricsTaskSettings> getTaskSettings() {
        return ReturnResult.ok(serverMetricsService.getTaskSettings());
    }

    /**
     * 更新指标采集任务配置。
     */
    @PutMapping("/task-settings")
    public ReturnResult<ServerMetricsTaskSettings> updateTaskSettings(
            @RequestBody ServerMetricsTaskSettingsRequest request
    ) {
        return ReturnResult.ok(serverMetricsService.updateTaskSettings(request));
    }

    /**
     * 读取指定服务器的最新指标快照。
     */
    @GetMapping("/{id}")
    public ReturnResult<ServerMetricsSnapshot> getMetrics(@PathVariable Integer id) {
        return ReturnResult.ok(serverMetricsService.getSnapshot(id));
    }

    /**
     * 读取指定服务器的指标详情视图。
     */
    @GetMapping("/{id}/detail")
    public ReturnResult<ServerMetricsDetail> getMetricsDetail(@PathVariable Integer id) {
        return ReturnResult.ok(serverMetricsService.getDetail(id));
    }

    /**
     * 查询指定服务器的历史指标点位。
     */
    @GetMapping("/{id}/history")
    public ReturnResult<List<ServerMetricsSnapshot>> getMetricsHistory(
            @PathVariable Integer id,
            @RequestParam(value = "minutes", required = false) Integer minutes
    ) {
        return ReturnResult.ok(serverMetricsService.listHistory(id, minutes));
    }
}
