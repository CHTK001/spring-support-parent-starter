package com.chua.starter.monitor.support.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.context.MonitorAgentContext;
import com.chua.starter.monitor.support.entity.MsMonitor;
import com.chua.starter.monitor.support.entity.MsMonitorTask;
import com.chua.starter.monitor.support.mapper.MsMonitorMapper;
import com.chua.starter.monitor.support.model.AgentConfigVO;
import com.chua.starter.monitor.support.model.AgentHeartbeatVO;
import com.chua.starter.monitor.support.model.AgentListItemVO;
import com.chua.starter.monitor.support.model.AgentMetricsPushVO;
import com.chua.starter.monitor.support.model.AgentStatusVO;
import com.chua.starter.monitor.support.model.AgentTaskVO;
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

import java.util.ArrayList;
import java.util.HashMap;
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

    /**
     * Agent 鉴权请求头
    */
    private static final String AGENT_KEY_HEADER = "X-Agent-Key";

    /**
     * 监控目标 映射器
    */
    private final MsMonitorMapper monitorMapper;

    /**
     * 指标服务
    */
    private final MetricService metricService;

    /**
     * 探针上下文（心跳/在线状态）
    */
    private final MonitorAgentContext agentContext;

    /**
     * 采集任务服务
    */
    private final MonitorTaskService taskService;

    /**
     * 全局配置服务
    */
    private final MonitorConfigService configService;

    /**
     * 采集间隔配置键
    */
    private static final String CONFIG_KEY_INTERVAL = "monitor.collect.interval.ms";

    /**
     * 采集间隔默认值(毫秒)
    */
    private static final long DEFAULT_INTERVAL_MS = 10_000L;

    /**
     * 指标点未指定落库表时的通用表
    */
    private static final String DEFAULT_TARGET_TABLE = "ms_monitor_metric";

    /**
     * 心跳：探针周期性上报存活并刷新在线状态
     *
     * @param agentKey 探针标识
     * @return 服务端时间
     */
    @Operation(summary = "Agent 心跳")
    @GetMapping("/heartbeat")
    public ReturnResult<AgentHeartbeatVO> heartbeat(
            @RequestHeader(value = AGENT_KEY_HEADER, required = false) String agentKey) {
        MsMonitor monitor = requireByAgentKey(agentKey);
        if (monitor == null) {
            return ReturnResult.error("无效的探针标识");
        }
        agentContext.markActive(monitor.getId());
        return ReturnResult.ok(AgentHeartbeatVO.of(monitor.getId()));
    }

    /**
     * 探针在线状态列表（心跳上下文快照）
     *
     * @return 每个目标的最后心跳时间与在线标记
     */
    @Operation(summary = "探针在线状态")
    @GetMapping("/status")
    public ReturnResult<List<AgentStatusVO>> status() {
        List<AgentStatusVO> result = new ArrayList<>();
        for (MonitorAgentContext.AgentStatus status : agentContext.snapshot()) {
            result.add(new AgentStatusVO(status.monitorId(), status.lastHeartbeat(), status.online()));
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
    public ReturnResult<List<AgentListItemVO>> listAgents() {
        Map<Long, MonitorAgentContext.AgentStatus> statusById = new HashMap<>();
        for (MonitorAgentContext.AgentStatus status : agentContext.snapshot()) {
            statusById.put(status.monitorId(), status);
        }
        List<MsMonitor> monitors = monitorMapper.selectList(new LambdaQueryWrapper<MsMonitor>()
                .isNotNull(MsMonitor::getMonitorAgentKey)
                .ne(MsMonitor::getMonitorAgentKey, ""));
        List<AgentListItemVO> result = new ArrayList<>(monitors.size());
        for (MsMonitor monitor : monitors) {
            MonitorAgentContext.AgentStatus status = statusById.get(monitor.getId());
            result.add(new AgentListItemVO(monitor.getId(), monitor.getMonitorName(), monitor.getMonitorHost(),
                    monitor.getMonitorAgentKey(),
                    status != null && status.online(),
                    status == null ? null : status.lastHeartbeat()));
        }
        return ReturnResult.list(result);
    }

    /**
     * Agent 配置：采集间隔与该资产下的采集任务
     *
     * @param monitorId 监控目标 标识
     * @return 采集间隔 collectIntervalMs + 归一化为 taskName/taskType/taskCron 的任务列表
     */
    @Operation(summary = "Agent 配置")
    @GetMapping("/config/{monitorId}")
    public ReturnResult<AgentConfigVO> agentConfig(@PathVariable Long monitorId) {
        MsMonitor monitor = monitorMapper.selectById(monitorId);
        if (monitor == null) {
            return ReturnResult.error("监控目标不存在: " + monitorId);
        }
        Long intervalMs = configService.getLong(CONFIG_KEY_INTERVAL, DEFAULT_INTERVAL_MS);

        List<MsMonitorTask> tasks = taskService.listByMonitor(monitorId).getData();
        List<AgentTaskVO> taskViews = new ArrayList<>();
        if (tasks != null) {
            for (MsMonitorTask task : tasks) {
                taskViews.add(new AgentTaskVO(task.getMonitorTaskName(), task.getServiceType(),
                        task.getMonitorTaskCron()));
            }
        }
        return ReturnResult.ok(new AgentConfigVO(intervalMs, taskViews));
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
     * }</pre>
     * targetTable 缺省时落通用表 ms_monitor_metric。
     *
     * @param agentKey 探针标识
     * @param body     指标载荷
     * @return 结果
     */
    @Operation(summary = "Agent 指标推送")
    @PostMapping("/metrics")
    public ReturnResult<Void> pushMetrics(
            @RequestHeader(value = AGENT_KEY_HEADER, required = false) String agentKey,
            @RequestBody AgentMetricsPushVO body) {
        MsMonitor monitor = requireByAgentKey(agentKey);
        if (monitor == null) {
            return ReturnResult.error("无效的探针标识");
        }
        // 推送本身即活跃证明, 刷新心跳上下文
        agentContext.markActive(monitor.getId());
        CollectResult result = new CollectResult();
        result.setMonitor(monitor);
        List<MetricPoint> points = normalizePoints(body);
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
     * 归一化上报指标点：丢弃缺指标名或缺数值的项，补齐缺省落库表
     *
     * @param body 指标载荷，可为 空
     * @return 可入库的指标点列表
     */
    private List<MetricPoint> normalizePoints(AgentMetricsPushVO body) {
        List<MetricPoint> points = new ArrayList<>();
        if (body == null || body.points() == null) {
            return points;
        }
        for (MetricPoint point : body.points()) {
            if (point == null || point.getMetricName() == null || point.getValue() == null) {
                continue;
            }
            if (point.getTargetTable() == null || point.getTargetTable().isBlank()) {
                point.setTargetTable(DEFAULT_TARGET_TABLE);
            }
            points.add(point);
        }
        return points;
    }
}
