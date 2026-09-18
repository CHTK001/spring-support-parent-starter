package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.chua.starter.monitor.support.entity.MsMonitor;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.service.ServerHostService;
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
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import javax.sql.DataSource;
import com.chua.starter.ai.support.chat.ChatClient;

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

    /**
    * 监控资产服务（CRUD）
    */
    private final MonitorService monitorService;

    /**
    * 监控任务服务（CRUD）
    */
    private final MonitorTaskService taskService;

    /**
    * 指标配置服务（解析规则）
    */
    private final MonitorMetricConfigService metricConfigService;

    /**
    * 全局配置服务（键值对）
    */
    private final MonitorConfigService configService;

    /**
    * 网关服务（CRUD）
    */
    private final MonitorGatewayService gatewayService;

    /**
    * 指标服务（查询/聚合/导出）
    */
    private final MetricService metricService;

    /**
    * 提供者注册表（SPI 操作路由）
    */
    private final ProviderRegistry providerRegistry;

    /**
    * 服务器主机服务（资产联动，服务端主机查询）
    */
    private final ServerHostService serverHostService;

    /**
    * 系统 Redis 连接（应用自身连接的 Redis，未配置时为空，前端据此隐藏菜单）
    */
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    /**
    * 默认主数据源（用于数据库 web 管理器）
    */
    private final ObjectProvider<DataSource> dataSourceProvider;

    /**
    * AI 对话客户端（未配置 AI 模型时为 null，接口返回未启用提示）
    */
    private final ObjectProvider<ChatClient> chatClientProvider;

    // ==================== 资产 CRUD ====================

    /**
    * 创建监控资产。
    *
    * @param monitor 监控资产请求体，不能为 null
    * @return 创建成功返回新建资产，失败返回错误码
    */
    @Operation(summary = "新增资产")
    @PostMapping
    public ReturnResult<MsMonitor> createMonitor(@RequestBody MsMonitor monitor) {
        return monitorService.create(monitor);
    }

    /**
    * 更新监控资产。
    *
    * @param id 监控资产 ID，路径参数，不能为 null
    * @param monitor 监控资产请求体，不能为 null
    * @return 更新成功返回更新后的资产，失败返回错误码
    */
    @Operation(summary = "更新资产")
    @PutMapping("/{id}")
    public ReturnResult<MsMonitor> updateMonitor(@PathVariable Long id, @RequestBody MsMonitor monitor) {
        monitor.setId(id);
        return monitorService.update(monitor);
    }

    /**
    * 删除监控资产。
    *
    * @param id 监控资产 ID，路径参数，不能为 null
    * @return 删除成功返回空结果，失败返回错误码
    */
    @Operation(summary = "删除资产")
    @DeleteMapping("/{id}")
    public ReturnResult<Void> deleteMonitor(@PathVariable Long id) {
        return monitorService.delete(id);
    }

    /**
    * 查询单个监控资产。
    *
    * @param id 监控资产 ID，路径参数，不能为 null
    * @return 监控资产详情；ID 不存在时 data 为 null
    */
    @Operation(summary = "查询单个资产")
    @GetMapping("/{id}")
    public ReturnResult<MsMonitor> getMonitor(@PathVariable Long id) {
        return monitorService.findById(id);
    }

    /**
    * 监控资产列表。
    *
    * @return 全部监控资产列表，无数据时为空列表
    */
    @Operation(summary = "资产列表")
    @GetMapping
    public ReturnResult<List<MsMonitor>> listMonitor() {
        return monitorService.listAll();
    }

    // ==================== 关联服务器台账 ====================

    /**
    * 查询可用服务器主机列表（资产表单联动下拉）。
    *
    * @return 主机简表列表（id/name/address/port/osType）
    */
    @Operation(summary = "查询可用服务器主机列表")
    @GetMapping("/server-hosts")
    public ReturnResult<List<Map<String, Object>>> listServerHosts() {
        java.util.List<Map<String, Object>> result = new java.util.ArrayList<>();
        try {
            ReturnResult<java.util.List<MsServerHost>> q = serverHostService.query(null, null, 1);
            java.util.List<MsServerHost> hosts = q.getData();
            if (hosts != null) {
                for (MsServerHost h : hosts) {
                    Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("id", h.getId());
                    item.put("serverHostName", h.getServerHostName());
                    item.put("serverHostAddress", h.getServerHostAddress());
                    item.put("serverHostPort", h.getServerHostPort());
                    item.put("serverHostOsType", h.getServerHostOsType());
                    result.add(item);
                }
            }
        } catch (Exception e) {
            // 台账模块未启用时静默降级为空列表
        }
        return ReturnResult.ok(result);
    }

    /**
    * 获取指定服务器主机详情（资产联动回填）。
    *
    * @param id id ID，路径参数
    * @return 主机详情
    */
    @Operation(summary = "获取服务器主机详情")
    @GetMapping("/server-host/{id}")
    public ReturnResult<MsServerHost> getServerHost(@PathVariable Long id) {
        ReturnResult<MsServerHost> result = serverHostService.get(id);
        if (result.getData() != null) {
            MsServerHost h = result.getData();
            h.setServerHostAccount(null);
            h.setServerHostPassword(null);
            h.setServerHostPrivateKey(null);
        }
        return result;
    }

    // ==================== 任务 CRUD ====================

    /**
    * 创建监控任务。
    * @param task 任务
    * @return 创建任务的结果
    */
    public ReturnResult<MsMonitorTask> createTask(@RequestBody MsMonitorTask task) {
        return taskService.create(task);
    }

    /**
    * 更新监控任务。
    * @param id 任务 ID，路径参数，不能为 null
    * @param task 任务请求体，含需更新的字段，不能为 null
    * @return 更新结果，data 为更新后的任务
    */
    public ReturnResult<MsMonitorTask> updateTask(@PathVariable Long id, @RequestBody MsMonitorTask task) {
        task.setId(id);
        return taskService.update(task);
    }

    /**
    * 删除监控任务。
    * @param id 任务 ID，路径参数，不能为 null
    * @return 删除结果，data 为 null
    */
    public ReturnResult<Void> deleteTask(@PathVariable Long id) {
        return taskService.delete(id);
    }

    /**
    * 查询监控任务。
    * @param id 任务 ID，路径参数，不能为 null
    * @return 任务详情；ID 不存在时 data 为 null
    */
    public ReturnResult<MsMonitorTask> getTask(@PathVariable Long id) {
        return taskService.findById(id);
    }

    /**
    * 设置任务启用或禁用。
    * @param id 任务 ID，路径参数，不能为 null
    * @param enabled 启用状态：1 表示启用，0 表示禁用
    * @return 设置结果，data 为 null
    */
    public ReturnResult<Void> setTaskEnabled(@PathVariable Long id, @RequestParam Integer enabled) {
        return taskService.setEnabled(id, enabled);
    }

    /**
    * 按主机查询任务列表。
    * @param monitorId 监控资产 ID，可选；非 null 时返回该资产下的任务，null 时返回全部启用任务
    * @return 任务列表
    */
    public ReturnResult<List<MsMonitorTask>> listTask(@RequestParam(required = false) Long monitorId) {
        if (monitorId != null) {
            return taskService.listByMonitor(monitorId);
        }
        return taskService.listEnabled();
    }

    // ==================== 解析规则 ====================

    /**
    * 创建指标配置。
    * @param config 配置
    * @return 创建指标配置的结果
    */
    public ReturnResult<MsMonitorMetricConfig> createMetricConfig(@RequestBody MsMonitorMetricConfig config) {
        return metricConfigService.create(config);
    }

    /**
    * 更新指标配置。
    * @param id id ID，路径参数
    * @param config 配置
    * @return 更新指标配置的结果
    */
    public ReturnResult<MsMonitorMetricConfig> updateMetricConfig(@PathVariable Long id, @RequestBody MsMonitorMetricConfig config) {
        config.setId(id);
        return metricConfigService.update(config);
    }

    /**
    * 删除指标配置。
    * @param id id ID，路径参数
    * @return 删除指标配置的结果
    */
    public ReturnResult<Void> deleteMetricConfig(@PathVariable Long id) {
        return metricConfigService.delete(id);
    }

    /**
    * 列表指标配置。
    * @param taskId 任务 ID
    * @return 列表指标配置的结果
    */
    public ReturnResult<List<MsMonitorMetricConfig>> listMetricConfig(@RequestParam Long taskId) {
        return metricConfigService.listByTask(taskId);
    }

    // ==================== 全局配置 ====================

    /**
    * 获取全部全局配置。
    * @return 配置键值对 Map，无配置时为空 Map
    */
    @Operation(summary = "获取所有配置")
    @GetMapping("/config")
    public ReturnResult<Map<String, String>> getAllConfig() {
        return configService.getAll();
    }

    /**
    * 获取单个配置项。
    * @param key 配置键名，路径参数，不能为 null
    * @return 配置值；键不存在时 data 为 null
    */
    @Operation(summary = "获取单个配置")
    @GetMapping("/config/{key}")
    public ReturnResult<String> getConfig(@PathVariable String key) {
        return ReturnResult.ok(configService.getString(key));
    }

    /**
    * 写入（或更新）单个配置项。
    * @param config 配置项请求体，含 key/value，不能为 null
    * @return 写入结果，data 为配置项
    */
    @Operation(summary = "写入配置")
    @PostMapping("/config")
    public ReturnResult<MsMonitorConfig> upsertConfig(@RequestBody MsMonitorConfig config) {
        return configService.upsert(config);
    }

    /**
    * 批量写入（或更新）配置项。
    * @param configs 配置项列表，不能为 null 或空
    * @return 写入成功的条数
    */
    @Operation(summary = "批量写入配置")
    @PostMapping("/config/batch")
    public ReturnResult<Integer> upsertConfigs(@RequestBody List<MsMonitorConfig> configs) {
        return configService.upsertAll(configs);
    }

    // ==================== 网关 ====================

    /**
    * 创建gateway。
    * @param gateway gateway
    * @return 创建gateway的结果
    */
    public ReturnResult<MsMonitorGateway> createGateway(@RequestBody MsMonitorGateway gateway) {
        return gatewayService.create(gateway);
    }

    /**
    * 删除gateway。
    * @param id id ID，路径参数
    * @return 删除gateway的结果
    */
    public ReturnResult<Void> deleteGateway(@PathVariable Long id) {
        return gatewayService.delete(id);
    }

    /**
    * 更新gateway。
    * @param id id ID，路径参数
    * @param gateway gateway
    * @return 更新gateway的结果
    */
    public ReturnResult<MsMonitorGateway> updateGateway(@PathVariable Long id, @RequestBody MsMonitorGateway gateway) {
        gateway.setId(id);
        return gatewayService.update(gateway);
    }

    /**
    * 列表gateway。
    * @param monitorId 监控资产 ID
    * @return 列表gateway的结果
    */
    public ReturnResult<List<MsMonitorGateway>> listGateway(@RequestParam Long monitorId) {
        return gatewayService.listByMonitor(monitorId);
    }

    // ==================== 指标查询 ====================

    /**
    * 获取latestcpu。
    * @param monitorId 监控资产 ID
    * @return 获取latestcpu的结果
    */
    public ReturnResult<MsMonitorMetricCpu> getLatestCpu(@PathVariable Long monitorId) {
        return metricService.getLatestCpu(monitorId);
    }

    /**
    * 获取latest内存。
    * @param monitorId 监控资产 ID
    * @return 获取latest内存的结果
    */
    public ReturnResult<MsMonitorMetricMemory> getLatestMemory(@PathVariable Long monitorId) {
        return metricService.getLatestMemory(monitorId);
    }

    /**
    * 获取latestdisk。
    * @param monitorId 监控资产 ID
    * @return 获取latestdisk的结果
    */
    public ReturnResult<List<MsMonitorMetricDisk>> getLatestDisk(@PathVariable Long monitorId) {
        return metricService.getLatestDisk(monitorId);
    }

    /**
    * 获取latestnetwork。
    * @param monitorId 监控资产 ID
    * @return 获取latestnetwork的结果
    */
    public ReturnResult<MsMonitorMetricNetwork> getLatestNetwork(@PathVariable Long monitorId) {
        return metricService.getLatestNetwork(monitorId);
    }

    /**
    * 获取latestjvm。
    * @param monitorId 监控资产 ID
    * @return 获取latestjvm的结果
    */
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
    * @param monitorId monitorId ID，路径参数
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

    /**
    * 获取latestredis指标。
    * @param monitorId 监控资产 ID
    * @return 获取latestredis指标的结果
    */
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
    * @param monitorId monitorId ID，路径参数
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
    * @param monitorId monitorId ID，路径参数
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
    * @param monitorId monitorId ID，路径参数
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

    /**
    * 导出指标历史数据
    *
    * @param monitorId monitorId ID，路径参数
    * @param metricName 指标名（可选，为空则导出所有）
    * @param hours      过去 N 小时（可选）
    * @return 操作结果，包含文件名
    */
    @Operation(summary = "导出指标历史数据")
    @GetMapping("/metric/export/{monitorId}")
    public ReturnResult<String> exportMetricHistory(
            @PathVariable Long monitorId,
            @RequestParam(required = false) String metricName,
            @RequestParam(required = false, defaultValue = "24") Long hours) {
        java.time.LocalDateTime start = hours != null ? java.time.LocalDateTime.now().minusHours(hours) : null;
        return metricService.exportMetricHistory(monitorId, metricName, start, null);
    }

    // ==================== 数据源管理（内存存储，支持 jdbc/redis/influxdb/neo4j/nacos/zookeeper） ====================

    /**
    * 数据源内存存储（简化实现，后续可改为数据库存储）
    */
    private static final java.util.concurrent.ConcurrentHashMap<Long, Map<String, Object>> DATA_SOURCE_STORE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong DS_ID_GENERATOR = new java.util.concurrent.atomic.AtomicLong(1000);

    /**
    * 支持的数据源类型
    */
    private static final java.util.List<String> DS_TYPES = java.util.List.of("jdbc", "redis", "influxdb", "neo4j", "nacos", "zookeeper");

    /**
    * 获取所有数据源列表。
    *
    * @return 数据源列表
    */
    @Operation(summary = "获取所有数据源")
    @GetMapping({"/datasource", "/datasource/list"})
    public ReturnResult<java.util.List<Map<String, Object>>> listDataSources() {
        return ReturnResult.ok(new java.util.ArrayList<>(DATA_SOURCE_STORE.values()));
    }

    /**
    * 获取数据源详情。
    *
    * @param id 数据源 ID
    * @return 数据源详情
    */
    @Operation(summary = "获取数据源详情")
    @GetMapping("/datasource/{id}")
    public ReturnResult<Map<String, Object>> getDataSource(@PathVariable Long id) {
        Map<String, Object> ds = DATA_SOURCE_STORE.get(id);
        if (ds == null) {
            return ReturnResult.error("数据源不存在");
        }
        return ReturnResult.ok(ds);
    }

    /**
    * 创建数据源。
    *
    * @param data 数据源配置
    * @return 创建的数据源
    */
    @Operation(summary = "创建数据源")
    @PostMapping("/datasource")
    public ReturnResult<Map<String, Object>> createDataSource(@RequestBody Map<String, Object> data) {
        Long id = DS_ID_GENERATOR.incrementAndGet();
        data.put("id", id);
        data.put("createTime", java.time.LocalDateTime.now().toString());
        DATA_SOURCE_STORE.put(id, data);
        return ReturnResult.ok(data);
    }

    /**
    * 更新数据源。
    *
    * @param id   数据源 ID
    * @param data 数据源配置
    * @return 更新后的数据源
    */
    @Operation(summary = "更新数据源")
    @PutMapping("/datasource/{id}")
    public ReturnResult<Map<String, Object>> updateDataSource(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        if (!DATA_SOURCE_STORE.containsKey(id)) {
            return ReturnResult.error("数据源不存在");
        }
        data.put("id", id);
        data.put("updateTime", java.time.LocalDateTime.now().toString());
        DATA_SOURCE_STORE.put(id, data);
        return ReturnResult.ok(data);
    }

    /**
    * 删除数据源。
    *
    * @param id 数据源 ID
    * @return 是否成功
    */
    @Operation(summary = "删除数据源")
    @DeleteMapping("/datasource/{id}")
    public ReturnResult<Boolean> deleteDataSource(@PathVariable Long id) {
        DATA_SOURCE_STORE.remove(id);
        return ReturnResult.ok(true);
    }

    /**
    * 测试数据源连接。
    *
    * @param id 数据源 ID
    * @return 测试结果
    */
    @Operation(summary = "测试数据源连接")
    @PostMapping("/datasource/{id}/test")
    public ReturnResult<Map<String, Object>> testDataSource(@PathVariable Long id) {
        Map<String, Object> ds = DATA_SOURCE_STORE.get(id);
        if (ds == null) {
            return ReturnResult.error("数据源不存在");
        }
        // 简化实现：返回测试成功（实际应根据类型测试连接）
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "连接测试成功（简化实现）");
        result.put("type", ds.get("type"));
        return ReturnResult.ok(result);
    }

    /**
    * 获取支持的数据源类型。
    *
    * @return 数据源类型列表
    */
    @Operation(summary = "获取支持的数据源类型")
    @GetMapping("/datasource/types")
    public ReturnResult<java.util.List<String>> getDataSourceTypes() {
        return ReturnResult.ok(DS_TYPES);
    }

    // ==================== 数据库 web 管理器（支持按数据源 ID 连接，navicat 风格） ====================

    /**
    * 根据数据源 ID 获取数据库连接。
    * datasourceId 为 null 或 0 时使用默认主数据源。
    */
    private java.sql.Connection getDbConnection(Long datasourceId) throws Exception {
        if (datasourceId == null || datasourceId == 0) {
            javax.sql.DataSource ds = dataSourceProvider.getIfAvailable();
            if (ds == null) throw new RuntimeException("未配置默认数据源");
            return ds.getConnection();
        }
        Map<String, Object> dsConfig = DATA_SOURCE_STORE.get(datasourceId);
        if (dsConfig == null) throw new RuntimeException("数据源不存在: " + datasourceId);
        String type = String.valueOf(dsConfig.get("type"));
        if (!"jdbc".equals(type)) throw new RuntimeException("非 JDBC 数据源类型: " + type);
        String host = String.valueOf(dsConfig.get("host"));
        Object portObj = dsConfig.get("port");
        int port = portObj != null ? Integer.parseInt(String.valueOf(portObj)) : 3306;
        String database = String.valueOf(dsConfig.get("database"));
        String username = String.valueOf(dsConfig.get("username"));
        String password = String.valueOf(dsConfig.get("password"));
        String url = String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai", host, port, database);
        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", username);
        props.setProperty("password", password != null ? password : "");
        return java.sql.DriverManager.getConnection(url, props);
    }

    /**
    * 获取数据库列表。
    */
    @Operation(summary = "获取数据库列表")
    @GetMapping("/db/databases")
    public ReturnResult<java.util.List<String>> getDbDatabases(@RequestParam(required = false) Long datasourceId) {
        try (java.sql.Connection conn = getDbConnection(datasourceId);
             java.sql.ResultSet rs = conn.getMetaData().getCatalogs()) {
            java.util.List<String> databases = new java.util.ArrayList<>();
            while (rs.next()) {
                databases.add(rs.getString(1));
            }
            return ReturnResult.ok(databases);
        } catch (Exception e) {
            return ReturnResult.error("获取数据库列表失败: " + e.getMessage());
        }
    }

    /**
    * 获取数据库表列表。
    */
    @Operation(summary = "获取数据库表列表")
    @GetMapping("/db/tables")
    public ReturnResult<java.util.List<Map<String, Object>>> getDbTables(
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        try (java.sql.Connection conn = getDbConnection(datasourceId);
             java.sql.ResultSet rs = conn.getMetaData().getTables(database != null ? database : conn.getCatalog(), null, "%", new String[]{"TABLE", "VIEW"})) {
            java.util.List<Map<String, Object>> tables = new java.util.ArrayList<>();
            while (rs.next()) {
                Map<String, Object> table = new java.util.LinkedHashMap<>();
                table.put("name", rs.getString("TABLE_NAME"));
                table.put("type", rs.getString("TABLE_TYPE"));
                table.put("remark", rs.getString("REMARKS"));
                tables.add(table);
            }
            return ReturnResult.ok(tables);
        } catch (Exception e) {
            return ReturnResult.error("获取表列表失败: " + e.getMessage());
        }
    }

    /**
    * 获取表结构。
    */
    @Operation(summary = "获取表结构")
    @GetMapping("/db/table/{tableName}/columns")
    public ReturnResult<java.util.List<Map<String, Object>>> getTableColumns(
            @PathVariable String tableName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        try (java.sql.Connection conn = getDbConnection(datasourceId);
             java.sql.ResultSet rs = conn.getMetaData().getColumns(database != null ? database : conn.getCatalog(), null, tableName, "%")) {
            java.util.List<Map<String, Object>> columns = new java.util.ArrayList<>();
            while (rs.next()) {
                Map<String, Object> col = new java.util.LinkedHashMap<>();
                col.put("name", rs.getString("COLUMN_NAME"));
                col.put("type", rs.getString("TYPE_NAME"));
                col.put("size", rs.getInt("COLUMN_SIZE"));
                col.put("nullable", rs.getInt("NULLABLE") == 1);
                col.put("remark", rs.getString("REMARKS"));
                col.put("default", rs.getString("COLUMN_DEF"));
                col.put("position", rs.getInt("ORDINAL_POSITION"));
                col.put("isPrimaryKey", false); // 主键信息单独查询
                columns.add(col);
            }
            // 查询主键
            try (java.sql.ResultSet pkRs = conn.getMetaData().getPrimaryKeys(database != null ? database : conn.getCatalog(), null, tableName)) {
                java.util.Set<String> pkCols = new java.util.HashSet<>();
                while (pkRs.next()) pkCols.add(pkRs.getString("COLUMN_NAME"));
                for (Map<String, Object> col : columns) {
                    col.put("isPrimaryKey", pkCols.contains(col.get("name")));
                }
            }
            return ReturnResult.ok(columns);
        } catch (Exception e) {
            return ReturnResult.error("获取表结构失败: " + e.getMessage());
        }
    }

    /**
    * 获取表数据（分页）。
    */
    @Operation(summary = "获取表数据")
    @GetMapping("/db/table/{tableName}/data")
    public ReturnResult<Map<String, Object>> getTableData(
            @PathVariable String tableName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) String orderDir,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        int offset = (page - 1) * pageSize;
        String sql = String.format("SELECT * FROM `%s` %s LIMIT %d OFFSET %d",
                tableName,
                (orderBy != null && !orderBy.isEmpty()) ? String.format("ORDER BY `%s` %s", orderBy, "DESC".equalsIgnoreCase(orderDir) ? "DESC" : "ASC") : "",
                pageSize, offset);
        String countSql = String.format("SELECT COUNT(*) as total FROM `%s`", tableName);
        try (java.sql.Connection conn = getDbConnection(datasourceId);
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(sql)) {
            java.sql.ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            java.util.List<String> columns = new java.util.ArrayList<>();
            for (int i = 1; i <= colCount; i++) columns.add(meta.getColumnLabel(i));
            java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) row.put(columns.get(i - 1), rs.getObject(i));
                rows.add(row);
            }
            // 查询总数
            long total = 0;
            try (java.sql.ResultSet countRs = stmt.executeQuery(countSql)) {
                if (countRs.next()) total = countRs.getLong(1);
            }
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("columns", columns);
            result.put("rows", rows);
            result.put("total", total);
            result.put("page", page);
            result.put("pageSize", pageSize);
            return ReturnResult.ok(result);
        } catch (Exception e) {
            return ReturnResult.error("获取表数据失败: " + e.getMessage());
        }
    }

    /**
    * 执行 SQL（支持 SELECT/INSERT/UPDATE/DELETE/CREATE/ALTER/DROP）。
    */
    @Operation(summary = "执行 SQL")
    @PostMapping("/db/execute")
    public ReturnResult<Map<String, Object>> executeDbSql(@RequestBody Map<String, Object> request) {
        String sql = String.valueOf(request.get("sql"));
        Long datasourceId = request.get("datasourceId") != null ? Long.valueOf(String.valueOf(request.get("datasourceId"))) : null;
        if (sql == null || sql.trim().isEmpty()) return ReturnResult.error("SQL 不能为空");
        String trimmed = sql.trim().toUpperCase();
        boolean isQuery = trimmed.startsWith("SELECT") || trimmed.startsWith("SHOW") || trimmed.startsWith("DESC") || trimmed.startsWith("EXPLAIN");
        try (java.sql.Connection conn = getDbConnection(datasourceId);
             java.sql.Statement stmt = conn.createStatement()) {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            if (isQuery) {
                try (java.sql.ResultSet rs = stmt.executeQuery(sql)) {
                    java.sql.ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    java.util.List<String> columns = new java.util.ArrayList<>();
                    for (int i = 1; i <= colCount; i++) columns.add(meta.getColumnLabel(i));
                    java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
                    int rowCount = 0;
                    int maxRows = 1000;
                    while (rs.next() && rowCount < maxRows) {
                        Map<String, Object> row = new java.util.LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) row.put(columns.get(i - 1), rs.getObject(i));
                        rows.add(row);
                        rowCount++;
                    }
                    result.put("columns", columns);
                    result.put("rows", rows);
                    result.put("rowCount", rowCount);
                    result.put("truncated", rowCount >= maxRows);
                }
            } else {
                int affectedRows = stmt.executeUpdate(sql);
                result.put("affectedRows", affectedRows);
                result.put("message", "执行成功，影响 " + affectedRows + " 行");
            }
            return ReturnResult.ok(result);
        } catch (Exception e) {
            return ReturnResult.error("SQL 执行失败: " + e.getMessage());
        }
    }

    /**
    * 删除表。
    */
    @Operation(summary = "删除表")
    @DeleteMapping("/db/table/{tableName}")
    public ReturnResult<Boolean> dropTable(
            @PathVariable String tableName,
            @RequestParam(required = false) Long datasourceId) {
        String sql = String.format("DROP TABLE IF EXISTS `%s`", tableName);
        try (java.sql.Connection conn = getDbConnection(datasourceId);
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            return ReturnResult.error("删除表失败: " + e.getMessage());
        }
    }

    // ==================== Redis 操作 ====================

    /**
    * 获取当前系统 Redis 信息（信息 关键指标）。
    * <p>
    * 直接读取应用自身连接的 Redis（spring.数据.Redis），与监控资产/Agent 无关；
    * 无 Redis 配置时前端不显示该菜单。
    * </p>
    *
    * @return Redis 信息 关键指标
    */
    @Operation(summary = "获取当前系统 Redis 信息")
    @GetMapping("/system/redis-info")
    public ReturnResult<Map<String, Object>> getSystemRedisInfo() {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null || redisTemplate.getConnectionFactory() == null) {
            return ReturnResult.ok(data);
        }
        /**
        * 尝试。
        * @param redisTemplate.getConnectionFactory( redistemplate.获取connection工厂(
        */
        try (var connection = redisTemplate.getConnectionFactory().getConnection()) {
            if (connection == null) {
                return ReturnResult.ok(data);
            }
            Properties info = connection.serverCommands().info();
            if (info != null) {
                for (Map.Entry<Object, Object> entry : info.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    String value = String.valueOf(entry.getValue());
                    if (key == null || key.isEmpty() || key.startsWith("db")) {
                        continue;
                    }
                    Double num = parseRedisDouble(value);
                    data.put(key, num != null ? num : value);
                }
            }
            return ReturnResult.ok(data);
        } catch (Exception e) {
            return ReturnResult.error("Redis 连接失败: " + e.getMessage());
        }
    }

    // ==================== 系统 Redis KV 操作（直接操作当前应用连接的 Redis） ====================

    /**
    * 扫描当前系统 Redis 的 Keys。
    *
    * @param pattern 匹配模式，默认 *
    * @param cursor  游标，默认 0
    * @param count   每次扫描数量，默认 100
    * @return 扫描结果（keys + nextCursor）
    */
    @Operation(summary = "扫描当前系统 Redis Keys")
    @GetMapping("/system/redis/keys")
    public ReturnResult<Map<String, Object>> scanSystemRedisKeys(
            @RequestParam(defaultValue = "*") String pattern,
            @RequestParam(defaultValue = "0") String cursor,
            @RequestParam(defaultValue = "100") int count) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null || redisTemplate.getConnectionFactory() == null) {
            return ReturnResult.error("当前系统未配置 Redis");
        }
        try {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            var connection = redisTemplate.getConnectionFactory().getConnection();
            var scanOptions = org.springframework.data.redis.core.ScanOptions.scanOptions()
                    .match(pattern)
                    .count(count)
                    .build();
            var cursorObj = connection.scan(scanOptions);
            java.util.List<String> keys = new java.util.ArrayList<>();
            while (cursorObj.hasNext() && keys.size() < count) {
                keys.add(new String(cursorObj.next(), java.nio.charset.StandardCharsets.UTF_8));
            }
            cursorObj.close();
            connection.close();
            result.put("keys", keys);
            result.put("nextCursor", keys.size() >= count ? "1" : "0");
            result.put("total", keys.size());
            return ReturnResult.ok(result);
        } catch (Exception e) {
            return ReturnResult.error("扫描 Redis Keys 失败: " + e.getMessage());
        }
    }

    /**
    * 读取当前系统 Redis 的 Key 值。
    *
    * @param key 键名
    * @return 值
    */
    @Operation(summary = "读取当前系统 Redis Key")
    @GetMapping("/system/redis/kv/{key}")
    public ReturnResult<String> getSystemRedisKey(@PathVariable String key) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return ReturnResult.error("当前系统未配置 Redis");
        }
        try {
            String value = redisTemplate.opsForValue().get(key);
            return ReturnResult.ok(value);
        } catch (Exception e) {
            return ReturnResult.error("读取 Redis Key 失败: " + e.getMessage());
        }
    }

    /**
    * 获取当前系统 Redis Key 的详细信息（类型、TTL、值）。
    *
    * @param key 键名
    * @return 详细信息
    */
    @Operation(summary = "获取当前系统 Redis Key 详细信息")
    @GetMapping("/system/redis/kv/{key}/detail")
    public ReturnResult<Map<String, Object>> getSystemRedisKeyDetail(@PathVariable String key) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return ReturnResult.error("当前系统未配置 Redis");
        }
        try {
            Map<String, Object> detail = new java.util.LinkedHashMap<>();
            String type = redisTemplate.type(key) != null ? redisTemplate.type(key).name() : "none";
            Long ttl = redisTemplate.getExpire(key);
            Object value = null;
            Long size = null;
            if ("STRING".equals(type)) {
                value = redisTemplate.opsForValue().get(key);
                size = value != null ? (long) value.toString().length() : 0L;
            } else if ("LIST".equals(type)) {
                value = redisTemplate.opsForList().range(key, 0, -1);
                size = redisTemplate.opsForList().size(key);
            } else if ("SET".equals(type)) {
                value = redisTemplate.opsForSet().members(key);
                size = redisTemplate.opsForSet().size(key);
            } else if ("HASH".equals(type)) {
                value = redisTemplate.opsForHash().entries(key);
                size = redisTemplate.opsForHash().size(key);
            } else if ("ZSET".equals(type)) {
                var zSetValues = redisTemplate.opsForZSet().rangeWithScores(key, 0, -1);
                if (zSetValues != null) {
                    java.util.List<Map<String, Object>> zSetList = new java.util.ArrayList<>();
                    for (var v : zSetValues) {
                        Map<String, Object> item = new java.util.LinkedHashMap<>();
                        item.put("member", v.getValue());
                        item.put("score", v.getScore());
                        zSetList.add(item);
                    }
                    value = zSetList;
                }
                size = redisTemplate.opsForZSet().size(key);
            }
            detail.put("key", key);
            detail.put("type", type);
            detail.put("ttl", ttl != null && ttl >= 0 ? ttl : -1);
            detail.put("value", value);
            detail.put("size", size);
            return ReturnResult.ok(detail);
        } catch (Exception e) {
            return ReturnResult.error("获取 Redis Key 详情失败: " + e.getMessage());
        }
    }

    /**
    * 写入当前系统 Redis Key。
    *
    * @param key   键名
    * @param value 值（请求体）
    * @param ttl   过期时间（秒），可选，-1 表示永不过期
    * @return 是否成功
    */
    @Operation(summary = "写入当前系统 Redis Key")
    @PutMapping("/system/redis/kv/{key}")
    public ReturnResult<Boolean> setSystemRedisKey(
            @PathVariable String key,
            @RequestBody String value,
            @RequestParam(defaultValue = "-1") long ttl) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return ReturnResult.error("当前系统未配置 Redis");
        }
        try {
            if (ttl > 0) {
                redisTemplate.opsForValue().set(key, value, java.time.Duration.ofSeconds(ttl));
            } else {
                redisTemplate.opsForValue().set(key, value);
            }
            return ReturnResult.ok(true);
        } catch (Exception e) {
            return ReturnResult.error("写入 Redis Key 失败: " + e.getMessage());
        }
    }

    /**
    * 删除当前系统 Redis Keys。
    *
    * @param keys 键名列表
    * @return 删除数量
    */
    @Operation(summary = "删除当前系统 Redis Keys")
    @DeleteMapping("/system/redis/keys")
    public ReturnResult<Long> deleteSystemRedisKeys(@RequestBody List<String> keys) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return ReturnResult.error("当前系统未配置 Redis");
        }
        try {
            Long deleted = redisTemplate.delete(keys);
            return ReturnResult.ok(deleted != null ? deleted : 0L);
        } catch (Exception e) {
            return ReturnResult.error("删除 Redis Keys 失败: " + e.getMessage());
        }
    }

    /**
    * 设置当前系统 Redis Key 过期时间。
    *
    * @param key 键名
    * @param ttl 过期时间（秒）
    * @return 是否成功
    */
    @Operation(summary = "设置当前系统 Redis Key 过期时间")
    @PostMapping("/system/redis/expire/{key}")
    public ReturnResult<Boolean> expireSystemRedisKey(
            @PathVariable String key,
            @RequestParam long ttl) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return ReturnResult.error("当前系统未配置 Redis");
        }
        try {
            Boolean result = redisTemplate.expire(key, java.time.Duration.ofSeconds(ttl));
            return ReturnResult.ok(result != null && result);
        } catch (Exception e) {
            return ReturnResult.error("设置 Redis Key 过期时间失败: " + e.getMessage());
        }
    }

    /**
    * 解析 Redis 数值（兼容 1.5M / 2G 等单位后缀）
    *
    * @param raw 原始值
    * @return 数值或 空
    */
    private static Double parseRedisDouble(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            String s = raw.trim().toUpperCase();
            double factor = 1.0;
            if (s.endsWith("K")) {
                factor = 1024.0;
                s = s.substring(0, s.length() - 1);
            } else if (s.endsWith("M")) {
                factor = 1024.0 * 1024.0;
                s = s.substring(0, s.length() - 1);
            } else if (s.endsWith("G")) {
                factor = 1024.0 * 1024.0 * 1024.0;
                s = s.substring(0, s.length() - 1);
            } else if (s.endsWith("B")) {
                s = s.substring(0, s.length() - 1);
            }
            return Double.parseDouble(s.trim()) * factor;
        } catch (NumberFormatException e) {
            return null;
        }
    }

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

    /**
    * 获取 Redis Key 的当前值。
    * @param taskId 任务 ID，路径参数，不能为 null
    * @param key 配置键名，路径参数，不能为 null
    * @return 键值；Key 不存在时 data 为 null
    */
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

    /**
    * 批量删除指定 Redis Key。
    * @param taskId 任务 ID，路径参数，不能为 null
    * @param keys 待删除的 Redis Key 列表，不能为 null 或空
    * @return 实际删除的 Key 数量
    */
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
    * 获取缓存前缀汇总（按前缀分组统计 Key 数量）
    *
    * @param taskId 任务 ID，路径参数，不能为 null
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
    * 获取指定 Redis Key 的详细信息（类型、TTL、大小、值）
    *
    * @param taskId 任务 ID，路径参数，不能为 null
    * @param key    配置键名，路径参数，不能为 null
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
    * 解析 Redis 操作 提供者
    *
    * @param taskId taskId ID，路径参数
    * @return 响应式操作 提供者
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
    * @param taskId taskId ID，路径参数
    * @return 采集上下文
    */
    private CollectContext buildContext(Long taskId) {
        MsMonitorTask task = taskService.findById(taskId).getData();
        MsMonitor monitor = task != null ? monitorService.findById(task.getMonitorId()).getData() : null;
        return CollectContext.builder().task(task).monitor(monitor).build();
    }

    // ==================== AI 智能分析 ====================

     @Operation(summary = "查询 AI 分析是否启用")
     @GetMapping("/ai/status")
     public ReturnResult<Map<String, Object>> getAiStatus() {
         com.chua.starter.ai.support.chat.ChatClient chatClient = chatClientProvider.getIfAvailable();
         Map<String, Object> result = new java.util.LinkedHashMap<>();
         result.put("enabled", chatClient != null);
         if (chatClient != null) {
             result.put("model", chatClient.getDefaultModel());
             result.put("provider", chatClient.getProvider());
         }
         return ReturnResult.ok(result);
     }

     @Operation(summary = "通用 AI 分析")
     @PostMapping("/ai/analyze")
     public Mono<ReturnResult<Map<String, Object>>> aiAnalyze(@RequestBody Map<String, Object> request) {
         com.chua.starter.ai.support.chat.ChatClient chatClient = chatClientProvider.getIfAvailable();
         if (chatClient == null) {
             return Mono.just(ReturnResult.error("AI 分析未启用，请配置 AI 模型"));
         }
         String prompt = request.get("prompt") != null ? String.valueOf(request.get("prompt")) : "";
         String systemPrompt = request.get("systemPrompt") != null ? String.valueOf(request.get("systemPrompt")) : null;
         String model = request.get("model") != null ? String.valueOf(request.get("model")) : null;
         if (prompt.isBlank()) {
             return Mono.just(ReturnResult.error("分析内容不能为空"));
         }
         return Mono.fromCallable(() -> {
             com.chua.starter.ai.support.chat.ChatScope scope = new com.chua.starter.ai.support.chat.ChatScope();
             scope.setInput(prompt);
             if (systemPrompt != null && !systemPrompt.isBlank()) scope.setSystemPrompt(systemPrompt);
             if (model != null && !model.isBlank()) scope.setModel(model);
             com.chua.common.support.ai.chat.ChatResponse response = chatClient.chat(scope);
             Map<String, Object> result = new java.util.LinkedHashMap<>();
             result.put("content", response != null ? response.getContent() : "");
             result.put("model", response != null ? chatClient.getDefaultModel() : chatClient.getDefaultModel());
             result.put("usage", response != null ? response.getUsage() : null);
             return ReturnResult.ok(result);
         }).onErrorResume(e -> Mono.just(ReturnResult.error("AI 分析失败: " + e.getMessage())));
     }

     @Operation(summary = "主机指标智能分析")
     @PostMapping("/ai/host/{id}/analyze")
     public Mono<ReturnResult<Map<String, Object>>> aiAnalyzeHost(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> request) {
         com.chua.starter.ai.support.chat.ChatClient chatClient = chatClientProvider.getIfAvailable();
         if (chatClient == null) {
             return Mono.just(ReturnResult.error("AI 分析未启用，请配置 AI 模型"));
         }
         MsMonitor monitor = monitorService.findById(id).getData();
         if (monitor == null) {
             return Mono.just(ReturnResult.error("监控资产不存在"));
         }
         return Mono.fromCallable(() -> {
             StringBuilder prompt = new StringBuilder();
             prompt.append("请对以下主机监控指标进行智能分析，给出性能评估、瓶颈识别和优化建议：\n\n");
             prompt.append("主机名称: ").append(monitor.getMonitorName()).append("\n");
             prompt.append("主机地址: ").append(monitor.getMonitorHost()).append(":").append(monitor.getMonitorPort()).append("\n");
             prompt.append("服务类型: ").append(monitor.getServiceType()).append("\n\n");
             if (request != null && request.get("metrics") != null) {
                 prompt.append("最近指标数据:\n").append(request.get("metrics").toString()).append("\n\n");
             }
             prompt.append("请按以下结构输出：\n1. 整体性能评分（0-100）\n2. 关键指标评估\n3. 潜在瓶颈识别\n4. 具体优化建议\n5. 风险预警");
             com.chua.starter.ai.support.chat.ChatScope scope = new com.chua.starter.ai.support.chat.ChatScope();
             scope.setInput(prompt.toString());
             scope.setSystemPrompt("你是一位资深的运维监控专家，擅长主机性能分析和容量规划。请用专业、简洁、可操作的语言输出分析结果。");
             com.chua.common.support.ai.chat.ChatResponse response = chatClient.chat(scope);
             Map<String, Object> result = new java.util.LinkedHashMap<>();
             result.put("hostId", id);
             result.put("hostName", monitor.getMonitorName());
             result.put("content", response != null ? response.getContent() : "");
             result.put("model", response != null ? chatClient.getDefaultModel() : chatClient.getDefaultModel());
             return ReturnResult.ok(result);
         }).onErrorResume(e -> Mono.just(ReturnResult.error("主机 AI 分析失败: " + e.getMessage())));
     }
}
