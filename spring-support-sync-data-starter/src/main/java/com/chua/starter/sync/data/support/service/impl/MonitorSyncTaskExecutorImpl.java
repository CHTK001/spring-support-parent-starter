package com.chua.starter.sync.data.support.service.impl;

import com.chua.common.support.text.json.Json;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.common.support.sync.SyncFlow;
import com.chua.common.support.sync.Input;
import com.chua.common.support.sync.Output;
import com.chua.common.support.sync.Sink;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.job.support.log.JobLogDetailService;
import com.chua.starter.sync.data.support.entity.MonitorSyncNode;
import com.chua.starter.sync.data.support.entity.MonitorSyncTask;
import com.chua.starter.sync.data.support.entity.MonitorSyncTaskLog;
import com.chua.starter.sync.data.support.mapper.MonitorSyncNodeMapper;
import com.chua.starter.sync.data.support.mapper.MonitorSyncTaskLogMapper;
import com.chua.starter.sync.data.support.mapper.MonitorSyncTaskMapper;
import com.chua.starter.sync.data.support.sync.ColumnDefinition;
import com.chua.starter.sync.data.support.service.sync.MonitorSyncTaskExecutor;
import com.chua.starter.sync.data.support.service.sync.OutputTableService;
import com.chua.starter.sync.data.support.service.sync.SyncTaskWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(10);
        taskScheduler.setThreadNamePrefix("sync-task-scheduler-");
        taskScheduler.initialize();
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

        if (runningFlows.containsKey(taskId)) {
            log.warn("任务已在运行中, taskId: {}", taskId);
            return;
        }

        MonitorSyncTask task = taskMapper.selectById(taskId);
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
        MonitorSyncTask task = taskMapper.selectById(taskId);
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

        webSocketService.pushTaskStarted(task, taskLog);

        logDetail(taskLog.getSyncLogId().intValue(), task.getSyncTaskId().intValue(),
                "INFO", "同步任务开始执行: " + task.getSyncTaskName(), "START", 0);

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

            logDetail(taskLog.getSyncLogId().intValue(), task.getSyncTaskId().intValue(),
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

            logDetailError(taskLog.getSyncLogId().intValue(), task.getSyncTaskId().intValue(),
                    "同步任务执行失败", e);

            long cost = System.currentTimeMillis() - startTime;
            taskLog.setSyncLogStatus("FAIL");
            taskLog.setSyncLogEndTime(LocalDateTime.now());
            taskLog.setSyncLogCost(cost);
            taskLog.setSyncLogMessage("执行失败: " + e.getMessage());
            taskLog.setSyncLogStackTrace(getStackTrace(e));
            logMapper.updateById(taskLog);

            task.setSyncTaskLastRunTime(LocalDateTime.now());
            task.setSyncTaskLastRunStatus("FAIL");
            task.setSyncTaskRunCount(task.getSyncTaskRunCount() + 1);
            task.setSyncTaskFailCount(task.getSyncTaskFailCount() + 1);
            task.setSyncTaskStatus("ERROR");
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
        }

        if (task.getSyncTaskBatchSize() != null) {
            builder.batchSize(task.getSyncTaskBatchSize());
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
        if (jobLogDetailService != null) {
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
        if (jobLogDetailService != null) {
            try {
                jobLogDetailService.error(logId, taskId, content, e);
            } catch (Exception ex) {
                log.warn("记录错误详细日志失败: {}", ex.getMessage());
            }
        }
    }

    @Override
    public void run(String... args) throws Exception {
        recoverRunningTasks();
    }
}
