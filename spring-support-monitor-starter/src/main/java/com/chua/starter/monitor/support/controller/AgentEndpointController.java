package com.chua.starter.monitor.support.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.context.MonitorAgentContext;
import com.chua.starter.monitor.support.entity.MsMonitor;
import com.chua.starter.monitor.support.entity.MsMonitorTask;
import com.chua.starter.monitor.support.mapper.MsMonitorMapper;
import com.chua.starter.monitor.support.service.MetricService;
import com.chua.starter.monitor.support.service.MonitorConfigService;
import com.chua.starter.monitor.support.service.MonitorTaskService;
import com.chua.starter.monitor.support.spi.CollectResult;
import com.chua.starter.monitor.support.spi.MetricPoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
* Agent 上报端点（开放 API，不走登录态）
* <p>
* 外部探针以 {@code X-Agent-Key} 头鉴权，键 与 ms监控.监控Agent键 匹配。
* 推送数据与 提供者 轮询共用同一条写入管道（MySQL + influxdb + 告警评估）；
* 心跳与推送均会刷新在线状态上下文。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Tag(name = "监控-Agent上报", description = "外部探针推送指标、心跳与在线状态")
@RestController
@RequestMapping("/ms/monitor/agent")
@RequiredArgsConstructor
public class AgentEndpointController {

    /** Agent 鉴权请求头 */
    private static final String AGENT_KEY_HEADER = "X-Agent-Key";

    /** 监控目标 映射器 */
    private final MsMonitorMapper monitorMapper;

    /** 指标服务 */
    private final MetricService metricService;

    /** 探针上下文（心跳/在线状态） */
    private final MonitorAgentContext agentContext;

    /** 采集任务服务 */
    private final MonitorTaskService taskService;

    /** 全局配置服务 */
    private final MonitorConfigService configService;

    /** 采集间隔配置键 */
    private static final String CONFIG_KEY_INTERVAL = "monitor.collect.interval.ms";

    /** 采集间隔默认值(毫秒) */
    private static final long DEFAULT_INTERVAL_MS = 10_000L;

    /**
    * 心跳：探针周期性上报存活并刷新在线状态
    *
    * @param agentKey 探针标识
    * @return 服务端时间
    */
    @Operation(summary = "Agent 心跳")
    @GetMapping("/heartbeat")
    public ReturnResult<Map<String, Object>> heartbeat(
            @RequestHeader(value = AGENT_KEY_HEADER, required = false) String agentKey) {
        MsMonitor monitor = requireByAgentKey(agentKey);
        if (monitor == null) {
            return ReturnResult.error("无效的探针标识");
        }
        agentContext.markActive(monitor.getId());
        return ReturnResult.ok(Map.of("serverTime", LocalDateTime.now().toString(),
                "monitorId", monitor.getId()));
    }

    /**
    * 探针在线状态列表（心跳上下文快照）
    *
    * @return 每个目标的最后心跳时间与在线标记
    */
    @Operation(summary = "探针在线状态")
    @GetMapping("/status")
    public ReturnResult<List<Map<String, Object>>> status() {
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (MonitorAgentContext.AgentStatus status : agentContext.snapshot()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("monitorId", status.monitorId());
            item.put("lastHeartbeat", status.lastHeartbeat());
            item.put("online", status.online());
            result.add(item);
        }
        return ReturnResult.list(result);
    }

    /**
    * Agent 列表：登记了探针标识的监控目标 + 在线状态
    *
    * @return 每个目标的 ID/名称/地址/探针标识/在线/最后心跳
    */
    @Operation(summary = "Agent 列表")
    @GetMapping("/list")
    public ReturnResult<List<Map<String, Object>>> listAgents() {
        Map<Long, MonitorAgentContext.AgentStatus> statusById = new LinkedHashMap<>();
        for (MonitorAgentContext.AgentStatus status : agentContext.snapshot()) {
            statusById.put(status.monitorId(), status);
        }
        List<MsMonitor> monitors = monitorMapper.selectList(new LambdaQueryWrapper<MsMonitor>()
                .isNotNull(MsMonitor::getMonitorAgentKey)
                .ne(MsMonitor::getMonitorAgentKey, ""));
        List<Map<String, Object>> result = new ArrayList<>(monitors.size());
        for (MsMonitor monitor : monitors) {
            MonitorAgentContext.AgentStatus status = statusById.get(monitor.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("monitorId", monitor.getId());
            item.put("monitorName", monitor.getMonitorName());
            item.put("monitorHost", monitor.getMonitorHost());
            item.put("monitorAgentKey", monitor.getMonitorAgentKey());
            item.put("online", status != null && status.online());
            item.put("lastHeartbeat", status == null ? null : status.lastHeartbeat());
            result.add(item);
        }
        return ReturnResult.list(result);
    }

    /**
    * Agent 配置：采集间隔与该资产下的采集任务
    *
    * @param monitorId 监控目标 标识
    * @return intervals + tasks，任务字段已归一化为 taskName/taskType/taskCron
    */
    @Operation(summary = "Agent 配置")
    @GetMapping("/config/{monitorId}")
    public ReturnResult<Map<String, Object>> agentConfig(@PathVariable Long monitorId) {
        MsMonitor monitor = monitorMapper.selectById(monitorId);
        if (monitor == null) {
            return ReturnResult.error("监控目标不存在: " + monitorId);
        }
        Map<String, Object> intervals = new LinkedHashMap<>();
        intervals.put("采集间隔", configService.getLong(CONFIG_KEY_INTERVAL, DEFAULT_INTERVAL_MS));

        List<MsMonitorTask> tasks = taskService.listByMonitor(monitorId).getData();
        List<Map<String, Object>> taskViews = new ArrayList<>();
        if (tasks != null) {
            for (MsMonitorTask task : tasks) {
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("taskName", task.getMonitorTaskName());
                view.put("taskType", task.getServiceType());
                view.put("taskCron", task.getMonitorTaskCron());
                taskViews.add(view);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("intervals", intervals);
        result.put("tasks", taskViews);
        return ReturnResult.ok(result);
    }

    /**
    * 指标推送
    * <p>
    * 请求体结构：
    * <pre>{@code
    * {
    *   "points": [
    *     { "metricName": "cpu_usage", "value": 35.2, "unit": "%",
    *       "tags": {"model":"x86"}, "targetTable": "ms_monitor_metric_cpu" }
    *   ]
    * }
    * }</pre>   * }
    * }</pre>
    * Targettable 缺省时落通用表 ms_监控_指标。
    * </p>
    *
    * @param agentKey 探针标识
    * @param body     指标载荷
    * @return 结果
    */
    @Operation(summary = "Agent 指标推送")
    @PostMapping("/metrics")
    public ReturnResult<Void> pushMetrics(
            @RequestHeader(value = AGENT_KEY_HEADER, required = false) String agentKey,
            @RequestBody Map<String, Object> body) {
        MsMonitor monitor = requireByAgentKey(agentKey);
        if (monitor == null) {
            return ReturnResult.error("无效的探针标识");
        }
        // 推送本身即活跃证明, 刷新心跳上下文
        agentContext.markActive(monitor.getId());
        CollectResult result = new CollectResult();
        result.setMonitor(monitor);
        List<MetricPoint> points = parsePoints(body.get("points"));
        if (points.isEmpty()) {
            return ReturnResult.error("points 为空或格式非法");
        }
        result.setPoints(points);
        ReturnResult<Void> writeResult = metricService.writeCollectResult(result);
        if (!writeResult.isOk()) {
            return writeResult;
        }
        return ReturnResult.ok();
    }

    /**
    * 按探针标识加载启用的监控目标
    *
    * @param agentKey 探针标识
    * @return 目标实体，未命中返回 空
    */
    private MsMonitor requireByAgentKey(String agentKey) {
        if (agentKey == null || agentKey.isBlank()) {
            return null;
        }
        return monitorMapper.selectOne(new LambdaQueryWrapper<MsMonitor>()
                .eq(MsMonitor::getMonitorAgentKey, agentKey)
                .eq(MsMonitor::getMonitorEnabled, 1)
                .last("LIMIT 1"));
    }

    /**
    * 宽松解析指标点数组
    *
    * @param raw 原始 JSON 数组对象
    * @return 指标点列表（非法项跳过）
    */
    @SuppressWarnings("unchecked")
    private List<MetricPoint> parsePoints(Object raw) {
        List<MetricPoint> points = new java.util.ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return points;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            try {
                var point = new MetricPoint();
                point.setMetricName(String.valueOf(map.get("metricName")));
                point.setValue(Double.parseDouble(String.valueOf(map.get("value"))));
                Object unit = map.get("unit");
                if (unit != null) {
                    point.setUnit(unit.toString());
                }
                Object tags = map.get("tags");
                if (tags instanceof Map<?, ?> tagMap) {
                    for (Map.Entry<?, ?> entry : tagMap.entrySet()) {
                        point.getTags().put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                    }
                }
                Object targetTable = map.get("targetTable");
                point.setTargetTable(targetTable == null || targetTable.toString().isBlank()
                        ? "ms_monitor_metric" : targetTable.toString());
                points.add(point);
            } catch (Exception e) {
                log.warn("[agent] 指标点解析失败: {}", e.getMessage());
            }
        }
        return points;
    }
}
