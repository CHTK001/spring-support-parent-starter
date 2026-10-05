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
import com.chua.starter.monitor.support.model.DbSqlBatchVO;
import com.chua.starter.monitor.support.support.DbColumnDef;
import com.chua.starter.monitor.support.support.DbFilter;
import com.chua.starter.monitor.support.support.DbDialectSupport;
import com.chua.starter.monitor.support.support.DbErDiagram;
import com.chua.starter.monitor.support.support.DbStructureDiff;
import com.chua.starter.monitor.support.support.DbObjectDialect;
import com.chua.starter.monitor.support.support.DbTableDialect;
import com.chua.starter.monitor.support.support.DbTxRegistry;
import com.chua.starter.monitor.support.support.DbTxSession;
import com.chua.starter.monitor.support.support.DbTxStatus;
import com.chua.starter.monitor.support.support.SqlScriptSplitter;
import com.chua.starter.monitor.support.support.SqlParameterBinder;
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
import java.util.Set;
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
     * 显式事务会话注册表（查询控制台的「事务」开关用）
     *
     * <p>为空时事务端点不可用：不要给个默认实现，
     * 那会让「事务没生效」变成一个静默的功能缺失。</p>
     */
    private final ObjectProvider<DbTxRegistry> dbTxRegistryProvider;

    /**
     * 取事务注册表。
     *
     * @return 注册表
     * @throws IllegalStateException 未装配事务支持
     */
    private DbTxRegistry dbTxRegistry() {
        DbTxRegistry registry = dbTxRegistryProvider.getIfAvailable();
        if (registry == null) {
            throw new IllegalStateException("当前实例未启用显式事务支持");
        }
        return registry;
    }

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
    /**
     * 系统库黑名单：不出现在库列表里，也不允许建/删。
     *
     * <p>{@code INFORMATION_SCHEMA} 在 MariaDB 上同时是 catalog 与 schema，
     * {@code SYS} 在 MySQL 上是 catalog，{@code PG_CATALOG} 是 PostgreSQL 的系统 schema。
     * {@code MYSQL} / {@code PERFORMANCE_SCHEMA} / {@code PG_TOAST} 同理属内核自有库，
     * 在上面建对象会被拒或产生难以理解的报错。</p>
     */
private static final java.util.Set<String> SYSTEM_SCHEMAS = java.util.Set.of(
            "INFORMATION_SCHEMA", "PG_CATALOG", "SYS", "MYSQL",
            "PERFORMANCE_SCHEMA", "PG_TOAST");

    /**
     * SQL 控制台单次查询返回的最大行数（超出置 truncated=true）
     */
    private static final int SQL_MAX_ROWS = 1000;

    // ==================== 数据库对象（存储过程 / 函数 / 触发器 / 事件）与外键 ====================

    /**
     * 各类对象的数量（树上的分类节点角标）。
 *
     * <p>一次返回四类计数，前端展开一个库时只多打一个请求，而不是四个。</p>
 *
 * @param datasourceId 数据源 ID
 * @param database 库名
 * @return 各类计数与错误信息
 */
@Operation(summary = "数据库对象分类计数")
    @GetMapping("/db/objects/summary")
    public ReturnResult<Map<String, Object>> dbObjectSummary(
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        try (java.sql.Connection conn = datasourceService.connection(datasourceId);
             SchemaScope scope = useSchema(conn, database)) {
            String schema = requireSchema(conn, database);
            Map<String, Object> counts = new java.util.LinkedHashMap<>();
            Map<String, String> errors = new java.util.LinkedHashMap<>();
            for (var type : DbObjectDialect.Category.values()) {
                try {
                    counts.put(type.name().toLowerCase(java.util.Locale.ROOT),
                            DbObjectDialect
                                    .list(conn, schema, type).size());
                } catch (Exception e) {
                    // 某一类读不到（权限不足 / 内核无此概念）不该拖垮整棵树：
                    // 记 0 并把原因带上，前端在节点上显示「不可用」
                    counts.put(type.name().toLowerCase(java.util.Locale.ROOT), 0);
                    errors.put(type.name().toLowerCase(java.util.Locale.ROOT), e.getMessage());
                }
            }
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("counts", counts);
            payload.put("errors", errors);
            return ReturnResult.ok(payload);
        } catch (Exception e) {
            return ReturnResult.error("获取对象计数失败: " + e.getMessage());
        }
    }

    // ==================== 表属性 / 索引增强 / 主键（Navicat 表属性三页） ====================

    /**
     * 读取列的属性维度（UNSIGNED / ZEROFILL / AUTO_INCREMENT / 字符集 / 排序规则 / ENUM 取值）。
     *
     * <p>JDBC 的 {@code getColumns} 不提供这些，必须查 {@code information_schema.COLUMNS}。
     * 键统一大写，便于与内核返回的列名对齐。</p>
     *
     * @param conn  数据库连接
     * @param table 表名
     * @return 列名（大写）→ 属性
     */
    private static Map<String, Map<String, Object>> readColumnAttributes(
            java.sql.Connection conn, String table) {
        Map<String, Map<String, Object>> result = new java.util.LinkedHashMap<>();
        String sql = "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, EXTRA, CHARACTER_SET_NAME,"
                + " COLLATION_NAME, COLUMN_DEFAULT, CHARACTER_MAXIMUM_LENGTH, NUMERIC_SCALE"
                + " FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?"
                + " ORDER BY ORDINAL_POSITION";
        try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    if (columnName == null) {
                        continue;
                    }
                    int length = rs.getInt("CHARACTER_MAXIMUM_LENGTH");
                    String extra = rs.getString("EXTRA");
                    var parsed = DbColumnDef.parseAttributes(
                            rs.getString("COLUMN_TYPE"),
                            rs.wasNull() ? 0 : length,
                            !"NO".equalsIgnoreCase(rs.getString("IS_NULLABLE")),
                            DbColumnDef.normalizeDefault(rs.getString("COLUMN_DEFAULT")),
                            extra != null && extra.toUpperCase(java.util.Locale.ROOT).contains("AUTO_INCREMENT"));
                    parsed.put("charset", rs.getString("CHARACTER_SET_NAME"));
                    parsed.put("collation", rs.getString("COLLATION_NAME"));
                    int scale = rs.getInt("NUMERIC_SCALE");
                    parsed.put("scale", rs.wasNull() ? 0 : scale);
                    result.put(columnName.toUpperCase(java.util.Locale.ROOT), parsed);
                }
            }
        } catch (java.sql.SQLException e) {
            // 降级为「只给基础字段」是对的（非 MySQL 系内核没有 information_schema.COLUMNS），
            // 但**必须留下痕迹**：本方法早前引用了一个没写进 SELECT 的列
            // （CHARACTER_MAXIMUM_LENGTH），异常被这里静默吞掉，整张属性表退化成空 Map，
            // 表现为「所有列的无符号 / 字符集 / 枚举取值全是空」，前端据此回填弹窗就会把
            // 属性清空后再保存 —— 一路走到内核才报 400，根因完全不可见。
            // 所以：先打日志，别让这类「静默降级」再发生。
            log.warn("读取列属性维度失败，本次降级为只返回基础字段。table={} cause={}",
                    table, e.getMessage());
        }
        return result;
    }

    /**
     * 读取表属性（引擎 / 字符集 / 排序规则 / 注释 / 自增起始值 / 行数）。
     *
     * @param tableName 表名
     * @param datasourceId 数据源 ID
     * @param database 库名
     * @return 表属性
     */
    @Operation(summary = "读取表属性")
    @GetMapping("/db/table/{tableName}/options")
    public ReturnResult<Map<String, Object>> getTableOptions(
            @PathVariable String tableName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String t = safeIdent(tableName, "表名");
        Long dsId = queryOrBodyDatasourceId(datasourceId, null);
        String db = queryOrBodyDatabase(database, null);
        return callDb("读取表属性", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                requireTable(conn, t);
                return DbTableDialect.readOptions(conn, t);
            }
        });
    }

    /**
     * 修改表属性。
     *
     * @param tableName 表名
     * @param body      属性请求体
     * @return 是否成功
     */
    @Operation(summary = "修改表属性")
    @PutMapping("/db/table/{tableName}/options")
    public ReturnResult<Boolean> updateTableOptions(
            @PathVariable String tableName,
            @RequestBody Map<String, Object> body) {
        String t = safeIdent(tableName, "表名");
        Long autoIncrement = intOrNull(body.get("autoIncrement")) == null
                ? null : intOrNull(body.get("autoIncrement")).longValue();
        var options = new DbTableDialect.TableOptions(
                body.get("engine") == null ? null : String.valueOf(body.get("engine")),
                body.get("charset") == null ? null : String.valueOf(body.get("charset")),
                body.get("collation") == null ? null : String.valueOf(body.get("collation")),
                body.get("comment") == null ? null : String.valueOf(body.get("comment")),
                autoIncrement);
        Long dsId = queryOrBodyDatasourceId(null, body);
        String db = queryOrBodyDatabase(null, body);
        return callDb("修改表属性", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                requireTable(conn, t);
                DbTableDialect.alterOptions(conn, t, options);
                return Boolean.TRUE;
            }
        });
    }

    /**
     * 列出可用的存储引擎、字符集与排序规则（前端下拉的数据源）。
     *
     * <p>刻意由内核下发而不是前端硬编码：不同内核、不同版本的可用值不同，
     * 前端写死会让用户选到一个内核不认的值。</p>
     *
     * @param charset 限定字符集，可为空
     * @param datasourceId 数据源 ID
     * @param database 库名
     * @return 引擎列表与排序规则列表
     */
    @Operation(summary = "列出可用引擎/字符集/排序规则")
    @GetMapping("/db/table-options/available")
    public ReturnResult<Map<String, Object>> listTableOptionValues(
            @RequestParam(required = false) String charset,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        return callDb("读取可用表属性值", () -> {
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                Map<String, Object> payload = new java.util.LinkedHashMap<>();
                payload.put("engines", DbTableDialect.listEngines(conn));
                payload.put("collations",
                        DbTableDialect.listCollations(conn, charset));
                return payload;
            }
        });
    }

    /**
     * 设置主键（可改可加可清空）。
     *
     * @param tableName 表名
     * @param body      columns 为主键列（顺序即复合主键列序），传空数组表示清除
     * @return 是否成功
     */
    @Operation(summary = "设置主键")
    @PutMapping("/db/table/{tableName}/primary-key")
    public ReturnResult<Boolean> setTablePrimaryKey(
            @PathVariable String tableName,
            @RequestBody Map<String, Object> body) {
        String t = safeIdent(tableName, "表名");
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) body.get("columns");
        Long dsId = queryOrBodyDatasourceId(null, body);
        String db = queryOrBodyDatabase(null, body);
        return callDb("设置主键", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                requireTable(conn, t);
                for (String column : columns == null ? List.<String>of() : columns) {
                    requireColumn(conn, t, safeIdent(column, "主键列"));
                }
                DbTableDialect.setPrimaryKey(conn, t, columns);
                return Boolean.TRUE;
            }
        });
    }

    /**
     * 数据库对象类别。
     *
     * @param category 类别名（procedure / function / trigger / event）
     * @return 类别
     */
    private static DbObjectDialect.Category objectCategory(String category) {
        return DbObjectDialect.Category.of(category);
    }

    /**
     * 列出数据库对象。
     *
     * <p>树上的「存储过程 / 函数 / 触发器 / 事件」四个分类节点都由它驱动。</p>
     *
     * @param category 类别名
     * @param datasourceId 数据源 ID
     * @param database 库名
     * @return 对象清单
     */
    @Operation(summary = "列出数据库对象（存储过程/函数/触发器/事件）")
    @GetMapping("/db/objects/{category}")
    public ReturnResult<java.util.List<Map<String, Object>>> listDbObjects(
            @PathVariable String category,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        try {
            var type = objectCategory(category);
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                return ReturnResult.ok(DbObjectDialect
                        .list(conn, requireSchema(conn, database), type));
            }
        } catch (IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            return ReturnResult.error("获取" + category + "列表失败: " + e.getMessage());
        }
    }

    /**
     * 读取单个对象的定义。
     *
     * @param category 类别名
     * @param name     对象名
     * @param datasourceId 数据源 ID
     * @param database 库名
     * @return 定义文本
     */
    @Operation(summary = "读取数据库对象定义")
    @GetMapping("/db/objects/{category}/{name}/definition")
    public ReturnResult<String> getDbObjectDefinition(
            @PathVariable String category,
            @PathVariable String name,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        return callDb("读取对象定义", () -> {
            var type = objectCategory(category);
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                return DbObjectDialect
                        .definition(conn, requireSchema(conn, database), name, type);
            }
        });
    }

    /**
     * 新建或整体替换一个对象。
     *
     * <p>传 {@code existOk=true} 时先删后建，实现「改定义」。</p>
     *
     * @param category 类别名
     * @param body     请求体（definition / existOk）
     * @param datasourceId 数据源 ID
     * @param database 库名
     * @return 是否成功
     */
    @Operation(summary = "新建或替换数据库对象")
    @PostMapping("/db/objects/{category}")
    public ReturnResult<Boolean> upsertDbObject(
            @PathVariable String category,
            @RequestBody Map<String, Object> body,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String definition = body.get("definition") == null ? "" : String.valueOf(body.get("definition")).trim();
        boolean existOk = Boolean.TRUE.equals(body.get("existOk"));
        // datasourceId / database 允许放在 body 或 query：与外键端点保持一致，
        // 否则只传 query 时会被静默当成「默认主数据源的默认库」——
        // 实测曾把存储过程建到 ngxacc 而不是请求的 jdbc_acc，且只在后续调用时才报错。
        Long dsId = bodyDatasourceId(body) != null ? bodyDatasourceId(body) : datasourceId;
        String db = bodyDatabase(body) != null ? bodyDatabase(body) : database;
        return callDb("保存" + category, () -> {
            var type = objectCategory(category);
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                String schema = requireSchema(conn, db);
                if (existOk) {
                    // 「改定义」= 先删后建；对象不存在就跳过删除
                    try {
                        DbObjectDialect.drop(conn, schema, nameFrom(definition), type, true);
                    } catch (Exception ignored) {
                        // 对象本来就不存在
                    }
                }
                DbObjectDialect.executeDefinition(conn, definition);
                return Boolean.TRUE;
            }
        });
    }

    /**
     * 删除对象。
     *
     * @param category 类别名
     * @param name     对象名
     * @param ifExists 对象不存在时是否静默
     * @param datasourceId 数据源 ID
     * @param database 库名
     * @return 是否成功
     */
    @Operation(summary = "删除数据库对象")
    @DeleteMapping("/db/objects/{category}/{name}")
    public ReturnResult<Boolean> dropDbObject(
            @PathVariable String category,
            @PathVariable String name,
            @RequestParam(required = false, defaultValue = "true") boolean ifExists,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        return callDb("删除" + category, () -> {
            var type = objectCategory(category);
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                DbObjectDialect
                        .drop(conn, requireSchema(conn, database), name, type, ifExists);
                return Boolean.TRUE;
            }
        });
    }

    /**
     * 从 DDL 文本里取对象名（用于 existOk 的先删后建）。
     *
     * <p>形如 {@code CREATE [DEFINER=…] PROCEDURE/FUNCTION/TRIGGER/EVENT 名(…)}，
     * 取该关键字<strong>之后</strong>的第一个标识符。</p>
     *
     * <p><b>必须从「关键字结尾」开始匹配，不能从 {@code lastIndexOf} 命中位置 +1 开始。</b>
     * {@code "CREATE PROCEDURE"} 里 {@code lastIndexOf("PROCEDURE")} 命中的是 {@code CREATE}
     * 之后那个单词的首字母，+1 会落进单词内部，先匹配到 {@code ROCEDURE} ——
     * 实测因此发出 {@code DROP PROCEDURE ROCEDURE}（IF EXISTS 静默通过），
     * 紧接着 CREATE 就报 "already exists"。</p>
     *
     * @param definition DDL 文本
     * @return 对象名，取不到返回空串（此时 existOk 分支跳过删除，不会误删）
     */
    private static String nameFrom(String definition) {
        String text = definition == null ? "" : definition;
        String upper = text.toUpperCase(java.util.Locale.ROOT);
        for (String keyword : new String[]{"PROCEDURE", "FUNCTION", "TRIGGER", "EVENT"}) {
            int at = upper.lastIndexOf(keyword);
            if (at < 0) {
                continue;
            }
            var matcher = java.util.regex.Pattern.compile("[A-Za-z0-9_]+").matcher(text);
            // +keyword.length() 跳过关键字本身，避免匹配到关键字的残片
            if (matcher.find(at + keyword.length())) {
                return matcher.group();
            }
        }
        return "";
    }

    /**
     * 取目标库名；为空时用连接当前所在的库。
     *
     * <p><b>必须走 {@link #currentDatabase} 而不是 {@code getCatalog()}／{@code getSchema()}。</b>
     * MySQL Connector/J 的 {@code getCatalog()} 只反映驱动内部记录的 URL 里的库，
     * {@code USE} 切库后它**不会变**。因此这里若用 {@code getCatalog()}，会出现
     * 「对象已建在 jdbc_acc、查询却打到 ngxacc」的错位：建的过程不报错，
     * 紧接着的列表/读定义/删除全部查不到，像是对象没建上。
     * {@link #currentDatabase} 用 {@code SELECT DATABASE()} 取服务端会话库，才是权威值。</p>
     *
     * @param conn     数据库连接
     * @param database 目标库名，可为空
     * @return 库名
     * @throws java.sql.SQLException 读取失败
     */
    private static String requireSchema(java.sql.Connection conn, String database) throws java.sql.SQLException {
        if (database != null && !database.isBlank()) {
            return database;
        }
        return currentDatabase(conn);
    }

    /**
     * 新增外键。
     *
     * @param tableName 表名
     * @param body      外键定义（name / columns / refTable / refColumns / onUpdate / onDelete）
     * @return 是否成功
     */
    @Operation(summary = "新增外键")
    @PostMapping("/db/table/{tableName}/foreign-key")
    public ReturnResult<Boolean> addForeignKey(
            @PathVariable String tableName,
            @RequestBody Map<String, Object> body,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String t = safeIdent(tableName, "表名");
        String fkName = safeIdent(String.valueOf(body.get("name")), "约束名");
        String refTable = safeIdent(String.valueOf(body.get("refTable")), "引用表");
        String onUpdate = body.get("onUpdate") == null ? null : String.valueOf(body.get("onUpdate"));
        String onDelete = body.get("onDelete") == null ? null : String.valueOf(body.get("onDelete"));
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) body.get("columns");
        @SuppressWarnings("unchecked")
        List<String> refColumns = (List<String>) body.get("refColumns");
        Long dsId = queryOrBodyDatasourceId(datasourceId, body);
        String db = queryOrBodyDatabase(database, body);
        return callDb("新增外键", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                requireTable(conn, t);
                requireTable(conn, refTable);
                var fk = new DbObjectDialect.ForeignKeyDefinition(
                        fkName, columns, refTable, refColumns, onUpdate, onDelete);
                DbObjectDialect.addForeignKey(conn, t, fk);
                return Boolean.TRUE;
            }
        });
    }

    /**
     * 删除外键。
     *
     * @param tableName 表名
     * @param fkName    约束名
     * @param datasourceId 数据源 ID
     * @param database 库名
     * @return 是否成功
     */
    @Operation(summary = "删除外键")
    @DeleteMapping("/db/table/{tableName}/foreign-key/{fkName}")
    public ReturnResult<Boolean> dropForeignKey(
            @PathVariable String tableName,
            @PathVariable String fkName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String t = safeIdent(tableName, "表名");
        String name = safeIdent(fkName, "约束名");
        return callDb("删除外键", () -> {
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                requireTable(conn, t);
                DbObjectDialect.dropForeignKey(conn, t, name);
                return Boolean.TRUE;
            }
        });
    }

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
        if (!DbDialectSupport.isSafeIdentifier(database)) {
            throw new java.sql.SQLException("数据库名不合法");
        }
        String original = currentDatabase(conn);
        if (!original.isBlank() && database.equalsIgnoreCase(original)) {
            return new SchemaScope(conn, original);
        }
        // original 为空 = 读不到当前会话库（未知）。此时**一律走切换**：
        // USE 是幂等的，多发一次没有代价；而按「未知 == 目标」早退会漏掉真正的切换。
        // 这里**不能**再用 conn.getCatalog() 判断「本就在该库上」。
        // 该值对 MySQL 系只是 JDBC URL 里记录的库名，与服务端会话库无关：连接从池里
        // 取出后若没发过 USE，会话可能停在别的库（实测数据源 20 的 URL 是 jdbc_acc，
        // 而会话实际在 ngxacc）。用它早退会导致**不发 USE**，于是整段逻辑静默地
        // 在错误的库里执行 —— 表现为「指定 database=jdbc_acc 却查不到 jdbc_acc 的对象」，
        // 甚至在 information_schema 上建对象时报一条无关的权限错误。
        // currentDatabase 走 SELECT DATABASE()，对 MySQL 系是权威值，
        // 对 H2/PostgreSQL 走 getSchema() 再退回 getCatalog()，已覆盖原分支的全部场景。
        if (!databaseExists(conn, database)) {
            throw new java.sql.SQLException("数据库 " + database + " 不存在");
        }
        switchDatabase(conn, database);
        String after = currentDatabase(conn);
        // 校验必须是**权威**读数：读不到（空串）说明无法确认，就报错。
        // 绝不能因为"读不到"就放行 —— 那等于在不确定的库里执行用户 SQL，
        // 且用户看到的仍是成功响应。宁可报「无法确认」，也不能静默跑错库。
        if (after.isBlank() || !database.equalsIgnoreCase(after)) {
            throw new java.sql.SQLException("无法确认已切换到数据库 " + database
                    + "（切换前=" + (original.isBlank() ? "未知" : original) + "）");
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
                // **不要**在这里回落到 getSchema()/getCatalog()：MySQL Connector/J 的这两个
                // 方法返回的是 JDBC URL 里记录的库名，与服务端会话库无关（元数据读取偶发失败
                // 时就会读到它）。一旦把「URL 里的库名」当成「当前会话库」，
                // useSchema 会误判「已经在目标库上」而**跳过 USE**，语句就静默跑在别的库上。
                // 读不到就返回空串（= 未知），由调用方按「未知」处理。
                return "";
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
        // 判内核：元数据读取可能偶发失败（连接池里的连接被回收/超时），
        // 此时 isMysqlFamily 会返回 false。若就此改走 setSchema/setCatalog 路径，
        // 对 Connector/J 而言**服务端会话库根本没变**（那两个方法只更新驱动内部字段），
        // 后续校验又用同一个不可靠的元数据读回，于是「静默在错误的库里执行」。
        // 因此：判定为 MySQL 系**或判定不出来**时，都先试 USE（USE 对非 MySQL 系会报语法错，
        // 那时再落到 setSchema 路径），不把内核判定的抖动放大成静默跑错库。
        boolean knownMysql = isMysqlFamily(conn);
        if (knownMysql || isMysqlFamilyUnknown(conn)) {
            // setCatalog 只更新驱动内部字段（供 getTables/getColumns 等元数据定位库），
            // 不真正切换服务端会话库；USE 才切会话。两者都要，缺一会导致「数据查对库、元数据查错库」。
            try {
                conn.setCatalog(db);
            } catch (java.sql.SQLException ignored) {
                // 驱动不支持 setCatalog，元数据退化为按会话默认库
            }
            try (java.sql.Statement st = conn.createStatement()) {
                st.executeUpdate("USE " + SqlName.quoteMysql(db, "数据库名"));
                return;
            } catch (java.sql.SQLException useFailed) {
                if (knownMysql) {
                    throw useFailed;
                }
                // 判定不出来且 USE 被拒（如 H2）→ 落到下面的 setSchema/setCatalog 路径
            }
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
     * 内核判定是否**读不出来**（与「确认不是 MySQL 系」区分开）。
     *
     * <p>{@link #isMysqlFamily} 把「元数据读取抛异常」和「确实不是 MySQL 系」
     * 都返回 false，而这两者必须区别对待：前者不能据此断定要改走 setSchema 路径，
     * 否则会在 MySQL 上执行一个不切会话库的「切换」。</p>
     *
     * @param conn 数据库连接
     * @return true=读不出内核信息
     */
    private static boolean isMysqlFamilyUnknown(java.sql.Connection conn) {
        try {
            return conn.getMetaData().getDatabaseProductName() == null;
        } catch (java.sql.SQLException e) {
            return true;
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
                    // 系统库必须一并过滤：MariaDB 的 information_schema 既是 catalog 也是
                    // schema，只过滤 schema 分支的话它会作为「可浏览的数据库」出现在树上，
                    // 而在它下面建对象会被内核拒绝（Access denied … to database
                    // 'information_schema'），用户看到的是一条与操作上下文无关的权限错误。
                    if (catalog != null
                            && !SYSTEM_SCHEMAS.contains(catalog.toUpperCase())
                            && seen.add(catalog.toUpperCase())) {
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
            // 属性维度（UNSIGNED/ZEROFILL/AUTO_INCREMENT/字符集/排序规则/ENUM 取值）
            // JDBC 的 getColumns 不给，只能查 information_schema.COLUMNS；查不到时降级为
            // 「只给基础字段」，而不是让整个接口失败 —— 基础信息仍然可用。
            Map<String, Map<String, Object>> attrs = readColumnAttributes(conn, table);
            try (java.sql.ResultSet rs = conn.getMetaData().getColumns(catalog, schema, table, "%")) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    String typeName = rs.getString("TYPE_NAME");
                    Map<String, Object> attr = attrs.get(name == null ? "" : name.toUpperCase(java.util.Locale.ROOT));
                    if (attr == null) {
                        columns.add(DbColumnVO.basic(name, typeName, rs.getInt("COLUMN_SIZE"),
                                rs.getInt("NULLABLE") == 1, rs.getString("REMARKS"),
                                DbColumnDef.normalizeDefault(rs.getString("COLUMN_DEF")),
                                rs.getInt("ORDINAL_POSITION"), pkCols.contains(name)));
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    List<String> enumValues = (List<String>) attr.getOrDefault("enumValues", List.of());
                    columns.add(new DbColumnVO(
                            name,
                            typeName,
                            rs.getInt("COLUMN_SIZE"),
                            rs.getInt("NULLABLE") == 1,
                            rs.getString("REMARKS"),
                            // 用归一化后的默认值，而不是 JDBC 的 COLUMN_DEF 原文：
                            // 后者对「可空且无显式默认值」的列是字面文本 NULL，
                            // 原样回填会让用户改个注释也撞上 Invalid default value。
                            String.valueOf(attr.getOrDefault("defaultValue", "")),
                            rs.getInt("ORDINAL_POSITION"),
                            pkCols.contains(name),
                            String.valueOf(attr.getOrDefault("baseType", typeName)),
                            Boolean.TRUE.equals(attr.get("unsigned")),
                            Boolean.TRUE.equals(attr.get("zerofill")),
                            // parseAttributes 放进来的是 Boolean，不是内核 EXTRA 的 "YES"/"no" 文本；
                            // 之前按 "YES" 文本比较，结果自增列恒为 false（且不报错，很难发现）。
                            Boolean.TRUE.equals(attr.get("autoIncrement")),
                            String.valueOf(attr.getOrDefault("charset", "")),
                            String.valueOf(attr.getOrDefault("collation", "")),
                            enumValues,
                            attr.get("scale") instanceof Integer i ? i : 0));
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
            @RequestParam(required = false) String where,
            @RequestParam(required = false) String filters) {
        int normalizedPage = Math.max(1, page);
        int normalizedPageSize = Math.min(500, Math.max(1, pageSize));
        long offset = (long) (normalizedPage - 1) * normalizedPageSize;
        String table = safeIdent(tableName, "表名");
        String column = orderBy == null || orderBy.isBlank() ? null : safeIdent(orderBy, "排序列");
        String direction = "DESC".equalsIgnoreCase(orderDir) ? "DESC" : "ASC";
        // WHERE 过滤：允许 Navicat 式自由条件，但拒绝语句分隔符/注释，避免堆叠多条语句
        String whereClause = sanitizeWhere(where);
        // 结构化筛选（高级筛选）：列名走白名单、值走 PreparedStatement 绑定，
        // 与上面那条「自由文本 + 黑名单」路径是**两套独立机制**，不互相兜底。
        // 两者同时给出时按 AND 组合（用户在文本框里写的条件仍生效）。
        DbFilter.Compiled compiled;
        try {
            compiled = DbFilter.compile(parseFilters(filters));
        } catch (IllegalArgumentException e) {
            return ReturnResult.error("筛选条件非法: " + e.getMessage());
        }
        List<String> both = new ArrayList<>(2);
        if (whereClause != null) {
            both.add(whereClause);
        }
        if (!compiled.isEmpty()) {
            both.add(compiled.whereSql());
        }
        String whereSql = both.isEmpty() ? "" : " WHERE " + String.join(" AND ", both);
        try (java.sql.Connection conn = datasourceService.connection(datasourceId);
             SchemaScope scope = useSchema(conn, database)) {
            requireTable(conn, table);
            if (column != null) {
                requireColumn(conn, table, column);
            }
            String quotedTable = SqlName.quoteMysql(table, "表名");
            String orderClause = column == null ? ""
                    : " ORDER BY " + SqlName.quoteMysql(column, "排序列") + " " + direction;
            String dataSql = "SELECT * FROM " + quotedTable + whereSql + orderClause + " LIMIT ? OFFSET ?";
            String countSql = "SELECT COUNT(*) FROM " + quotedTable + whereSql;
            java.util.List<String> columns;
            java.util.List<Map<String, Object>> rows;
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(dataSql)) {
                // 绑定顺序必须是「筛选参数 → 分页参数」：whereSql 在 SQL 文本里
                // 位于 LIMIT/OFFSET 之前，顺序反了不会报错、只会查错行。
                int idx = 1;
                for (Object p : compiled.params()) {
                    stmt.setObject(idx++, p);
                }
                stmt.setInt(idx++, normalizedPageSize);
                stmt.setLong(idx, offset);
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
                // countSql 里没有 LIMIT/OFFSET，只绑筛选参数
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(countSql)) {
                    int idx = 1;
                    for (Object p : compiled.params()) {
                        stmt.setObject(idx++, p);
                    }
                    try (java.sql.ResultSet countRs = stmt.executeQuery()) {
                        if (countRs.next()) {
                            total = countRs.getLong(1);
                        }
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
     * 解析结构化筛选条件（JSON 数组）。
     *
     * <p>刻意**用原始字符串手工解析**而不是引入 Jackson 树：
     * 这层已经有 {@code ReturnResult} 的 Jackson 配置，再引一份 ObjectMapper
     * 只会带来「两套配置不一致」的坑，而这里的结构极简
     * （列名/运算符/值1/值2 四个字符串字段）。</p>
     *
     * @param json JSON 数组文本；为空视为无条件
     * @return 条件列表
     * @throws IllegalArgumentException 结构不是数组或含非对象元素
     */
    private static List<DbFilter.Condition> parseFilters(String json) {
        List<DbFilter.Condition> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        String text = json.strip();
        if (!text.startsWith("[")) {
            throw new IllegalArgumentException("filters 必须是 JSON 数组");
        }
        int depth = 0;
        boolean inStr = false;
        StringBuilder cur = new StringBuilder();
        java.util.List<String> items = new java.util.ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inStr) {
                if (c == '\\' && i + 1 < text.length()) {
                    cur.append(c).append(text.charAt(++i));
                } else {
                    if (c == '"') {
                        inStr = false;
                    }
                    cur.append(c);
                }
                continue;
            }
            if (c == '"') {
                inStr = true;
                cur.append(c);
                continue;
            }
            if (c == '{') {
                depth++;
                cur.append(c);
                continue;
            }
            if (c == '}') {
                depth--;
                cur.append(c);
                continue;
            }
            if (c == ']' && depth == 0) {
                break;
            }
            if (c == '}' && depth == 0) {
                throw new IllegalArgumentException("filters 的对象没有正确闭合");
            }
            if (c == ',' && depth == 0) {
                if (!cur.isEmpty()) {
                    items.add(cur.toString());
                    cur.setLength(0);
                }
                continue;
            }
            cur.append(c);
        }
        if (!cur.isEmpty()) {
            items.add(cur.toString());
        }
        for (String item : items) {
            out.add(new DbFilter.Condition(
                    jsonField(item, "column"),
                    jsonField(item, "op"),
                    jsonField(item, "value"),
                    jsonField(item, "value2")));
        }
        return out;
    }

    /**
     * 从一个 JSON 对象文本里取字符串字段。
     *
     * <p>只支持字符串与数字两种取值 —— 够用且不必处理转义地狱；
     * 解析不出来返回空串，由 {@link DbFilter#compile} 按「空值跳过」处理。</p>
     *
     * @param obj   对象文本
     * @param field 字段名
     * @return 字段值；不存在或非标量返回空串
     */
    private static String jsonField(String obj, String field) {
        String needle = "\"" + field + "\"";
        int at = obj.indexOf(needle);
        if (at < 0) {
            return "";
        }
        int i = obj.indexOf(':', at + needle.length());
        if (i < 0) {
            return "";
        }
        i++;
        while (i < obj.length() && Character.isWhitespace(obj.charAt(i))) {
            i++;
        }
        if (i >= obj.length()) {
            return "";
        }
        char c = obj.charAt(i);
        if (c != '"') {
            // 数字 / 布尔 / null：读到下一个分隔符为止
            int end = i;
            while (end < obj.length() && ",}".indexOf(obj.charAt(end)) < 0) {
                end++;
            }
            return obj.substring(i, end).trim();
        }
        StringBuilder sb = new StringBuilder();
        for (i++; i < obj.length(); i++) {
            char ch = obj.charAt(i);
            if (ch == '\\' && i + 1 < obj.length()) {
                sb.append(obj.charAt(++i));
                continue;
            }
            if (ch == '"') {
                break;
            }
            sb.append(ch);
        }
        return sb.toString();
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
                    // SHOW CREATE TABLE 的列是「表名 / 建表语句」；判定必须用「以 CREATE 开头」，
                    // 用「包含 CREATE」会命中相邻的表名列（表名里带 create 字样时）
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                        Object v = rs.getObject(i);
                        if (v instanceof String s && s.strip().toUpperCase().startsWith("CREATE")) {
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
    // ---------- 显式事务（Navicat 的「事务」模式） ----------

    /** 开启事务的请求体 */
    public record DbTxBeginRequest(Long datasourceId, String database) {
    }

    /** 提交 / 回滚的请求体 */
    public record DbTxActionRequest(String txId) {
    }

    /**
     * 开启一个显式事务会话。
     *
     * <p>开启后这条连接被注册表持有，后续带 {@code txId} 的
     * {@code /db/execute}、{@code /db/execute-batch}、单元格编辑、清空表
     * 都在**同一条连接**上执行，直到显式提交或回滚。
     * 不带 {@code txId} 的请求不受影响，仍各自独立自动提交。</p>
     *
     * @param request 数据源与库
     * @return 新建的事务标识
     */
    @Operation(summary = "开启显式事务")
    @PostMapping("/db/tx/begin")
    public ReturnResult<Map<String, Object>> beginDbTx(@RequestBody DbTxBeginRequest request) {
        return callDb("开启事务", () -> {
            DbTxSession session = dbTxRegistry().begin(
                    request == null ? null : request.datasourceId(),
                    request == null ? null : request.database());
            return Map.<String, Object>of(
                    "txId", session.txId(),
                    "datasourceId", session.datasourceId() == null ? 0L : session.datasourceId(),
                    "database", session.database() == null ? "" : session.database());
        });
    }

    /**
     * 提交事务。
     *
     * @param request 事务标识
     * @return 是否成功
     */
    @Operation(summary = "提交显式事务")
    @PostMapping("/db/tx/commit")
    public ReturnResult<Boolean> commitDbTx(@RequestBody DbTxActionRequest request) {
        return callDb("提交事务", () -> {
            dbTxRegistry().commit(request == null ? null : request.txId());
            return Boolean.TRUE;
        });
    }

    /**
     * 回滚事务。
     *
     * @param request 事务标识
     * @return 是否成功
     */
    @Operation(summary = "回滚显式事务")
    @PostMapping("/db/tx/rollback")
    public ReturnResult<Boolean> rollbackDbTx(@RequestBody DbTxActionRequest request) {
        return callDb("回滚事务", () -> {
            dbTxRegistry().rollback(request == null ? null : request.txId());
            return Boolean.TRUE;
        });
    }

    /**
     * 当前未结束的事务列表。
     *
     * @return 事务状态
     */
    @Operation(summary = "查询未结束的事务")
    @GetMapping("/db/tx/status")
    public ReturnResult<List<DbTxStatus>> dbTxStatus() {
        return callDb("查询事务", () -> dbTxRegistry().status());
    }

    /**
     * 库级 ER 图元数据：表清单 + 全部外键关系。
     *
     * <p>一次连接读完整库，而不是让前端逐表调外键端点 ——
     * 40 张表就是 40 次往返，每次重新连库重新切 catalog，整体视图会明显卡住。</p>
     *
     * @param datasourceId 数据源 ID
     * @param database     目标库，为空用连接当前库
     * @param tables       表名过滤（LIKE 模式，可为空表示全部）
     * @return 表清单与外键关系
     */
    @Operation(summary = "读取 ER 图元数据")
    @GetMapping("/db/er-diagram")
    public ReturnResult<Map<String, Object>> erDiagram(
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database,
            @RequestParam(required = false) String tables) {
        return callDb("读取 ER 图", () -> {
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                return DbErDiagram.load(conn, database, tables);
            }
        });
    }

    /** 结构对比请求 */
    public record DbStructureDiffRequest(
            Long sourceDatasourceId,
            String sourceDatabase,
            Long targetDatasourceId,
            String targetDatabase,
            String tables) {
    }

    /**
     * 比较两个库的结构并生成同步脚本（Navicat 的「比较 / 结构同步」）。
     *
     * <p>方向固定「源 → 目标」。结构同步是单向操作 ——
     * 「以谁为准」必须由用户在界面上选，不该由实现替用户决定。</p>
     *
     * <p><b>只生成脚本，不代执行</b>：差异清单里含 {@code DROP INDEX}
     * 这类不可逆语句，是否执行由用户看过脚本后决定。</p>
     *
     * @param request 源/目标数据源与库
     * @return 差异清单
     */
    @Operation(summary = "比较两个库的结构")
    @PostMapping("/db/structure-diff")
    public ReturnResult<List<DbStructureDiff.DiffItem>> structureDiff(
            @RequestBody DbStructureDiffRequest request) {
        return callDb("比较库结构", () -> {
            Long srcDs = request.sourceDatasourceId();
            String srcDb = request.sourceDatabase();
            Long dstDs = request.targetDatasourceId() == null ? srcDs : request.targetDatasourceId();
            String dstDb = request.targetDatabase();
            // 源与目标同库时没有任何差异，直接返回空而不是白开两条连接比一遍
            if (Objects.equals(srcDs, dstDs) && Objects.equals(srcDb, dstDb)) {
                return List.of();
            }
            // 源与目标**同一个数据源**时只能共用一条连接并切 catalog：
            // datasourceService.connection(id) 返回的是缓存共享的同一条物理连接，
            // 两次拿到同一个对象，第二次 USE 会覆盖第一次，
            // 于是变成「拿目标库跟自己比」并恒返回空列表（2026-10-05 实测）。
            if (Objects.equals(srcDs, dstDs)) {
                try (java.sql.Connection conn = datasourceService.connection(srcDs)) {
                    return DbStructureDiff.diffSameDatasource(
                            conn, srcDb, dstDb, request.tables());
                }
            }
            try (java.sql.Connection src = datasourceService.connection(srcDs);
                 SchemaScope srcScope = useSchema(src, srcDb);
                 java.sql.Connection dst = datasourceService.connection(dstDs);
                 SchemaScope dstScope = useSchema(dst, dstDb)) {
                return DbStructureDiff.diffAcrossDatasources(src, dst, request.tables());
            }
        });
    }

    @Operation(summary = "执行 SQL")
    @PostMapping("/db/execute")
    public ReturnResult<DbSqlResultVO> executeDbSql(@RequestBody DbSqlExecuteDTO request) {
        String sql = request.sql();
        if (sql == null || sql.trim().isEmpty()) {
            return ReturnResult.error("SQL 不能为空");
        }
        String bound;
        try {
            bound = bindParams(sql, request.params());
        } catch (IllegalArgumentException e) {
            return ReturnResult.error("SQL 参数错误: " + e.getMessage());
        }
        Long datasourceId = request.datasourceId();
        // 事务里跑：复用注册表持有的那条连接，不新建。
        // 语句失败**不自动回滚整段事务** —— 「一条失败就整体回滚」是用户显式
        // 点回滚时的语义；批量执行更是要「继续跑完并逐条记账」。
        if (request.inTransaction()) {
            try {
                DbTxSession session = dbTxRegistry().require(request.txId());
                DbSqlResultVO result = session.run(conn -> {
                    try (java.sql.Statement stmt = conn.createStatement()) {
                        return runOneStatement(stmt, bound);
                    } catch (java.sql.SQLException e) {
                        throw new IllegalStateException(e.getMessage(), e);
                    }
                });
                return ReturnResult.ok(result);
            } catch (IllegalStateException e) {
                return ReturnResult.error("SQL 执行失败: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                return ReturnResult.error("SQL 执行失败: " + e.getMessage());
            }
        }
        // 顺序要求：先切库、再建 Statement。
        // Connector/J 的 Statement 会在执行时按**创建时**捕获的 catalog 还原会话库，
        // 若在 USE 之前创建 Statement，USE 会被它覆盖 —— 实测 `database=sync_src` 的查询
        // 跑在 jdbc_acc 上（返回 jdbc_acc 的表数），而同样带 database 参数的 /db/tables
        // 因为没有提前建 Statement，切库是正确的。两者行为不一致正是这个顺序问题。
        try (java.sql.Connection conn = datasourceService.connection(datasourceId);
             SchemaScope scope = useSchema(conn, request.database());
             java.sql.Statement stmt = conn.createStatement()) {
            return ReturnResult.ok(runOneStatement(stmt, bound));
        } catch (Exception e) {
            return ReturnResult.error("SQL 执行失败: " + e.getMessage());
        }
    }

    /**
     * 把 {@code ${name}} 占位符替换成字面量。
     *
     * <p>参数错误**在连库之前**就抛出来：拿到「参数 x 未提供」比先建立连接、
     * 再等内核报一个语法错误要快得多，也更容易看懂。</p>
     *
     * @param sql    原始 SQL
     * @param params 变量表
     * @return 替换后的 SQL
     * @throws IllegalArgumentException 变量名不合法或引用了未提供的变量
     */
    private static String bindParams(String sql, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            if (SqlParameterBinder.hasPlaceholder(sql)) {
                throw new IllegalArgumentException("SQL 里有 " + SqlParameterBinder.requiredNames(sql)
                        + " 占位符，但没有提供参数");
            }
            return sql;
        }
        return SqlParameterBinder.substitute(sql, SqlParameterBinder.normalize(params));
    }

    /**
     * 扫描 SQL 里的 {@code ${name}} 占位符，供 UI 提示「这个脚本需要哪些变量」。
     *
     * <p>不连库、也不执行，纯文本扫描 —— 编辑器每次改动都问一次后端的话，
     * 成本会体现在每次按键的延迟上。</p>
     *
     * @param sql 待扫描 SQL
     * @return 占位符名列表
     */
    @Operation(summary = "扫描 SQL 参数占位符")
    @PostMapping("/db/scan-params")
    public ReturnResult<List<String>> scanSqlParams(@RequestBody DbSqlExecuteDTO request) {
        String sql = request.sql();
        if (sql == null || sql.isBlank()) {
            return ReturnResult.ok(List.of());
        }
        return ReturnResult.ok(SqlParameterBinder.requiredNames(sql));
    }

    /**
     * 执行单条语句并把结果集读成 {@link DbSqlResultVO}。
     *
     * <p>单条端点与批量端点**必须**共用这一份实现，否则同一条 SQL 在两条路径下
     * 会出现「一条当查询、一条当更新」的行为差异（表现为同一句 {@code WITH ...}
     * 在批量里被当成更新语句、影响行数为 0）。</p>
     *
     * @param stmt 已建好的语句（调用方保证已完成切库）
     * @param sql  单条 SQL
     * @return 执行结果
     * @throws java.sql.SQLException 内核报错
     */
    private static DbSqlResultVO runOneStatement(java.sql.Statement stmt, String sql) throws java.sql.SQLException {
        if (SqlScriptSplitter.isQuery(sql)) {
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
                return new DbSqlResultVO(columns, rows, rowCount, rowCount >= SQL_MAX_ROWS, null, null);
            }
        }
        int affectedRows = stmt.executeUpdate(sql);
        return new DbSqlResultVO(null, null, null, null, affectedRows,
                "执行成功，影响 " + affectedRows + " 行");
    }

    /**
     * 批量执行 SQL 脚本，逐条返回结果（查询控制台的「执行脚本」）。
     *
     * <p><b>为什么不复用 {@code /db/execute}</b>：那个端点的响应形状被 5 个 UI 套件
     * 与 4 个接口套件按 {@code data.columns} / {@code data.rows} 读着，
     * 往里加多结果集字段会让既有断言全部失效。批量另开端点、形状自由。</p>
     *
     * <p>逐条执行、遇错即停（可由 {@code stopOnError=false} 改为继续），
     * 失败时仍返回已执行部分的结果与失败语句的下标，
     * 便于定位「第几条开始错」。</p>
     *
     * @param request SQL 执行请求（sql / datasourceId / database / stopOnError / params）
     * @return 批量执行结果
     */
    @Operation(summary = "批量执行 SQL 脚本")
    @PostMapping("/db/execute-batch")
    public ReturnResult<DbSqlBatchVO> executeDbSqlBatch(@RequestBody DbSqlExecuteDTO request) {
        String sql = request.sql();
        if (sql == null || sql.trim().isEmpty()) {
            return ReturnResult.error("SQL 不能为空");
        }
        java.util.List<String> statements;
        try {
            statements = SqlScriptSplitter.split(sql);
        } catch (IllegalArgumentException e) {
            return ReturnResult.error("SQL 脚本无法切分: " + e.getMessage());
        }
        if (statements.isEmpty()) {
            return ReturnResult.ok(DbSqlBatchVO.allOk(java.util.List.of(), 0, "脚本里没有可执行的语句"));
        }
        // 顺序要求：**先切分、后替换参数**。
        // 反过来做的话，参数值里只要带 `;`（'a;b' 这种完全合法的值）
        // 就会被当成语句分隔符，把一条语句拦腰截断 —— 现象是「脚本莫名其妙多切了几条」，
        // 根因（替换早于切分）却完全不在报错信息里。
        boolean stopOnError = request.stopOnErrorOrDefault();
        java.util.List<DbSqlResultVO> results = new java.util.ArrayList<>(statements.size());
        // 事务里跑：整段脚本共用注册表持有的那条连接，
        // 这样「BEGIN 在这条连接上、COMMIT 也在同一条连接上」才成立。
        if (request.inTransaction()) {
            try {
                DbTxSession session = dbTxRegistry().require(request.txId());
                return ReturnResult.ok(session.run(conn -> {
                    try (java.sql.Statement stmt = conn.createStatement()) {
                        DbSqlBatchVO early = runStatementList(statements, request.params(),
                                stopOnError, results, stmt);
                        if (early != null) {
                            return early;
                        }
                        return summarizeBatch(statements.size(), results);
                    } catch (java.sql.SQLException e) {
                        throw new IllegalStateException("批量执行失败: " + e.getMessage(), e);
                    }
                }));
            } catch (IllegalArgumentException | IllegalStateException e) {
                return ReturnResult.error(e.getMessage());
            }
        }
        try (java.sql.Connection conn = datasourceService.connection(request.datasourceId());
             SchemaScope scope = useSchema(conn, request.database());
             java.sql.Statement stmt = conn.createStatement()) {
            DbSqlBatchVO early = runStatementList(statements, request.params(), stopOnError, results, stmt);
            if (early != null) {
                return ReturnResult.ok(early);
            }
        } catch (Exception e) {
            return ReturnResult.error("批量执行失败: " + e.getMessage());
        }
        return ReturnResult.ok(summarizeBatch(statements.size(), results));
    }

    /**
     * 逐条执行脚本，把结果累加进 {@code results}。
     *
     * <p>事务与非事务两条路径共用这一段：两条路径的**逐条语义必须完全一致**
     * （同样替换参数、同样记账失败、同样按 stopOnError 提前收尾），
     * 各写一份的话，事务模式下就会出现「同样的脚本，非事务能跑完、事务跑到一半就停」
     * 这类只在开了事务才复现的偏差。</p>
     *
     * @param statements  已切分的语句
     * @param params      {@code ${name}} 取值表
     * @param stopOnError 遇错是否停止
     * @param results     结果累加容器
     * @param stmt        已创建的 Statement
     * @return 遇错停止时的结果；正常跑完返回 null
     */
    private DbSqlBatchVO runStatementList(java.util.List<String> statements,
                                         Map<String, Object> params,
                                         boolean stopOnError,
                                         java.util.List<DbSqlResultVO> results,
                                         java.sql.Statement stmt) {
        for (int i = 0; i < statements.size(); i++) {
            String one;
            try {
                one = bindParams(statements.get(i), params);
            } catch (IllegalArgumentException e) {
                // 参数缺失是「脚本问题」，不是「执行失败」，但要按同一条的失败口径报，
                // 否则 UI 上会显示成执行期错误，看不出是没传参数
                String summary = "第 " + (i + 1) + "/" + statements.size() + " 条语句参数错误: "
                        + e.getMessage() + "；语句=" + abbreviate(statements.get(i));
                if (stopOnError) {
                    return new DbSqlBatchVO(results, statements.size(), i, statements.get(i), summary);
                }
                results.add(new DbSqlResultVO(null, java.util.List.of(), 0, false, null, summary));
                continue;
            }
            try {
                results.add(runOneStatement(stmt, one));
            } catch (Exception e) {
                String reason = e.getMessage();
                String summary = "第 " + (i + 1) + "/" + statements.size() + " 条语句失败: " + reason
                        + "；语句=" + abbreviate(one);
                if (stopOnError) {
                    return new DbSqlBatchVO(results, statements.size(), i, statements.get(i), summary);
                }
                // 继续执行：把失败也记成一条结果，rowCount=0 且带上原因
                results.add(new DbSqlResultVO(null, java.util.List.of(), 0, false, null, summary));
            }
        }
        return null;
    }

    /**
     * 汇总批量执行结果。
     *
     * @param total   语句总数
     * @param results 逐条结果
     * @return 汇总视图
     */
    private DbSqlBatchVO summarizeBatch(int total, java.util.List<DbSqlResultVO> results) {
        int queryCount = 0;
        int updateCount = 0;
        for (DbSqlResultVO r : results) {
            if (r.affectedRows() != null) {
                updateCount++;
            } else if (r.columns() != null) {
                queryCount++;
            }
        }
        return DbSqlBatchVO.allOk(results, total,
                "共 " + total + " 条语句执行完成（查询 " + queryCount + " 条，更新 " + updateCount + " 条）");
    }

    /**
     * 把语句缩到可放进提示的长度。
     *
     * @param sql 语句原文
     * @return 截断后的文本
     */
    private static String abbreviate(String sql) {
        String flat = sql.replaceAll("\\s+", " ").strip();
        return flat.length() <= 120 ? flat : flat.substring(0, 120) + "…";
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
     * 交由 {@link DbDialectSupport} 选型。</p>
     *
     * @param request 请求，不允许为 null
     * @return Return结果 对象
     */
    @Operation(summary = "新建数据库")
    @PostMapping("/db/database")
    public ReturnResult<Boolean> createDatabase(@RequestBody com.chua.starter.monitor.support.model.dto.DbDatabaseDTO request) {
        String name = request.name() == null ? "" : request.name().trim();
        if (!DbDialectSupport.isSafeIdentifier(name)
                || SYSTEM_SCHEMAS.contains(name.toUpperCase())) {
            return ReturnResult.error("数据库名只能由字母、数字和下划线组成，且不能以数字开头");
        }
        try (java.sql.Connection conn = datasourceService.connection(request.datasourceId())) {
            DbDialectSupport.createDatabase(
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
        if (!DbDialectSupport.isSafeIdentifier(database)
                || SYSTEM_SCHEMAS.contains(database.toUpperCase())) {
            return ReturnResult.error("数据库名只能由字母、数字和下划线组成，且不能以数字开头");
        }
        try (java.sql.Connection conn = datasourceService.connection(datasourceId)) {
            DbDialectSupport.dropDatabase(conn, database);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            return ReturnResult.error("删除数据库失败: " + e.getMessage());
        }
    }


    // ==================== 数据库表级编辑（单元格 / 索引 / 清空表 / 列） ====================

    /**
     * 允许通过 DDL 拼接的列类型白名单。
     *
     * @deprecated 类型白名单已移到 {@link DbColumnDef#SUPPORTED_TYPES}，
     *     并且扩展到 40 余种（含 ENUM / SET / JSON / 日期时间 / 二进制等）。本常量不再使用。
     */
    @Deprecated
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
    public ReturnResult<Integer> updateCell(@PathVariable String tableName,
                                         @RequestBody Map<String, Object> body,
                                         @RequestParam(required = false) Long datasourceId,
                                         @RequestParam(required = false) String database) {
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
        Long dsId = queryOrBodyDatasourceId(datasourceId, body);
        String db = queryOrBodyDatabase(database, body);
        Object txIdRaw = body.get("txId");
        String txId = txIdRaw == null ? null : String.valueOf(txIdRaw);
        boolean inTx = txId != null && !txId.isBlank();
        // 「事务内更新」的共用实现：连接由调用方给（要么是新建的，要么是事务持有的），
        // 所以校验、base64 解码、绑定三段逻辑只能写一次。
        java.util.function.Function<java.sql.Connection, Integer> update = conn -> {
            try {
                requireTable(conn, t);
                requireColumn(conn, t, column);
                for (Map.Entry<String, Object> entry : primaryKey.entrySet()) {
                    requireColumn(conn, t, safeIdent(entry.getKey(), "主键列"));
                }
                // 二进制列必须把 base64 **解成字节**再绑定。
                // 不解的话内核会把 base64 的 ASCII 文本原样存进去：
                // 界面看着「保存成功」，实际数据已经被改坏，
                // 而且再次读出来是一段「base64 文本的 base64」，越改越离谱。
                if (newValue instanceof String text && isBinaryColumn(conn, t, column)) {
                    params.set(0, decodeBase64(text));
                }
                try (java.sql.PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                    return ps.executeUpdate();
                }
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        };
        if (inTx) {
            return callDb("更新单元格", () -> dbTxRegistry().require(txId).run(update));
        }
        return callDb("更新单元格", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                return update.apply(conn);
            }
        });
    }

    /** 二进制列的类型名（小写比较） */
    private static final Set<String> BINARY_TYPES = Set.of(
            "binary", "varbinary", "blob", "tinyblob", "mediumblob", "longblob", "bytea");

    /**
     * 判断某列是不是二进制类型。
     *
     * <p>走 {@link java.sql.DatabaseMetaData#getColumns} 而不在 SQL 里查
     * {@code information_schema}：前者对各内核通用，
     * 且与 {@link #requireColumn} 的存在性校验走同一套元数据口径。</p>
     *
     * @param conn   连接
     * @param table  表名
     * @param column 列名
     * @return true=二进制列
     */
    private static boolean isBinaryColumn(java.sql.Connection conn, String table, String column) {
        java.sql.ResultSet rs = null;
        try {
            java.sql.DatabaseMetaData md = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();
            // 与 hasTable 同样的四种 (catalog,schema) 组合，逐个试
            for (int[] shape : new int[][] {{0, 1}, {0, 0}, {1, 1}, {1, 0}}) {
                rs = md.getColumns(shape[0] == 0 ? catalog : null,
                        shape[1] == 1 ? schema : null, table, column);
                if (rs.next()) {
                    String type = String.valueOf(rs.getString("TYPE_NAME")).toLowerCase(java.util.Locale.ROOT);
                    return BINARY_TYPES.contains(type) || type.contains("blob") || type.contains("binary");
                }
                rs.close();
                rs = null;
            }
        } catch (java.sql.SQLException ignored) {
            // 读不到类型就当不是二进制列：走原有行为，不因元数据缺失而拒绝写入
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (java.sql.SQLException ignored) {
                    // 关闭失败无需处理
                }
            }
        }
        return false;
    }

    /**
     * 解 base64 为字节。
     *
     * @param text base64 文本（允许含空白）
     * @return 字节
     * @throws IllegalArgumentException 不是合法 base64
     */
    private static byte[] decodeBase64(String text) {
        try {
            return java.util.Base64.getDecoder().decode(text.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("内容不是合法的 base64（无法写入二进制列）: " + e.getMessage());
        }
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
    public ReturnResult<Boolean> createIndex(@PathVariable String tableName,
                                            @RequestBody Map<String, Object> body,
                                            @RequestParam(required = false) Long datasourceId,
                                            @RequestParam(required = false) String database) {
        String t = safeIdent(tableName, "表名");
        String indexName = safeIdent(String.valueOf(body.getOrDefault("indexName", "idx")), "索引名");
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) body.get("columns");
        boolean unique = Boolean.TRUE.equals(body.get("unique"));
        boolean primary = Boolean.TRUE.equals(body.get("primary"));
        String indexType = body.get("indexType") == null ? null : String.valueOf(body.get("indexType"));
        List<Integer> prefixes = intListOrNull(body.get("prefixes"));
        if (columns == null || columns.isEmpty()) {
            return ReturnResult.error("请至少选择一列");
        }
        Long dsId = queryOrBodyDatasourceId(datasourceId, body);
        String db = queryOrBodyDatabase(database, body);
        return callDb("新建索引", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                requireTable(conn, t);
                for (String indexColumn : columns) {
                    requireColumn(conn, t, safeIdent(indexColumn, "索引列"));
                }
                // 列序即复合索引的列序，原样透传（不排序），否则索引前缀语义会变
                DbTableDialect
                        .createIndex(conn, t, indexName, columns, prefixes, unique, indexType, primary);
                return Boolean.TRUE;
            }
        });

    }

    /**
     * 把请求里的整数数组解析出来。
     *
     * @param value 原始值，可为空
     * @return 整数列表；输入为空返回 null
     */
    private static List<Integer> intListOrNull(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<Integer> result = new java.util.ArrayList<>(list.size());
        for (Object item : list) {
            result.add(intOrNull(item));
        }
        return result;
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
            @RequestParam(required = false) String database,
            @RequestParam(required = false) String txId) {
        String t = safeIdent(tableName, "表名");
        java.util.function.Function<java.sql.Connection, Boolean> truncate = conn -> {
            try {
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
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        };
        // 事务内清空：TRUNCATE 在 MySQL 是 DDL，**本身就会隐式提交**，
        // 所以事务里执行它不会像 UPDATE 那样可以回滚。
        // 界面必须如实告知，否则用户会以为「回滚能把数据找回来」而实际数据已经没了。
        if (txId != null && !txId.isBlank()) {
            return callDb("清空表", () -> dbTxRegistry().require(txId).run(truncate));
        }
        return callDb("清空表", () -> {
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                return truncate.apply(conn);
            }
        });

    }

    /**
     * 从请求体构造完整列定义。
     *
     * <p>所有属性由 {@link DbColumnDef} 在构造期校验，
     * 校验不通过直接抛 {@link IllegalArgumentException}，由 {@link #callDb} 转成可读错误。</p>
     *
     * @param body 列定义请求体
     * @return 完整列定义
     */
    private static DbColumnDef columnDef(Map<String, Object> body) {
        String columnName = String.valueOf(body.getOrDefault("columnName", ""));
        String type = String.valueOf(body.getOrDefault("type", ""));
        Integer size = intOrNull(body.get("size"));
        Integer scale = intOrNull(body.get("scale"));
        @SuppressWarnings("unchecked")
        List<String> enumValues = (List<String>) body.get("enumValues");
        return new DbColumnDef(
                columnName,
                type,
                size,
                scale,
                Boolean.TRUE.equals(body.get("unsigned")),
                Boolean.TRUE.equals(body.get("zerofill")),
                body.get("charset") == null ? "" : String.valueOf(body.get("charset")),
                body.get("collation") == null ? "" : String.valueOf(body.get("collation")),
                !Boolean.FALSE.equals(body.get("nullable")),
                body.get("defaultValue") == null ? "" : String.valueOf(body.get("defaultValue")),
                body.get("onUpdate") == null ? "" : String.valueOf(body.get("onUpdate")),
                Boolean.TRUE.equals(body.get("autoIncrement")),
                enumValues,
                Boolean.TRUE.equals(body.get("generated")),
                body.get("comment") == null ? "" : String.valueOf(body.get("comment")));
    }

    /**
     * 数据源 ID：body 优先，query 兜底。
     *
     * <p>新增端点历史上只从 query 取，前端把参数放 body 时就被静默忽略，
     * 于是 {@code useSchema} 收到 null、语句跑在连接默认库上（表现为
     * 「建到了 ngxacc 而不是请求的 jdbc_acc」，且建的过程不报错）。
     * 与 {@code DbObjectDialect} 那次是同一个坑，这里统一。</p>
     *
     * @param queryValue query 参数
     * @param body       请求体，可为空
     * @return 数据源 ID
     */
    private static Long queryOrBodyDatasourceId(Long queryValue, Map<String, Object> body) {
        return body == null ? queryValue : (bodyDatasourceId(body) != null ? bodyDatasourceId(body) : queryValue);
    }

    /**
     * 目标库：body 优先，query 兜底。
     *
     * @param queryValue query 参数
     * @param body       请求体，可为空
     * @return 库名，可为空（表示沿用连接默认库）
     */
    private static String queryOrBodyDatabase(String queryValue, Map<String, Object> body) {
        String fromBody = body == null ? null : bodyDatabase(body);
        return fromBody != null ? fromBody : queryValue;
    }

    /**
     * 取整数，缺失或非数字返回 null。
     *
     * @param value 原始值
     * @return 整数或 null
     */
    private static Integer intOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("必须是整数: " + value);
        }
    }

    /**
     * 新增列（ALTER TABLE ADD COLUMN）。
     *
     * <p>走完整列定义：类型全集 + UNSIGNED/ZEROFILL + 字符集/排序规则 +
     * AUTO_INCREMENT + ON UPDATE + ENUM/SET 取值 + 生成列 + 注释。</p>
     *
     * @param tableName 表名
     * @param body      列定义
     * @return 是否成功
     */
    @Operation(summary = "新增列")
    @PostMapping("/db/table/{tableName}/column")
    public ReturnResult<Boolean> addColumn(@PathVariable String tableName,
                                           @RequestBody Map<String, Object> body,
                                           @RequestParam(required = false) Long datasourceId,
                                           @RequestParam(required = false) String database) {
        String t = safeIdent(tableName, "表名");
        var def = columnDef(body);
        Long dsId = queryOrBodyDatasourceId(datasourceId, body);
        String db = queryOrBodyDatabase(database, body);
        return callDb("新增列", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                requireMissingColumn(conn, t, def.name());
                DbDialectSupport.addColumn(conn, t, def);
                return Boolean.TRUE;
            }
        });
    }

    /**
     * 修改列（ALTER TABLE MODIFY COLUMN）。
     *
     * @param tableName  表名
     * @param columnName 列名
     * @param body       完整列定义（列名以路径参数为准）
     * @return 是否成功
     */
    @Operation(summary = "修改列")
    @PutMapping("/db/table/{tableName}/column/{columnName}")
    public ReturnResult<Boolean> modifyColumn(
            @PathVariable String tableName,
            @PathVariable String columnName,
            @RequestBody Map<String, Object> body,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String t = safeIdent(tableName, "表名");
        String col = safeIdent(columnName, "列名");
        // 改列时列名不可变：忽略请求体里的 columnName，强制用路径参数
        body.put("columnName", col);
        var def = columnDef(body);
        Long dsId = queryOrBodyDatasourceId(datasourceId, body);
        String db = queryOrBodyDatabase(database, body);
        return callDb("修改列", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                requireColumn(conn, t, col);
                DbDialectSupport.alterColumn(conn, t, def);
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