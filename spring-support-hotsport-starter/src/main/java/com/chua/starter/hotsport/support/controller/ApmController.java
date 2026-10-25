package com.chua.starter.hotsport.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.hotsport.support.model.LeakQuery;
import com.chua.starter.hotsport.support.model.LogQuery;
import com.chua.starter.hotsport.support.model.TransmissionQuery;
import com.chua.starter.hotsport.support.service.ApmQueryService;
import com.chua.starter.hotsport.support.service.ApmInjectService;
import com.chua.starter.hotsport.support.service.JvmMetricsService;
import com.chua.starter.hotsport.support.service.JvmMemoryService;
import com.chua.starter.hotsport.support.service.AlertMonitorService;
import com.chua.starter.agent.support.service.AgentAttachService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * APM 热点观测 REST 接口。
 *
 * <p>统一前缀 {@code /api/apm}，由前端 vue-support-hotspot-starter 经
 * {@code @repo/utils} 的 {@code http().get('/api/apm/...')} 调用。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequestMapping("/api/apm")
@RequiredArgsConstructor
@Slf4j
public class ApmController {

    /**
     * APM 查询服务。
    */
    private final ApmQueryService apmQueryService;

    /**
     * APM 数据注入服务（页面操作产生数据）。
    */
    private final ApmInjectService apmInjectService;

    /**
     * JVM 指标服务（快照 + 时序 + 采样调度）。
    */
    private final JvmMetricsService jvmMetricsService;

    /**
     * JVM 内存诊断服务（对象内存 + 手动 GC + 类加载）。
     */
    private final JvmMemoryService jvmMemoryService;

    /**
     * 告警监控服务（阈值巡检事件流）。
    */
    private final AlertMonitorService alertMonitorService;

    /**
     * Agent 自注入服务（状态 / 启停 / 重注入）。
     */
    private final AgentAttachService agentAttachService;

    /**
     * JSON 序列化（SSE data 事件）。
    */
    private final ObjectMapper objectMapper;

    /**
     * Hotsport 配置（agent 中心回显实际生效的存储参数）。
    */
    private final com.chua.starter.hotsport.support.config.HotsportProperties hotsportProperties;

    /**
     * SSE 推送线程池。
     *
     * <p>不能是单线程：{@code /stream} 与 {@code /stream/logs} 的所有订阅者共用这一个
     * 调度器，某个客户端断开后写入阻塞会把整站 SSE 一起拖死；给小线程池后单个慢客户端
     * 最多占用一个线程。</p>
    */
    private final ScheduledExecutorService sseExecutor =
            Executors.newScheduledThreadPool(4, r -> {
                Thread t = new Thread(r, "hotsport-sse");
                t.setDaemon(true);
                return t;
            });

    /**
     * 概览统计。
     *
     * @return 各类型总量 + agent 状态
     */
    @GetMapping("/stats")
    public ReturnResult<Map<String, Object>> stats() {
        return ReturnResult.ok(apmQueryService.stats());
    }

    /**
     * 分页查询传输记录。
     *
     * @param query 查询参数对象（字段与 HTTP 查询参数一一对应）
     * @return 分页数据
     */
    @GetMapping("/transmissions")
    public ReturnResult<Map<String, Object>> transmissions(TransmissionQuery query) {
        return ReturnResult.ok(apmQueryService.transmissions(query));
    }

    /**
     * 查询依赖边。
     *
     * @param size 上限，默认 100
     * @return 依赖边列表
     */
    @GetMapping("/dependencies")
    public ReturnResult<Map<String, Object>> dependencies(
            @RequestParam(defaultValue = "100") int size) {
        List<Map<String, Object>> items = apmQueryService.dependencies(size);
        Map<String, Object> result = Map.of("total", items.size(), "items", items);
        return ReturnResult.ok(result);
    }

    /**
     * 分页查询资源泄漏。
     *
     * @param query 查询参数对象（字段与 HTTP 查询参数一一对应）
     * @return 分页数据
     */
    @GetMapping("/leaks")
    public ReturnResult<Map<String, Object>> leaks(LeakQuery query) {
        return ReturnResult.ok(apmQueryService.leaks(query));
    }

    /**
     * 分页查询日志。
     *
     * @param query 查询参数对象（字段与 HTTP 查询参数一一对应）
     * @return 分页数据
     */
    @GetMapping("/logs")
    public ReturnResult<Map<String, Object>> logs(LogQuery query) {
        return ReturnResult.ok(apmQueryService.logs(query));
    }

    /**
     * 全量句柄快照（含分配堆栈 / FD / 线程）。
     *
     * @param limit 每分类上限
     * @return 句柄清单
     */
    @GetMapping("/agent/handles")
    public ReturnResult<Map<String, Object>> agentHandles(
            @RequestParam(defaultValue = "200") int limit) {
        return ReturnResult.ok(apmQueryService.handleInventory(limit));
    }

    /**
     * 句柄增长趋势 — 判断是否在泄漏。
     *
     * <p>{@code opened} 累计打开、{@code closed} 累计关闭、{@code alive} 当前存活、
     * {@code leakDelta} 打开-关闭-存活（同类型同时存活计数饱满时只能反映部分口径，
     * 重点看 opened 持续上涨而 closed 不涨）。</p>
     *
     * @return 增长统计 { windowMs, perKind: { kind: { opened, closed, alive, leakDelta } } }
     */
    @GetMapping("/handles/growth")
    public ReturnResult<Map<String, Object>> handlesGrowth() {
        return ReturnResult.ok(apmQueryService.handleGrowth());
    }

    /**
     * System.out / System.err 实时捕获日志。
     *
     * @param limit 条数上限
     * @return 系统输出条目
     */
    @GetMapping("/agent/system-out")
    public ReturnResult<List<Map<String, Object>>> agentSystemOut(
            @RequestParam(defaultValue = "200") int limit) {
        return ReturnResult.ok(apmQueryService.systemOut(limit));
    }

    /**
     * 方法热点排行（按 Span 聚合耗时/错误）。
     *
     * @param limit 返回上限
     * @return 热点方法列表
     */
    @GetMapping("/agent/trace-hotspots")
    public ReturnResult<List<Map<String, Object>>> agentTraceHotspots(
            @RequestParam(defaultValue = "20") int limit) {
        return ReturnResult.ok(apmQueryService.traceHotspots(limit));
    }

    /**
     * 链路阶段耗时汇总（http/network/db/cache/mq/rpc/method）。
     *
     * @return 阶段汇总
     */
    @GetMapping("/agent/trace-phases")
    public ReturnResult<List<Map<String, Object>>> agentTracePhases() {
        return ReturnResult.ok(apmQueryService.tracePhases());
    }

    /**
     * 根因路径自动定位（沿最慢子 Span 链定位瓶颈）。
     *
     * @param traceId 链路 ID
     * @return 根因路径
     */
    @GetMapping("/trace/{traceId}/root-cause")
    public ReturnResult<Map<String, Object>> traceRootCause(
            @PathVariable String traceId) {
        return ReturnResult.ok(apmQueryService.rootCausePath(traceId));
    }

    /**
     * SkyWalking 兼容 Segment 导出。
     *
     * @return Segment 列表
     */
    @GetMapping("/agent/skywalking/export")
    public ReturnResult<List<Map<String, Object>>> agentSkywalkingExport() {
        return ReturnResult.ok(apmQueryService.skywalkingExport());
    }

    /**
     * Agent 实时状态。
     *
     * @return agent 是否激活 + Shell 端口等
     */
    @GetMapping("/agent/status")
    public ReturnResult<Map<String, Object>> agentStatus() {
        Map<String, Object> status = apmQueryService.stats();
        return ReturnResult.ok(status);
    }

    /**
     * Agent 配置（Agent 中心数据源）。
     *
     * @return 当前配置；后端未配置时返回默认值
     */
    @GetMapping("/agent/config")
    public ReturnResult<Map<String, Object>> agentConfig() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("samplingRate", 100);
        cfg.put("slowThresholdMs", 500);
        cfg.put("logLevel", "INFO");
        cfg.put("enabled", agentAttachService.isAgentEnabled());
        cfg.put("injectMode", "SIMULATE");
        cfg.put("storage", hotsportProperties.getApm().toStorageConfig());
        cfg.put("storageName", com.chua.runtime.apm.storage.StorageManager.get().name());
        cfg.put("updatedAt", System.currentTimeMillis());
        return ReturnResult.ok(cfg);
    }

    /**
     * 存储运行时状态（运维自检）。
     *
     * <p>回答两个问题：配置里写了什么（{@code configured}）、实际生效的是什么
     * （{@code path/async/flushed/dropped/...}）。两者不一致通常就是「配置没生效」。</p>
     *
     * @return 状态映射
     */
    @GetMapping("/storage/status")
    public ReturnResult<Map<String, Object>> storageStatus() {
        return ReturnResult.ok(apmQueryService.storageStatus());
    }

    /**
     * 系统资源聚合快照 — 一屏看懂「这台机器的资源往哪消耗」。
     *
     * <p>把散在各处的资源信息拼成一份，专供调试和研究：</p>
     * <ul>
     *   <li>{@code os} — 物理内存 / 交换 / 系统与进程 CPU 占用</li>
     *   <li>{@code disks} — 逐盘总容量 / 可用 / 使用率，一眼看出哪个盘快满</li>
     *   <li>{@code jvm} — JVM 堆 / GC / 线程（实时快照）</li>
     *   <li>{@code storage} — APM 存储状态（含 db 所在盘的磁盘余量与接近耗尽标记）</li>
     *   <li>{@code ownThreads} — APM 自身内部线程画像（观测组件自己别泄漏）</li>
     *   <li>{@code trend} — 最近 N 分钟资源时序（堆百分比 / GC / 线程 / 系统 CPU / 物理内存可用）</li>
     * </ul>
     *
     * @param minutes 时序窗口（分钟，默认 10）
     * @return 资源聚合快照
     */
    @GetMapping("/resources")
    public ReturnResult<Map<String, Object>> systemResources(
            @RequestParam(defaultValue = "10") int minutes) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ts", System.currentTimeMillis());
        try {
            r.put("os", jvmMetricsService.systemResources().get("os"));
            r.put("disks", jvmMetricsService.systemResources().get("disks"));
        } catch (Exception e) {
            r.put("os", Map.of("error", e.getMessage()));
            r.put("disks", List.of());
        }
        try {
            r.put("jvm", jvmMetricsService.snapshot());
        } catch (Exception e) {
            r.put("jvm", Map.of("error", e.getMessage()));
        }
        try {
            r.put("storage", apmQueryService.storageStatus());
        } catch (Exception e) {
            r.put("storage", Map.of("error", e.getMessage()));
        }
        try {
            r.put("ownThreads", apmQueryService.ownThreads());
        } catch (Exception e) {
            r.put("ownThreads", Map.of("error", e.getMessage()));
        }
        try {
            r.put("trend", jvmMetricsService.metrics(minutes));
        } catch (Exception e) {
            r.put("trend", List.of());
        }
        return ReturnResult.ok(r);
    }

    /**
     * 修改存储配置并立即生效（无需重启）。
     *
     * <p>只传需要修改的项，未传的保持原值。语义：</p>
     * <ul>
     *   <li>{@code type} — inmemory / sqlite / noop</li>
     *   <li>{@code path} — 落盘文件路径；传空串表示回到默认临时目录</li>
     *   <li>{@code retentionMs} / {@code capacity} — 保留期与单表容量</li>
     *   <li>{@code async} — 异步批量写开关</li>
     *   <li>{@code maintenanceIntervalMs} — 自维护周期（保留期清理 + 容量淘汰），下限 5000</li>
     *   <li>{@code flushTimeoutMs} — 关闭时等待落盘的最长时间</li>
     * </ul>
     *
     * <p>新存储先完整启动成功才替换旧存储；启动失败时旧存储继续工作，
     * 返回 {@code success=false} 并在 {@code message} 说明原因。</p>
     *
     * @param type                  存储类型
     * @param path                  落盘路径（空串表示默认目录）
     * @param retentionMs           数据保留毫秒
     * @param capacity              单表容量上限
     * @param async                 是否异步批量写
     * @param maintenanceIntervalMs 自维护周期（毫秒）
     * @param flushTimeoutMs        关闭时等待落盘的最长时间（毫秒）
     * @return 更新结果（含变更前后状态）
     */
    @PostMapping("/storage/config")
    public ReturnResult<Map<String, Object>> updateStorageConfig(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) Long retentionMs,
            @RequestParam(required = false) Integer capacity,
            @RequestParam(required = false) Boolean async,
            @RequestParam(required = false) Long maintenanceIntervalMs,
            @RequestParam(required = false) Long flushTimeoutMs) {
        com.chua.starter.hotsport.support.config.HotsportProperties.Storage storage =
                hotsportProperties.getApm().getStorage();
        if (type != null && !type.isBlank()) {
            String normalized = type.trim().toLowerCase(java.util.Locale.ROOT);
            if (!java.util.Set.of("inmemory", "sqlite", "noop").contains(normalized)) {
                return storageResult(false, "不支持的存储类型: " + type + "（可选 inmemory / sqlite / noop）");
            }
            storage.setType(normalized);
        }
        if (path != null) {
            // 空串是有意义的值：表示「回到默认目录」，不能当成「未传」忽略
            storage.setPath(path.trim());
        }
        if (retentionMs != null) {
            if (retentionMs <= 0) {
                return storageResult(false, "retentionMs 必须大于 0");
            }
            storage.setRetentionMs(retentionMs);
        }
        if (capacity != null) {
            if (capacity <= 0) {
                return storageResult(false, "capacity 必须大于 0");
            }
            storage.setCapacity(capacity);
        }
        if (async != null) {
            storage.setAsync(async);
        }
        if (maintenanceIntervalMs != null) {
            if (maintenanceIntervalMs < 5_000L) {
                return storageResult(false, "maintenanceIntervalMs 不能小于 5000（再快只会白烧 CPU）");
            }
            storage.setMaintenanceIntervalMs(maintenanceIntervalMs);
        }
        if (flushTimeoutMs != null) {
            if (flushTimeoutMs <= 0) {
                return storageResult(false, "flushTimeoutMs 必须大于 0");
            }
            storage.setFlushTimeoutMs(flushTimeoutMs);
        }
        Map<String, Object> result = apmQueryService.reloadStorage();
        return storageResult(Boolean.TRUE.equals(result.get("success")), String.valueOf(result.get("message")), result);
    }

    /**
     * 重新加载存储配置（配置来自外部修改，如配置中心推送后调用）。
     *
     * <p>与 {@code POST /storage/config} 的区别：本接口不修改任何参数，
     * 只让当前配置重新生效一次，适合配置对象被其他组件直接改写的场景。</p>
     *
     * @return 更新结果
     */
    @PostMapping("/storage/reload")
    public ReturnResult<Map<String, Object>> reloadStorageConfig() {
        Map<String, Object> result = apmQueryService.reloadStorage();
        return storageResult(Boolean.TRUE.equals(result.get("success")), String.valueOf(result.get("message")), result);
    }

    /**
     * 统一构造存储操作响应：成功走 {@code ok}，失败走业务错误码。
     *
     * <p>失败若也用 {@code ok} 返回，前端会当作成功处理（业务码为 0），
     * 用户看不到「配置没生效」，因此必须走 {@link ReturnResult#error}。</p>
     *
     * @param success 是否成功
     * @param message 提示信息
     * @return 响应
     */
    private ReturnResult<Map<String, Object>> storageResult(boolean success, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", success);
        body.put("message", message);
        return success ? ReturnResult.ok(body) : ReturnResult.error("APM_STORAGE", message, body);
    }

    /**
     * 统一构造存储操作响应（带完整结果体）。
     *
     * @param success 是否成功
     * @param message 提示信息
     * @param body    结果体
     * @return 响应
     */
    private ReturnResult<Map<String, Object>> storageResult(boolean success, String message,
                                                           Map<String, Object> body) {
        Map<String, Object> payload = body == null ? new LinkedHashMap<>() : body;
        payload.put("success", success);
        payload.put("message", message);
        return success ? ReturnResult.ok(payload) : ReturnResult.error("APM_STORAGE", message, payload);
    }

    /**
     * 更新 Agent 配置（运行时生效；仅内存态，重启回退配置文件）。
     *
     * @param samplingRate    采样率（0~100）
     * @param slowThresholdMs 慢调用阈值（ms）
     * @param logLevel        日志级别
     * @param injectMode      注入模式
     * @param enabled         是否启用 Agent
     * @return 保存后的配置
     */
    @PostMapping("/agent/config")
    public ReturnResult<Map<String, Object>> updateAgentConfig(
            @RequestParam(required = false) Integer samplingRate,
            @RequestParam(required = false) Integer slowThresholdMs,
            @RequestParam(required = false) String logLevel,
            @RequestParam(required = false) String injectMode,
            @RequestParam(required = false) Boolean enabled) {
        if (enabled != null) {
            agentAttachService.setAgentEnabled(enabled);
        }
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("samplingRate", samplingRate == null ? 100 : Math.max(0, Math.min(100, samplingRate)));
        cfg.put("slowThresholdMs", slowThresholdMs == null ? 500 : Math.max(10, slowThresholdMs));
        cfg.put("logLevel", logLevel != null && !logLevel.isBlank() ? logLevel : "INFO");
        cfg.put("injectMode", injectMode != null && !injectMode.isBlank() ? injectMode : "SIMULATE");
        cfg.put("enabled", enabled == null ? agentAttachService.isAgentEnabled() : enabled);
        cfg.put("updatedAt", System.currentTimeMillis());
        return ReturnResult.ok(cfg);
    }

    /**
     * Agent 启停。
     *
     * @param enabled 是否启用
     * @return 启停结果
     */
    @PostMapping("/agent/enabled")
    public ReturnResult<Map<String, Object>> setAgentEnabled(
            @RequestParam boolean enabled) {
        agentAttachService.setAgentEnabled(enabled);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", enabled);
        result.put("agentActive", agentAttachService.isAgentActive());
        return ReturnResult.ok(result);
    }

    /**
     * Agent 实时 Trace Span 列表（前端 Traces 页数据源）。
     *
     * @param limit 返回上限，默认 200
     * @return 传输记录 DTO 列表（含 traceId/spanId/className/methodName）
     */
    /**
     * Agent 实时 Trace Span 列表（Traces 页数据源，新事件在前）。
     *
     * @param limit     返回上限，默认 200
     * @param errorOnly 为 true 时只返回错误状态的 span
     * @return span DTO 列表（含 isError / shortError）
     */
    @GetMapping("/agent/traces")
    public ReturnResult<List<Map<String, Object>>> agentTraces(
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(defaultValue = "false") boolean errorOnly) {
        return ReturnResult.ok(apmQueryService.agentTraces(limit, errorOnly));
    }

    /**
     * 链路概览 — 按 traceId 聚合的扁平列表，每 trace 一行。
     *
     * @param limit     返回上限，默认 100
     * @param errorOnly 为 true 时只保留含错误 span 的链路
     * @return 链路概览列表
     */
    @GetMapping("/traces/summary")
    public ReturnResult<List<Map<String, Object>>> traceSummaries(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "false") boolean errorOnly) {
        return ReturnResult.ok(apmQueryService.traceSummaries(limit, errorOnly));
    }

    /**
     * Agent 实时依赖边列表（前端 Traces/Dependencies 页数据源）。
     *
     * @param limit 返回上限，默认 50
     * @return 依赖边 DTO 列表
     */
    @GetMapping("/agent/dependencies")
    public ReturnResult<List<Map<String, Object>>> agentDependencies(
            @RequestParam(defaultValue = "50") int limit) {
        return ReturnResult.ok(apmQueryService.agentDependencies(limit));
    }

    /**
     * 按 traceId 查询整条链路的全部 span（Trace 详情抽屉数据源）。
     *
     * @param traceId 链路 ID
     * @return span 列表（按开始时间升序）
     */
    @GetMapping("/trace/{traceId}")
    public ReturnResult<List<Map<String, Object>>> traceDetail(
            @PathVariable String traceId) {
        return ReturnResult.ok(apmQueryService.traceDetail(traceId));
    }

    /**
     * 按 traceId 查询瀑布数据（SkyWalking 风格横向时间轴）。
     *
     * @param traceId 链路 ID
     * @return 瀑布结构 { traceId, spans, totalMs, spanCount }
     */
    @GetMapping("/trace/{traceId}/waterfall")
    public ReturnResult<Map<String, Object>> traceWaterfall(
            @PathVariable String traceId) {
        return ReturnResult.ok(apmQueryService.traceWaterfall(traceId));
    }

    /**
     * 服务聚合（SkyWalking service 层）。
     *
     * @return 服务列表
     */
    @GetMapping("/services")
    public ReturnResult<List<Map<String, Object>>> services() {
        return ReturnResult.ok(apmQueryService.services());
    }

    /**
     * 端点聚合 + 分位数（SkyWalking endpoint 层）。
     *
     * @param limit   返回上限
     * @param keyword 端点关键字过滤（可空）
     * @return 端点列表
     */
    @GetMapping("/endpoints")
    public ReturnResult<List<Map<String, Object>>> endpoints(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String keyword) {
        return ReturnResult.ok(apmQueryService.endpoints(limit, keyword));
    }

    /**
     * 依赖边聚合（按 edgeId 归并，修复每事件 callCount=1 的存储侧问题）。
     *
     * @param size 返回上限
     * @return 聚合依赖边列表
     */
    @GetMapping("/dependencies/aggregated")
    public ReturnResult<List<Map<String, Object>>> dependenciesAggregated(
            @RequestParam(defaultValue = "100") int size) {
        return ReturnResult.ok(apmQueryService.dependenciesAggregated(size));
    }

    /**
     * 系统健康状态（SkyWalking 饱和度派生指标）。
     *
     * @return 健康 { health, qps, errorRate, activeLeaks, windowSeconds }
     */
    @GetMapping("/health")
    public ReturnResult<Map<String, Object>> health() {
        return ReturnResult.ok(apmQueryService.health());
    }

    /**
     * JVM 指标实时快照。
     *
     * @return JVM 快照 Map
     */
    @GetMapping("/jvm/snapshot")
    public ReturnResult<Map<String, Object>> jvmSnapshot() {
        return ReturnResult.ok(jvmMetricsService.snapshot());
    }

    /**
     * JVM 指标时序（最近 N 分钟）。
     *
     * @param minutes 时间窗（分钟），默认 30
     * @return 时序点列表
     */
    @GetMapping("/jvm/metrics")
    public ReturnResult<List<Map<String, Object>>> jvmMetrics(
            @RequestParam(defaultValue = "30") int minutes) {
        return ReturnResult.ok(jvmMetricsService.metrics(minutes));
    }

    /**
     * JVM 内存池对象明细（对象内存查看）。
     *
     * @return 各内存池 used/committed/max + 对象估算
     */
    @GetMapping("/jvm/memory/pools")
    public ReturnResult<Map<String, Object>> jvmMemoryPools() {
        return ReturnResult.ok(jvmMemoryService.memoryPools());
    }

    /**
     * 手动触发 GC 并返回释放量。
     *
     * @return GC 前后堆用量与差值
     */
    @PostMapping("/jvm/gc")
    public ReturnResult<Map<String, Object>> jvmGc() {
        return ReturnResult.ok(jvmMemoryService.gc());
    }

    /**
     * 类加载统计。
     *
     * @return 已加载/卸载类数量
     */
    @GetMapping("/jvm/class-loading")
    public ReturnResult<Map<String, Object>> jvmClassLoading() {
        return ReturnResult.ok(jvmMemoryService.classLoading());
    }

    /**
     * 全量线程 Dump 快照。
     *
     * @return 线程列表（含栈帧/锁信息）
     */
    @GetMapping("/jvm/thread-dump")
    public ReturnResult<List<Map<String, Object>>> jvmThreadDump() {
        return ReturnResult.ok(jvmMetricsService.threadDump());
    }

    /**
     * 系统拓扑（真实运行值）。
     *
     * @return 拓扑结构
     */
    @GetMapping("/system/topology")
    public ReturnResult<Map<String, Object>> systemTopology() {
        return ReturnResult.ok(apmQueryService.systemTopology());
    }

    /**
     * 链路树（trace → span 两级 ReTree 数据源）。
     *
     * @param limit trace 上限
     * @return trace 节点列表
     */
    @GetMapping("/agent/traces/tree")
    public ReturnResult<List<Map<String, Object>>> agentTracesTree(
            @RequestParam(defaultValue = "50") int limit) {
        return ReturnResult.ok(apmQueryService.agentTracesTree(limit));
    }

    /**
     * 告警事件（新事件在前）。
     *
     * @param limit 返回上限
     * @return 告警事件列表
     */
    @GetMapping("/alerts")
    public ReturnResult<List<Map<String, Object>>> alerts(
            @RequestParam(defaultValue = "100") int limit) {
        return ReturnResult.ok(alertMonitorService.alerts(limit));
    }

    /**
     * 告警规则列表。
     *
     * @return 规则列表
     */
    @GetMapping("/alerts/rules")
    public ReturnResult<List<Map<String, Object>>> alertRules() {
        return ReturnResult.ok(alertMonitorService.listRules().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.id);
            m.put("metric", r.metric);
            m.put("threshold", r.threshold);
            m.put("severity", r.severity);
            m.put("debounceMs", r.debounceMs);
            m.put("enabled", r.enabled);
            return m;
        }).collect(Collectors.toList()));
    }

    /**
     * 新增告警规则。
     *
     * @param metric     指标键
     * @param threshold  阈值
     * @param severity   严重级别（CRITICAL / WARNING）
     * @param debounceMs 去抖毫秒
     * @param enabled    是否启用
     * @return 保存后的规则
     */
    @PostMapping("/alerts/rules")
    public ReturnResult<Map<String, Object>> addAlertRule(
            @RequestParam String metric, @RequestParam double threshold,
            @RequestParam(defaultValue = "WARNING") String severity,
            @RequestParam(defaultValue = "5000") long debounceMs,
            @RequestParam(defaultValue = "true") boolean enabled) {
        AlertMonitorService.AlertRule rule =
                new AlertMonitorService.AlertRule(0, metric, threshold, severity, debounceMs, enabled);
        AlertMonitorService.AlertRule saved = alertMonitorService.addRule(rule);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", saved.id);
        m.put("metric", saved.metric);
        m.put("threshold", saved.threshold);
        m.put("severity", saved.severity);
        m.put("debounceMs", saved.debounceMs);
        m.put("enabled", saved.enabled);
        return ReturnResult.ok(m);
    }

    /**
     * 启停告警规则。
     *
     * @param metric  指标键
     * @param enabled 是否启用
     */
    @PostMapping("/alerts/rules/toggle")
    public ReturnResult<Void> toggleAlertRule(
            @RequestParam String metric, @RequestParam boolean enabled) {
        alertMonitorService.toggleRule(metric, enabled);
        return ReturnResult.ok(null);
    }

    /**
     * 删除告警规则。
     *
     * @param metric 指标键
     */
    @DeleteMapping("/alerts/rules/{metric}")
    public ReturnResult<Void> deleteAlertRule(
            @PathVariable String metric) {
        alertMonitorService.deleteRule(metric);
        return ReturnResult.ok(null);
    }

    /**
     * 确认告警事件（OPEN → ACKNOWLEDGED）。
     *
     * @param id 事件 ID
     * @return 是否处置成功
     */
    @PostMapping("/alerts/{id}/acknowledge")
    public ReturnResult<Boolean> acknowledgeAlert(
            @PathVariable long id) {
        return ReturnResult.ok(alertMonitorService.acknowledgeEvent(id, "ACKNOWLEDGED"));
    }

    /**
     * 静默告警事件（OPEN → MUTED）。
     *
     * @param id 事件 ID
     * @return 是否处置成功
     */
    @PostMapping("/alerts/{id}/mute")
    public ReturnResult<Boolean> muteAlert(
            @PathVariable long id,
            @RequestParam(defaultValue = "300000") long durationMs) {
        return ReturnResult.ok(alertMonitorService.silenceEvent(id, durationMs));
    }

    /**
     * 忽略告警事件（OPEN → IGNORED）。
     *
     * @param id 事件 ID
     * @return 是否处置成功
     */
    @PostMapping("/alerts/{id}/ignore")
    public ReturnResult<Boolean> ignoreAlert(
            @PathVariable long id) {
        return ReturnResult.ok(alertMonitorService.acknowledgeEvent(id, "IGNORED"));
    }

    /**
     * 慢调用排行（按耗时降序）。
     *
     * @param limit 返回上限
     * @return 慢调用列表
     */
    @GetMapping("/slow-calls")
    public ReturnResult<List<Map<String, Object>>> slowCalls(
            @RequestParam(defaultValue = "20") int limit) {
        return ReturnResult.ok(apmQueryService.slowCalls(limit));
    }

    /**
     * 慢 SQL 分析（按 SQL 协议 + 耗时聚合）。
     *
     * @param limit     返回上限
     * @param minDurationMs 最小耗时阈值
     * @return 慢 SQL 列表
     */
    @GetMapping("/slow-sql")
    public ReturnResult<List<Map<String, Object>>> slowSql(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "200") long minDurationMs) {
        return ReturnResult.ok(apmQueryService.slowSql(limit, minDurationMs));
    }

    /**
     * 错误排行（按错误类型 + 消息聚合）。
     *
     * @param limit 返回上限
     * @return 错误排行列表
     */
    @GetMapping("/error-ranking")
    public ReturnResult<List<Map<String, Object>>> errorRanking(
            @RequestParam(defaultValue = "20") int limit) {
        return ReturnResult.ok(apmQueryService.errorRanking(limit));
    }

    /**
     * SLA 统计。
     *
     * @return SLA 统计 Map
     */
    @GetMapping("/sla")
    public ReturnResult<Map<String, Object>> sla() {
        return ReturnResult.ok(apmQueryService.slaStats());
    }

    /**
     * 调用量 / 耗时趋势（按时间桶聚合，Dashboard 折线图）。
     *
     * @param minutes       统计窗口（分钟）
     * @param bucketSeconds 桶宽（秒）
     * @return 趋势点列表
     */
    @GetMapping("/trend")
    public ReturnResult<List<Map<String, Object>>> trend(
            @RequestParam(defaultValue = "30") int minutes,
            @RequestParam(defaultValue = "60") int bucketSeconds) {
        return ReturnResult.ok(apmQueryService.trend(minutes, bucketSeconds));
    }

    /**
     * 日志等级分布。
     *
     * @return 等级统计列表
     */
    @GetMapping("/log-stats")
    public ReturnResult<List<Map<String, Object>>> logStats() {
        return ReturnResult.ok(apmQueryService.logStats());
    }

    /**
     * 端点时序统计（按「时间桶 × 端点」聚合）。
     *
     * @param service       服务名过滤（空表示全部）
     * @param minutes       统计窗口（分钟）
     * @param bucketSeconds 桶宽（秒）
     * @return 端点点序列
     */
    @GetMapping("/endpoint-stats")
    public ReturnResult<List<Map<String, Object>>> endpointStats(
            @RequestParam(defaultValue = "") String service,
            @RequestParam(defaultValue = "60") int minutes,
            @RequestParam(defaultValue = "60") int bucketSeconds) {
        return ReturnResult.ok(apmQueryService.endpointStats(service, minutes, bucketSeconds));
    }

    /**
     * 协议分布统计。
     *
     * @return 协议统计列表
     */
    @GetMapping("/protocol-stats")
    public ReturnResult<List<Map<String, Object>>> protocolStats() {
        return ReturnResult.ok(apmQueryService.protocolStats());
    }

    /**
     * 线程池视图（JVM 实时线程按池分组）。
     *
     * @return 线程池列表
     */
    @GetMapping("/thread-pools")
    public ReturnResult<List<Map<String, Object>>> threadPools() {
        return ReturnResult.ok(apmQueryService.threadPools());
    }

    /**
     * 连接池近似视图（按目标地址聚合调用）。
     *
     * @return 连接统计列表
     */
    @GetMapping("/connection-pools")
    public ReturnResult<List<Map<String, Object>>> connectionPools() {
        return ReturnResult.ok(apmQueryService.connectionPools());
    }

    /**
     * 数据清理（按保留时长删除过期记录）。
     *
     * @param retentionMs 保留时长（毫秒）
     * @return 清理统计
     */
    @PostMapping("/cleanup")
    public ReturnResult<Map<String, Object>> cleanup(
            @RequestParam(defaultValue = "86400000") long retentionMs) {
        return ReturnResult.ok(apmQueryService.cleanup(retentionMs));
    }

    // ------------------------------------------------------------------
    // 数据注入（增强）— 页面操作产生真实数据
    // ------------------------------------------------------------------

    /**
     * 注入一条传输记录（自动聚合进依赖拓扑图）。
     *
     * @param source    源端（host:port）
     * @param target    目标端（host:port）
     * @param protocol  协议（HTTP/REDIS/MYSQL/GRPC/KAFKA…）
     * @param operation 操作描述
     * @param error     是否为错误调用
     * @return 注入结果
     */
    @PostMapping("/inject/transmission")
    public ReturnResult<Map<String, Object>> injectTransmission(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String target,
            @RequestParam(required = false) String protocol,
            @RequestParam(required = false) String operation,
            @RequestParam(defaultValue = "false") boolean error) {
        return ReturnResult.ok(apmInjectService.injectTransmission(source, target, protocol, operation, error));
    }

    /**
     * 批量注入随机调用（多条依赖边 + 传输记录）。
     *
     * @param count 条数（1~200）
     * @return 注入结果
     */
    @PostMapping("/inject/batch")
    public ReturnResult<Map<String, Object>> injectBatch(
            @RequestParam(defaultValue = "10") int count) {
        return ReturnResult.ok(apmInjectService.injectBatch(count));
    }

    /**
     * 注入一条资源泄漏记录。
     *
     * @param resourceType 类型（File/Socket/Connection/Thread）
     * @param resourceName 资源名
     * @return 注入结果
     */
    @PostMapping("/inject/leak")
    public ReturnResult<Map<String, Object>> injectLeak(
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceName) {
        return ReturnResult.ok(apmInjectService.injectLeak(resourceType, resourceName));
    }

    /**
     * 注入一条日志记录。
     *
     * @param level   级别（INFO/WARN/ERROR/DEBUG）
     * @param message 消息
     * @return 注入结果
     */
    @PostMapping("/inject/log")
    public ReturnResult<Map<String, Object>> injectLog(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String message) {
        return ReturnResult.ok(apmInjectService.injectLog(level, message));
    }

    /**
     * 注入慢调用（验证慢调用排行）。
     *
     * @param source     源端 host:port
     * @param target     目标端 host:port
     * @param protocol   协议名
     * @param durationMs 耗时（毫秒）
     * @param error      是否标记错误
     * @return 注入结果
     */
    @PostMapping("/inject/slow")
    public ReturnResult<Map<String, Object>> injectSlow(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String target,
            @RequestParam(required = false) String protocol,
            @RequestParam(required = false) Integer durationMs,
            @RequestParam(defaultValue = "false") boolean error) {
        return ReturnResult.ok(apmInjectService.injectSlowCall(source, target, protocol, durationMs, error));
    }

    /**
     * 注入 Span 树（验证链路瀑布）。
     *
     * @param traceId   自定义 traceId
     * @param spans     期望 span 数
     * @param depth     树深度
     * @param errorSpan 是否在叶子注入错误
     * @return 注入结果
     */
    @PostMapping("/inject/span-tree")
    public ReturnResult<Map<String, Object>> injectSpanTree(
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) Integer spans,
            @RequestParam(required = false) Integer depth,
            @RequestParam(defaultValue = "false") boolean errorSpan) {
        return ReturnResult.ok(apmInjectService.injectSpanTree(traceId, spans, depth, errorSpan));
    }

    /**
     * 注入 JVM 压力（制造线程 / CPU 变化）。
     *
     * @param threads    线程数
     * @param cpuPercent CPU 目标百分比
     * @param durationMs 持续毫秒
     * @return 注入结果
     */
    @PostMapping("/inject/jvm-pressure")
    public ReturnResult<Map<String, Object>> injectJvmPressure(
            @RequestParam(required = false) Integer threads,
            @RequestParam(required = false) Integer cpuPercent,
            @RequestParam(required = false) Integer durationMs) {
        return ReturnResult.ok(apmInjectService.injectJvmPressure(threads, cpuPercent, durationMs));
    }

    // ------------------------------------------------------------------
    // 全量 CSV 导出（增强）— 直接下载文件，不受分页限制
    // ------------------------------------------------------------------

    /**
     * 全量导出传输记录 CSV。
     *
     * @param query    过滤条件参数对象（分页字段忽略）
     * @param response HTTP 响应（直写 CSV 下载流）
     * @throws IOException 响应流写出失败时
     */
    @GetMapping("/export/transmissions")
    public void exportTransmissions(TransmissionQuery query,
                                    HttpServletResponse response) throws IOException {
        String csv = apmQueryService.exportTransmissionsCsv(query);
        writeCsvAttachment(response, "transmissions-" + System.currentTimeMillis() + ".csv", csv);
    }

    /**
     * 全量导出资源泄漏 CSV。
     *
     * @param query    过滤条件参数对象（分页字段忽略）
     * @param response HTTP 响应（直写 CSV 下载流）
     * @throws IOException 响应流写出失败时
     */
    @GetMapping("/export/leaks")
    public void exportLeaks(LeakQuery query,
                            HttpServletResponse response) throws IOException {
        String csv = apmQueryService.exportLeaksCsv(query);
        writeCsvAttachment(response, "leaks-" + System.currentTimeMillis() + ".csv", csv);
    }

    /**
     * 全量导出日志 CSV。
     *
     * @param query    过滤条件参数对象（分页字段忽略）
     * @param response HTTP 响应（直写 CSV 下载流）
     * @throws IOException 响应流写出失败时
     */
    @GetMapping("/export/logs")
    public void exportLogs(LogQuery query,
                           HttpServletResponse response) throws IOException {
        String csv = apmQueryService.exportLogsCsv(query);
        writeCsvAttachment(response, "logs-" + System.currentTimeMillis() + ".csv", csv);
    }

    /**
     * 直接写 CSV 下载响应（绕过消息转换器；UTF-8 BOM 保证 Excel 中文不乱码）。
     */
    private static void writeCsvAttachment(HttpServletResponse response,
                                           String filename, String csv) throws IOException {
        response.setStatus(200);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        response.setContentLength(bom.length + body.length);
        try (OutputStream os = response.getOutputStream()) {
            os.write(bom);
            os.write(body);
            os.flush();
        }
    }

    // ------------------------------------------------------------------
    // SSE 实时流（增强）
    // ------------------------------------------------------------------

    /**
     * SSE 实时统计流。
     *
     * <p>每 {@code intervalMs} 毫秒推送一次 {@code data} 事件（stats 快照），
     * 前端用 EventSource 接收并实时刷新看板，替代 HTTP 轮询。</p>
     *
     * @param intervalMs 推送间隔（毫秒），默认 3000，钳制到 [1000, 60000]
     * @return SSE 流
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(defaultValue = "3000") long intervalMs) {
        long effective = Math.max(1000, Math.min(60000, intervalMs));
        SseEmitter emitter = new SseEmitter(5L * 60 * 1000);
        ScheduledFuture<?> task = sseExecutor.scheduleAtFixedRate(() -> {
            try {
                Map<String, Object> stats = apmQueryService.stats();
                // 并入健康/饱和度指标（前端 watchStats 读 stats.health/qps/errorRate）
                Map<String, Object> health = apmQueryService.health();
                stats.putAll(health);
                // 并入最近告警（前端 Dashboard 可选消费）
                stats.put("recentAlerts", alertMonitorService.recentAlerts());
                String json = objectMapper.writeValueAsString(Map.of("ts", System.currentTimeMillis(), "data", stats));
                emitter.send(SseEmitter.event().name("stats").data(json, MediaType.TEXT_PLAIN));
            } catch (IOException e) {
                emitter.complete();
            }
        }, 0, effective, TimeUnit.MILLISECONDS);
        emitter.onCompletion(() -> task.cancel(true));
        emitter.onTimeout(() -> {
            task.cancel(false);
            emitter.complete();
        });
        return emitter;
    }

    /**
     * 实时日志流（SSE）— 按序号游标增量推送系统输出日志。
     *
     * <p>与 {@code /stream}（推 stats）不同，这里专门推日志行。客户端传 {@code after}
     * 作为起始游标（0 = 从最新开始），每次推送增量条目与该时刻的最大序号
     * （{@code lastSeq}），下个请求把它传回来即可无重复续读。</p>
     *
     * @param after 起始序号游标，默认 0（只推新产生的日志）
     * @return SSE 事件流（name = log）
     */
    @GetMapping(value = "/stream/logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(@RequestParam(defaultValue = "0") long after) {
        SseEmitter emitter = new SseEmitter(5L * 60 * 1000);
        // 游标由服务端推进：下次推送用本次 lastSeq，天然无重复
        final long[] cursor = {Math.max(0L, after)};
        ScheduledFuture<?> task = sseExecutor.scheduleAtFixedRate(() -> {
            try {
                Map<String, Object> delta = apmQueryService.systemOutAfter(cursor[0]);
                List<?> items = (List<?>) delta.get("items");
                long lastSeq = ((Number) delta.get("lastSeq")).longValue();
                cursor[0] = lastSeq;
                Map<String, Object> payload = Map.of(
                        "ts", System.currentTimeMillis(),
                        "items", items,
                        "lastSeq", lastSeq);
                String json = objectMapper.writeValueAsString(payload);
                emitter.send(SseEmitter.event().name("log").data(json, MediaType.TEXT_PLAIN));
            } catch (IOException e) {
                emitter.complete();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        emitter.onCompletion(() -> task.cancel(true));
        emitter.onTimeout(() -> {
            task.cancel(false);
            emitter.complete();
        });
        return emitter;
    }

    /**
     * 一键导出诊断包 — 聚合线程/内存/慢调用/错误/句柄/链路等当前状态。
     *
     * @return JSON 诊断包
     */
    @GetMapping("/diagnostics/bundle")
    public ReturnResult<Map<String, Object>> diagnosticBundle() {
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("exportedAt", System.currentTimeMillis());
        bundle.put("agent", agentAttachService.getStatus());
        bundle.put("stats", apmQueryService.stats());
        try { bundle.put("health", apmQueryService.health()); } catch (Exception e) { bundle.put("health", Map.of("error", e.getMessage())); }
        try { bundle.put("jvm", jvmMetricsService.snapshot()); } catch (Exception e) { bundle.put("jvm", Map.of("error", e.getMessage())); }
        try { bundle.put("threadDump", jvmMetricsService.threadDump()); } catch (Exception e) { bundle.put("threadDump", List.of()); }
        try { bundle.put("memoryPools", jvmMemoryService.memoryPools()); } catch (Exception e) { bundle.put("memoryPools", Map.of("error", e.getMessage())); }
        try { bundle.put("classLoading", jvmMemoryService.classLoading()); } catch (Exception e) { bundle.put("classLoading", Map.of("error", e.getMessage())); }
        try { bundle.put("slowCalls", apmQueryService.slowCalls(20)); } catch (Exception e) { bundle.put("slowCalls", List.of()); }
        try { bundle.put("slowSql", apmQueryService.slowSql(20, 200)); } catch (Exception e) { bundle.put("slowSql", List.of()); }
        try { bundle.put("errorRanking", apmQueryService.errorRanking(20)); } catch (Exception e) { bundle.put("errorRanking", List.of()); }
        try { bundle.put("handles", apmQueryService.handleInventory(100)); } catch (Exception e) { bundle.put("handles", Map.of("error", e.getMessage())); }
        try { bundle.put("systemOut", apmQueryService.systemOut(100)); } catch (Exception e) { bundle.put("systemOut", List.of()); }
        try { bundle.put("traceHotspots", apmQueryService.traceHotspots(20)); } catch (Exception e) { bundle.put("traceHotspots", List.of()); }
        try { bundle.put("tracePhases", apmQueryService.tracePhases()); } catch (Exception e) { bundle.put("tracePhases", List.of()); }
        try { bundle.put("skywalking", apmQueryService.skywalkingExport()); } catch (Exception e) { bundle.put("skywalking", List.of()); }
        try { bundle.put("alerts", alertMonitorService.recentAlerts()); } catch (Exception e) { bundle.put("alerts", List.of()); }
        return ReturnResult.ok(bundle);
    }

    /**
     * 容器销毁时关闭推送线程。
    */
    @PreDestroy
    public void shutdownSse() {
        sseExecutor.shutdownNow();
    }
}
