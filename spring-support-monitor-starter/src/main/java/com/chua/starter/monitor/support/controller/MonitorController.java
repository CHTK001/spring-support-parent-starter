package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsMonitor;
import com.chua.starter.monitor.support.entity.MsMonitorConfig;
import com.chua.starter.monitor.support.entity.MsMonitorGateway;
import com.chua.starter.monitor.support.entity.MsMonitorMetric;
import com.chua.starter.monitor.support.entity.MsMonitorMetricCpu;
import com.chua.starter.monitor.support.entity.MsMonitorMetricConfig;
import com.chua.starter.monitor.support.entity.MsMonitorMetricDisk;
import com.chua.starter.monitor.support.entity.MsMonitorMetricJvm;
import com.chua.starter.monitor.support.entity.MsMonitorMetricMemory;
import com.chua.starter.monitor.support.entity.MsMonitorMetricNetwork;
import com.chua.starter.monitor.support.entity.MsMonitorMetricStatistics;
import com.chua.starter.monitor.support.entity.MsMonitorRedisMetric;
import com.chua.starter.monitor.support.entity.MsMonitorRedisMetricStatistics;
import com.chua.starter.monitor.support.entity.MsMonitorTask;
import com.chua.starter.monitor.support.enums.ServiceType;
import com.chua.starter.monitor.support.registry.ProviderRegistry;
import com.chua.starter.monitor.support.service.MonitorConfigService;
import com.chua.starter.monitor.support.service.MonitorGatewayService;
import com.chua.starter.monitor.support.service.MonitorMetricConfigService;
import com.chua.starter.monitor.support.service.MetricService;
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
 * @since 2026/8/4
 * @author CH
 */
@Tag(name = "监控管理", description = "资产/任务/指标/Redis操作/AI分析")
@RestController
@RequestMapping("/ms/monitor")
@RequiredArgsConstructor
public class MonitorController {

    /** 资产 Service */
    /** Monitor服务 */
    private final MonitorService monitorService;
    /** 任务 Service */
    /** 任务服务 */
    private final MonitorTaskService taskService;
    /** 指标配置 Service */
    /** 指标配置服务 */
    private final MonitorMetricConfigService metricConfigService;
    /** 全局配置 Service */
    /** 配置服务 */
    private final MonitorConfigService configService;
    /** 网关 Service */
    /** Gateway服务 */
    private final MonitorGatewayService gatewayService;
    /** 指标 Service */
    /** 指标服务 */
    private final MetricService metricService;
    /** Provider 注册中心 */
    /** 提供者注册表 */
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

    @Operation(summary = "设置任务启用/禁用")
    @PutMapping("/task/{id}/enabled")
    public ReturnResult<Void> setTaskEnabled(@PathVariable Long id, @RequestParam Integer enabled) {
        return taskService.setEnabled(id, enabled);
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

    @Operation(summary = "更新解析规则")
    @PutMapping("/metric-config/{id}")
    public ReturnResult<MsMonitorMetricConfig> updateMetricConfig(@PathVariable Long id, @RequestBody MsMonitorMetricConfig config) {
        config.setId(id);
        return metricConfigService.update(config);
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

    @Operation(summary = "更新网关")
    @PutMapping("/gateway/{id}")
    public ReturnResult<MsMonitorGateway> updateGateway(@PathVariable Long id, @RequestBody MsMonitorGateway gateway) {
        gateway.setId(id);
        return gatewayService.update(gateway);
    }

    @Operation(summary = "按主机查询网关")
    @GetMapping("/gateway")
    public ReturnResult<List<MsMonitorGateway>> listGateway(@RequestParam Long monitorId) {
        return gatewayService.listByMonitor(monitorId);
    }

    // ==================== 指标查询 ====================

    @Operation(summary = "获取最新 CPU 指标")
    @GetMapping("/metric/cpu/{monitorId}")
    public ReturnResult<MsMonitorMetricCpu> getLatestCpu(@PathVariable Long monitorId) {
        return metricService.getLatestCpu(monitorId);
    }

    @Operation(summary = "获取最新内存指标")
    @GetMapping("/metric/memory/{monitorId}")
    public ReturnResult<MsMonitorMetricMemory> getLatestMemory(@PathVariable Long monitorId) {
        return metricService.getLatestMemory(monitorId);
    }

    @Operation(summary = "获取最新磁盘指标")
    @GetMapping("/metric/disk/{monitorId}")
    public ReturnResult<List<MsMonitorMetricDisk>> getLatestDisk(@PathVariable Long monitorId) {
        return metricService.getLatestDisk(monitorId);
    }

    @Operation(summary = "获取最新网络指标")
    @GetMapping("/metric/network/{monitorId}")
    public ReturnResult<MsMonitorMetricNetwork> getLatestNetwork(@PathVariable Long monitorId) {
        return metricService.getLatestNetwork(monitorId);
    }

    @Operation(summary = "获取最新 JVM 指标")
    @GetMapping("/metric/jvm/{monitorId}")
    public ReturnResult<MsMonitorMetricJvm> getLatestJvm(@PathVariable Long monitorId) {
        return metricService.getLatestJvm(monitorId);
    }

    @Operation(summary = "获取 CPU 历史指标")
    @GetMapping("/metric/cpu/history/{monitorId}")
    public ReturnResult<List<MsMonitorMetricCpu>> getCpuHistory(
            @PathVariable Long monitorId,
            @RequestParam(required = false) Long hours) {
        java.time.LocalDateTime start = hours != null ? java.time.LocalDateTime.now().minusHours(hours) : null;
        return metricService.getCpuHistory(monitorId, start, null);
    }

    @Operation(summary = "获取内存历史指标")
    @GetMapping("/metric/memory/history/{monitorId}")
    public ReturnResult<List<MsMonitorMetricMemory>> getMemoryHistory(
            @PathVariable Long monitorId,
            @RequestParam(required = false) Long hours) {
        java.time.LocalDateTime start = hours != null ? java.time.LocalDateTime.now().minusHours(hours) : null;
        return metricService.getMemoryHistory(monitorId, start, null);
    }

    @Operation(summary = "获取 JVM 历史指标")
    @GetMapping("/metric/jvm/history/{monitorId}")
    public ReturnResult<List<MsMonitorMetricJvm>> getJvmHistory(
            @PathVariable Long monitorId,
            @RequestParam(required = false) Long hours) {
        java.time.LocalDateTime start = hours != null ? java.time.LocalDateTime.now().minusHours(hours) : null;
        return metricService.getJvmHistory(monitorId, start, null);
    }

    @Operation(summary = "获取网络历史指标")
    @GetMapping("/metric/network/history/{monitorId}")
    public ReturnResult<List<MsMonitorMetricNetwork>> getNetworkHistory(
            @PathVariable Long monitorId,
            @RequestParam(required = false) Long hours) {
        java.time.LocalDateTime start = hours != null ? java.time.LocalDateTime.now().minusHours(hours) : null;
        return metricService.getNetworkHistory(monitorId, start, null);
    }

    /**
     * 获取磁盘历史指标
     *
     * @param monitorId 监控 ID
     * @param hours     过去 N 小时（可选）
     * @return 磁盘历史指标列表
     */
    @Operation(summary = "获取磁盘历史指标")
    @GetMapping("/metric/disk/history/{monitorId}")
    public ReturnResult<List<MsMonitorMetricDisk>> getDiskHistory(
            @PathVariable Long monitorId,
            @RequestParam(required = false) Long hours) {
        java.time.LocalDateTime start = hours != null ? java.time.LocalDateTime.now().minusHours(hours) : null;
        return metricService.getDiskHistory(monitorId, start, null);
    }

    @Operation(summary = "获取 Redis 最新指标")
    @GetMapping("/metric/redis/{monitorId}")
    public ReturnResult<MsMonitorRedisMetric> getLatestRedisMetric(@PathVariable Long monitorId) {
        return metricService.getLatestRedisMetric(monitorId);
    }

    @Operation(summary = "获取 Redis 历史指标")
    @GetMapping("/metric/redis/history/{monitorId}")
    public ReturnResult<List<MsMonitorRedisMetric>> getRedisMetricHistory(
            @PathVariable Long monitorId,
            @RequestParam(required = false) Long hours) {
        java.time.LocalDateTime start = hours != null ? java.time.LocalDateTime.now().minusHours(hours) : null;
        return metricService.getRedisMetricHistory(monitorId, start, null);
    }

    /**
     * 获取通用指标历史
     *
     * @param monitorId 监控 ID
     * @param metricName 指标名（可选）
     * @param hours      过去 N 小时（可选）
     * @return 通用指标列表
     */
    @Operation(summary = "获取通用指标历史")
    @GetMapping("/metric/generic/history/{monitorId}")
    public ReturnResult<List<MsMonitorMetric>> getGenericMetricHistory(
            @PathVariable Long monitorId,
            @RequestParam(required = false) String metricName,
            @RequestParam(required = false) Long hours) {
        java.time.LocalDateTime start = hours != null ? java.time.LocalDateTime.now().minusHours(hours) : null;
        return metricService.getGenericMetricHistory(monitorId, metricName, start, null);
    }

    /**
     * 获取通用指标小时聚合统计
     *
     * @param monitorId 监控 ID
     * @param metricName 指标名（可选）
     * @param hours      过去 N 小时（可选，默认 24）
     * @return 聚合统计列表
     */
    @Operation(summary = "获取通用指标小时聚合统计")
    @GetMapping("/metric/statistics/{monitorId}")
    public ReturnResult<List<MsMonitorMetricStatistics>> getMetricStatistics(
            @PathVariable Long monitorId,
            @RequestParam(required = false) String metricName,
            @RequestParam(required = false, defaultValue = "24") Long hours) {
        java.time.LocalDateTime start = hours != null ? java.time.LocalDateTime.now().minusHours(hours) : null;
        return metricService.getMetricStatistics(monitorId, metricName, start, null);
    }

    /**
     * 获取 Redis 指标小时聚合统计
     *
     * @param monitorId 监控 ID
     * @param metricName 指标名（可选）
     * @param hours      过去 N 小时（可选，默认 24）
     * @return Redis 聚合统计列表
     */
    @Operation(summary = "获取 Redis 指标小时聚合统计")
    @GetMapping("/metric/redis-statistics/{monitorId}")
    public ReturnResult<List<MsMonitorRedisMetricStatistics>> getRedisMetricStatistics(
            @PathVariable Long monitorId,
            @RequestParam(required = false) String metricName,
            @RequestParam(required = false, defaultValue = "24") Long hours) {
        java.time.LocalDateTime start = hours != null ? java.time.LocalDateTime.now().minusHours(hours) : null;
        return metricService.getRedisMetricStatistics(monitorId, metricName, start, null);
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
     * 获取缓存前缀汇总（按前缀分组统计 key 数量）
     *
     * @param taskId 任务 id
     * @param prefix 前缀匹配模式（可选，默认 "*"）
     * @return 缓存前缀汇总列表
     */
    @Operation(summary = "获取缓存前缀汇总")
    @GetMapping("/operation/redis/{taskId}/cache-summary")
    public Mono<List<Map<String, Object>>> getCacheSummary(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "*") String prefix) {
        return resolveOperation(taskId).flatMap(op -> {
            CollectContext ctx = buildContext(taskId);
            return op.scanKeys(ctx, prefix, null, 10000)
                    .map(result -> {
                        Map<String, List<String>> grouped = new java.util.LinkedHashMap<>();
                        if (result.keys() != null) {
                            for (String key : result.keys()) {
                                String group = key.contains(":") ? key.substring(0, key.indexOf(":") + 1) + "*" : key;
                                grouped.computeIfAbsent(group, k -> new java.util.ArrayList<>()).add(key);
                            }
                        }
                        String cursor = result.nextCursor();
                        int maxIterations = 10;
                        int iteration = 0;
                        while (cursor != null && !"0".equals(cursor) && iteration < maxIterations) {
                            final String c = cursor;
                            OperationProvider.ScanResult next = op.scanKeys(ctx, prefix, c, 1000).block();
                            if (next != null && next.keys() != null) {
                                for (String key : next.keys()) {
                                    String group = key.contains(":") ? key.substring(0, key.indexOf(":") + 1) + "*" : key;
                                    grouped.computeIfAbsent(group, k -> new java.util.ArrayList<>()).add(key);
                                }
                                cursor = next.nextCursor();
                            } else {
                                break;
                            }
                            iteration++;
                        }
                        List<Map<String, Object>> resultList = new java.util.ArrayList<>();
                        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
                            Map<String, Object> item = new java.util.LinkedHashMap<>();
                            item.put("prefix", entry.getKey());
                            item.put("count", entry.getValue().size());
                            resultList.add(item);
                        }
                        resultList.sort((a, b) -> Integer.compare((int) b.get("count"), (int) a.get("count")));
                        return resultList;
                    });
        });
    }

    /**
     * 获取 Redis key 的详细信息（类型、TTL、大小、值）
     *
     * @param taskId 任务 id
     * @param key    key 名称
     * @return key 详细信息
     */
    @Operation(summary = "获取 Redis key 详细信息")
    @GetMapping("/operation/redis/{taskId}/kv/{key}/detail")
    public Mono<Map<String, Object>> getKeyDetail(@PathVariable Long taskId, @PathVariable String key) {
        return resolveOperation(taskId).flatMap(op -> {
            CollectContext ctx = buildContext(taskId);
            return Mono.zip(
                    op.getValue(ctx, key).defaultIfEmpty(""),
                    op.ttl(ctx, key).defaultIfEmpty(-1L),
                    op.type(ctx, key).defaultIfEmpty("STRING")
            ).map(tuple -> {
                Map<String, Object> detail = new java.util.LinkedHashMap<>();
                detail.put("key", key);
                detail.put("value", tuple.getT1());
                detail.put("ttl", tuple.getT2());
                detail.put("type", tuple.getT3());
                detail.put("size", tuple.getT1() != null ? tuple.getT1().length() : 0);
                return detail;
            });
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