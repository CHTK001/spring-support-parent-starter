package com.chua.starter.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.monitor.entity.MonitorSyncConnection;
import com.chua.starter.monitor.entity.MonitorSyncNode;
import com.chua.starter.monitor.entity.MonitorSyncTask;
import com.chua.starter.monitor.entity.MonitorSyncTaskLog;
import com.chua.starter.monitor.mapper.MonitorSyncConnectionMapper;
import com.chua.starter.monitor.mapper.MonitorSyncNodeMapper;
import com.chua.starter.monitor.mapper.MonitorSyncTaskLogMapper;
import com.chua.starter.monitor.mapper.MonitorSyncTaskMapper;
import com.chua.starter.monitor.pojo.sync.SyncTaskDesign;
import com.chua.starter.monitor.pojo.sync.SyncTaskQuery;
import com.chua.starter.monitor.pojo.sync.SyncTaskStatistics;
import com.chua.starter.monitor.service.sync.MonitorSyncTaskExecutor;
import com.chua.starter.monitor.service.sync.MonitorSyncTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 同步任务服务实现类
 *
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorSyncTaskServiceImpl extends ServiceImpl<MonitorSyncTaskMapper, MonitorSyncTask>
        implements MonitorSyncTaskService {

    private final MonitorSyncTaskMapper taskMapper;
    private final MonitorSyncNodeMapper nodeMapper;
    private final MonitorSyncConnectionMapper connectionMapper;
    private final MonitorSyncTaskLogMapper logMapper;

    @Autowired
    @Lazy
    private MonitorSyncTaskExecutor taskExecutor;

    @Override
    public ReturnResult<Page<MonitorSyncTask>> pageList(SyncTaskQuery query) {
        log.debug("查询同步任务列表, query: {}", query);

        try {
            Page<MonitorSyncTask> page = new Page<>(query.getPage(), query.getSize());
            LambdaQueryWrapper<MonitorSyncTask> wrapper = new LambdaQueryWrapper<>();

            if (StringUtils.isNotEmpty(query.getTaskName())) {
                wrapper.like(MonitorSyncTask::getSyncTaskName, query.getTaskName());
            }
            if (StringUtils.isNotEmpty(query.getTaskStatus())) {
                wrapper.eq(MonitorSyncTask::getSyncTaskStatus, query.getTaskStatus());
            }

            if (Boolean.TRUE.equals(query.getDesc())) {
                wrapper.orderByDesc(MonitorSyncTask::getSyncTaskCreateTime);
            } else {
                wrapper.orderByAsc(MonitorSyncTask::getSyncTaskCreateTime);
            }

            Page<MonitorSyncTask> result = taskMapper.selectPage(page, wrapper);
            return ReturnResult.ok(result);
        } catch (Exception e) {
            log.error("查询同步任务列表失败", e);
            return ReturnResult.error("查询失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<MonitorSyncTask> createTask(MonitorSyncTask task) {
        log.info("创建同步任务: {}", task.getSyncTaskName());

        try {
            task.setSyncTaskStatus("STOPPED");
            task.setSyncTaskCreateTime(LocalDateTime.now());
            task.setSyncTaskUpdateTime(LocalDateTime.now());
            task.setSyncTaskRunCount(0L);
            task.setSyncTaskSuccessCount(0L);
            task.setSyncTaskFailCount(0L);

            if (task.getSyncTaskBatchSize() == null) {
                task.setSyncTaskBatchSize(1000);
            }
            if (task.getSyncTaskRetryCount() == null) {
                task.setSyncTaskRetryCount(3);
            }
            if (task.getSyncTaskRetryInterval() == null) {
                task.setSyncTaskRetryInterval(1000L);
            }
            if (task.getSyncTaskAckEnabled() == null) {
                task.setSyncTaskAckEnabled(1);
            }
            if (task.getSyncTaskTransactionEnabled() == null) {
                task.setSyncTaskTransactionEnabled(0);
            }

            taskMapper.insert(task);
            log.info("同步任务创建成功, taskId: {}", task.getSyncTaskId());
            return ReturnResult.ok(task);
        } catch (Exception e) {
            log.error("创建同步任务失败", e);
            return ReturnResult.error("创建失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> updateTask(MonitorSyncTask task) {
        if (task == null || task.getSyncTaskId() == null) {
            return ReturnResult.error("任务信息不能为空");
        }

        log.info("更新同步任务: {}", task.getSyncTaskId());

        try {
            MonitorSyncTask existing = taskMapper.selectById(task.getSyncTaskId());
            if (existing == null) {
                return ReturnResult.error("任务不存在");
            }

            if ("RUNNING".equals(existing.getSyncTaskStatus())) {
                return ReturnResult.error("运行中的任务不允许修改");
            }

            task.setSyncTaskUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("更新同步任务失败", e);
            return ReturnResult.error("更新失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> deleteTask(Long taskId) {
        if (taskId == null) {
            return ReturnResult.error("任务ID不能为空");
        }

        log.info("删除同步任务: {}", taskId);

        try {
            MonitorSyncTask task = taskMapper.selectById(taskId);
            if (task == null) {
                return ReturnResult.error("任务不存在");
            }

            if ("RUNNING".equals(task.getSyncTaskStatus())) {
                return ReturnResult.error("运行中的任务不允许删除, 请先停止任务");
            }

            connectionMapper.deleteByTaskId(taskId);
            nodeMapper.deleteByTaskId(taskId);
            logMapper.deleteByTaskId(taskId);
            taskMapper.deleteById(taskId);

            log.info("同步任务删除成功: {}", taskId);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("删除同步任务失败", e);
            return ReturnResult.error("删除失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<SyncTaskDesign> getTaskDesign(Long taskId) {
        if (taskId == null) {
            return ReturnResult.error("任务ID不能为空");
        }

        try {
            MonitorSyncTask task = taskMapper.selectById(taskId);
            if (task == null) {
                return ReturnResult.error("任务不存在");
            }

            SyncTaskDesign design = new SyncTaskDesign();
            design.setTask(task);
            design.setNodes(nodeMapper.selectByTaskId(taskId));
            design.setConnections(connectionMapper.selectByTaskId(taskId));
            design.setLayout(task.getSyncTaskLayout());

            return ReturnResult.ok(design);
        } catch (Exception e) {
            log.error("获取任务设计失败", e);
            return ReturnResult.error("获取失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> saveTaskDesign(Long taskId, SyncTaskDesign design) {
        if (taskId == null) {
            return ReturnResult.error("任务ID不能为空");
        }

        log.info("保存任务设计, taskId: {}", taskId);

        try {
            MonitorSyncTask task = taskMapper.selectById(taskId);
            if (task == null) {
                return ReturnResult.error("任务不存在");
            }

            if ("RUNNING".equals(task.getSyncTaskStatus())) {
                return ReturnResult.error("运行中的任务不允许修改设计");
            }

            connectionMapper.deleteByTaskId(taskId);
            nodeMapper.deleteByTaskId(taskId);

            Map<String, Long> nodeKeyIdMap = new HashMap<>();
            List<MonitorSyncNode> nodes = design.getNodes();
            if (nodes != null && !nodes.isEmpty()) {
                for (MonitorSyncNode node : nodes) {
                    node.setSyncTaskId(taskId);
                    node.setSyncNodeCreateTime(LocalDateTime.now());
                    node.setSyncNodeUpdateTime(LocalDateTime.now());
                    if (node.getSyncNodeEnabled() == null) {
                        node.setSyncNodeEnabled(1);
                    }
                    nodeMapper.insert(node);
                    if (StringUtils.isNotEmpty(node.getSyncNodeKey())) {
                        nodeKeyIdMap.put(node.getSyncNodeKey(), node.getSyncNodeId());
                    }
                }
            }

            List<MonitorSyncConnection> connections = design.getConnections();
            if (connections != null && !connections.isEmpty()) {
                for (MonitorSyncConnection conn : connections) {
                    conn.setSyncTaskId(taskId);
                    conn.setSyncConnectionCreateTime(LocalDateTime.now());
                    if (StringUtils.isNotEmpty(conn.getSourceNodeKey()) && conn.getSourceNodeId() == null) {
                        conn.setSourceNodeId(nodeKeyIdMap.get(conn.getSourceNodeKey()));
                    }
                    if (StringUtils.isNotEmpty(conn.getTargetNodeKey()) && conn.getTargetNodeId() == null) {
                        conn.setTargetNodeId(nodeKeyIdMap.get(conn.getTargetNodeKey()));
                    }
                    connectionMapper.insert(conn);
                }
            }

            if (design.getLayout() != null) {
                task.setSyncTaskLayout(design.getLayout());
                task.setSyncTaskUpdateTime(LocalDateTime.now());
                taskMapper.updateById(task);
            }

            log.info("任务设计保存成功, taskId: {}", taskId);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("保存任务设计失败", e);
            return ReturnResult.error("保存失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> startTask(Long taskId) {
        if (taskId == null) {
            return ReturnResult.error("任务ID不能为空");
        }

        log.info("启动同步任务: {}", taskId);

        try {
            MonitorSyncTask task = taskMapper.selectById(taskId);
            if (task == null) {
                return ReturnResult.error("任务不存在");
            }

            if ("RUNNING".equals(task.getSyncTaskStatus())) {
                return ReturnResult.error("任务已在运行中");
            }

            SyncTaskDesign design = new SyncTaskDesign();
            design.setTask(task);
            design.setNodes(nodeMapper.selectByTaskId(taskId));
            design.setConnections(connectionMapper.selectByTaskId(taskId));

            ReturnResult<Boolean> validateResult = validateDesign(design);
            if (!validateResult.isSuccess() || !Boolean.TRUE.equals(validateResult.getData())) {
                return ReturnResult.error("任务设计验证失败: " + validateResult.getMsg());
            }

            taskExecutor.start(taskId);

            task.setSyncTaskStatus("RUNNING");
            task.setSyncTaskUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);

            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("启动同步任务失败", e);
            return ReturnResult.error("启动失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> stopTask(Long taskId) {
        if (taskId == null) {
            return ReturnResult.error("任务ID不能为空");
        }

        log.info("停止同步任务: {}", taskId);

        try {
            MonitorSyncTask task = taskMapper.selectById(taskId);
            if (task == null) {
                return ReturnResult.error("任务不存在");
            }

            if (!"RUNNING".equals(task.getSyncTaskStatus())) {
                return ReturnResult.error("任务未在运行中");
            }

            taskExecutor.stop(taskId);

            task.setSyncTaskStatus("STOPPED");
            task.setSyncTaskUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);

            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("停止同步任务失败", e);
            return ReturnResult.error("停止失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Long> executeOnce(Long taskId) {
        if (taskId == null) {
            return ReturnResult.error("任务ID不能为空");
        }

        log.info("手动执行同步任务: {}", taskId);

        try {
            MonitorSyncTask task = taskMapper.selectById(taskId);
            if (task == null) {
                return ReturnResult.error("任务不存在");
            }

            Long logId = taskExecutor.executeOnce(taskId);
            return ReturnResult.ok(logId);
        } catch (Exception e) {
            log.error("执行同步任务失败", e);
            return ReturnResult.error("执行失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Page<MonitorSyncTaskLog>> getTaskLogs(Long taskId, Integer page, Integer size) {
        if (taskId == null) {
            return ReturnResult.error("任务ID不能为空");
        }

        try {
            Page<MonitorSyncTaskLog> pageParam = new Page<>(page != null ? page : 1, size != null ? size : 10);
            LambdaQueryWrapper<MonitorSyncTaskLog> wrapper = new LambdaQueryWrapper<MonitorSyncTaskLog>()
                    .eq(MonitorSyncTaskLog::getSyncTaskId, taskId)
                    .orderByDesc(MonitorSyncTaskLog::getSyncLogStartTime);

            Page<MonitorSyncTaskLog> result = logMapper.selectPage(pageParam, wrapper);
            return ReturnResult.ok(result);
        } catch (Exception e) {
            log.error("获取任务日志失败", e);
            return ReturnResult.error("获取失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<MonitorSyncTaskLog> getLogDetail(Long logId) {
        if (logId == null) {
            return ReturnResult.error("日志ID不能为空");
        }

        try {
            MonitorSyncTaskLog log = logMapper.selectById(logId);
            if (log == null) {
                return ReturnResult.error("日志不存在");
            }
            return ReturnResult.ok(log);
        } catch (Exception e) {
            log.error("获取日志详情失败", e);
            return ReturnResult.error("获取失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> validateDesign(SyncTaskDesign design) {
        if (design == null) {
            return ReturnResult.error("设计数据不能为空");
        }

        List<MonitorSyncNode> nodes = design.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return ReturnResult.error("至少需要一个节点");
        }

        boolean hasInput = nodes.stream()
                .anyMatch(n -> "INPUT".equals(n.getSyncNodeType()));
        if (!hasInput) {
            return ReturnResult.error("必须有至少一个输入节点");
        }

        boolean hasOutput = nodes.stream()
                .anyMatch(n -> "OUTPUT".equals(n.getSyncNodeType()));
        if (!hasOutput) {
            return ReturnResult.error("必须有至少一个输出节点");
        }

        List<MonitorSyncConnection> connections = design.getConnections();
        if (connections == null || connections.isEmpty()) {
            return ReturnResult.error("必须有至少一条连线");
        }

        return ReturnResult.ok(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<MonitorSyncTask> copyTask(Long taskId, String newName) {
        if (taskId == null) {
            return ReturnResult.error("任务ID不能为空");
        }
        if (StringUtils.isEmpty(newName)) {
            return ReturnResult.error("新任务名称不能为空");
        }

        log.info("复制任务, 原taskId: {}, 新名称: {}", taskId, newName);

        try {
            MonitorSyncTask originTask = taskMapper.selectById(taskId);
            if (originTask == null) {
                return ReturnResult.error("原任务不存在");
            }

            MonitorSyncTask newTask = new MonitorSyncTask();
            newTask.setSyncTaskName(newName);
            newTask.setSyncTaskDesc(originTask.getSyncTaskDesc());
            newTask.setSyncTaskStatus("STOPPED");
            newTask.setSyncTaskBatchSize(originTask.getSyncTaskBatchSize());
            newTask.setSyncTaskConsumeTimeout(originTask.getSyncTaskConsumeTimeout());
            newTask.setSyncTaskRetryCount(originTask.getSyncTaskRetryCount());
            newTask.setSyncTaskRetryInterval(originTask.getSyncTaskRetryInterval());
            newTask.setSyncTaskSyncInterval(originTask.getSyncTaskSyncInterval());
            newTask.setSyncTaskAckEnabled(originTask.getSyncTaskAckEnabled());
            newTask.setSyncTaskTransactionEnabled(originTask.getSyncTaskTransactionEnabled());
            newTask.setSyncTaskCron(originTask.getSyncTaskCron());
            newTask.setSyncTaskLayout(originTask.getSyncTaskLayout());
            newTask.setSyncTaskRunCount(0L);
            newTask.setSyncTaskSuccessCount(0L);
            newTask.setSyncTaskFailCount(0L);
            newTask.setSyncTaskCreateTime(LocalDateTime.now());
            newTask.setSyncTaskUpdateTime(LocalDateTime.now());

            taskMapper.insert(newTask);

            List<MonitorSyncNode> originNodes = nodeMapper.selectByTaskId(taskId);
            Map<String, String> nodeKeyMap = new HashMap<>();
            for (MonitorSyncNode originNode : originNodes) {
                MonitorSyncNode newNode = new MonitorSyncNode();
                String newNodeKey = "node_" + System.currentTimeMillis() + "_" + originNode.getSyncNodeId();
                nodeKeyMap.put(originNode.getSyncNodeKey(), newNodeKey);

                newNode.setSyncTaskId(newTask.getSyncTaskId());
                newNode.setSyncNodeType(originNode.getSyncNodeType());
                newNode.setSyncNodeSpiName(originNode.getSyncNodeSpiName());
                newNode.setSyncNodeName(originNode.getSyncNodeName());
                newNode.setSyncNodeKey(newNodeKey);
                newNode.setSyncNodeConfig(originNode.getSyncNodeConfig());
                newNode.setSyncNodePosition(originNode.getSyncNodePosition());
                newNode.setSyncNodeOrder(originNode.getSyncNodeOrder());
                newNode.setSyncNodeEnabled(originNode.getSyncNodeEnabled());
                newNode.setSyncNodeDesc(originNode.getSyncNodeDesc());
                newNode.setSyncNodeCreateTime(LocalDateTime.now());
                newNode.setSyncNodeUpdateTime(LocalDateTime.now());

                nodeMapper.insert(newNode);
            }

            List<MonitorSyncConnection> originConnections = connectionMapper.selectByTaskId(taskId);
            for (MonitorSyncConnection originConn : originConnections) {
                MonitorSyncConnection newConn = new MonitorSyncConnection();
                newConn.setSyncTaskId(newTask.getSyncTaskId());
                newConn.setSourceNodeKey(nodeKeyMap.get(originConn.getSourceNodeKey()));
                newConn.setTargetNodeKey(nodeKeyMap.get(originConn.getTargetNodeKey()));
                newConn.setSourceHandle(originConn.getSourceHandle());
                newConn.setTargetHandle(originConn.getTargetHandle());
                newConn.setConnectionType(originConn.getConnectionType());
                newConn.setConnectionLabel(originConn.getConnectionLabel());
                newConn.setSyncConnectionCreateTime(LocalDateTime.now());

                connectionMapper.insert(newConn);
            }

            log.info("任务复制成功, 新taskId: {}", newTask.getSyncTaskId());
            return ReturnResult.ok(newTask);
        } catch (Exception e) {
            log.error("复制任务失败", e);
            return ReturnResult.error("复制失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<MonitorSyncNode>> getTaskNodes(Long taskId) {
        if (taskId == null) {
            return ReturnResult.error("任务ID不能为空");
        }

        try {
            List<MonitorSyncNode> nodes = nodeMapper.selectByTaskId(taskId);
            return ReturnResult.ok(nodes);
        } catch (Exception e) {
            log.error("获取任务节点失败", e);
            return ReturnResult.error("获取失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<MonitorSyncConnection>> getTaskConnections(Long taskId) {
        if (taskId == null) {
            return ReturnResult.error("任务ID不能为空");
        }

        try {
            List<MonitorSyncConnection> connections = connectionMapper.selectByTaskId(taskId);
            return ReturnResult.ok(connections);
        } catch (Exception e) {
            log.error("获取任务连线失败", e);
            return ReturnResult.error("获取失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<SyncTaskStatistics> getStatistics(LocalDateTime startTime, LocalDateTime endTime, String granularity) {
        return buildStatistics(null, startTime, endTime, granularity);
    }

    @Override
    public ReturnResult<SyncTaskStatistics> getTaskStatistics(Long taskId, LocalDateTime startTime, LocalDateTime endTime, String granularity) {
        if (taskId == null) {
            return ReturnResult.error("任务ID不能为空");
        }
        return buildStatistics(taskId, startTime, endTime, granularity);
    }

    /**
     * 构建统计数据
     */
    private ReturnResult<SyncTaskStatistics> buildStatistics(Long taskId, LocalDateTime startTime, LocalDateTime endTime, String granularity) {
        try {
            if (endTime == null) {
                endTime = LocalDateTime.now();
            }
            if (startTime == null) {
                startTime = endTime.minusDays(7);
            }
            if (granularity == null || granularity.isEmpty()) {
                granularity = "day";
            }

            LambdaQueryWrapper<MonitorSyncTaskLog> wrapper = new LambdaQueryWrapper<MonitorSyncTaskLog>()
                    .ge(MonitorSyncTaskLog::getSyncLogStartTime, startTime)
                    .le(MonitorSyncTaskLog::getSyncLogStartTime, endTime);
            if (taskId != null) {
                wrapper.eq(MonitorSyncTaskLog::getSyncTaskId, taskId);
            }
            List<MonitorSyncTaskLog> logs = logMapper.selectList(wrapper);

            SyncTaskStatistics statistics = new SyncTaskStatistics();

            statistics.setSummary(buildSummary(logs));
            statistics.setTrend(buildTrendData(logs, startTime, endTime, granularity));
            statistics.setStatusDistribution(buildStatusDistribution(logs));
            statistics.setTriggerTypeDistribution(buildTriggerTypeDistribution(logs));

            if (taskId == null) {
                statistics.setTaskRanking(buildTaskRanking(logs));
            }

            return ReturnResult.ok(statistics);
        } catch (Exception e) {
            log.error("获取统计数据失败", e);
            return ReturnResult.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 构建汇总信息
     */
    private SyncTaskStatistics.Summary buildSummary(List<MonitorSyncTaskLog> logs) {
        SyncTaskStatistics.Summary summary = new SyncTaskStatistics.Summary();

        long total = logs.size();
        long success = logs.stream().filter(l -> "SUCCESS".equals(l.getSyncLogStatus())).count();
        long fail = logs.stream().filter(l -> "FAIL".equals(l.getSyncLogStatus())).count();
        long running = logs.stream().filter(l -> "RUNNING".equals(l.getSyncLogStatus())).count();

        summary.setTotalExecutions(total);
        summary.setSuccessCount(success);
        summary.setFailCount(fail);
        summary.setRunningCount(running);
        summary.setSuccessRate(total > 0 ? (double) success / total * 100 : 0.0);

        OptionalDouble avgCost = logs.stream()
                .filter(l -> l.getSyncLogCost() != null)
                .mapToLong(MonitorSyncTaskLog::getSyncLogCost)
                .average();
        summary.setAvgCost(avgCost.orElse(0.0));

        summary.setTotalReadCount(logs.stream()
                .filter(l -> l.getSyncLogReadCount() != null)
                .mapToLong(MonitorSyncTaskLog::getSyncLogReadCount)
                .sum());
        summary.setTotalWriteCount(logs.stream()
                .filter(l -> l.getSyncLogWriteCount() != null)
                .mapToLong(MonitorSyncTaskLog::getSyncLogWriteCount)
                .sum());

        OptionalDouble avgThroughput = logs.stream()
                .filter(l -> l.getSyncLogThroughput() != null)
                .mapToDouble(MonitorSyncTaskLog::getSyncLogThroughput)
                .average();
        summary.setAvgThroughput(avgThroughput.orElse(0.0));

        return summary;
    }

    /**
     * 构建趋势数据
     */
    private SyncTaskStatistics.TrendData buildTrendData(List<MonitorSyncTaskLog> logs,
                                                        LocalDateTime startTime, LocalDateTime endTime, String granularity) {
        SyncTaskStatistics.TrendData trend = new SyncTaskStatistics.TrendData();

        List<String> labels = new ArrayList<>();
        List<Long> executions = new ArrayList<>();
        List<Long> successCounts = new ArrayList<>();
        List<Long> failCounts = new ArrayList<>();
        List<Double> avgCosts = new ArrayList<>();
        List<Long> dataCounts = new ArrayList<>();

        boolean isHourly = "hour".equals(granularity);
        DateTimeFormatter formatter = isHourly
                ? DateTimeFormatter.ofPattern("MM-dd HH:00")
                : DateTimeFormatter.ofPattern("yyyy-MM-dd");

        LocalDateTime current = isHourly
                ? startTime.truncatedTo(ChronoUnit.HOURS)
                : startTime.truncatedTo(ChronoUnit.DAYS);
        LocalDateTime end = isHourly
                ? endTime.truncatedTo(ChronoUnit.HOURS)
                : endTime.truncatedTo(ChronoUnit.DAYS);

        while (!current.isAfter(end)) {
            LocalDateTime periodStart = current;
            LocalDateTime periodEnd = isHourly ? current.plusHours(1) : current.plusDays(1);

            List<MonitorSyncTaskLog> periodLogs = logs.stream()
                    .filter(l -> l.getSyncLogStartTime() != null
                            && !l.getSyncLogStartTime().isBefore(periodStart)
                            && l.getSyncLogStartTime().isBefore(periodEnd))
                    .collect(Collectors.toList());

            labels.add(formatter.format(current));
            executions.add((long) periodLogs.size());
            successCounts.add(periodLogs.stream().filter(l -> "SUCCESS".equals(l.getSyncLogStatus())).count());
            failCounts.add(periodLogs.stream().filter(l -> "FAIL".equals(l.getSyncLogStatus())).count());

            OptionalDouble avg = periodLogs.stream()
                    .filter(l -> l.getSyncLogCost() != null)
                    .mapToLong(MonitorSyncTaskLog::getSyncLogCost)
                    .average();
            avgCosts.add(avg.orElse(0.0));

            dataCounts.add(periodLogs.stream()
                    .filter(l -> l.getSyncLogReadCount() != null)
                    .mapToLong(MonitorSyncTaskLog::getSyncLogReadCount)
                    .sum());

            current = isHourly ? current.plusHours(1) : current.plusDays(1);
        }

        trend.setLabels(labels);
        trend.setExecutions(executions);
        trend.setSuccessCounts(successCounts);
        trend.setFailCounts(failCounts);
        trend.setAvgCosts(avgCosts);
        trend.setDataCounts(dataCounts);

        return trend;
    }

    /**
     * 构建状态分布
     */
    private List<SyncTaskStatistics.StatusDistribution> buildStatusDistribution(List<MonitorSyncTaskLog> logs) {
        Map<String, Long> statusCount = logs.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getSyncLogStatus() != null ? l.getSyncLogStatus() : "UNKNOWN",
                        Collectors.counting()));

        long total = logs.size();
        Map<String, String> statusNames = Map.of(
                "SUCCESS", "成功",
                "FAIL", "失败",
                "RUNNING", "运行中",
                "TIMEOUT", "超时",
                "UNKNOWN", "未知"
        );

        return statusCount.entrySet().stream()
                .map(entry -> {
                    SyncTaskStatistics.StatusDistribution dist = new SyncTaskStatistics.StatusDistribution();
                    dist.setStatus(entry.getKey());
                    dist.setStatusName(statusNames.getOrDefault(entry.getKey(), entry.getKey()));
                    dist.setCount(entry.getValue());
                    dist.setPercentage(total > 0 ? (double) entry.getValue() / total * 100 : 0.0);
                    return dist;
                })
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());
    }

    /**
     * 构建触发类型分布
     */
    private List<SyncTaskStatistics.TriggerTypeDistribution> buildTriggerTypeDistribution(List<MonitorSyncTaskLog> logs) {
        Map<String, Long> triggerCount = logs.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getSyncLogTriggerType() != null ? l.getSyncLogTriggerType() : "UNKNOWN",
                        Collectors.counting()));

        long total = logs.size();
        Map<String, String> triggerNames = Map.of(
                "MANUAL", "手动执行",
                "SCHEDULE", "定时调度",
                "API", "API调用",
                "UNKNOWN", "未知"
        );

        return triggerCount.entrySet().stream()
                .map(entry -> {
                    SyncTaskStatistics.TriggerTypeDistribution dist = new SyncTaskStatistics.TriggerTypeDistribution();
                    dist.setTriggerType(entry.getKey());
                    dist.setTriggerTypeName(triggerNames.getOrDefault(entry.getKey(), entry.getKey()));
                    dist.setCount(entry.getValue());
                    dist.setPercentage(total > 0 ? (double) entry.getValue() / total * 100 : 0.0);
                    return dist;
                })
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());
    }

    /**
     * 构建任务排行
     */
    private List<SyncTaskStatistics.TaskRanking> buildTaskRanking(List<MonitorSyncTaskLog> logs) {
        Map<Long, List<MonitorSyncTaskLog>> taskLogs = logs.stream()
                .filter(l -> l.getSyncTaskId() != null)
                .collect(Collectors.groupingBy(MonitorSyncTaskLog::getSyncTaskId));

        Set<Long> taskIds = taskLogs.keySet();
        Map<Long, String> taskNames = new HashMap<>();
        if (!taskIds.isEmpty()) {
            List<MonitorSyncTask> tasks = taskMapper.selectBatchIds(taskIds);
            taskNames = tasks.stream()
                    .collect(Collectors.toMap(MonitorSyncTask::getSyncTaskId, MonitorSyncTask::getSyncTaskName));
        }

        final Map<Long, String> finalTaskNames = taskNames;
        return taskLogs.entrySet().stream()
                .map(entry -> {
                    Long taskId = entry.getKey();
                    List<MonitorSyncTaskLog> taskLogList = entry.getValue();

                    SyncTaskStatistics.TaskRanking ranking = new SyncTaskStatistics.TaskRanking();
                    ranking.setTaskId(taskId);
                    ranking.setTaskName(finalTaskNames.getOrDefault(taskId, "未知任务"));
                    ranking.setExecutions((long) taskLogList.size());

                    long success = taskLogList.stream().filter(l -> "SUCCESS".equals(l.getSyncLogStatus())).count();
                    ranking.setSuccessRate(taskLogList.isEmpty() ? 0.0 : (double) success / taskLogList.size() * 100);

                    OptionalDouble avgCost = taskLogList.stream()
                            .filter(l -> l.getSyncLogCost() != null)
                            .mapToLong(MonitorSyncTaskLog::getSyncLogCost)
                            .average();
                    ranking.setAvgCost(avgCost.orElse(0.0));

                    ranking.setTotalDataCount(taskLogList.stream()
                            .filter(l -> l.getSyncLogReadCount() != null)
                            .mapToLong(MonitorSyncTaskLog::getSyncLogReadCount)
                            .sum());

                    return ranking;
                })
                .sorted((a, b) -> Long.compare(b.getExecutions(), a.getExecutions()))
                .limit(10)
                .collect(Collectors.toList());
    }
}
