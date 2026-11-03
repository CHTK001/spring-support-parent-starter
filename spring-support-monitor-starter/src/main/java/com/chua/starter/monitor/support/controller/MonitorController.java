// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
import com.chua.starter.monitor.support.model.ServerHostOptionVO;
import com.chua.starter.monitor.support.model.AiAnalyzeVO;
import com.chua.starter.monitor.support.model.AiHostAnalyzeVO;
import com.chua.starter.monitor.support.model.AiStatusVO;
import com.chua.starter.monitor.support.model.DashboardOverviewVO;
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
