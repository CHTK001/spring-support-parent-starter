package com.chua.starter.sync.data.support.service.impl;

import com.chua.common.support.text.json.Json;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.common.support.sync.SyncFlow;
import com.chua.common.support.sync.Input;
import com.chua.common.support.sync.Output;
import com.chua.common.support.sync.Sink;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.job.support.log.JobLogDetailService;
import com.chua.starter.sync.data.support.properties.SyncJobIntegrationProperties;
import com.chua.starter.sync.data.support.entity.MonitorSyncNode;
import com.chua.starter.sync.data.support.entity.MonitorSyncTask;
import com.chua.starter.sync.data.support.entity.MonitorSyncTaskLog;
import com.chua.starter.sync.data.support.mapper.MonitorSyncNodeMapper;
import com.chua.starter.sync.data.support.mapper.MonitorSyncTaskLogMapper;
import com.chua.starter.sync.data.support.mapper.MonitorSyncTaskMapper;
import com.chua.starter.sync.data.support.sync.ColumnDefinition;
import com.chua.starter.sync.data.support.service.sync.MonitorSyncTaskExecutor;
import com.chua.starter.sync.data.support.service.sync.OutputTableService;
import com.chua.starter.sync.data.support.service.sync.SyncJobIntegrationService;
import com.chua.starter.sync.data.support.service.sync.SyncTaskLogAdapter;
import com.chua.starter.sync.data.support.service.sync.SyncTaskWebSocketService;
import com.chua.starter.sync.data.support.sync.performance.AdaptiveBatchSizeCalculator;
import com.chua.starter.sync.data.support.sync.performance.StreamingSyncProcessor;
import com.chua.starter.sync.data.support.sync.performance.MemoryMonitor;
import com.chua.starter.sync.data.support.sync.sink.InMemorySyncSink;
import com.chua.starter.sync.data.support.sync.concurrency.RateLimiterConfig;
import com.chua.starter.sync.data.support.service.MemoryAlertService;
import com.chua.starter.sync.data.support.properties.SyncProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 同步任务执行器实现类
 *
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorSyncTaskExecutorImpl implements MonitorSyncTaskExecutor, CommandLineRunner {

    private final MonitorSyncTaskMapper taskMapper;
    private final MonitorSyncNodeMapper nodeMapper;
    private final MonitorSyncTaskLogMapper logMapper;
    private final SyncTaskWebSocketService webSocketService;
    private final OutputTableService outputTableService;
    private final SyncProperties syncProperties;
    private final SyncJobIntegrationProperties syncJobIntegrationProperties;
    private final AdaptiveBatchSizeCalculator batchSizeCalculator;
    private final StreamingSyncProcessor streamingProcessor;
    private final MemoryMonitor memoryMonitor;
    private final ObjectProvider<SyncJobIntegrationService> syncJobIntegrationServiceProvider;
    private final ObjectProvider<SyncTaskLogAdapter> syncTaskLogAdapterProvider;
    
    @Autowired(required = false)
    private MemoryAlertService memoryAlertService;
    
    @Autowired(required = false)
    private RateLimiterConfig rateLimiterConfig;

    /**
     * Job日志详情服务（可选，集成spring-job时使用）
     */
    @Autowired(required = false)
    private JobLogDetailService jobLogDetailService;

    /**
     * 运行中的 SyncFlow 实例
     */
    private final Map<Long, SyncFlow> runningFlows = new ConcurrentHashMap<>();

    /**
     * 定时任务调度器
     */
    private ThreadPoolTaskScheduler taskScheduler;

    /**
     * 定时任务 Future 映射
     */
    private final Map<Long, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (!isJobIntegrationEnabled()) {
            taskScheduler = new ThreadPoolTaskScheduler();
            taskScheduler.setPoolSize(10);
            taskScheduler.setThreadNamePrefix("sync-task-scheduler-");
            taskScheduler.initialize();
        }
        log.info("同步任务执行器初始化完成");

    }

    /**
     * 恢复运行中的任务
     * 应用重启后自动恢复状态为 RUNNING 的定时任务
     */
    private void recoverRunningTasks() {
        try {
            List<MonitorSyncTask> runningTasks = taskMapper.selectRunningTasks();
            if (runningTasks == null || runningTasks.isEmpty()) {
                log.info("没有需要恢复的运行中任务");
                return;
            }

            if (isJobIntegrationEnabled()) {
                SyncJobIntegrationService syncJobIntegrationService = syncJobIntegrationServiceProvider.getIfAvailable();
                if (syncJobIntegrationService == null) {
                    log.warn("Sync Job 集成已启用，但未找到可用的集成服务，跳过恢复");
                    return;
                }

                log.info("开始恢复 {} 个运行中的 Sync Job 映射", runningTasks.size());
                for (MonitorSyncTask task : runningTasks) {
                    try {
                        syncJobIntegrationService.createOrUpdateJob(task);
                        syncJobIntegrationService.startJob(task.getSyncTaskId());
                    } catch (Exception e) {
                        log.error("恢复 Sync Job 映射失败, taskId: {}, taskName: {}",
                                task.getSyncTaskId(), task.getSyncTaskName(), e);
                    }
                }
                log.info("Sync Job 映射恢复完成");
                return;
            }

            log.info("开始恢复 {} 个运行中的任务", runningTasks.size());
            for (MonitorSyncTask task : runningTasks) {
                try {
                    if (StringUtils.isNotEmpty(task.getSyncTaskCron())
                            || (task.getSyncTaskSyncInterval() != null && task.getSyncTaskSyncInterval() > 0)) {
                        start(task.getSyncTaskId());
                        log.info("任务恢复成功, taskId: {}, taskName: {}",
                                task.getSyncTaskId(), task.getSyncTaskName());
                    }
                } catch (Exception e) {
                    log.error("任务恢复失败, taskId: {}, taskName: {}",
                            task.getSyncTaskId(), task.getSyncTaskName(), e);
                }
            }
            log.info("任务恢复完成");
        } catch (Exception e) {
            log.error("恢复运行中任务失败", e);
        }
    }

    @PreDestroy
    public void destroy() {
        stopAll();
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
    }

    @Override
    public void start(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("任务ID不能为空");
        }

        if (isJobIntegrationEnabled()) {
            throw new IllegalStateException("当前已启用 Job 集成，请通过 SyncJobIntegrationService 启动任务");
        }

        if (runningFlows.containsKey(taskId)) {
            log.warn("任务已在运行中, taskId: {}", taskId);
            return;
        }

        MonitorSyncTask task = taskMapper.selectCompatibleById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        log.info("启动同步任务, taskId: {}, taskName: {}", taskId, task.getSyncTaskName());

        try {
            SyncFlow syncFlow = buildSyncFlow(task);
            runningFlows.put(taskId, syncFlow);

            if (StringUtils.isNotEmpty(task.getSyncTaskCron())) {
                ScheduledFuture<?> future = taskScheduler.schedule(
                        () -> doExecute(taskId, "SCHEDULE"),
                        new CronTrigger(task.getSyncTaskCron())
                );
                scheduledFutures.put(taskId, future);
                log.info("已设置定时调度, taskId: {}, cron: {}", taskId, task.getSyncTaskCron());
            } else if (task.getSyncTaskSyncInterval() != null && task.getSyncTaskSyncInterval() > 0) {
                ScheduledFuture<?> future = taskScheduler.scheduleWithFixedDelay(
                        () -> doExecute(taskId, "SCHEDULE"),
                        Duration.ofMillis(task.getSyncTaskSyncInterval())
                );
                scheduledFutures.put(taskId, future);
                log.info("已设置间隔调度, taskId: {}, interval: {}ms", taskId, task.getSyncTaskSyncInterval());
            }

            log.info("同步任务启动成功, taskId: {}", taskId);
        } catch (Exception e) {
            log.error("启动同步任务失败, taskId: {}", taskId, e);
            runningFlows.remove(taskId);
            throw new RuntimeException("启动任务失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void stop(Long taskId) {
        if (taskId == null) {
            return;
        }

        if (isJobIntegrationEnabled()) {
            runningFlows.remove(taskId);
            return;
        }

        log.info("停止同步任务, taskId: {}", taskId);

        ScheduledFuture<?> future = scheduledFutures.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }

        SyncFlow syncFlow = runningFlows.remove(taskId);
        if (syncFlow != null) {
            try {
                syncFlow.stop();
            } catch (Exception e) {
                log.error("停止 SyncFlow 失败, taskId: {}", taskId, e);
            }
        }

        log.info("同步任务已停止, taskId: {}", taskId);
    }

    @Override
    public Long executeOnce(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("任务ID不能为空");
        }

        log.info("手动执行同步任务, taskId: {}", taskId);
        return doExecute(taskId, "MANUAL");
    }

    @Override
    public Long executeOnce(Long taskId, String triggerType) {
        if (taskId == null) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        return doExecute(taskId, triggerType);
    }

    @Override
    public boolean isRunning(Long taskId) {
        return runningFlows.containsKey(taskId);
    }

    @Override
    public void stopAll() {
        log.info("停止所有同步任务, 当前运行数: {}", runningFlows.size());

        for (Long taskId : new ArrayList<>(runningFlows.keySet())) {
            stop(taskId);
        }
    }

    /**
     * 执行同步任务
     */
    private Long doExecute(Long taskId, String triggerType) {
        // 限流检查
        if (rateLimiterConfig != null && syncProperties.isRateLimitEnabled()) {
            if (!rateLimiterConfig.tryAcquire(taskId, 5, java.util.concurrent.TimeUnit.SECONDS)) {
                log.warn("任务执行被限流: taskId={}", taskId);
                return null;
            }
        }
        
        MonitorSyncTask task = taskMapper.selectCompatibleById(taskId);
        if (task == null) {
            log.error("任务不存在, taskId: {}", taskId);
            return null;
        }

        MonitorSyncTaskLog taskLog = new MonitorSyncTaskLog();
        taskLog.setSyncTaskId(taskId);
        taskLog.setSyncLogStatus("RUNNING");
        taskLog.setSyncLogTriggerType(triggerType);
        taskLog.setSyncLogStartTime(LocalDateTime.now());
        taskLog.setSyncLogReadCount(0L);
        taskLog.setSyncLogWriteCount(0L);
        taskLog.setSyncLogSuccessCount(0L);
        taskLog.setSyncLogFailCount(0L);
        taskLog.setSyncLogRetryCount(0L);
        taskLog.setSyncLogDeadLetterCount(0L);
        taskLog.setSyncLogFilterCount(0L);
        logMapper.insert(taskLog);

        SyncTaskLogAdapter syncTaskLogAdapter = syncTaskLogAdapterProvider.getIfAvailable();
        Integer jobLogId = syncTaskLogAdapter != null ? syncTaskLogAdapter.createJobLog(task, taskLog) : null;

        webSocketService.pushTaskStarted(task, taskLog);

        logDetail(jobLogId, resolveJobId(task, jobLogId),
                "INFO", "同步任务开始执行: " + task.getSyncTaskName(), "START", 0);

        // 检查内存可用性
        double currentMemoryUsage = memoryMonitor.getCurrentMemoryUsage();
        if (!memoryMonitor.isMemoryAvailable()) {
            String errorMsg = String.format("内存不足，无法启动任务。当前内存使用率: %.2f%%", 
                    currentMemoryUsage * 100);
            log.warn(errorMsg);
            
            // 发送内存告警
            if (memoryAlertService != null) {
                memoryAlertService.sendTaskMemoryAlert(taskId, currentMemoryUsage);
            }
            
            taskLog.setSyncLogStatus("FAIL");
            taskLog.setSyncLogEndTime(LocalDateTime.now());
            taskLog.setSyncLogMessage(errorMsg);
            logMapper.updateById(taskLog);
            if (syncTaskLogAdapter != null) {
                syncTaskLogAdapter.updateJobLog(task, taskLog, jobLogId);
            }
            
            logDetail(jobLogId, resolveJobId(task, jobLogId),
                    "WARN", errorMsg, "MEMORY_CHECK", 0);
            
            webSocketService.pushError(taskId, taskLog.getSyncLogId(), null, "内存不足", 
                    new RuntimeException(errorMsg));
            
            return taskLog.getSyncLogId();
        }

        long startTime = System.currentTimeMillis();

        try {
            SyncFlow syncFlow = runningFlows.get(taskId);
            if (syncFlow == null) {
                syncFlow = buildSyncFlow(task);
            }

            syncFlow.start();

            long cost = System.currentTimeMillis() - startTime;
            taskLog.setSyncLogStatus("SUCCESS");
            taskLog.setSyncLogEndTime(LocalDateTime.now());
            taskLog.setSyncLogCost(cost);
            taskLog.setSyncLogMessage("执行成功");

            logMapper.updateById(taskLog);
            if (syncTaskLogAdapter != null) {
                syncTaskLogAdapter.updateJobLog(task, taskLog, jobLogId);
            }

            logDetail(jobLogId, resolveJobId(task, jobLogId),
                    "INFO", String.format("同步任务执行成功, 读取: %d, 写入: %d, 成功: %d, 失败: %d, 耗时: %dms",
                            taskLog.getSyncLogReadCount(), taskLog.getSyncLogWriteCount(),
                            taskLog.getSyncLogSuccessCount(), taskLog.getSyncLogFailCount(), cost),
                    "END", 100);

            task.setSyncTaskLastRunTime(LocalDateTime.now());
            task.setSyncTaskLastRunStatus("SUCCESS");
            task.setSyncTaskRunCount(task.getSyncTaskRunCount() + 1);
            task.setSyncTaskSuccessCount(task.getSyncTaskSuccessCount() + 1);
            taskMapper.updateById(task);

            webSocketService.pushTaskCompleted(task, taskLog);

            log.info("同步任务执行成功, taskId: {}, 耗时: {}ms", taskId, cost);
            return taskLog.getSyncLogId();

        } catch (Exception e) {
            log.error("同步任务执行失败, taskId: {}", taskId, e);

            logDetailError(jobLogId, resolveJobId(task, jobLogId),
                    "同步任务执行失败", e);

            long cost = System.currentTimeMillis() - startTime;
            taskLog.setSyncLogStatus("FAIL");
            taskLog.setSyncLogEndTime(LocalDateTime.now());
            taskLog.setSyncLogCost(cost);
            taskLog.setSyncLogMessage("执行失败: " + e.getMessage());
            taskLog.setSyncLogStackTrace(getStackTrace(e));
            logMapper.updateById(taskLog);
            if (syncTaskLogAdapter != null) {
                syncTaskLogAdapter.updateJobLog(task, taskLog, jobLogId);
            }

            task.setSyncTaskLastRunTime(LocalDateTime.now());
            task.setSyncTaskLastRunStatus("FAIL");
            task.setSyncTaskRunCount(task.getSyncTaskRunCount() + 1);
            task.setSyncTaskFailCount(task.getSyncTaskFailCount() + 1);
            taskMapper.updateById(task);

            webSocketService.pushTaskCompleted(task, taskLog);
            webSocketService.pushError(taskId, taskLog.getSyncLogId(), null, "任务执行失败", e);

            return taskLog.getSyncLogId();
        }
    }

    /**
     * 构建 SyncFlow
     */
    private SyncFlow buildSyncFlow(MonitorSyncTask task) {
        Long taskId = task.getSyncTaskId();

        List<MonitorSyncNode> nodes = nodeMapper.selectByTaskId(taskId);
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("任务没有配置节点");
        }

        List<MonitorSyncNode> inputNodes = new ArrayList<>();
        List<MonitorSyncNode> outputNodes = new ArrayList<>();
        List<MonitorSyncNode> filterNodes = new ArrayList<>();
        List<MonitorSyncNode> dataCenterNodes = new ArrayList<>();

        for (MonitorSyncNode node : nodes) {
            if (node.getSyncNodeEnabled() == null || node.getSyncNodeEnabled() != 1) {
                continue;
            }
            switch (node.getSyncNodeType()) {
                case "INPUT":
                    inputNodes.add(node);
                    break;
                case "OUTPUT":
                    outputNodes.add(node);
                    break;
                case "FILTER":
                    filterNodes.add(node);
                    break;
                case "DATA_CENTER":
                    dataCenterNodes.add(node);
                    break;
            }
        }

        if (inputNodes.isEmpty()) {
            throw new IllegalArgumentException("必须至少有一个输入节点");
        }
        if (outputNodes.isEmpty()) {
            throw new IllegalArgumentException("必须至少有一个输出节点");
        }

        SyncFlow.Builder builder = SyncFlow.builder("sync-task-" + task.getSyncTaskId());

        for (MonitorSyncNode inputNode : inputNodes) {
            Input input = createInput(inputNode);
            builder.addInput(input);
        }

        for (MonitorSyncNode outputNode : outputNodes) {
            Output output = createOutput(outputNode);
            builder.addOutput(output);
        }

        if (!dataCenterNodes.isEmpty()) {
            MonitorSyncNode dataCenterNode = dataCenterNodes.get(0);
            Sink sink = createSink(dataCenterNode);
            builder.sink(sink);
        } else {
            builder.sink(createDefaultSink(task));
        }

        if (task.getSyncTaskBatchSize() != null) {
            // 使用动态批次大小调整
            if (syncProperties.isAdaptiveBatchSizeEnabled()) {
                int adjustedBatchSize = batchSizeCalculator.calculateBatchSize(
                        batchSizeCalculator.getCurrentMemoryUsage(),
                        task.getSyncTaskBatchSize()
                );
                builder.batchSize(adjustedBatchSize);
                log.debug("动态调整批次大小: {} -> {}", task.getSyncTaskBatchSize(), adjustedBatchSize);
            } else {
                builder.batchSize(task.getSyncTaskBatchSize());
            }
        } else {
            // 使用默认批次大小
            int defaultBatchSize = syncProperties.getDefaultBatchSize();
            if (syncProperties.isAdaptiveBatchSizeEnabled()) {
                int adjustedBatchSize = batchSizeCalculator.calculateBatchSize(
                        batchSizeCalculator.getCurrentMemoryUsage(),
                        defaultBatchSize
                );
                builder.batchSize(adjustedBatchSize);
            } else {
                builder.batchSize(defaultBatchSize);
            }
        }

        return builder.build();
    }

    /**
     * 创建 Input
     */
    @SuppressWarnings("unchecked")
    private Input createInput(MonitorSyncNode node) {
        String spiName = node.getSyncNodeSpiName();
        Map<String, Object> config = parseConfig(node.getSyncNodeConfig());

        ServiceProvider<Input> provider = ServiceProvider.of(Input.class);
        return provider.getNewExtension(spiName, config);
    }

    /**
     * 创建 Output
     */
    @SuppressWarnings("unchecked")
    private Output createOutput(MonitorSyncNode node) {
        String spiName = node.getSyncNodeSpiName();
        Map<String, Object> config = parseConfig(node.getSyncNodeConfig());

        handleAutoCreateTable(node, config);

        ServiceProvider<Output> provider = ServiceProvider.of(Output.class);
        return provider.getNewExtension(spiName, config);
    }

    /**
     * 处理自动建表逻辑
     */
    @SuppressWarnings("unchecked")
    private void handleAutoCreateTable(MonitorSyncNode node, Map<String, Object> config) {
        Boolean autoCreateTable = (Boolean) config.get("autoCreateTable");
        if (!Boolean.TRUE.equals(autoCreateTable)) {
            return;
        }

        String tableName = (String) config.get("table");
        if (StringUtils.isEmpty(tableName)) {
            tableName = (String) config.get("tableName");
        }
        if (StringUtils.isEmpty(tableName)) {
            log.warn("自动建表已启用但未配置表名, nodeKey: {}", node.getSyncNodeKey());
            return;
        }

        List<Map<String, Object>> columnsConfig = (List<Map<String, Object>>) config.get("columns");
        if (columnsConfig == null || columnsConfig.isEmpty()) {
            log.warn("自动建表已启用但未配置列定义, nodeKey: {}", node.getSyncNodeKey());
            return;
        }

        try {
            String nodeConfig = node.getSyncNodeConfig();

            var existsResult = outputTableService.checkTableExists(nodeConfig, tableName);
            if (existsResult.isSuccess() && Boolean.TRUE.equals(existsResult.getData())) {
                log.info("目标表已存在, 跳过自动建表: {}", tableName);

                Boolean syncStructure = (Boolean) config.get("syncTableStructure");
                if (Boolean.TRUE.equals(syncStructure)) {
                    List<ColumnDefinition> columns = parseColumnDefinitions(columnsConfig);
                    outputTableService.syncTableStructure(nodeConfig, tableName, columns);
                    log.info("表结构同步完成: {}", tableName);
                }
                return;
            }

            List<ColumnDefinition> columns = parseColumnDefinitions(columnsConfig);
            var createResult = outputTableService.createTable(nodeConfig, tableName, columns);
            if (createResult.isSuccess()) {
                log.info("自动建表成功: {}", tableName);
            } else {
                log.error("自动建表失败: {}, 原因: {}", tableName, createResult.getMsg());
            }
        } catch (Exception e) {
            log.error("自动建表异常: {}", tableName, e);
        }
    }

    /**
     * 解析列定义
     */
    private List<ColumnDefinition> parseColumnDefinitions(List<Map<String, Object>> columnsConfig) {
        List<ColumnDefinition> columns = new ArrayList<>();
        for (Map<String, Object> colConfig : columnsConfig) {
            ColumnDefinition col = ColumnDefinition.builder()
                    .name((String) colConfig.get("name"))
                    .type((String) colConfig.get("type"))
                    .length(colConfig.get("length") != null ? ((Number) colConfig.get("length")).intValue() : null)
                    .scale(colConfig.get("scale") != null ? ((Number) colConfig.get("scale")).intValue() : null)
                    .nullable((Boolean) colConfig.get("nullable"))
                    .defaultValue((String) colConfig.get("defaultValue"))
                    .primaryKey((Boolean) colConfig.get("primaryKey"))
                    .autoIncrement((Boolean) colConfig.get("autoIncrement"))
                    .comment((String) colConfig.get("comment"))
                    .order(colConfig.get("order") != null ? ((Number) colConfig.get("order")).intValue() : null)
                    .sourceField((String) colConfig.get("sourceField"))
                    .build();
            columns.add(col);
        }
        return columns;
    }

    /**
     * 创建 Sink
     */
    @SuppressWarnings("unchecked")
    private Sink createSink(MonitorSyncNode node) {
        String spiName = node.getSyncNodeSpiName();
        Map<String, Object> config = parseConfig(node.getSyncNodeConfig());

        ServiceProvider<Sink> provider = ServiceProvider.of(Sink.class);
        return provider.getNewExtension(spiName, config);
    }

    private Sink createDefaultSink(MonitorSyncTask task) {
        Integer batchSize = task != null ? task.getSyncTaskBatchSize() : null;
        int sinkCapacity = Math.max(batchSize != null ? batchSize * 4 : 1000, 1000);
        return new InMemorySyncSink(sinkCapacity);
    }

    /**
     * 解析配置JSON
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String configJson) {
        if (StringUtils.isEmpty(configJson)) {
            return new HashMap<>();
        }
        try {
            return Json.fromJson(configJson, Map.class);
        } catch (Exception e) {
            log.warn("解析配置JSON失败: {}", configJson);
            return new HashMap<>();
        }
    }

    /**
     * 获取异常堆栈
     */
    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.toString()).append("\n");
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
            if (sb.length() > 4000) {
                sb.append("...(truncated)");
                break;
            }
        }
        return sb.toString();
    }

    /**
     * 记录详细日志（集成spring-job模块）
     *
     * @param logId    日志ID
     * @param taskId   任务ID
     * @param level    日志级别
     * @param content  日志内容
     * @param phase    执行阶段
     * @param progress 执行进度
     */
    private void logDetail(Integer logId, Integer taskId, String level, String content,
                           String phase, Integer progress) {
        if (logId != null && taskId != null && jobLogDetailService != null) {
            try {
                jobLogDetailService.log(logId, taskId, level, content, phase, progress);
            } catch (Exception e) {
                log.warn("记录详细日志失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 记录错误详细日志
     */
    private void logDetailError(Integer logId, Integer taskId, String content, Throwable e) {
        if (logId != null && taskId != null && jobLogDetailService != null) {
            try {
                jobLogDetailService.error(logId, taskId, content, e);
            } catch (Exception ex) {
                log.warn("记录错误详细日志失败: {}", ex.getMessage());
            }
        }
    }

    private Integer resolveJobId(MonitorSyncTask task, Integer jobLogId) {
        if (jobLogId == null) {
            return null;
        }

        SyncJobIntegrationService syncJobIntegrationService = syncJobIntegrationServiceProvider.getIfAvailable();
        if (syncJobIntegrationService == null) {
            return task.getSyncTaskId() != null ? task.getSyncTaskId().intValue() : null;
        }

        Integer jobId = syncJobIntegrationService.getJobId(task.getSyncTaskId());
        return jobId != null ? jobId : (task.getSyncTaskId() != null ? task.getSyncTaskId().intValue() : null);
    }

    private boolean isJobIntegrationEnabled() {
        return syncJobIntegrationProperties.isEnabled();
    }

    @Override
    public void run(String... args) throws Exception {
        recoverRunningTasks();
    }
}
