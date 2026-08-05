package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsMonitor;
import com.chua.starter.monitor.support.entity.MsMonitorConfig;
import com.chua.starter.monitor.support.entity.MsMonitorGateway;
import com.chua.starter.monitor.support.entity.MsMonitorMetricConfig;
import com.chua.starter.monitor.support.entity.MsMonitorTask;
import com.chua.starter.monitor.support.enums.ServiceType;
import com.chua.starter.monitor.support.registry.ProviderRegistry;
import com.chua.starter.monitor.support.service.MonitorConfigService;
import com.chua.starter.monitor.support.service.MonitorGatewayService;
import com.chua.starter.monitor.support.service.MonitorMetricConfigService;
import com.chua.starter.monitor.support.service.MonitorService;
import com.chua.starter.monitor.support.service.MonitorTaskService;
import com.chua.starter.monitor.support.spi.CollectContext;
import com.chua.starter.monitor.support.spi.OperationProvider;
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
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 监控模块 REST 接口
 *
 * @author CH
 * @since 2026/8/4
 */
@Tag(name = "监控管理", description = "资产/任务/指标/Redis操作/AI分析")
@RestController
@RequestMapping("/ms/monitor")
@RequiredArgsConstructor
public class MonitorController {

    /** 资产 Service */
    private final MonitorService monitorService;
    /** 任务 Service */
    private final MonitorTaskService taskService;
    /** 指标配置 Service */
    private final MonitorMetricConfigService metricConfigService;
    /** 全局配置 Service */
    private final MonitorConfigService configService;
    /** 网关 Service */
    private final MonitorGatewayService gatewayService;
    /** Provider 注册中心 */
    private final ProviderRegistry providerRegistry;

    // ==================== 资产 CRUD ====================

    @Operation(summary = "新增资产")
    @PostMapping
    public ReturnResult<MsMonitor> createMonitor(@RequestBody MsMonitor monitor) {
        return monitorService.create(monitor);
    }

    @Operation(summary = "更新资产")
    @PutMapping("/{id}")
    public ReturnResult<MsMonitor> updateMonitor(@PathVariable Long id, @RequestBody MsMonitor monitor) {
        monitor.setId(id);
        return monitorService.update(monitor);
    }

    @Operation(summary = "删除资产")
    @DeleteMapping("/{id}")
    public ReturnResult<Void> deleteMonitor(@PathVariable Long id) {
        return monitorService.delete(id);
    }

    @Operation(summary = "查询单个资产")
    @GetMapping("/{id}")
    public ReturnResult<MsMonitor> getMonitor(@PathVariable Long id) {
        return monitorService.findById(id);
    }

    @Operation(summary = "资产列表")
    @GetMapping
    public ReturnResult<List<MsMonitor>> listMonitor() {
        return monitorService.listAll();
    }

    // ==================== 任务 CRUD ====================

    @Operation(summary = "新增任务")
    @PostMapping("/task")
    public ReturnResult<MsMonitorTask> createTask(@RequestBody MsMonitorTask task) {
        return taskService.create(task);
    }

    @Operation(summary = "更新任务")
    @PutMapping("/task/{id}")
    public ReturnResult<MsMonitorTask> updateTask(@PathVariable Long id, @RequestBody MsMonitorTask task) {
        task.setId(id);
        return taskService.update(task);
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/task/{id}")
    public ReturnResult<Void> deleteTask(@PathVariable Long id) {
        return taskService.delete(id);
    }

    @Operation(summary = "查询单个任务")
    @GetMapping("/task/{id}")
    public ReturnResult<MsMonitorTask> getTask(@PathVariable Long id) {
        return taskService.findById(id);
    }

    @Operation(summary = "按主机查询任务")
    @GetMapping("/task")
    public ReturnResult<List<MsMonitorTask>> listTask(@RequestParam(required = false) Long monitorId) {
        if (monitorId != null) {
            return taskService.listByMonitor(monitorId);
        }
        return taskService.listEnabled();
    }

    // ==================== 解析规则 ====================

    @Operation(summary = "新增解析规则")
    @PostMapping("/metric-config")
    public ReturnResult<MsMonitorMetricConfig> createMetricConfig(@RequestBody MsMonitorMetricConfig config) {
        return metricConfigService.create(config);
    }

    @Operation(summary = "删除解析规则")
    @DeleteMapping("/metric-config/{id}")
    public ReturnResult<Void> deleteMetricConfig(@PathVariable Long id) {
        return metricConfigService.delete(id);
    }

    @Operation(summary = "按任务查询解析规则")
    @GetMapping("/metric-config")
    public ReturnResult<List<MsMonitorMetricConfig>> listMetricConfig(@RequestParam Long taskId) {
        return metricConfigService.listByTask(taskId);
    }

    // ==================== 全局配置 ====================

    @Operation(summary = "获取所有配置")
    @GetMapping("/config")
    public ReturnResult<Map<String, String>> getAllConfig() {
        return configService.getAll();
    }

    @Operation(summary = "获取单个配置")
    @GetMapping("/config/{key}")
    public ReturnResult<String> getConfig(@PathVariable String key) {
        return ReturnResult.ok(configService.getString(key));
    }

    @Operation(summary = "写入配置")
    @PostMapping("/config")
    public ReturnResult<MsMonitorConfig> upsertConfig(@RequestBody MsMonitorConfig config) {
        return configService.upsert(config);
    }

    // ==================== 网关 ====================

    @Operation(summary = "新增网关")
    @PostMapping("/gateway")
    public ReturnResult<MsMonitorGateway> createGateway(@RequestBody MsMonitorGateway gateway) {
        return gatewayService.create(gateway);
    }

    @Operation(summary = "删除网关")
    @DeleteMapping("/gateway/{id}")
    public ReturnResult<Void> deleteGateway(@PathVariable Long id) {
        return gatewayService.delete(id);
    }

    @Operation(summary = "按主机查询网关")
    @GetMapping("/gateway")
    public ReturnResult<List<MsMonitorGateway>> listGateway(@RequestParam Long monitorId) {
        return gatewayService.listByMonitor(monitorId);
    }

    // ==================== Redis 操作 ====================

    @Operation(summary = "扫描 Redis Keys")
    @GetMapping("/operation/redis/{taskId}/keys")
    public Mono<OperationProvider.ScanResult> scanKeys(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "*") String pattern) {
        MsMonitorTask task = taskService.findById(taskId).getData();
        if (task == null) {
            return Mono.error(new IllegalArgumentException("任务不存在"));
        }
        Optional<OperationProvider> op = providerRegistry.resolveOperation(ServiceType.REDIS);
        if (op.isEmpty()) {
            return Mono.error(new UnsupportedOperationException("未找到 Redis 操作 Provider"));
        }
        MsMonitor monitor = monitorService.findById(task.getMonitorId()).getData();
        CollectContext ctx = CollectContext.builder().task(task).monitor(monitor).build();
        return op.get().scanKeys(ctx, pattern, null, 100);
    }

    @Operation(summary = "读取 Redis Key")
    @GetMapping("/operation/redis/{taskId}/kv/{key}")
    public Mono<String> getValue(@PathVariable Long taskId, @PathVariable String key) {
        return resolveOperation(taskId).flatMap(op -> {
            CollectContext ctx = buildContext(taskId);
            return op.getValue(ctx, key);
        });
    }

    @Operation(summary = "写入 Redis Key")
    @PutMapping("/operation/redis/{taskId}/kv/{key}")
    public Mono<Boolean> setValue(@PathVariable Long taskId, @PathVariable String key,
                                   @RequestBody String value,
                                   @RequestParam(required = false) Integer ttl) {
        return resolveOperation(taskId).flatMap(op -> {
            CollectContext ctx = buildContext(taskId);
            Duration duration = ttl != null ? Duration.ofSeconds(ttl) : null;
            return op.setValue(ctx, key, value, duration);
        });
    }

    @Operation(summary = "删除 Redis Keys")
    @DeleteMapping("/operation/redis/{taskId}/keys")
    public Mono<Long> deleteKeys(@PathVariable Long taskId, @RequestBody List<String> keys) {
        return resolveOperation(taskId).flatMap(op -> {
            CollectContext ctx = buildContext(taskId);
            return op.deleteKeys(ctx, keys);
        });
    }

    @Operation(summary = "设置过期时间")
    @PostMapping("/operation/redis/{taskId}/expire/{key}")
    public Mono<Boolean> expire(@PathVariable Long taskId, @PathVariable String key,
                                 @RequestParam long ttlSeconds) {
        return resolveOperation(taskId).flatMap(op -> {
            CollectContext ctx = buildContext(taskId);
            return op.expire(ctx, key, Duration.ofSeconds(ttlSeconds));
        });
    }

    /**
     * 解析 Redis 操作 Provider
     *
     * @param taskId 任务 id
     * @return 响应式操作 Provider
     */
    private Mono<OperationProvider> resolveOperation(Long taskId) {
        MsMonitorTask task = taskService.findById(taskId).getData();
        if (task == null) {
            return Mono.error(new IllegalArgumentException("任务不存在"));
        }
        Optional<OperationProvider> op = providerRegistry.resolveOperation(ServiceType.REDIS);
        if (op.isEmpty()) {
            return Mono.error(new UnsupportedOperationException("未找到 Redis 操作 Provider"));
        }
        return Mono.just(op.get());
    }

    /**
     * 构建采集上下文
     *
     * @param taskId 任务 id
     * @return 采集上下文
     */
    private CollectContext buildContext(Long taskId) {
        MsMonitorTask task = taskService.findById(taskId).getData();
        MsMonitor monitor = task != null ? monitorService.findById(task.getMonitorId()).getData() : null;
        return CollectContext.builder().task(task).monitor(monitor).build();
    }
}