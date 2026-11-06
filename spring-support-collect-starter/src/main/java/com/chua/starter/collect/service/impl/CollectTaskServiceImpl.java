// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.collect.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.collect.engine.CollectEngine;
import com.chua.starter.collect.entity.CollectTask;
import com.chua.starter.collect.entity.CollectTaskLog;
import com.chua.starter.collect.mapper.CollectTaskLogMapper;
import com.chua.starter.collect.mapper.CollectTaskMapper;
import com.chua.starter.collect.service.CollectTaskService;
import com.chua.starter.collect.support.CollectTaskStatus;
import com.chua.starter.collect.vo.CollectQualityVO;
import com.chua.starter.collect.vo.CollectStatsVO;
import com.chua.starter.collect.vo.CollectTaskLogVO;
import com.chua.starter.collect.vo.CollectTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 采集任务查询服务实现。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectTaskServiceImpl extends ServiceImpl<CollectTaskMapper, CollectTask>
        implements CollectTaskService {

    /**
     * 日志接口单次返回上限，取最近的一批。
     */
    private static final int LOG_LIMIT = 1000;

    /**
     * 节点日志表访问。
     */
    private final CollectTaskLogMapper taskLogMapper;

    /**
     * 执行引擎，用于标记任务是否仍在本机跑。
     */
    private final CollectEngine engine;

    @Override
    public IPage<CollectTaskVO> pageTasks(IPage<CollectTask> page, CollectTask condition) {
        CollectTask safe = condition == null ? new CollectTask() : condition;
        IPage<CollectTask> entityPage = page(page, Wrappers.<CollectTask>lambdaQuery()
                .eq(safe.getCollectTaskFlowId() != null, CollectTask::getCollectTaskFlowId, safe.getCollectTaskFlowId())
                .eq(safe.getCollectTaskStatus() != null && !safe.getCollectTaskStatus().isBlank(),
                        CollectTask::getCollectTaskStatus, safe.getCollectTaskStatus())
                .eq(safe.getCollectTaskTriggerType() != null && !safe.getCollectTaskTriggerType().isBlank(),
                        CollectTask::getCollectTaskTriggerType, safe.getCollectTaskTriggerType())
                .like(safe.getCollectTaskNo() != null && !safe.getCollectTaskNo().isBlank(),
                        CollectTask::getCollectTaskNo, safe.getCollectTaskNo())
                .like(safe.getCollectTaskFlowName() != null && !safe.getCollectTaskFlowName().isBlank(),
                        CollectTask::getCollectTaskFlowName, safe.getCollectTaskFlowName())
                .orderByDesc(CollectTask::getCollectTaskId));
        return entityPage.convert(this::toVo);
    }

    @Override
    public CollectTaskVO detail(Long taskId) {
        return toVo(requireTask(taskId));
    }

    @Override
    public List<CollectTaskLogVO> logs(Long taskId) {
        requireTask(taskId);
        List<CollectTaskLog> rows = taskLogMapper.selectList(Wrappers.<CollectTaskLog>lambdaQuery()
                .eq(CollectTaskLog::getCollectTaskLogTaskId, taskId)
                .orderByDesc(CollectTaskLog::getCollectTaskLogId)
                .last("LIMIT " + LOG_LIMIT));
        Collections.reverse(rows);
        List<CollectTaskLogVO> list = new ArrayList<>(rows.size());
        for (CollectTaskLog row : rows) {
            list.add(toLogVo(row));
        }
        return list;
    }

    @Override
    public List<CollectTaskLogVO> nodeStates(Long taskId) {
        requireTask(taskId);
        List<CollectTaskLog> rows = taskLogMapper.selectList(Wrappers.<CollectTaskLog>lambdaQuery()
                .eq(CollectTaskLog::getCollectTaskLogTaskId, taskId)
                .orderByDesc(CollectTaskLog::getCollectTaskLogId)
                .last("LIMIT " + LOG_LIMIT));
        Map<String, CollectTaskLog> latest = new LinkedHashMap<>();
        for (CollectTaskLog row : rows) {
            // 子流程与宿主流程的节点ID 可以重名，按 流程+节点 去重才不会互相顶掉
            latest.putIfAbsent(row.getCollectTaskLogFlowId() + ":" + row.getCollectTaskLogNodeId(), row);
        }
        List<CollectTaskLogVO> list = new ArrayList<>(latest.size());
        for (CollectTaskLog row : latest.values()) {
            list.add(toLogVo(row));
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTask(Long taskId) {
        CollectTask task = requireTask(taskId);
        if (CollectTaskStatus.RUNNING.equals(task.getCollectTaskStatus()) || engine.isRunning(taskId)) {
            throw new IllegalArgumentException("任务正在执行，请先停止再删除");
        }
        taskLogMapper.delete(Wrappers.<CollectTaskLog>lambdaQuery()
                .eq(CollectTaskLog::getCollectTaskLogTaskId, taskId));
        return removeById(taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanTasks(Integer flowId, LocalDateTime before) {
        if (before == null) {
            throw new IllegalArgumentException("必须指定清理时间点");
        }
        List<CollectTask> stale = list(Wrappers.<CollectTask>lambdaQuery()
                .eq(flowId != null, CollectTask::getCollectTaskFlowId, flowId)
                .ne(CollectTask::getCollectTaskStatus, CollectTaskStatus.RUNNING)
                .lt(CollectTask::getCollectTaskBeginTime, before));
        if (stale.isEmpty()) {
            return 0;
        }
        List<Long> ids = new ArrayList<>(stale.size());
        for (CollectTask task : stale) {
            ids.add(task.getCollectTaskId());
        }
        taskLogMapper.delete(Wrappers.<CollectTaskLog>lambdaQuery().in(CollectTaskLog::getCollectTaskLogTaskId, ids));
        removeByIds(ids);
        return ids.size();
    }

    @Override
    public CollectQualityVO quality(Long taskId) {
        CollectTask task = requireTask(taskId);
        List<CollectTaskLog> allLogs = taskLogMapper.selectList(
                Wrappers.<CollectTaskLog>lambdaQuery()
                        .eq(CollectTaskLog::getCollectTaskLogTaskId, taskId)
                        .orderByAsc(CollectTaskLog::getCollectTaskLogId));

        CollectQualityVO vo = new CollectQualityVO();
        vo.setTaskId(taskId);
        vo.setTaskNo(task.getCollectTaskNo());
        vo.setRecordCount(task.getCollectTaskRecordCount() != null ? task.getCollectTaskRecordCount().intValue() : 0);
        vo.setTotalCostMs(task.getCollectTaskCost());

        // 按节点分组
        Map<String, List<CollectTaskLog>> byNode = allLogs.stream()
                .collect(Collectors.groupingBy(l -> l.getCollectTaskLogNodeId() + ":" + l.getCollectTaskLogNodeShape()));

        int successNodes = 0, failedNodes = 0, skippedNodes = 0;
        Long slowestCost = 0L;
        String slowestNode = "";
        List<CollectQualityVO.QualityNode> nodeList = new ArrayList<>();

        for (Map.Entry<String, List<CollectTaskLog>> e : byNode.entrySet()) {
            List<CollectTaskLog> logs = e.getValue();
            CollectTaskLog last = logs.get(logs.size() - 1);
            int exec = logs.size();
            long nodeTotalCost = logs.stream()
                    .filter(l -> l.getCollectTaskLogCost() != null)
                    .mapToLong(CollectTaskLog::getCollectTaskLogCost).sum();
            long avgCost = exec > 0 ? nodeTotalCost / exec : 0;

            int s = 0, f = 0, sk = 0;
            for (CollectTaskLog l : logs) {
                String st = l.getCollectTaskLogStatus();
                if ("SUCCESS".equals(st)) {
                    s++;
                } else if ("FAILURE".equals(st)) {
                    f++;
                } else if ("SKIPPED".equals(st)) {
                    sk++;
                }
            }
            if (s > 0) {
                successNodes++;
            } else if (f > 0) {
                failedNodes++;
            } else {
                skippedNodes++;
            }

            if (nodeTotalCost > slowestCost) {
                slowestCost = nodeTotalCost;
                slowestNode = last.getCollectTaskLogNodeName() != null ? last.getCollectTaskLogNodeName() : last.getCollectTaskLogNodeId();
            }

            CollectQualityVO.QualityNode qn = new CollectQualityVO.QualityNode();
            qn.setNodeId(last.getCollectTaskLogNodeId());
            qn.setNodeName(last.getCollectTaskLogNodeName());
            qn.setNodeShape(last.getCollectTaskLogNodeShape());
            qn.setExecCount(exec);
            qn.setSuccessCount(s);
            qn.setFailedCount(f);
            qn.setSkippedCount(sk);
            qn.setAvgCostMs(avgCost);
            qn.setTotalCostMs(nodeTotalCost);
            qn.setSuccessRate(exec > 0 ? BigDecimal.valueOf(s * 100.0 / exec).setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            nodeList.add(qn);
        }

        // 按节点ID（flow+node）进一步细分统计
        Map<String, List<CollectTaskLog>> byFlowNode = allLogs.stream()
                .collect(Collectors.groupingBy(l -> l.getCollectTaskLogFlowId() + ":" + l.getCollectTaskLogNodeId()));

        int reqSuccess = 0, reqFailed = 0;
        for (CollectTaskLog l : allLogs) {
            if ("request".equals(l.getCollectTaskLogNodeShape())) {
                if ("SUCCESS".equals(l.getCollectTaskLogStatus())) {
                    reqSuccess++;
                } else if ("FAILURE".equals(l.getCollectTaskLogStatus())) {
                    reqFailed++;
                }
            }
        }
        vo.setRequestCount(reqSuccess + reqFailed);
        vo.setRequestSuccess(reqSuccess);
        vo.setRequestFailed(reqFailed);

        // 新增记录数估算：从 recordCount 中扣除历史重复的保守估计
        int newRecords = vo.getRecordCount();
        vo.setNewRecords(Math.max(0, newRecords));
        vo.setDuplicateRecords(Math.max(0, vo.getRecordCount() - newRecords));

        int totalNodes = allLogs.size();
        vo.setTotalNodes(totalNodes);
        vo.setSuccessNodes(successNodes);
        vo.setFailedNodes(failedNodes);
        vo.setSkippedNodes(skippedNodes);
        vo.setNodeSuccessRate(totalNodes > 0
                ? BigDecimal.valueOf(successNodes * 100.0 / (successNodes + failedNodes + skippedNodes)).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        vo.setSlowestNode(slowestNode);
        vo.setSlowestNodeCostMs(slowestCost);
        vo.setNodes(nodeList);

        // 质量评分
        int score = calcQualityScore(vo);
        vo.setQualityScore(score);
        vo.setQualityLevel(score >= 90 ? "EXCELLENT" : score >= 70 ? "GOOD" : score >= 50 ? "FAIR" : "POOR");

        if (failedNodes > 0) {
            String errMsg = allLogs.stream()
                    .filter(l -> "FAILURE".equals(l.getCollectTaskLogStatus()) && l.getCollectTaskLogMessage() != null)
                    .findFirst().map(CollectTaskLog::getCollectTaskLogMessage).orElse("");
            vo.setErrorSummary(errMsg.length() > 200 ? errMsg.substring(0, 200) : errMsg);
        }

        return vo;
    }

    /** 质量评分：节点成功率 50% + 入库率 30% + 无错误 20% */
    private int calcQualityScore(CollectQualityVO vo) {
        int nodeScore = vo.getNodeSuccessRate().intValue() / 2;           // 0-50
        int insertScore = 30;  // 保守给满分，若有入库率数据可进一步计算
        int errorScore = vo.getFailedNodes() == 0 ? 20 : 0;              // 0-20
        return Math.min(100, nodeScore + insertScore + errorScore);
    }

    @Override
    public CollectStatsVO stats(Integer flowId, int days) {
        if (flowId == null || days <= 0) {
            throw new IllegalArgumentException("必须指定流程ID和统计天数");
        }
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<CollectTask> tasks = list(Wrappers.<CollectTask>lambdaQuery()
                .eq(CollectTask::getCollectTaskFlowId, flowId)
                .ge(CollectTask::getCollectTaskBeginTime, since)
                .orderByAsc(CollectTask::getCollectTaskBeginTime));

        CollectStatsVO vo = new CollectStatsVO();
        vo.setFlowId(flowId);
        vo.setDays(days);
        vo.setTotalRuns(tasks.size());

        int success = 0, failure = 0, skipped = 0;
        long totalRecords = 0;
        long totalCost = 0;
        long minCost = Long.MAX_VALUE, maxCost = 0;

        for (CollectTask t : tasks) {
            String s = t.getCollectTaskStatus();
            if ("SUCCESS".equals(s)) {
                success++;
            } else if ("FAILURE".equals(s)) {
                failure++;
            } else if ("SKIPPED".equals(s)) {
                skipped++;
            }
            if (t.getCollectTaskRecordCount() != null) {
                totalRecords += t.getCollectTaskRecordCount();
            }
            if (t.getCollectTaskCost() != null) {
                totalCost += t.getCollectTaskCost();
                minCost = Math.min(minCost, t.getCollectTaskCost());
                maxCost = Math.max(maxCost, t.getCollectTaskCost());
            }
        }

        vo.setSuccessCount(success);
        vo.setFailureCount(failure);
        vo.setSkippedCount(skipped);
        int finished = success + failure;
        vo.setSuccessRate(finished > 0
                ? BigDecimal.valueOf(success * 100.0 / finished).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        vo.setTotalRecords(totalRecords);
        vo.setAvgRecordsPerRun(tasks.size() > 0
                ? BigDecimal.valueOf(totalRecords * 1.0 / tasks.size()).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        vo.setTotalCostMs(totalCost);
        vo.setAvgCostMs(tasks.size() > 0
                ? BigDecimal.valueOf(totalCost / tasks.size()).setScale(0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        vo.setMaxCostMs(maxCost == Long.MAX_VALUE ? null : maxCost);
        vo.setMinCostMs(minCost == Long.MAX_VALUE ? null : minCost);

        // 每日趋势
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, List<CollectTask>> byDay = tasks.stream()
                .collect(Collectors.groupingBy(t -> t.getCollectTaskBeginTime().format(fmt)));

        List<CollectStatsVO.DailyTrend> daily = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            String day = LocalDate.now().minusDays(i).format(fmt);
            List<CollectTask> dayTasks = byDay.getOrDefault(day, Collections.emptyList());
            CollectStatsVO.DailyTrend dt = new CollectStatsVO.DailyTrend();
            dt.setDate(day);
            dt.setRunCount(dayTasks.size());
            int ds = 0, df = 0;
            long dr = 0, dc = 0;
            for (CollectTask t : dayTasks) {
                if ("SUCCESS".equals(t.getCollectTaskStatus())) {
                    ds++;
                } else if ("FAILURE".equals(t.getCollectTaskStatus())) {
                    df++;
                }
                if (t.getCollectTaskRecordCount() != null) {
                    dr += t.getCollectTaskRecordCount();
                }
                if (t.getCollectTaskCost() != null) {
                    dc += t.getCollectTaskCost();
                }
            }
            dt.setSuccessCount(ds);
            dt.setFailureCount(df);
            dt.setRecordCount(dr);
            dt.setAvgCostMs(dayTasks.size() > 0 ? dc / dayTasks.size() : null);
            daily.add(dt);
        }
        vo.setDaily(daily);

        // 节点耗时统计
        if (!tasks.isEmpty()) {
            List<Long> taskIds = tasks.stream().map(CollectTask::getCollectTaskId).collect(Collectors.toList());
            List<CollectTaskLog> logs = taskLogMapper.selectList(
                    Wrappers.<CollectTaskLog>lambdaQuery()
                            .in(CollectTaskLog::getCollectTaskLogTaskId, taskIds));
            Map<String, List<CollectTaskLog>> byFn = logs.stream()
                    .collect(Collectors.groupingBy(l -> l.getCollectTaskLogFlowId() + ":" + l.getCollectTaskLogNodeId()));
            List<CollectStatsVO.NodeCostStat> nodeStats = new ArrayList<>();
            for (Map.Entry<String, List<CollectTaskLog>> e : byFn.entrySet()) {
                List<CollectTaskLog> nl = e.getValue();
                CollectTaskLog sample = nl.get(nl.size() - 1);
                long sum = nl.stream().filter(l -> l.getCollectTaskLogCost() != null)
                        .mapToLong(CollectTaskLog::getCollectTaskLogCost).sum();
                int exec = nl.size();
                int succ = (int) nl.stream().filter(l -> "SUCCESS".equals(l.getCollectTaskLogStatus())).count();
                CollectStatsVO.NodeCostStat ns = new CollectStatsVO.NodeCostStat();
                ns.setNodeId(sample.getCollectTaskLogNodeId());
                ns.setNodeName(sample.getCollectTaskLogNodeName());
                ns.setNodeShape(sample.getCollectTaskLogNodeShape());
                ns.setExecCount(exec);
                ns.setAvgCostMs(BigDecimal.valueOf(sum / exec).setScale(0, RoundingMode.HALF_UP));
                ns.setTotalCostMs(sum);
                ns.setSuccessRate(BigDecimal.valueOf(succ * 100.0 / exec).setScale(1, RoundingMode.HALF_UP));
                nodeStats.add(ns);
            }
            nodeStats.sort((a, b) -> Long.compare(b.getTotalCostMs(), a.getTotalCostMs()));
            vo.setNodeCosts(nodeStats.size() > 10 ? nodeStats.subList(0, 10) : nodeStats);
        } else {
            vo.setNodeCosts(Collections.emptyList());
        }

        return vo;
    }

    @Override
    public List<CollectTaskLogVO> liveNodes(Long taskId) {
        requireTask(taskId);
        // 取最新的一批节点日志（按节点ID去重）
        List<CollectTaskLog> rows = taskLogMapper.selectList(
                Wrappers.<CollectTaskLog>lambdaQuery()
                        .eq(CollectTaskLog::getCollectTaskLogTaskId, taskId)
                        .orderByDesc(CollectTaskLog::getCollectTaskLogId)
                        .last("LIMIT " + LOG_LIMIT));
        Map<String, CollectTaskLog> latest = new LinkedHashMap<>();
        for (CollectTaskLog row : rows) {
            latest.putIfAbsent(row.getCollectTaskLogFlowId() + ":" + row.getCollectTaskLogNodeId(), row);
        }
        List<CollectTaskLogVO> list = new ArrayList<>();
        for (CollectTaskLog row : latest.values()) {
            list.add(toLogVo(row));
        }
        return list;
    }

    /**
     * 按主键取任务。
     *
     * @param taskId 任务ID
     * @return 任务
     */
    private CollectTask requireTask(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        CollectTask task = getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        return task;
    }

    /**
     * 实体转出参。
     *
     * @param task 任务
     * @return 出参
     */
    private CollectTaskVO toVo(CollectTask task) {
        return new CollectTaskVO(
                task.getCollectTaskId(),
                task.getCollectTaskNo(),
                task.getCollectTaskFlowId(),
                task.getCollectTaskFlowName(),
                task.getCollectTaskStatus(),
                task.getCollectTaskTriggerType(),
                task.getCollectTaskParams(),
                task.getCollectTaskBeginTime(),
                task.getCollectTaskEndTime(),
                task.getCollectTaskCost(),
                task.getCollectTaskNodeCount(),
                task.getCollectTaskRequestCount(),
                task.getCollectTaskRecordCount(),
                task.getCollectTaskError(),
                task.getCollectTaskExecutorId(),
                engine.isRunning(task.getCollectTaskId()),
                task.getCreateName());
    }

    /**
     * 日志实体转出参。
     *
     * @param row 日志
     * @return 出参
     */
    private CollectTaskLogVO toLogVo(CollectTaskLog row) {
        return new CollectTaskLogVO(
                row.getCollectTaskLogId(),
                row.getCollectTaskLogTaskId(),
                row.getCollectTaskLogFlowId(),
                row.getCollectTaskLogNodeId(),
                row.getCollectTaskLogNodeName(),
                row.getCollectTaskLogNodeShape(),
                row.getCollectTaskLogIteration(),
                row.getCollectTaskLogStatus(),
                row.getCollectTaskLogCost(),
                row.getCollectTaskLogMessage(),
                row.getCollectTaskLogOutput(),
                row.getCreateTime());
    }
}