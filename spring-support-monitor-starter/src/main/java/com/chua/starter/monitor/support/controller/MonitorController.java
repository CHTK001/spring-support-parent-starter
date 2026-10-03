package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.datasource.dialect.SqlName;
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
import com.chua.starter.monitor.support.model.ServerHostOptionVO;
import com.chua.starter.monitor.support.model.AiAnalyzeVO;
import com.chua.starter.monitor.support.model.AiHostAnalyzeVO;
import com.chua.starter.monitor.support.model.AiStatusVO;
import com.chua.starter.monitor.support.model.DashboardOverviewVO;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import com.chua.starter.monitor.support.model.DbColumnVO;
import com.chua.starter.monitor.support.model.DbSqlResultVO;
import com.chua.starter.monitor.support.model.DbTableDataVO;
import com.chua.starter.monitor.support.model.dto.DbSqlExecuteDTO;
import com.chua.starter.monitor.support.model.DbTableVO;
import com.chua.starter.monitor.support.model.RedisCacheSummaryVO;
import com.chua.starter.monitor.support.model.RedisKeyDetailVO;
import com.chua.starter.monitor.support.model.RedisMemberScoreVO;
import com.chua.starter.monitor.support.model.RedisOperationKeyDetailVO;
import com.chua.starter.monitor.support.model.RedisScanVO;
import com.chua.starter.monitor.support.model.RedisStreamEntryVO;
import com.chua.starter.monitor.support.model.RedisValueVO;
import com.chua.starter.monitor.support.model.dto.AiAnalyzeDTO;
import com.chua.starter.monitor.support.model.dto.AiHostAnalyzeDTO;

import com.chua.starter.ai.support.chat.ChatClientSettings;
import com.chua.starter.ai.support.chat.ChatClientSettingsResolver;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.monitor.support.registry.ProviderRegistry;
import com.chua.starter.monitor.support.scheduler.MonitorScheduler;
import com.chua.starter.monitor.support.service.MonitorConfigService;

import com.chua.starter.monitor.support.service.MonitorGatewayService;
import com.chua.starter.monitor.support.service.MonitorMetricConfigService;
import com.chua.starter.monitor.support.service.MetricService;
import com.chua.starter.monitor.support.service.MonitorService;
import com.chua.starter.monitor.support.service.MonitorTaskService;
import com.chua.starter.monitor.support.service.SystemRedisKvService;
import com.chua.starter.monitor.support.spi.CollectContext;
import com.chua.starter.monitor.support.spi.OperationProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.model.AiChatResult;
import com.chua.starter.ai.support.model.AiClientProbe;

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
@Slf4j
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
     * 采集调度器（手动触发复用其采集管道）
     */
    private final MonitorScheduler scheduler;

    /**
     * 服务器主机服务（资产联动，服务端主机查询）
     */
    private final ServerHostService serverHostService;

    /**
     * 系统 Redis 连接（应用自身连接的 Redis，未配置时为空，前端据此隐藏菜单）
     */
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    /**
     * 系统 Redis 集合类型元素操作服务（键详情的 STREAM 分支读取流消息）
     */
    private final SystemRedisKvService kvService;

    /**
     * 键详情一次性读取的流消息上限。
     */
    private static final int STREAM_DETAIL_LIMIT = 100;

    /**
     * 数据源服务（数据源配置落库与 JDBC 连接构建）
     */
    private final DatasourceService datasourceService;

    /**
     * AI 对话门面服务（宿主未装配 ai-starter 时为空，接口返回未启用提示）
     */
    private final ObjectProvider<AiChatFacadeService> aiChatFacadeProvider;

    /**
     * AI 全局设置服务（判定模型是否真的配置好，并取默认系统提示词）
     */
    private final ObjectProvider<SysAiGlobalSettingService> aiGlobalSettingProvider;

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
    public ReturnResult<List<ServerHostOptionVO>> listServerHosts() {
        List<ServerHostOptionVO> result = new ArrayList<>();
        try {
            ReturnResult<List<MsServerHost>> q = serverHostService.query(null, null, 1);
            List<MsServerHost> hosts = q.getData();
            if (hosts != null) {
                for (MsServerHost h : hosts) {
                    result.add(new ServerHostOptionVO(h.getId(), h.getServerHostName(),
                            h.getServerHostAddress(), h.getServerHostPort(), h.getServerHostOsType()));
                }
            }
        } catch (Exception e) {
            // 台账模块未启用时静默降级为空列表
            log.debug("[monitor] 服务器主机列表不可用: {}", e.getMessage());
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
    @Operation(summary = "新增任务")
    @PostMapping("/task")
    public ReturnResult<MsMonitorTask> createTask(@RequestBody MsMonitorTask task) {
        return taskService.create(task);
    }

    /**
     * 更新监控任务。
     * @param id 任务 ID，路径参数，不能为 null
     * @param task 任务请求体，含需更新的字段，不能为 null
     * @return 更新结果，data 为更新后的任务
     */
    @Operation(summary = "更新任务")
    @PutMapping("/task/{id}")
    public ReturnResult<MsMonitorTask> updateTask(@PathVariable Long id, @RequestBody MsMonitorTask task) {
        task.setId(id);
        return taskService.update(task);
    }

    /**
     * 删除监控任务。
     * @param id 任务 ID，路径参数，不能为 null
     * @return 删除结果，data 为 null
     */
    @Operation(summary = "删除任务")
    @DeleteMapping("/task/{id}")
    public ReturnResult<Void> deleteTask(@PathVariable Long id) {
        return taskService.delete(id);
    }

    /**
     * 查询监控任务。
     * @param id 任务 ID，路径参数，不能为 null
     * @return 任务详情；ID 不存在时 data 为 null
     */
    @Operation(summary = "查询单个任务")
    @GetMapping("/task/{id}")
    public ReturnResult<MsMonitorTask> getTask(@PathVariable Long id) {
        return taskService.findById(id);
    }

    /**
     * 设置任务启用或禁用。
     * @param id 任务 ID，路径参数，不能为 null
     * @param enabled 启用状态，前端以 true/false 传参
     * @return 设置结果，data 为 null
     */
    @Operation(summary = "启用/禁用任务")
    @PutMapping("/task/{id}/enabled")
    public ReturnResult<Void> setTaskEnabled(@PathVariable Long id, @RequestParam Boolean enabled) {
        return taskService.setEnabled(id, Boolean.TRUE.equals(enabled) ? 1 : 0);
    }

    /**
     * 按主机查询任务列表。
     * @param monitorId 监控资产 ID，可选；非 null 时返回该资产下的任务，null 时返回全部启用任务
     * @return 任务列表
     */
    @Operation(summary = "查询任务列表")
    @GetMapping("/task")
    public ReturnResult<List<MsMonitorTask>> listTask(@RequestParam(required = false) Long monitorId) {
        if (monitorId != null) {
            return taskService.listByMonitor(monitorId);
        }
        return taskService.listEnabled();
    }

    /**
     * 手动触发一次采集。
     * @param id 任务 ID，路径参数，不能为 null
     * @return 受理结果；采集本身异步执行
     */
    @Operation(summary = "手动触发采集")
    @PostMapping("/task/{id}/trigger")
    public ReturnResult<Void> triggerTask(@PathVariable Long id) {
        MsMonitorTask task = taskService.findById(id).getData();
        if (task == null) {
            return ReturnResult.error("任务不存在: " + id);
        }
        scheduler.triggerNow(task);
        return ReturnResult.success();
    }

    // ==================== 解析规则 ====================

    /**
     * 创建指标配置。
     * @param config 配置
     * @return 创建指标配置的结果
     */
    @Operation(summary = "新增解析规则")
    @PostMapping("/metric-config")
    public ReturnResult<MsMonitorMetricConfig> createMetricConfig(@RequestBody MsMonitorMetricConfig config) {
        return metricConfigService.create(config);
    }

    /**
     * 更新指标配置。
     * @param id id ID，路径参数
     * @param config 配置
     * @return 更新指标配置的结果
     */
    @Operation(summary = "更新解析规则")
    @PutMapping("/metric-config/{id}")
    public ReturnResult<MsMonitorMetricConfig> updateMetricConfig(@PathVariable Long id, @RequestBody MsMonitorMetricConfig config) {
        config.setId(id);
        return metricConfigService.update(config);
    }

    /**
     * 删除指标配置。
     * @param id id ID，路径参数
     * @return 删除指标配置的结果
     */
    @Operation(summary = "删除解析规则")
    @DeleteMapping("/metric-config/{id}")
    public ReturnResult<Void> deleteMetricConfig(@PathVariable Long id) {
        return metricConfigService.delete(id);
    }

    /**
     * 列表指标配置。
     * @param taskId 任务 ID
     * @return 列表指标配置的结果
     */
    @Operation(summary = "按任务查询解析规则")
    @GetMapping("/metric-config")
    public ReturnResult<List<MsMonitorMetricConfig>> listMetricConfig(@RequestParam Long taskId) {
        return metricConfigService.listByTask(taskId);
    }

    // ==================== 全局配置 ====================

    /**
     * 获取全部全局配置。
     * <p>
     * 配置项由用户在配置页自定义键名，属通用动态键值结构而非业务载荷，故不改为 VO。
     * </p>
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
    @Operation(summary = "新增网关")
    @PostMapping("/gateway")
    public ReturnResult<MsMonitorGateway> createGateway(@RequestBody MsMonitorGateway gateway) {
        return gatewayService.create(gateway);
    }

    /**
     * 删除gateway。
     * @param id id ID，路径参数
     * @return 删除gateway的结果
     */
    @Operation(summary = "删除网关")
    @DeleteMapping("/gateway/{id}")
    public ReturnResult<Void> deleteGateway(@PathVariable Long id) {
        return gatewayService.delete(id);
    }

    /**
     * 更新gateway。
     * @param id id ID，路径参数
     * @param gateway gateway
     * @return 更新gateway的结果
     */
    @Operation(summary = "更新网关")
    @PutMapping("/gateway/{id}")
    public ReturnResult<MsMonitorGateway> updateGateway(@PathVariable Long id, @RequestBody MsMonitorGateway gateway) {
        gateway.setId(id);
        return gatewayService.update(gateway);
    }

    /**
     * 列表gateway。
     * @param monitorId 监控资产 ID
     * @return 列表gateway的结果
     */
    @Operation(summary = "按资产查询网关")
    @GetMapping("/gateway")
    public ReturnResult<List<MsMonitorGateway>> listGateway(@RequestParam Long monitorId) {
        return gatewayService.listByMonitor(monitorId);
    }

    // ==================== 指标查询 ====================

    /**
     * 获取latestcpu。
     * @param monitorId 监控资产 ID
     * @return 获取latestcpu的结果
     */
    @Operation(summary = "获取 CPU 最新指标")
    @GetMapping("/metric/cpu/{monitorId}")
    public ReturnResult<MsMonitorMetricCpu> getLatestCpu(@PathVariable Long monitorId) {
        return metricService.getLatestCpu(monitorId);
    }

    /**
     * 获取latest内存。
     * @param monitorId 监控资产 ID
     * @return 获取latest内存的结果
     */
    @Operation(summary = "获取内存最新指标")
    @GetMapping("/metric/memory/{monitorId}")
    public ReturnResult<MsMonitorMetricMemory> getLatestMemory(@PathVariable Long monitorId) {
        return metricService.getLatestMemory(monitorId);
    }

    /**
     * 获取latestdisk。
     * @param monitorId 监控资产 ID
     * @return 获取latestdisk的结果
     */
    @Operation(summary = "获取磁盘最新指标")
    @GetMapping("/metric/disk/{monitorId}")
    public ReturnResult<List<MsMonitorMetricDisk>> getLatestDisk(@PathVariable Long monitorId) {
        return metricService.getLatestDisk(monitorId);
    }

    /**
     * 获取latestnetwork。
     * @param monitorId 监控资产 ID
     * @return 获取latestnetwork的结果
     */
    @Operation(summary = "获取网络最新指标")
    @GetMapping("/metric/network/{monitorId}")
    public ReturnResult<MsMonitorMetricNetwork> getLatestNetwork(@PathVariable Long monitorId) {
        return metricService.getLatestNetwork(monitorId);
    }

    /**
     * 获取latestjvm。
     * @param monitorId 监控资产 ID
     * @return 获取latestjvm的结果
     */
    @Operation(summary = "获取 JVM 最新指标")
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
     * 导出指标历史数据 CSV（直接写文件流，前端以 blob 下载）
     *
     * @param monitorId  监控资产 ID，可缺省表示导出全部
     * @param metricName 指标名（可选，为空则导出所有）
     * @param hours      过去 N 小时（可选）
     * @param response   HTTP 响应
     */
    @Operation(summary = "导出指标历史数据")
    @GetMapping({"/metric/export", "/metric/export/{monitorId}"})
    public void exportMetricHistory(
            @PathVariable(required = false) Long monitorId,
            @RequestParam(required = false) String metricName,
            @RequestParam(required = false, defaultValue = "24") Long hours,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        java.time.LocalDateTime start = hours != null ? java.time.LocalDateTime.now().minusHours(hours) : null;
        ReturnResult<String> result = metricService.exportMetricHistory(monitorId, metricName, start, null);
        response.setCharacterEncoding("UTF-8");
        if (!result.isOk()) {
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write(result.getMsg() == null ? "导出失败" : result.getMsg());
            return;
        }
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"monitor_metrics_" + (monitorId == null ? "all" : monitorId) + ".csv\"");
        response.getWriter().write(result.getData() == null ? "" : result.getData());
        response.getWriter().flush();
    }

    // ==================== 数据源管理 ====================
    // 数据源台账 CRUD/测试/类型已抽为独立模块 spring-support-datasource-hub-starter，
    // 接口迁移至 /ms/datasource/**（DatasourceController）；本模块仅注入其 DatasourceService
    // 获取 JDBC 连接，供下方数据库管理器使用。

    // ==================== 数据库 web 管理器（支持按数据源 ID 连接，navicat 风格） ====================

    /**
     * 系统内置 schema，不作为可浏览的数据库展示。
     */
    private static final java.util.Set<String> SYSTEM_SCHEMAS = java.util.Set.of("INFORMATION_SCHEMA", "PG_CATALOG", "SYS");

    /**
     * SQL 控制台单次查询返回的最大行数（超出置 truncated=true）
     */
    private static final int SQL_MAX_ROWS = 1000;

    /**
     * 把连接切到指定库，并在返回的作用域关闭时还原。
     *
     * <p>MySQL 用 catalog 表达库、PostgreSQL/H2 用 schema，两侧都尝试一次；
     * 都没切过去时抛错，避免语句静默跑在默认库上。</p>
     *
     * <p>连接取自连接池，会话级 schema 不会随归还而复位，调用方必须把返回值放进
     * try-with-resources，否则切库会污染后续取到同一物理连接的语句。</p>
     *
     * <p>安全豁免：schema 名经 {@link SqlName#checkSimple(String, String)} 严格校验，
     * MySQL 的底层方言 {@code USE} 语句只拼接已校验标识符。</p>
     *
     * @param conn     数据库连接
     * @param database 目标库名，为空表示不切换
     * @return 用完需关闭的切库作用域
     * @throws java.sql.SQLException 目标库无法选中时抛出
     */
    private SchemaScope useSchema(java.sql.Connection conn, String database) throws java.sql.SQLException {
        // 目标库为空：不切换（默认主数据源/沿用连接默认库）
        if (database == null || database.isBlank()) {
            return new SchemaScope(conn, null);
        }
        if (!com.chua.starter.monitor.support.support.DbDialectSupport.isSafeIdentifier(database)) {
            throw new java.sql.SQLException("数据库名不合法");
        }
        String original = currentDatabase(conn);
        if (database.equalsIgnoreCase(original)) {
            return new SchemaScope(conn, original);
        }
        // H2 等内核把「库名」暴露为 catalog（如 file 库 monitordb）而非 schema：
        // 目标等于当前 catalog 时本就在该库上，无需也无法切换，直接返回。
        try {
            if (database.equalsIgnoreCase(conn.getCatalog())) {
                return new SchemaScope(conn, original);
            }
        } catch (java.sql.SQLException ignored) {
            // 驱动不支持 catalog，继续尝试切换
        }
        if (!databaseExists(conn, database)) {
            throw new java.sql.SQLException("数据库 " + database + " 不存在");
        }
        switchDatabase(conn, database);
        if (!database.equalsIgnoreCase(currentDatabase(conn))) {
            throw new java.sql.SQLException("无法切换到数据库 " + database);
        }
        return new SchemaScope(conn, original);
    }

    /**
     * 读取连接当前所在库（权威值）。
     *
     * <p>MySQL 系用 {@code SELECT DATABASE()}：Connector/J 的 {@code getCatalog()} 只反映
     * 驱动内部字段、{@code setCatalog()} 不会真正切换服务端会话库，必须以服务端查询为准；
     * 其余内核用 {@code getSchema()}（H2/PostgreSQL）再退回 {@code getCatalog()}。</p>
     *
     * @param conn 数据库连接
     * @return 当前库名，读取失败返回 空
     */
    private static String currentDatabase(java.sql.Connection conn) {
        if (isMysqlFamily(conn)) {
            try (java.sql.Statement st = conn.createStatement();
                 java.sql.ResultSet rs = st.executeQuery("SELECT DATABASE()")) {
                if (rs.next()) {
                    String value = rs.getString(1);
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            } catch (java.sql.SQLException ignored) {
                // 退回元数据读取
            }
        }
        try {
            String schema = conn.getSchema();
            if (schema != null && !schema.isBlank()) {
                return schema;
            }
        } catch (java.sql.SQLException ignored) {
            // 驱动不支持 schema，退回 catalog
        }
        try {
            return conn.getCatalog();
        } catch (java.sql.SQLException ignored) {
            return null;
        }
    }

    /**
     * 切换连接所在库（跨方言）。
     *
     * <p>MySQL 系执行 {@code USE `db`}（唯一可靠方式）；H2/PostgreSQL 用
     * {@code setSchema}（失败退回 {@code setCatalog}）。</p>
     *
     * @param conn 数据库连接
     * @param db   目标库名
     * @throws java.sql.SQLException 切换失败
     */
    private static void switchDatabase(java.sql.Connection conn, String db) throws java.sql.SQLException {
        if (isMysqlFamily(conn)) {
            // setCatalog 只更新驱动内部字段（供 getTables/getColumns 等元数据定位库），
            // 不真正切换服务端会话库；USE 才切会话。两者都要，缺一会导致「数据查对库、元数据查错库」。
            try {
                conn.setCatalog(db);
            } catch (java.sql.SQLException ignored) {
                // 驱动不支持 setCatalog，元数据退化为按会话默认库
            }
            try (java.sql.Statement st = conn.createStatement()) {
                st.executeUpdate("USE " + SqlName.quoteMysql(db, "数据库名"));
            }
            return;
        }
        java.sql.SQLException first = null;
        try {
            conn.setSchema(db);
            return;
        } catch (java.sql.SQLException e) {
            first = e;
        }
        try {
            conn.setCatalog(db);
        } catch (java.sql.SQLException e) {
            throw first;
        }
    }

    /**
     * 是否 MySQL 系内核（元数据读取失败时按非 MySQL 处理）。
     *
     * @param conn 数据库连接
     * @return true=MySQL/MariaDB 一类
     */
    private static boolean isMysqlFamily(java.sql.Connection conn) {
        try {
            String product = conn.getMetaData().getDatabaseProductName();
            if (product == null) {
                return false;
            }
            String lower = product.toLowerCase(java.util.Locale.ROOT);
            return lower.contains("mysql") || lower.contains("mariadb") || lower.contains("oceanbase");
        } catch (java.sql.SQLException e) {
            return false;
        }
    }

    /**
     * 判断库是否存在。
     *
     * <p>必须显式校验：部分驱动（如 H2）接受任意 catalog 值而不报错，
     * 只比对切换结果会让写错的库名静默落在当前库上执行。</p>
     *
     * @param conn     数据库连接
     * @param database 库名
     * @return 存在返回 true
     * @throws java.sql.SQLException 读取元数据失败时抛出
     */
    private static boolean databaseExists(java.sql.Connection conn, String database) throws java.sql.SQLException {
        try (java.sql.ResultSet rs = conn.getMetaData().getSchemas()) {
            while (rs.next()) {
                if (database.equalsIgnoreCase(rs.getString(1))) {
                    return true;
                }
            }
        } catch (java.sql.SQLException ignored) {
            // 驱动不支持 schema 视图，继续用 catalog 判断
        }
        try (java.sql.ResultSet rs = conn.getMetaData().getCatalogs()) {
            while (rs.next()) {
                if (database.equalsIgnoreCase(rs.getString(1))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 切库作用域的还原句柄，关闭时把连接切回原库，避免会话状态泄漏给连接池的下一个使用者。
     *
     * <p>原先还带 {@code catalog}/{@code schema} 两个快照字段，重构后合并成单库的
     * {@code database}，注释没跟着改——读代码的人会以为还能拿到 schema 信息。</p>
     *
     * @param conn     被切换的连接，close 时负责切回；取自连接池，恒非 null
     *                  （紧凑构造器已做非空校验）
     * @param database 切库前所在的库名，close 时按方言还原到这个库。取值来自
     *                  {@code useSchema} 里的 {@code currentDatabase(conn)}：MySQL 系读
     *                  {@code SELECT DATABASE()}，其余内核先读 {@code getSchema()} 再退回
     *                  {@code getCatalog()}，三者都失败时为 null。因此它究竟是 catalog 还是
     *                  schema 取决于数据库内核，H2 等把库名暴露为 catalog 时即为 catalog。
     *                  允许为 null 或空白串：那表示本次根本没有发生切库（目标库名本身为空），
     *                  或没能读到原库名，此时 {@code close()} 直接返回、不做还原，
     *                  连接将以当前库状态被归还给连接池
     */
    private record SchemaScope(java.sql.Connection conn, String database) implements AutoCloseable {

        /**
         * 规范构造器：被切换的连接非空校验。
         *
         * <p>value class 前置条件——语义必填的引用组件必须空值敌对。连接取自数据源服务，
         * 不可能为 null；目标库名则显式允许为 null（为空表示不切换）。</p>
         *
         * @param conn     被切换的连接
         * @param database 切换前的 catalog，可为空
         */
        SchemaScope {
            conn = Objects.requireNonNull(conn, "conn 不能为 null");
        }

        @Override
        public void close() {
            if (database == null || database.isBlank()) {
                return;
            }
            try {
                // 按方言还原到进入前的库（MySQL 用 USE；H2/PG 用 setSchema）
                switchDatabase(conn, database);
            } catch (java.sql.SQLException ignored) {
                // 还原失败只能随连接一起被池回收；连接缓存下下次访问会按目标库重切
            }
        }
    }

    /**
     * 获取数据库列表。
     * @param datasourceId datasourceID，不允许为 null
     * @return Return结果 对象
     */
    @Operation(summary = "获取数据库列表")
    @GetMapping("/db/databases")
    public ReturnResult<java.util.List<String>> getDbDatabases(@RequestParam(required = false) Long datasourceId) {
        try (java.sql.Connection conn = datasourceService.connection(datasourceId)) {
            java.util.List<String> databases = new java.util.ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            try (java.sql.ResultSet rs = conn.getMetaData().getCatalogs()) {
                while (rs.next()) {
                    String catalog = rs.getString(1);
                    if (catalog != null && seen.add(catalog.toUpperCase())) {
                        databases.add(catalog);
                    }
                }
            }
            // MySQL 用 catalog 表达库，H2/PostgreSQL 用 schema，两侧都取一次
            try (java.sql.ResultSet rs = conn.getMetaData().getSchemas()) {
                while (rs.next()) {
                    String schema = rs.getString(1);
                    if (schema == null || SYSTEM_SCHEMAS.contains(schema.toUpperCase()) || !seen.add(schema.toUpperCase())) {
                        continue;
                    }
                    databases.add(schema);
                }
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
    public ReturnResult<java.util.List<DbTableVO>> getDbTables(
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        try (java.sql.Connection conn = datasourceService.connection(datasourceId);
             SchemaScope scope = useSchema(conn, database)) {
            java.util.List<DbTableVO> tables = new java.util.ArrayList<>();
            try (java.sql.ResultSet rs = conn.getMetaData().getTables(conn.getCatalog(), conn.getSchema(), "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    tables.add(new DbTableVO(rs.getString("TABLE_NAME"), rs.getString("TABLE_TYPE"), rs.getString("REMARKS")));
                }
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
    public ReturnResult<java.util.List<DbColumnVO>> getTableColumns(
            @PathVariable String tableName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        try (java.sql.Connection conn = datasourceService.connection(datasourceId);
             SchemaScope scope = useSchema(conn, database)) {
            String table = safeIdent(tableName, "表名");
            requireTable(conn, table);
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();
            java.util.Set<String> pkCols = new java.util.HashSet<>();
            try (java.sql.ResultSet pkRs = conn.getMetaData().getPrimaryKeys(catalog, schema, table)) {
                while (pkRs.next()) {
                    pkCols.add(pkRs.getString("COLUMN_NAME"));
                }
            }
            java.util.List<DbColumnVO> columns = new java.util.ArrayList<>();
            try (java.sql.ResultSet rs = conn.getMetaData().getColumns(catalog, schema, table, "%")) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    columns.add(new DbColumnVO(name,
                            rs.getString("TYPE_NAME"),
                            rs.getInt("COLUMN_SIZE"),
                            rs.getInt("NULLABLE") == 1,
                            rs.getString("REMARKS"),
                            rs.getString("COLUMN_DEF"),
                            rs.getInt("ORDINAL_POSITION"),
                            pkCols.contains(name)));
                }
            }
            return ReturnResult.ok(columns);
        } catch (Exception e) {
            return ReturnResult.error("获取表结构失败: " + e.getMessage());
        }
    }

    /**
     * 获取表数据（分页）。
     *
     * <p>安全豁免：表名、排序列先经标识符白名单与 JDBC 元数据校验；分页值使用
     * PreparedStatement 绑定。</p>
     */
    @Operation(summary = "获取表数据")
    @GetMapping("/db/table/{tableName}/data")
    public ReturnResult<DbTableDataVO> getTableData(
            @PathVariable String tableName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) String orderDir,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database,
            @RequestParam(defaultValue = "true") boolean count,
            @RequestParam(required = false) String where) {
        int normalizedPage = Math.max(1, page);
        int normalizedPageSize = Math.min(500, Math.max(1, pageSize));
        long offset = (long) (normalizedPage - 1) * normalizedPageSize;
        String table = safeIdent(tableName, "表名");
        String column = orderBy == null || orderBy.isBlank() ? null : safeIdent(orderBy, "排序列");
        String direction = "DESC".equalsIgnoreCase(orderDir) ? "DESC" : "ASC";
        // WHERE 过滤：允许 Navicat 式自由条件，但拒绝语句分隔符/注释，避免堆叠多条语句
        String whereClause = sanitizeWhere(where);
        try (java.sql.Connection conn = datasourceService.connection(datasourceId);
             SchemaScope scope = useSchema(conn, database)) {
            requireTable(conn, table);
            if (column != null) {
                requireColumn(conn, table, column);
            }
            String quotedTable = SqlName.quoteMysql(table, "表名");
            String whereSql = whereClause == null ? "" : " WHERE " + whereClause;
            String orderClause = column == null ? ""
                    : " ORDER BY " + SqlName.quoteMysql(column, "排序列") + " " + direction;
            String dataSql = "SELECT * FROM " + quotedTable + whereSql + orderClause + " LIMIT ? OFFSET ?";
            String countSql = "SELECT COUNT(*) FROM " + quotedTable + whereSql;
            java.util.List<String> columns;
            java.util.List<Map<String, Object>> rows;
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(dataSql)) {
                stmt.setInt(1, normalizedPageSize);
                stmt.setLong(2, offset);
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    java.sql.ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    columns = new java.util.ArrayList<>();
                    for (int i = 1; i <= colCount; i++) {
                        columns.add(meta.getColumnLabel(i));
                    }
                    rows = new java.util.ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> row = new java.util.LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {
                            row.put(columns.get(i - 1), rs.getObject(i));
                        }
                        rows.add(row);
                    }
                }
            }
            long total = -1;
            if (count) {
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(countSql);
                     java.sql.ResultSet countRs = stmt.executeQuery()) {
                    if (countRs.next()) {
                        total = countRs.getLong(1);
                    }
                }
            }
            return ReturnResult.ok(new DbTableDataVO(columns, rows, total, normalizedPage, normalizedPageSize));
} catch (Exception e) {
            return ReturnResult.error("获取表数据失败: " + e.getMessage());
        }
    }

    /**
     * 校验 WHERE 过滤条件（Navicat 式自由条件）。
     *
     * <p>仅作为管理端的数据筛选用途，允许列名/运算符/函数；为防堆叠多条语句，
     * 拒绝语句分隔符与注释符。空白输入返回 null（不过滤）。</p>
     *
     * @param where 过滤条件
     * @return 规整后的条件；为空返回 null
     * @throws IllegalArgumentException 含禁止字符时抛出
     */
    private static String sanitizeWhere(String where) {
        if (where == null || where.isBlank()) {
            return null;
        }
        String value = where.trim();
        for (String forbidden : new String[]{";", "--", "/*", "*/", "#"}) {
            if (value.contains(forbidden)) {
                throw new IllegalArgumentException("过滤条件含非法字符: " + forbidden);
            }
        }
        return value;
    }

    /**
     * 获取表的建表语句（DDL）。
     *
     * <p>优先 {@code SHOW CREATE TABLE}（MySQL/H2 兼容）；不支持时按列+主键拼装 CREATE TABLE，
     * 保证在 PostgreSQL 等内核也能给出可用 DDL。</p>
     *
     * @param tableName    表名
     * @param datasourceId 数据源 ID
     * @param database     库名
     * @return 建表语句
     */
    @Operation(summary = "获取建表语句")
    @GetMapping("/db/table/{tableName}/ddl")
    public ReturnResult<String> getTableDdl(
            @PathVariable String tableName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String table = safeIdent(tableName, "表名");
        try (java.sql.Connection conn = datasourceService.connection(datasourceId);
             SchemaScope scope = useSchema(conn, database)) {
            requireTable(conn, table);
            String quoted = SqlName.quoteMysql(table, "表名");
            try (java.sql.Statement st = conn.createStatement();
                 java.sql.ResultSet rs = st.executeQuery("SHOW CREATE TABLE " + quoted)) {
                if (rs.next()) {
                    // MySQL 第 2 列为 DDL；部分内核列名不同，取首个含 CREATE 的文本列
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                        Object v = rs.getObject(i);
                        if (v instanceof String s && s.toUpperCase().contains("CREATE")) {
                            return ReturnResult.ok(s);
                        }
                    }
                }
            } catch (java.sql.SQLException ignored) {
                // 非 SHOW CREATE 内核，走列结构拼装
            }
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();
            java.util.Set<String> pkCols = new java.util.LinkedHashSet<>();
            try (java.sql.ResultSet pkRs = conn.getMetaData().getPrimaryKeys(catalog, schema, table)) {
                while (pkRs.next()) {
                    pkCols.add(pkRs.getString("COLUMN_NAME"));
                }
            }
            StringBuilder ddl = new StringBuilder("CREATE TABLE ").append(quoted).append(" (\n");
            java.util.List<String> defs = new java.util.ArrayList<>();
            try (java.sql.ResultSet rs = conn.getMetaData().getColumns(catalog, schema, table, "%")) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    String type = rs.getString("TYPE_NAME");
                    int size = rs.getInt("COLUMN_SIZE");
                    boolean nullable = rs.getInt("NULLABLE") == 1;
                    String def = rs.getString("COLUMN_DEF");
                    StringBuilder line = new StringBuilder("  ").append(SqlName.quoteMysql(name, "列名"))
                            .append(' ').append(type);
                    if (size > 0 && java.util.Set.of("VARCHAR", "CHAR", "DECIMAL", "NUMERIC").contains(type.toUpperCase())) {
                        line.append('(').append(size).append(')');
                    }
                    line.append(nullable ? " NULL" : " NOT NULL");
                    if (def != null && !def.isBlank()) {
                        line.append(" DEFAULT ").append(def);
                    }
                    if (pkCols.contains(name)) {
                        line.append(" PRIMARY KEY");
                    }
                    defs.add(line.toString());
                }
            }
            ddl.append(String.join(",\n", defs)).append("\n);");
            return ReturnResult.ok(ddl.toString());
        } catch (Exception e) {
            return ReturnResult.error("获取建表语句失败: " + e.getMessage());
        }
    }

    /**
     * 获取表的外键列表。
     *
     * @param tableName    表名
     * @param datasourceId 数据源 ID
     * @param database     库名
     * @return 外键列表（约束名 / 本表列 / 引用表 / 引用列）
     */
    @Operation(summary = "获取外键列表")
    @GetMapping("/db/table/{tableName}/foreign-keys")
    public ReturnResult<java.util.List<Map<String, Object>>> getForeignKeys(
            @PathVariable String tableName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String table = safeIdent(tableName, "表名");
        try (java.sql.Connection conn = datasourceService.connection(datasourceId);
             SchemaScope scope = useSchema(conn, database)) {
            requireTable(conn, table);
            java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
            try (java.sql.ResultSet rs = conn.getMetaData().getImportedKeys(conn.getCatalog(), conn.getSchema(), table)) {
                while (rs.next()) {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("fkName", rs.getString("FK_NAME"));
                    row.put("columnName", rs.getString("FKCOLUMN_NAME"));
                    row.put("refTable", rs.getString("PKTABLE_NAME"));
                    row.put("refColumn", rs.getString("PKCOLUMN_NAME"));
                    row.put("updateRule", rs.getShort("UPDATE_RULE"));
                    row.put("deleteRule", rs.getShort("DELETE_RULE"));
                    list.add(row);
                }
            }
            return ReturnResult.ok(list);
        } catch (Exception e) {
            return ReturnResult.error("获取外键失败: " + e.getMessage());
        }
    }

    /**
     * 执行 SQL（支持 SELECT/SHOW/DESC/EXPLAIN 查询与 INSERT/UPDATE/DELETE/CREATE/ALTER/DROP 等更新）。
     *
     * <p>查询语句最多返回 {@value #SQL_MAX_ROWS} 行（超出置 truncated）；更新语句返回影响行数。
     * 数据库管理器的建表/改名/复制表/查看建表语句/导入数据/查询控制台均经此端点。</p>
     *
     * @param request SQL 执行请求（sql / datasourceId / database）
     * @return 执行结果
     */
    @Operation(summary = "执行 SQL")
    @PostMapping("/db/execute")
    public ReturnResult<DbSqlResultVO> executeDbSql(@RequestBody DbSqlExecuteDTO request) {
        String sql = request.sql();
        if (sql == null || sql.trim().isEmpty()) {
            return ReturnResult.error("SQL 不能为空");
        }
        Long datasourceId = request.datasourceId();
        String trimmed = sql.trim().toUpperCase();
        boolean isQuery = trimmed.startsWith("SELECT") || trimmed.startsWith("SHOW")
                || trimmed.startsWith("DESC") || trimmed.startsWith("EXPLAIN");
        try (java.sql.Connection conn = datasourceService.connection(datasourceId);
             java.sql.Statement stmt = conn.createStatement();
             SchemaScope scope = useSchema(conn, request.database())) {
            if (isQuery) {
                try (java.sql.ResultSet rs = stmt.executeQuery(sql)) {
                    java.sql.ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    java.util.List<String> columns = new java.util.ArrayList<>(colCount);
                    for (int i = 1; i <= colCount; i++) {
                        columns.add(meta.getColumnLabel(i));
                    }
                    java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
                    int rowCount = 0;
                    while (rs.next() && rowCount < SQL_MAX_ROWS) {
                        Map<String, Object> row = new java.util.LinkedHashMap<>(colCount);
                        for (int i = 1; i <= colCount; i++) {
                            row.put(columns.get(i - 1), rs.getObject(i));
                        }
                        rows.add(row);
                        rowCount++;
                    }
                    return ReturnResult.ok(new DbSqlResultVO(columns, rows, rowCount, rowCount >= SQL_MAX_ROWS, null, null));
                }
            }
            int affectedRows = stmt.executeUpdate(sql);
            return ReturnResult.ok(new DbSqlResultVO(null, null, null, null, affectedRows,
                    "执行成功，影响 " + affectedRows + " 行"));
        } catch (Exception e) {
            return ReturnResult.error("SQL 执行失败: " + e.getMessage());
        }
    }

    /**
     * 删除表。
     *
     * <p>安全豁免：表名经标识符白名单与 JDBC 元数据校验后才进入 MySQL 底层方言 DDL。</p>
     */
    @Operation(summary = "删除表")
    @DeleteMapping("/db/table/{tableName}")
    public ReturnResult<Boolean> dropTable(
            @PathVariable String tableName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String table = safeIdent(tableName, "表名");
        return callDb("删除表", () -> {
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                requireTable(conn, table);
                try (java.sql.PreparedStatement stmt = conn.prepareStatement("DROP TABLE " + SqlName.quoteMysql(table, "表名"))) {
                    stmt.executeUpdate();
                }
                return Boolean.TRUE;
            }
        });
    }

    /**
     * 新建数据库。
     *
     * <p>库的 DDL 随内核不同（MySQL 用 DATABASE、H2/PostgreSQL 用 SCHEMA），
     * 交由 {@link com.chua.starter.monitor.support.support.DbDialectSupport} 选型。</p>
     *
     * @param request 请求，不允许为 null
     * @return Return结果 对象
     */
    @Operation(summary = "新建数据库")
    @PostMapping("/db/database")
    public ReturnResult<Boolean> createDatabase(@RequestBody com.chua.starter.monitor.support.model.dto.DbDatabaseDTO request) {
        String name = request.name() == null ? "" : request.name().trim();
        if (!com.chua.starter.monitor.support.support.DbDialectSupport.isSafeIdentifier(name)
                || SYSTEM_SCHEMAS.contains(name.toUpperCase())) {
            return ReturnResult.error("数据库名只能由字母、数字和下划线组成，且不能以数字开头");
        }
        try (java.sql.Connection conn = datasourceService.connection(request.datasourceId())) {
            com.chua.starter.monitor.support.support.DbDialectSupport.createDatabase(
                    conn, name, request.charset(), request.collation());
            return ReturnResult.ok(true);
        } catch (Exception e) {
            return ReturnResult.error("创建数据库失败: " + e.getMessage());
        }
    }

    /**
     * 删除数据库。
     *
     * @param datasourceId 数据源 ID，为空或 0 表示默认主数据源
     * @param name         数据库名
     * @return Return结果 对象
     */
    @Operation(summary = "删除数据库")
    @DeleteMapping("/db/database")
    public ReturnResult<Boolean> dropDatabase(
            @RequestParam(required = false) Long datasourceId,
            @RequestParam String name) {
        String database = name == null ? "" : name.trim();
        if (!com.chua.starter.monitor.support.support.DbDialectSupport.isSafeIdentifier(database)
                || SYSTEM_SCHEMAS.contains(database.toUpperCase())) {
            return ReturnResult.error("数据库名只能由字母、数字和下划线组成，且不能以数字开头");
        }
        try (java.sql.Connection conn = datasourceService.connection(datasourceId)) {
            com.chua.starter.monitor.support.support.DbDialectSupport.dropDatabase(conn, database);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            return ReturnResult.error("删除数据库失败: " + e.getMessage());
        }
    }


    // ==================== 数据库表级编辑（单元格 / 索引 / 清空表 / 列） ====================

    /** 允许通过 DDL 拼接的列类型白名单，杜绝把前端传入的类型字段拼进 SQL 造成注入 */
    private static final java.util.Set<String> SAFE_COLUMN_TYPES =
            java.util.Set.of("VARCHAR", "INT", "BIGINT", "TEXT", "DATETIME", "DECIMAL", "FLOAT", "DOUBLE", "BOOLEAN", "BLOB");

    /** 可抛受检异常的 Supplier，配合 callDb 把 SQLException 转成可读错误 */
    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    /**
     * 执行一次表级 DML/DDL 操作，把底层异常换成可读原因而不是 500 堆栈。
     */
    private <T> ReturnResult<T> callDb(String action, ThrowingSupplier<T> supplier) {
        try {
            return ReturnResult.ok(supplier.get());
        } catch (Exception e) {
            log.error("[db] {}失败", action, e);
            return ReturnResult.error(action + "失败: " + e.getMessage());
        }
    }

    /** 校验并规整 SQL 标识符，非法直接抛异常，由 callDb 转成可读错误 */
    private static String safeIdent(String name, String label) {
        try {
            return SqlName.checkSimple(name == null ? null : name.trim(), label);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(label + "只能由字母、数字和下划线组成，且最长 64 字符: " + name, ex);
        }
    }

    private static void requireTable(java.sql.Connection conn, String table) throws java.sql.SQLException {
        java.sql.DatabaseMetaData metadata = conn.getMetaData();
        // 依次尝试 (catalog,schema) / (catalog,null) / (null,schema) / (null,null)：
        // MySQL 的 getSchema() 常为空串，配 useInformationSchema 时只传 catalog 才能命中
        if (!hasTable(metadata, conn.getCatalog(), conn.getSchema(), table)
                && !hasTable(metadata, conn.getCatalog(), null, table)
                && !hasTable(metadata, null, conn.getSchema(), table)
                && !hasTable(metadata, null, null, table)) {
            throw new java.sql.SQLException("表不存在: " + table);
        }
    }

    private static boolean hasTable(java.sql.DatabaseMetaData metadata, String catalog,
                                   String schema, String table) throws java.sql.SQLException {
        try (java.sql.ResultSet rs = metadata.getTables(catalog, schema, table, new String[]{"TABLE", "VIEW"})) {
            while (rs.next()) {
                if (table.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void requireColumn(java.sql.Connection conn, String table, String column) throws java.sql.SQLException {
        requireTable(conn, table);
        java.sql.DatabaseMetaData metadata = conn.getMetaData();
        if (!hasColumn(metadata, conn.getCatalog(), conn.getSchema(), table, column)
                && !hasColumn(metadata, conn.getCatalog(), null, table, column)
                && !hasColumn(metadata, null, conn.getSchema(), table, column)
                && !hasColumn(metadata, null, null, table, column)) {
            throw new java.sql.SQLException("列不存在: " + table + "." + column);
        }
    }

    private static boolean hasColumn(java.sql.DatabaseMetaData metadata, String catalog,
                                     String schema, String table, String column) throws java.sql.SQLException {
        try (java.sql.ResultSet rs = metadata.getColumns(catalog, schema, table, column)) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void requireMissingColumn(java.sql.Connection conn, String table, String column)
            throws java.sql.SQLException {
        requireTable(conn, table);
        java.sql.DatabaseMetaData metadata = conn.getMetaData();
        if (hasColumn(metadata, conn.getCatalog(), conn.getSchema(), table, column)
                || hasColumn(metadata, null, null, table, column)) {
            throw new java.sql.SQLException("列已存在: " + table + "." + column);
        }
    }

    private static void requireIndex(java.sql.Connection conn, String table, String index) throws java.sql.SQLException {
        requireTable(conn, table);
        try (java.sql.ResultSet rs = conn.getMetaData().getIndexInfo(conn.getCatalog(), conn.getSchema(), table, false, true)) {
            while (rs.next()) {
                if (index.equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
                    return;
                }
            }
        }
        throw new java.sql.SQLException("索引不存在: " + index);
    }


    /** 从请求体里取可选的 datasourceId，空串/0 统一归一为 null（走默认主数据源） */
    private static Long bodyDatasourceId(Map<String, Object> body) {
        Object v = body.get("datasourceId");
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        long id = Long.parseLong(String.valueOf(v));
        return id == 0 ? null : id;
    }

    /** 从请求体里取可选 database */
    private static String bodyDatabase(Map<String, Object> body) {
        Object v = body.get("database");
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        return String.valueOf(v);
    }

    /**
     * 更新单元格：按主键等值条件定位，PreparedStatement 绑定值防注入。
     *
     * <p>安全豁免：表名、目标列和主键列均经标识符白名单与 JDBC 元数据双重校验。</p>
     */
    @Operation(summary = "更新单元格")
    @PutMapping("/db/table/{tableName}/cell")
    public ReturnResult<Integer> updateCell(@PathVariable String tableName, @RequestBody Map<String, Object> body) {
        String t = safeIdent(tableName, "表名");
        String column = safeIdent(String.valueOf(body.get("column")), "列名");
        Object newValue = body.get("newValue");
        @SuppressWarnings("unchecked")
        Map<String, Object> primaryKey = (Map<String, Object>) body.getOrDefault("primaryKey", java.util.Collections.emptyMap());
        if (primaryKey == null || primaryKey.isEmpty()) {
            return ReturnResult.error("缺少主键定位条件 primaryKey");
        }
        StringBuilder sql = new StringBuilder("UPDATE ").append(SqlName.quoteMysql(t, "表名"))
                .append(" SET ").append(SqlName.quoteMysql(column, "列名")).append(" = ? WHERE ");
        List<Object> params = new ArrayList<>();
        params.add(newValue);
        boolean first = true;
        for (Map.Entry<String, Object> e : primaryKey.entrySet()) {
            if (!first) {
                sql.append(" AND ");
            }
            sql.append(SqlName.quoteMysql(safeIdent(e.getKey(), "主键列"), "主键列")).append(" = ?");
            params.add(e.getValue());
            first = false;
        }
        Long dsId = bodyDatasourceId(body);
        String db = bodyDatabase(body);
        return callDb("更新单元格", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                requireTable(conn, t);
                requireColumn(conn, t, column);
                for (Map.Entry<String, Object> entry : primaryKey.entrySet()) {
                    requireColumn(conn, t, safeIdent(entry.getKey(), "主键列"));
                }
                try (java.sql.PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                    return ps.executeUpdate();
                }
            }

        });
    }

    /**
     * 读取表索引列表（经 DatabaseMetaData.getIndexInfo）。
     */
    @Operation(summary = "获取表索引")
    @GetMapping("/db/table/{tableName}/indexes")
    public ReturnResult<java.util.List<Map<String, Object>>> getTableIndexes(
            @PathVariable String tableName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String t = safeIdent(tableName, "表名");
        return callDb("获取表索引", () -> {
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                requireTable(conn, t);
                String catalog = conn.getCatalog();

                String schema = conn.getSchema();
                java.util.List<Map<String, Object>> list = new ArrayList<>();
                try (java.sql.ResultSet rs = conn.getMetaData().getIndexInfo(catalog, schema, t, false, false)) {
                    while (rs.next()) {
                        String idxName = rs.getString("INDEX_NAME");
                        if (idxName == null || "PRIMARY".equalsIgnoreCase(idxName)) {
                            continue;
                        }
                        Map<String, Object> row = new java.util.LinkedHashMap<>();
                        row.put("indexName", idxName);
                        row.put("columnName", rs.getString("COLUMN_NAME"));
                        row.put("nonUnique", rs.getBoolean("NON_UNIQUE"));
                        row.put("indexType", rs.getInt("TYPE"));
                        row.put("seqInIndex", rs.getShort("ORDINAL_POSITION"));
                        list.add(row);
                    }
                }
                return list;
            }
        });
    }

    /**
     * 新建索引（支持联合列与唯一索引）。
     *
     * <p>安全豁免：索引名、表名和索引列经标识符白名单及 JDBC 元数据校验后进入 MySQL 底层方言 DDL。</p>
     */
    @Operation(summary = "新建索引")
    @PostMapping("/db/table/{tableName}/index")
    public ReturnResult<Boolean> createIndex(@PathVariable String tableName, @RequestBody Map<String, Object> body) {
        String t = safeIdent(tableName, "表名");
        String indexName = safeIdent(String.valueOf(body.get("indexName")), "索引名");
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) body.get("columns");
        boolean unique = Boolean.TRUE.equals(body.get("unique"));
        if (columns == null || columns.isEmpty()) {
            return ReturnResult.error("请至少选择一列");
        }
        StringBuilder sql = new StringBuilder(unique ? "CREATE UNIQUE INDEX " : "CREATE INDEX ");
        sql.append(SqlName.quoteMysql(indexName, "索引名"))
                .append(" ON ").append(SqlName.quoteMysql(t, "表名")).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(SqlName.quoteMysql(safeIdent(columns.get(i), "索引列"), "索引列"));
        }
        sql.append(")");
        Long dsId = bodyDatasourceId(body);
        String db = bodyDatabase(body);
        return callDb("新建索引", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                requireTable(conn, t);
                for (String indexColumn : columns) {
                    requireColumn(conn, t, safeIdent(indexColumn, "索引列"));
                }
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                    stmt.executeUpdate();
                }
                return Boolean.TRUE;
            }
        });

    }

    /**
     * 删除索引。
     *
     * <p>安全豁免：索引名和表名经标识符白名单及 JDBC 元数据校验。</p>
     */
    @Operation(summary = "删除索引")
    @DeleteMapping("/db/table/{tableName}/index/{indexName}")
    public ReturnResult<Boolean> dropIndex(
            @PathVariable String tableName,
            @PathVariable String indexName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String t = safeIdent(tableName, "表名");
        String idx = safeIdent(indexName, "索引名");
        String sql = "DROP INDEX " + SqlName.quoteMysql(idx, "索引名")
                + " ON " + SqlName.quoteMysql(t, "表名");
        return callDb("删除索引", () -> {
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                requireIndex(conn, t, idx);
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.executeUpdate();
                }
                return Boolean.TRUE;
            }
        });

    }

    // ==================== 数据导入 / 导出（对齐 Navicat 导入导出向导） ====================

    /**
     * 导入预览：解析上传文件、回显列名与前若干行，供用户在向导第一步确认解析结果。
     *
     * <p>只解析不落库，因此不会对目标库产生任何副作用。</p>
     *
     * @param file     上传的数据文件
     * @param format   格式：auto / csv / tsv / json
     * @param hasHeader 首行是否为表头
     * @param encoding  字符集名，可为空
     * @return 预览结果
     */
    @Operation(summary = "导入预览（解析文件但不写库）")
    @PostMapping("/db/import/preview")
    public ReturnResult<com.chua.starter.monitor.support.model.DbImportPreviewVO> previewImportFile(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(required = false) String format,
            @RequestParam(required = false, defaultValue = "true") boolean hasHeader,
            @RequestParam(required = false) String encoding) {
        int previewSize = 50;
        return callDb("解析导入文件", () -> {
            byte[] bytes = file.getBytes();
            String text = com.chua.starter.monitor.support.support.DbTransferCodec.decode(bytes, encoding);
            String resolvedFormat = resolveImportFormat(format, file.getOriginalFilename(), text);
            if ("sql".equals(resolvedFormat)) {
                int statements = com.chua.starter.monitor.support.support.DbTransferCodec.splitSqlStatements(text).size();
                return new com.chua.starter.monitor.support.model.DbImportPreviewVO(
                        List.of(), List.of(), statements, 0, ";", encoding == null ? "UTF-8" : encoding);
            }
            char delimiter = resolveDelimiter(resolvedFormat, text);
            var table = "json".equals(resolvedFormat)
                    ? com.chua.starter.monitor.support.support.DbTransferCodec.parseJson(text, List.of())
                    : com.chua.starter.monitor.support.support.DbTransferCodec.parseDelimited(text, delimiter, hasHeader);
            var rows = table.rows();
            var preview = rows.size() <= previewSize ? rows : rows.subList(0, previewSize);
            return new com.chua.starter.monitor.support.model.DbImportPreviewVO(
                    table.columns(), preview, rows.size(), previewSize,
                    delimiter == '\t' ? "\\t" : String.valueOf(delimiter),
                    encoding == null ? "UTF-8" : encoding);
        });
    }

    /**
     * 执行导入：按字段映射把上传文件写入目标表。
     *
     * <p>安全豁免：目标表名与参与写入的列名经标识符白名单与 JDBC 元数据校验后引用；
     * 单元格值全部走 PreparedStatement 参数绑定。字段映射为空时报错而非静默按表结构硬套。</p>
     *
     * @param file   上传的数据文件
     * @param option 导入选项（格式 / 表头 / 映射 / 冲突策略 / 是否先清空）
     * @return 导入统计
     */
    @Operation(summary = "执行数据导入")
    @PostMapping("/db/import")
    public ReturnResult<com.chua.starter.monitor.support.support.DbImporter.ImportResult> importDataFile(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "option", required = false) String optionJson) {
        return callDb("导入数据", () -> {
            com.chua.starter.monitor.support.model.dto.DbImportDTO option = parseImportOption(optionJson);
            if (option == null) {
                throw new IllegalArgumentException("缺少导入选项 option");
            }
            String table = safeIdent(option.tableName(), "表名");
            String format = resolveImportFormat(option.format(), file.getOriginalFilename(), null);
            byte[] bytes = file.getBytes();
            String text = com.chua.starter.monitor.support.support.DbTransferCodec.decode(bytes, option.encoding());
            if ("sql".equals(format)) {
                return executeSqlScript(text, option);
            }
            var options = new com.chua.starter.monitor.support.support.DbImporter.ImportOptions(
                    table,
                    option.sourceColumns().isEmpty() ? List.of() : option.sourceColumns(),
                    option.columnMapping() == null ? Map.of() : option.columnMapping(),
                    option.truncateFirst(),
                    option.onConflict(),
                    List.of(),
                    option.batchSize() == null ? 0 : option.batchSize());
            try (java.sql.Connection conn = datasourceService.connection(option.datasourceId());
                 SchemaScope scope = useSchema(conn, option.database())) {
                requireTable(conn, table);
                if (option.truncateFirst()) {
                    try (java.sql.Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate("TRUNCATE TABLE " + SqlName.quoteMysql(table, "表名"));
                    } catch (java.sql.SQLException fallback) {
                        try (java.sql.Statement stmt = conn.createStatement()) {
                            stmt.executeUpdate("DELETE FROM " + SqlName.quoteMysql(table, "表名"));
                        }
                    }
                }
                char delimiter = resolveDelimiter(format, text);
                var parsed = "json".equals(format)
                        ? com.chua.starter.monitor.support.support.DbTransferCodec.parseJson(text, List.of())
                        : com.chua.starter.monitor.support.support.DbTransferCodec.parseDelimited(
                                text, delimiter, !Boolean.FALSE.equals(option.hasHeader()));
                if (options.sourceColumns().isEmpty() && option.columnMapping() == null) {
                    throw new IllegalArgumentException("请先配置字段映射");
                }
                return com.chua.starter.monitor.support.support.DbImporter.importRows(conn, parsed.rows(), options);
            }
        });
    }

    /**
     * 解析导入选项 JSON（前端以字符串形式随 multipart 一起提交）。
     *
     * @param optionJson 选项 JSON
     * @return 导入选项；输入为空返回 null
     */
    private static com.chua.starter.monitor.support.model.dto.DbImportDTO parseImportOption(String optionJson) {
        if (optionJson == null || optionJson.isBlank()) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(optionJson, com.chua.starter.monitor.support.model.dto.DbImportDTO.class);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("导入选项格式不正确: " + e.getMessage());
        }
    }

    /**
     * 导入 SQL 脚本：逐条执行，支持空行与注释跳过。
     *
     * @param text   SQL 文本
     * @param option 导入选项（取 datasourceId / database）
     * @return 导入统计
     * @throws Exception 任一语句失败或取连接失败
     */
    private com.chua.starter.monitor.support.support.DbImporter.ImportResult executeSqlScript(
            String text, com.chua.starter.monitor.support.model.dto.DbImportDTO option) throws Exception {
        var statements = com.chua.starter.monitor.support.support.DbTransferCodec.splitSqlStatements(text);
        int affected = 0;
        try (java.sql.Connection conn = datasourceService.connection(option.datasourceId());
             SchemaScope scope = useSchema(conn, option.database());
             java.sql.Statement stmt = conn.createStatement()) {
            for (String statement : statements) {
                affected += stmt.executeUpdate(statement);
            }
        }
        return new com.chua.starter.monitor.support.support.DbImporter.ImportResult(
                statements.size(), affected, 0, 0, List.of());
    }

    /**
     * 导出：把表（或其结构 / 数据）序列化为目标格式。
     *
     * <p>安全豁免：表名、排序列经标识符白名单与 JDBC 元数据校验；WHERE 条件仅允许
     * 单条筛选语句（已剔除分隔符与注释符）。行数上限 {@value #EXPORT_MAX_ROWS}。</p>
     *
     * @param request 导出请求（tableName / format / scope / limit / where / orderBy）
     * @return 导出内容（字符串，前端侧落盘为文件）
     */
    @Operation(summary = "导出数据")
    @PostMapping("/db/export")
    public ReturnResult<Map<String, Object>> exportData(@RequestBody Map<String, Object> request) {
        String table = safeIdent(String.valueOf(request.get("tableName")), "表名");
        String format = String.valueOf(request.getOrDefault("format", "csv"));
        String scopeName = String.valueOf(request.getOrDefault("scope", "data"));
        int limit = intOrDefault(request.get("limit"), 0);
        String where = request.get("where") == null ? null : sanitizeWhere(String.valueOf(request.get("where")));
        String orderBy = request.get("orderBy") == null ? null
                : safeIdent(String.valueOf(request.get("orderBy")), "排序列");
        String orderDir = request.get("orderDir") == null ? "ASC" : String.valueOf(request.get("orderDir"));
        Long dsId = bodyDatasourceId(request);
        String db = bodyDatabase(request);
        return callDb("导出数据", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                requireTable(conn, table);
                var formatEnum = com.chua.starter.monitor.support.support.DbExporter.Format.of(format);
                var scopeEnum = com.chua.starter.monitor.support.support.DbExporter.Scope.of(scopeName);
                var options = new com.chua.starter.monitor.support.support.DbExporter.ExportOptions(
                        table, formatEnum, scopeEnum, limit, where, orderBy, orderDir, 100);
                var result = com.chua.starter.monitor.support.support.DbExporter.export(conn, options);
                Map<String, Object> payload = new java.util.LinkedHashMap<>();
                payload.put("content", result.content());
                payload.put("rowCount", result.rowCount());
                payload.put("truncated", result.truncated());
                payload.put("format", formatEnum.name().toLowerCase(java.util.Locale.ROOT));
                payload.put("fileName", table + "_" + com.chua.starter.monitor.support.support.DbTransferCodec.timestamp()
                        + "." + formatEnum.extension());
                return payload;
            }
        });
    }

    /**
     * 导出格式与范围的可选值（供前端下拉，避免前后端枚举各写一份）。
     *
     * @return 格式列表与范围列表
     */
    @Operation(summary = "导出格式与范围枚举")
    @GetMapping("/db/export/options")
    public ReturnResult<Map<String, Object>> exportOptions() {
        List<Map<String, String>> formats = Arrays.stream(
                        com.chua.starter.monitor.support.support.DbExporter.Format.values())
                .map(f -> Map.of("value", f.name().toLowerCase(java.util.Locale.ROOT), "label", switch (f) {
                    case CSV -> "CSV（逗号分隔）";
                    case TSV -> "TSV（制表符分隔）";
                    case JSON -> "JSON";
                    case XML -> "XML";
                    case SQL -> "SQL 脚本";
                }))
                .toList();
        List<Map<String, String>> scopes = Arrays.stream(
                        com.chua.starter.monitor.support.support.DbExporter.Scope.values())
                .map(s -> Map.of("value", s.name().toLowerCase(java.util.Locale.ROOT), "label", switch (s) {
                    case STRUCTURE -> "仅结构";
                    case DATA -> "仅数据";
                    case ALL -> "结构 + 数据";
                }))
                .toList();
        return ReturnResult.ok(Map.of("formats", formats, "scopes", scopes));
    }

    /** 导出单次行数上限 */
    private static final int EXPORT_MAX_ROWS = com.chua.starter.monitor.support.support.DbTransferCodec.MAX_EXPORT_ROWS;

    /**
     * 从请求体取整数，缺失或非数字时用默认值。
     */
    private static int intOrDefault(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 判定导入文件格式：显式指定优先，否则按后缀，再否则按内容首字符。
     *
     * @param format    前端指定的格式
     * @param fileName  原始文件名
     * @param text      文件文本（可能为 null）
     * @return 格式标识
     */
    private static String resolveImportFormat(String format, String fileName, String text) {
        String explicit = format == null ? "" : format.trim().toLowerCase(java.util.Locale.ROOT);
        if (!explicit.isEmpty() && !"auto".equals(explicit)) {
            return explicit;
        }
        String name = fileName == null ? "" : fileName.toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".sql")) {
            return "sql";
        }
        if (name.endsWith(".json")) {
            return "json";
        }
        if (name.endsWith(".tsv") || name.endsWith(".tab")) {
            return "tsv";
        }
        if (name.endsWith(".csv")) {
            return "csv";
        }
        String sample = text == null ? "" : text.stripLeading();
        if (sample.startsWith("[") || sample.startsWith("{")) {
            return "json";
        }
        return sample.toUpperCase(java.util.Locale.ROOT).startsWith("INSERT") ? "sql" : "csv";
    }

    /**
     * 判定分隔符：TSV 固定制表符，其余按内容探测（首行含制表符则视为 TSV）。
     */
    private static char resolveDelimiter(String format, String text) {
        if ("tsv".equals(format)) {
            return '\t';
        }
        if (text == null) {
            return ',';
        }
        return com.chua.starter.monitor.support.support.DbTransferCodec.detectDelimiter(text);
    }

    /**
     * 清空表：优先 TRUNCATE，不支持的内核退化为 DELETE FROM。
     *
     * <p>安全豁免：表名经标识符白名单和 JDBC 元数据校验后才进入数据库方言 DDL。</p>
     */
    @Operation(summary = "清空表")
    @PostMapping("/db/table/{tableName}/truncate")
    public ReturnResult<Boolean> truncateTable(
            @PathVariable String tableName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String t = safeIdent(tableName, "表名");
        return callDb("清空表", () -> {
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                requireTable(conn, t);
                try {
                    try (java.sql.PreparedStatement stmt = conn.prepareStatement("TRUNCATE TABLE " + SqlName.quoteMysql(t, "表名"))) {
                        stmt.executeUpdate();
                    }
                } catch (java.sql.SQLException fallback) {
                    try (java.sql.PreparedStatement stmt = conn.prepareStatement("DELETE FROM " + SqlName.quoteMysql(t, "表名"))) {
                        stmt.executeUpdate();
                    }
                }
                return Boolean.TRUE;
            }
        });

    }

    /**
     * 拼接列定义片段（类型 + 可空 + 默认值 + 注释），类型走白名单。
     */
    private static String buildColumnDef(Map<String, Object> body) {
        String rawType = body.get("type") == null ? "" : String.valueOf(body.get("type")).trim().toUpperCase();
        if (!SAFE_COLUMN_TYPES.contains(rawType)) {
            throw new IllegalArgumentException("不支持的列类型: " + body.get("type"));
        }
        StringBuilder ddl = new StringBuilder(rawType);
        if (rawType.equals("VARCHAR") || rawType.equals("DECIMAL")) {
            int size;
            try {
                size = body.get("size") == null || String.valueOf(body.get("size")).isBlank()
                        ? (rawType.equals("VARCHAR") ? 255 : 10)
                        : Integer.parseInt(String.valueOf(body.get("size")).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("长度必须是整数: " + body.get("size"));
            }
            if (size <= 0 || size > 10000) {
                throw new IllegalArgumentException("长度必须在 1 到 10000 之间");
            }
            ddl.append(rawType.equals("VARCHAR") ? "(" + size + ")" : "(" + size + ",2)");

        }
        boolean nullable = !Boolean.FALSE.equals(body.get("nullable"));
        ddl.append(nullable ? " NULL" : " NOT NULL");
        return ddl.toString();
    }

/**
     * 仅取类型定义（如 VARCHAR(80)），供跨方言 ALTER COLUMN 使用。
     *
     * @param body 列定义请求体
     * @return 类型定义
     */
    private static String columnTypeDef(Map<String, Object> body) {
        String rawType = body.get("type") == null ? "" : String.valueOf(body.get("type")).trim().toUpperCase();
        if (!SAFE_COLUMN_TYPES.contains(rawType)) {
            throw new IllegalArgumentException("不支持的列类型: " + body.get("type"));
        }
        if (rawType.equals("VARCHAR") || rawType.equals("DECIMAL")) {
            int size;
            try {
                size = body.get("size") == null || String.valueOf(body.get("size")).isBlank()
                        ? (rawType.equals("VARCHAR") ? 255 : 10)
                        : Integer.parseInt(String.valueOf(body.get("size")).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("长度必须是整数: " + body.get("size"));
            }
            if (size <= 0 || size > 10000) {
                throw new IllegalArgumentException("长度必须在 1 到 10000 之间");
            }
            return rawType.equals("VARCHAR") ? "VARCHAR(" + size + ")" : "DECIMAL(" + size + ",2)";
        }
        return rawType;
    }

    /**
     * 新增列（ALTER TABLE ADD COLUMN）。
     *
     * <p>安全豁免：表名和列名经严格校验；默认值与注释由
     * {@link com.chua.starter.monitor.support.support.DbDialectSupport} 参数绑定。</p>
     */
    @Operation(summary = "新增列")
    @PostMapping("/db/table/{tableName}/column")
    public ReturnResult<Boolean> addColumn(@PathVariable String tableName, @RequestBody Map<String, Object> body) {
        String t = safeIdent(tableName, "表名");
        String col = safeIdent(String.valueOf(body.get("columnName")), "列名");
        String typeDefinition = buildColumnDef(body);
        boolean nullable = !Boolean.FALSE.equals(body.get("nullable"));
        String defaultValue = body.get("defaultValue") == null ? null : String.valueOf(body.get("defaultValue"));
        String comment = body.get("comment") == null ? null : String.valueOf(body.get("comment"));
        Long dsId = bodyDatasourceId(body);
        String db = bodyDatabase(body);
        return callDb("新增列", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                requireMissingColumn(conn, t, col);
                com.chua.starter.monitor.support.support.DbDialectSupport.addColumn(
                        conn, t, col, typeDefinition, nullable, defaultValue, comment);
                return Boolean.TRUE;
            }
        });

    }

    /**
     * 修改列（ALTER TABLE MODIFY COLUMN）。
     *
     * <p>安全豁免：标识符和类型定义经白名单校验；默认值与注释全部参数绑定。</p>
     */
    @Operation(summary = "修改列")
    @PutMapping("/db/table/{tableName}/column/{columnName}")
    public ReturnResult<Boolean> modifyColumn(
            @PathVariable String tableName,
            @PathVariable String columnName,
            @RequestBody Map<String, Object> body) {
        String t = safeIdent(tableName, "表名");
        String col = safeIdent(columnName, "列名");
        String typeDef = columnTypeDef(body);
        boolean nullable = !Boolean.FALSE.equals(body.get("nullable"));
        String defaultValue = body.get("defaultValue") == null ? null : String.valueOf(body.get("defaultValue"));
        String comment = body.get("comment") == null ? null : String.valueOf(body.get("comment"));
        Long dsId = bodyDatasourceId(body);
        String db = bodyDatabase(body);
        return callDb("修改列", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                requireColumn(conn, t, col);
                com.chua.starter.monitor.support.support.DbDialectSupport.alterColumn(
                        conn, t, col, typeDef, nullable, defaultValue, comment);
                return Boolean.TRUE;
            }
        });
    }

    /**
     * 删除列（ALTER TABLE DROP COLUMN）。
     *
     * <p>安全豁免：表名和列名经标识符白名单及 JDBC 元数据校验。</p>
     */
    @Operation(summary = "删除列")
    @DeleteMapping("/db/table/{tableName}/column/{columnName}")
    public ReturnResult<Boolean> dropColumn(
            @PathVariable String tableName,
            @PathVariable String columnName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String t = safeIdent(tableName, "表名");
        String col = safeIdent(columnName, "列名");
        String sql = "ALTER TABLE " + SqlName.quoteMysql(t, "表名")
                + " DROP COLUMN " + SqlName.quoteMysql(col, "列名");
        return callDb("删除列", () -> {
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                requireColumn(conn, t, col);
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.executeUpdate();
                }
                return Boolean.TRUE;
            }
        });

    }

    // ==================== Redis 操作 ====================

    /**
     * 获取当前系统 Redis 信息（信息 关键指标）。
     * <p>
     * 直接读取应用自身连接的 Redis（spring.数据.Redis），与监控资产/Agent 无关；
     * 无 Redis 配置时前端不显示该菜单。
     * </p>
     * <p>
     * INFO 输出的键集随 Redis 版本变化，属通用动态键值结构而非业务载荷，故不改为 VO。
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
    public ReturnResult<RedisScanVO> scanSystemRedisKeys(
            @RequestParam(defaultValue = "*") String pattern,
            @RequestParam(defaultValue = "0") String cursor,
            @RequestParam(defaultValue = "100") int count) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null || redisTemplate.getConnectionFactory() == null) {
            return ReturnResult.error("当前系统未配置 Redis");
        }
        try {
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
            return ReturnResult.ok(new RedisScanVO(keys, keys.size() >= count ? "1" : "0", keys.size()));
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
    public ReturnResult<RedisKeyDetailVO> getSystemRedisKeyDetail(@PathVariable String key) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return ReturnResult.error("当前系统未配置 Redis");
        }
        try {
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
                    java.util.List<RedisMemberScoreVO> zSetList = new java.util.ArrayList<>();
                    for (var v : zSetValues) {
                        zSetList.add(new RedisMemberScoreVO(v.getValue(), v.getScore()));
                    }
                    value = zSetList;
                }
                size = redisTemplate.opsForZSet().size(key);
            } else if ("STREAM".equals(type)) {
                java.util.List<RedisStreamEntryVO> entries = kvService.readStream(key, STREAM_DETAIL_LIMIT);
                value = entries;
                size = (long) entries.size();
            }
            return ReturnResult.ok(new RedisKeyDetailVO(key, type,
                    ttl != null && ttl >= 0 ? ttl : -1, value, size));
        } catch (Exception e) {
            return ReturnResult.error("获取 Redis Key 详情失败: " + e.getMessage());
        }
    }

    /**
     * 写入当前系统 Redis Key。
     *
     * @param key   键名
     * @param body  值（请求体）
     * @param ttl   过期时间（秒），可选，-1 表示永不过期
     * @return 是否成功
     */
    @Operation(summary = "写入当前系统 Redis Key")
    @PutMapping("/system/redis/kv/{key}")
    public ReturnResult<Boolean> setSystemRedisKey(
            @PathVariable String key,
            @RequestBody RedisValueVO body,
            @RequestParam(defaultValue = "-1") long ttl) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return ReturnResult.error("当前系统未配置 Redis");
        }
        if (body == null || body.value() == null) {
            return ReturnResult.error("值不能为空");
        }
        String value = body.value();
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
     * @return 键值结构 {key, value, ttl, type, size}；Key 不存在时 value 为空串
     */
    @Operation(summary = "读取 Redis Key")
    @GetMapping("/operation/redis/{taskId}/kv/{key}")
    public Mono<RedisOperationKeyDetailVO> getValue(@PathVariable Long taskId, @PathVariable String key) {
        return getKeyDetail(taskId, key);
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
    @Operation(summary = "批量删除 Redis Key")
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
     * 获取缓存前缀汇总（按前缀分组统计 Key 数量）
     *
     * @param taskId 任务 ID，路径参数，不能为 null
     * @param prefix 前缀匹配模式（可选，默认 "*"）
     * @return 缓存前缀汇总列表
     */
    @Operation(summary = "获取缓存前缀汇总")
    @GetMapping("/operation/redis/{taskId}/cache-summary")
    public Mono<List<RedisCacheSummaryVO>> getCacheSummary(
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
                        List<RedisCacheSummaryVO> resultList = new java.util.ArrayList<>();
                        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
                            resultList.add(new RedisCacheSummaryVO(entry.getKey(), entry.getValue().size()));
                        }
                        resultList.sort((a, b) -> Integer.compare(b.count(), a.count()));
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
    public Mono<RedisOperationKeyDetailVO> getKeyDetail(@PathVariable Long taskId, @PathVariable String key) {
        return resolveOperation(taskId).flatMap(op -> {
            CollectContext ctx = buildContext(taskId);
            return Mono.zip(
                    op.getValue(ctx, key).defaultIfEmpty(""),
                    op.ttl(ctx, key).defaultIfEmpty(-1L),
                    op.type(ctx, key).defaultIfEmpty("STRING")
            ).map(tuple -> new RedisOperationKeyDetailVO(key, tuple.getT1(), tuple.getT2(), tuple.getT3(),
                    tuple.getT1() != null ? tuple.getT1().length() : 0));
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

    // ==================== 全局概览 ====================

    /**
     * 全局概览统计。
     * @return 资产总数、启用/停用数与服务器/Redis/数据库分类计数
     */
    @Operation(summary = "全局概览统计")
    @GetMapping("/dashboard/overview")
    public ReturnResult<DashboardOverviewVO> dashboardOverview() {
        List<MsMonitor> monitors = monitorService.listAll().getData();
        int total = monitors == null ? 0 : monitors.size();
        int enabled = 0;
        int serverCount = 0;
        int redisCount = 0;
        int dbCount = 0;
        if (monitors != null) {
            for (MsMonitor monitor : monitors) {
                if (Integer.valueOf(1).equals(monitor.getMonitorEnabled())) {
                    enabled++;
                }
                switch (ServiceType.of(monitor.getServiceType())) {
                    case SERVER -> serverCount++;
                    case REDIS -> redisCount++;
                    case MYSQL, PG, ORACLE, MSSQL -> dbCount++;
                    default -> {
                        // MQ 等类型不计入三类卡片
                    }
                }
            }
        }
        return ReturnResult.ok(new DashboardOverviewVO(total, enabled, total - enabled,
                serverCount, redisCount, dbCount));
    }

    // ==================== AI 智能分析 ====================

    /**
     * 主机指标分析的系统提示词。
     */
    private static final String HOST_ANALYZE_SYSTEM_PROMPT =
            "你是一位资深的运维监控专家，擅长主机性能分析和容量规划。请用专业、简洁、可操作的语言输出分析结果。";

    /**
     * 查询 AI 分析是否可用。
     *
     * <p>判据与真实对话同源：先用全局设置解析出默认模型（解析失败即视为不可用），再让对话门面按
     * 「真实调用时完全相同的解析链路」探测客户端。自己按凭证字段猜会把未配置的实例报成可用，
     * 页面就会出现「状态显示正常、点击按钮报错」。</p>
     *
     * @return AI 状态，不可用时携带原因
     */
    @Operation(summary = "查询 AI 分析是否启用")
    @GetMapping("/ai/status")
    public ReturnResult<AiStatusVO> getAiStatus() {
        AiChatFacadeService facade = aiChatFacadeProvider.getIfAvailable();
        if (facade == null) {
            return ReturnResult.ok(new AiStatusVO(false, null, null, "未装配 AI 对话服务"));
        }
        ChatClientSettings settings = resolveAiSettings();
        if (settings == null) {
            return ReturnResult.ok(new AiStatusVO(false, null, null, "AI 全局设置不可用或已禁用"));
        }
        AiClientProbe probe = facade.probeChatClient(settings.getDefaultModel());
        if (!probe.available()) {
            return ReturnResult.ok(new AiStatusVO(false, probe.model(), null, probe.reason()));
        }
        return ReturnResult.ok(new AiStatusVO(true, probe.model(),
                isBlank(probe.provider()) ? settings.getProvider() : probe.provider(), null));
    }

    /**
     * 通用 AI 分析。
     *
     * @param request 请求，不允许为 null
     * @return Mono 对象
     */
    @Operation(summary = "通用 AI 分析")
    @PostMapping("/ai/analyze")
    public Mono<ReturnResult<AiAnalyzeVO>> aiAnalyze(@RequestBody AiAnalyzeDTO request) {
        String prompt = request.prompt() == null ? "" : request.prompt().trim();
        if (prompt.isEmpty()) {
            return Mono.just(ReturnResult.error("分析内容不能为空"));
        }
        AiChatFacadeService facade = aiChatFacadeProvider.getIfAvailable();
        if (facade == null) {
            return Mono.just(ReturnResult.error("AI 分析未启用，请配置 AI 模型"));
        }
        AiChatRequest chatRequest = buildChatRequest(prompt, request.systemPrompt(), request.model(), null);
        return Mono.fromCallable(() -> {
            AiChatResult result = facade.callForResult(chatRequest);
            if (!result.success()) {
                return ReturnResult.<AiAnalyzeVO>error("AI 分析失败: " + result.errorMessage());
            }
            return ReturnResult.ok(new AiAnalyzeVO(result.text(), result.model(), result.usage()));
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .onErrorResume(e -> Mono.just(ReturnResult.<AiAnalyzeVO>error("AI 分析失败: " + e.getMessage())));
    }

    /**
     * 主机指标智能分析。
     *
     * <p>指标由服务端从主机资产实时取数，不接受前端回传的指标快照——否则页面伪造一份数据就能"分析"出结论。</p>
     *
     * @param id      主机资产 ID（与 /server-host/{id} 同一 ID 域），路径参数，不能为 null
     * @param request 请求，可为空
     * @return Mono 对象
     */
    @Operation(summary = "主机指标智能分析")
    @PostMapping("/ai/host/{id}/analyze")
    public Mono<ReturnResult<AiHostAnalyzeVO>> aiAnalyzeHost(@PathVariable Long id,
                                                             @RequestBody(required = false) AiHostAnalyzeDTO request) {
        AiChatFacadeService facade = aiChatFacadeProvider.getIfAvailable();
        if (facade == null) {
            return Mono.just(ReturnResult.error("AI 分析未启用，请配置 AI 模型"));
        }
        MsServerHost host = serverHostService.get(id).getData();
        if (host == null) {
            return Mono.just(ReturnResult.error("主机资产不存在"));
        }
        String dimension = request == null ? null : request.dimension();
        String prompt = buildHostPrompt(host, dimension);
        if (prompt == null) {
            return Mono.just(ReturnResult.error("该主机暂无指标数据，无法分析"));
        }
        AiChatRequest chatRequest = buildChatRequest(prompt,
                request == null ? null : request.systemPrompt(),
                request == null ? null : request.model(),
                HOST_ANALYZE_SYSTEM_PROMPT);
        chatRequest.setCallType("host_analyze");
        return Mono.fromCallable(() -> {
            AiChatResult result = facade.callForResult(chatRequest);
            if (!result.success()) {
                return ReturnResult.<AiHostAnalyzeVO>error("主机 AI 分析失败: " + result.errorMessage());
            }
            return ReturnResult.ok(new AiHostAnalyzeVO(id, host.getServerHostName(),
                    result.text(), result.model(), result.usage()));
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .onErrorResume(e -> Mono.just(ReturnResult.<AiHostAnalyzeVO>error("主机 AI 分析失败: " + e.getMessage())));
    }

    /**
     * 解析 AI 全局设置。
     *
     * @return 设置，未装配 ai-starter 或被显式禁用时返回 null
     */
    private ChatClientSettings resolveAiSettings() {
        SysAiGlobalSettingService settingService = aiGlobalSettingProvider.getIfAvailable();
        if (settingService == null) {
            return null;
        }
        try {
            return ChatClientSettingsResolver.resolve(settingService);
        } catch (Exception e) {
            log.info("[MonitorAi] AI 全局设置不可用: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 组装对话请求：模型缺省时沿用全局默认模型，系统提示词缺省时沿用全局设置里的值。
     *
     * @param prompt         用户提示词
     * @param systemPrompt   系统提示词，可为空
     * @param model          指定模型，可为空
     * @param defaultSystem  全局设置也没有值时的兜底角色，可为空
     * @return 对话请求
     */
    private AiChatRequest buildChatRequest(String prompt, String systemPrompt, String model, String defaultSystem) {
        AiChatRequest chatRequest = new AiChatRequest();
        chatRequest.setPrompt(prompt);
        ChatClientSettings settings = resolveAiSettings();
        String resolvedSystem = systemPrompt;
        if (isBlank(resolvedSystem) && settings != null) {
            resolvedSystem = settings.getSystemPrompt();
        }
        if (isBlank(resolvedSystem)) {
            resolvedSystem = defaultSystem;
        }
        if (!isBlank(resolvedSystem)) {
            chatRequest.setSystemPrompt(resolvedSystem);
        }
        String resolvedModel = model;
        if (isBlank(resolvedModel) && settings != null) {
            resolvedModel = settings.getDefaultModel();
        }
        if (!isBlank(resolvedModel)) {
            chatRequest.setModel(resolvedModel);
        }
        return chatRequest;
    }

    /**
     * 拼装主机指标分析提示词。
     *
     * @param host      主机资产
     * @param dimension 分析重点，可为空
     * @return 提示词；没有任何指标数据时返回 null
     */
    private String buildHostPrompt(MsServerHost host, String dimension) {
        if (host.getServerHostCpuUsage() == null && host.getServerHostMemUsage() == null
                && host.getServerHostDiskUsage() == null) {
            return null;
        }
        StringBuilder prompt = new StringBuilder();
        prompt.append("请对以下主机监控指标进行智能分析，给出性能评估、瓶颈识别和优化建议：\n\n");
        prompt.append("主机名称: ").append(text(host.getServerHostName())).append("\n");
        prompt.append("主机地址: ").append(text(host.getServerHostAddress())).append(":").append(text(host.getServerHostPort())).append("\n");
        prompt.append("操作系统: ").append(text(host.getServerHostOsType())).append(" ")
                .append(text(host.getServerHostOsArch())).append(" ").append(text(host.getServerHostOsVersion())).append("\n");
        prompt.append("采集方式: ").append(text(host.getServerHostCollectMode()))
                .append(", Agent 版本 ").append(text(host.getServerHostAgentVersion()))
                .append(", 在线 ").append(Integer.valueOf(1).equals(host.getServerHostOnline()) ? "是" : "否").append("\n\n");
        prompt.append("最近指标数据:\n");
        prompt.append("- CPU: 使用率 ").append(text(host.getServerHostCpuUsage())).append("%");
        if (host.getServerHostCpuCores() != null) {
            prompt.append(", 核心数 ").append(host.getServerHostCpuCores());
        }
        prompt.append("\n");
        prompt.append("- 内存: 使用率 ").append(text(host.getServerHostMemUsage())).append("%");
        if (host.getServerHostMemoryMb() != null) {
            prompt.append(", 总量 ").append(host.getServerHostMemoryMb()).append("MB");
        }
        prompt.append("\n");
        prompt.append("- 磁盘: 使用率 ").append(text(host.getServerHostDiskUsage())).append("%");
        if (host.getServerHostDiskGb() != null) {
            prompt.append(", 总量 ").append(host.getServerHostDiskGb()).append("GB");
        }
        if (host.getServerHostDelayMs() != null) {
            prompt.append("\n- 网络延迟: ").append(host.getServerHostDelayMs()).append("ms");
        }
        prompt.append("\n采集时间: ").append(text(host.getServerHostLastCollectTime())).append("\n");
        appendCpuTrend(prompt, host.getId());
        if (!isBlank(dimension)) {
            prompt.append("\n分析重点: ").append(dimension).append("\n");
        }
        prompt.append("请按以下结构输出：\n1. 整体性能评分（0-100）\n2. 关键指标评估\n3. 潜在瓶颈识别\n4. 具体优化建议\n5. 风险预警");
        return prompt.toString();
    }

    /**
     * 追加最近 1 小时的 CPU 采样统计，让模型看到趋势而非单点。
     * <p>
     * 一台主机名下可能有多个监控项（存量数据按地址兜底匹配），逐个尝试直到命中有采样的那个。
     * </p>
     *
     * @param prompt 提示词缓冲
     * @param hostId 主机资产 ID
     */
    private void appendCpuTrend(StringBuilder prompt, Long hostId) {
        if (hostId == null) {
            return;
        }
        List<MsMonitor> linked = monitorService.listByServerHost(hostId).getData();
        if (linked == null || linked.isEmpty()) {
            return;
        }
        java.time.LocalDateTime start = java.time.LocalDateTime.now().minusHours(1);
        for (MsMonitor monitor : linked) {
            if (monitor == null || monitor.getId() == null) {
                continue;
            }
            List<MsMonitorMetricCpu> history = metricService.getCpuHistory(monitor.getId(), start, null).getData();
            if (history == null || history.isEmpty()) {
                continue;
            }
            appendCpuStats(prompt, history);
            return;
        }
    }

    /**
     * 把 CPU 历史采样汇总成一行趋势文本。
     *
     * @param prompt  提示词缓冲
     * @param history 最近 1 小时的 CPU 采样，非空
     */
    private void appendCpuStats(StringBuilder prompt, List<MsMonitorMetricCpu> history) {
        double sum = 0D;
        double max = Double.NEGATIVE_INFINITY;
        int counted = 0;
        for (MsMonitorMetricCpu item : history) {
            Double usage = item == null ? null : item.getCpuUsage();
            if (usage == null) {
                continue;
            }
            sum += usage;
            max = Math.max(max, usage);
            counted++;
        }
        if (counted == 0) {
            return;
        }
        prompt.append("- 最近 1 小时 CPU 采样: 样本 ").append(counted)
                .append(" 个, 平均 ").append(String.format(java.util.Locale.ROOT, "%.2f", sum / counted))
                .append("%, 峰值 ").append(String.format(java.util.Locale.ROOT, "%.2f", max)).append("%\n");
    }

    /**
     * 空值安全的文本形式。
     *
     * @param value 任意值，可为空
     * @return 字符串，null 转为「未知」
     */
    private String text(Object value) {
        return value == null ? "未知" : String.valueOf(value);
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param value 待判断值
     * @return true 表示 null 或全空白
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}