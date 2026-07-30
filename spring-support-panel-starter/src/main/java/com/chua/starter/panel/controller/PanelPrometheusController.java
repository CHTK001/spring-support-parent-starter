package com.chua.starter.panel.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.panel.service.PrometheusPanelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Prometheus 面板控制器。
 *
 * @author CH
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/panel/prometheus")
@Tag(name = "Panel Prometheus", description = "Panel Prometheus 监控接口")
public class PanelPrometheusController {

    private final PrometheusPanelService prometheusPanelService;

    /**
     * 打开 Prometheus 连接。
     *
     * @param body 连接参数
     * @return 连接标识
     */
    @Operation(summary = "打开连接")
    @PostMapping("/open")
    public ReturnResult<String> open(@RequestBody Map<String, Object> body) {
        String host = String.valueOf(body.getOrDefault("host", "127.0.0.1"));
        int port = Integer.parseInt(String.valueOf(body.getOrDefault("port", 9090)));
        String username = body.containsKey("username") ? String.valueOf(body.get("username")) : null;
        String password = body.containsKey("password") ? String.valueOf(body.get("password")) : null;
        try {
            return ReturnResult.ok(prometheusPanelService.open(host, port, username, password));
        } catch (Exception e) {
            return ReturnResult.error("Prometheus 连接失败: " + e.getMessage());
        }
    }

    /**
     * 关闭连接。
     *
     * @param connectionId 连接标识
     * @return 关闭结果
     */
    @Operation(summary = "关闭连接")
    @DeleteMapping("/{connectionId}")
    public ReturnResult<Void> close(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        prometheusPanelService.close(connectionId);
        return ReturnResult.ok(null);
    }

    /**
     * 执行即时查询。
     *
     * @param connectionId 连接标识
     * @param promql PromQL 表达式
     * @param time 查询时间
     * @return 查询结果
     */
    @Operation(summary = "即时查询")
    @GetMapping("/{connectionId}/query")
    public ReturnResult<Map<String, Object>> query(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "PromQL") @RequestParam String promql,
            @Parameter(description = "查询时间") @RequestParam(required = false) String time) {
        try {
            return ReturnResult.ok(prometheusPanelService.query(connectionId, promql, time));
        } catch (Exception e) {
            return ReturnResult.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 执行范围查询。
     *
     * @param connectionId 连接标识
     * @param promql PromQL 表达式
     * @param start 开始时间
     * @param end 结束时间
     * @param step 步长
     * @return 查询结果
     */
    @Operation(summary = "范围查询")
    @GetMapping("/{connectionId}/query_range")
    public ReturnResult<Map<String, Object>> queryRange(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "PromQL") @RequestParam String promql,
            @Parameter(description = "开始时间") @RequestParam String start,
            @Parameter(description = "结束时间") @RequestParam String end,
            @Parameter(description = "步长") @RequestParam(defaultValue = "60") String step) {
        try {
            return ReturnResult.ok(prometheusPanelService.queryRange(connectionId, promql, start, end, step));
        } catch (Exception e) {
            return ReturnResult.error("范围查询失败: " + e.getMessage());
        }
    }

    /**
     * 查询可用指标名称。
     *
     * @param connectionId 连接标识
     * @return 指标名称列表
     */
    @Operation(summary = "列出指标")
    @GetMapping("/{connectionId}/metrics")
    public ReturnResult<List<String>> listMetrics(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        try {
            return ReturnResult.ok(prometheusPanelService.listMetrics(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("获取 Metrics 失败: " + e.getMessage());
        }
    }

    /**
     * 查询可用标签名称。
     *
     * @param connectionId 连接标识
     * @return 标签列表
     */
    @Operation(summary = "列出标签")
    @GetMapping("/{connectionId}/labels")
    public ReturnResult<List<String>> listLabels(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        try {
            return ReturnResult.ok(prometheusPanelService.listLabels(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("获取 Labels 失败: " + e.getMessage());
        }
    }

    /**
     * 查询抓取目标列表。
     *
     * @param connectionId 连接标识
     * @return 目标数据
     */
    @Operation(summary = "查询 Targets")
    @GetMapping("/{connectionId}/targets")
    public ReturnResult<Map<String, Object>> listTargets(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        try {
            return ReturnResult.ok(prometheusPanelService.listTargets(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("获取 Targets 失败: " + e.getMessage());
        }
    }

    /**
     * 查询当前告警。
     *
     * @param connectionId 连接标识
     * @return 告警数据
     */
    @Operation(summary = "查询告警")
    @GetMapping("/{connectionId}/alerts")
    public ReturnResult<Map<String, Object>> alerts(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        try {
            return ReturnResult.ok(prometheusPanelService.alerts(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("获取告警失败: " + e.getMessage());
        }
    }

    /**
     * 查询规则列表。
     *
     * @param connectionId 连接标识
     * @return 规则数据
     */
    @Operation(summary = "查询规则")
    @GetMapping("/{connectionId}/rules")
    public ReturnResult<Map<String, Object>> rules(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        try {
            return ReturnResult.ok(prometheusPanelService.rules(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("获取规则失败: " + e.getMessage());
        }
    }
}
