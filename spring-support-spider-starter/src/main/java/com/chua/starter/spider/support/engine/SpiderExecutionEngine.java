package com.chua.starter.spider.support.engine;

import com.alibaba.fastjson2.JSON;
import com.chua.spider.support.SpiderToolkit;
import com.chua.spider.support.model.SpiderExecutionDefinition;
import com.chua.spider.support.model.SpiderExecutionMode;
import com.chua.spider.support.model.SpiderTaskResult;
import com.chua.starter.spider.support.domain.SpiderExecutionPolicy;
import com.chua.starter.spider.support.domain.SpiderExecutionRecord;
import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.SpiderRuntimeSnapshot;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;
import com.chua.starter.spider.support.domain.enums.SpiderExecutionType;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.domain.enums.SpiderTaskStatus;
import com.chua.starter.spider.support.repository.SpiderExecutionRecordRepository;
import com.chua.starter.spider.support.repository.SpiderFlowRepository;
import com.chua.starter.spider.support.repository.SpiderRuntimeSnapshotRepository;
import com.chua.starter.spider.support.repository.SpiderTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 爬虫执行引擎。
 *
 * <p>底层执行委托给 {@link SpiderToolkit}，本类负责：
 * <ul>
 *   <li>平台层任务定义 → 底层任务定义的转换</li>
 *   <li>运行时快照回写</li>
 *   <li>执行记录更新</li>
 *   <li>REPEAT_N 次数控制</li>
 *   <li>节点级日志记录</li>
 * </ul>
 * </p>
 *
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpiderExecutionEngine {

    private final SpiderTaskRepository taskRepository;
    private final SpiderFlowRepository flowRepository;
    private final SpiderRuntimeSnapshotRepository snapshotRepository;
    private final SpiderExecutionRecordRepository recordRepository;
    private final SpiderToolkit spiderToolkit;

    /**
     * 执行指定任务。
     *
     * @param taskId 任务 ID
     */
    public void execute(Long taskId) {
        SpiderTaskDefinition task = taskRepository.getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务 [" + taskId + "] 不存在");
        }

        SpiderFlowDefinition flow = flowRepository.findByTaskId(taskId).orElse(null);
        SpiderExecutionPolicy policy = parsePolicy(task);

        // 更新状态为 RUNNING
        updateTaskStatus(task, SpiderTaskStatus.RUNNING);
        updateSnapshot(taskId, SpiderTaskStatus.RUNNING, null, null, null);

        // 9.5 REPEAT_N 控制
        int repeatTimes = (policy != null && policy.getRepeatTimes() != null) ? policy.getRepeatTimes() : 1;
        boolean isRepeatN = task.getExecutionType() == SpiderExecutionType.REPEAT_N;

        long totalSuccess = 0;
        long totalFailure = 0;
        String lastError = null;

        for (int round = 0; round < repeatTimes; round++) {
            log.info("[Spider] 开始执行, taskId={}, round={}/{}", taskId, round + 1, repeatTimes);

            // 9.2 节点级日志
            List<String> nodeLog = buildNodeLog(flow, round);

            SpiderTaskResult result = null;
            try {
                // 9.1 转换并执行
                com.chua.spider.support.model.SpiderTaskDefinition utilsDef = toUtilsDefinition(task, policy, flow);
                result = spiderToolkit.run(utilsDef);

                totalSuccess += result.getSuccessRequests();
                totalFailure += result.getFailureRequests();

                // 9.4 回写快照
                updateSnapshot(taskId, SpiderTaskStatus.RUNNING, LocalDateTime.now(),
                        totalSuccess, totalFailure);

                log.info("[Spider] 第 {}/{} 轮执行完成, taskId={}, success={}, failure={}",
                        round + 1, repeatTimes, taskId, result.getSuccessRequests(), result.getFailureRequests());

            } catch (Exception e) {
                lastError = e.getMessage();
                totalFailure++;
                log.error("[Spider] 第 {}/{} 轮执行失败, taskId={}", round + 1, repeatTimes, taskId, e);

                // 9.3 重试耗尽后标记 FAILED
                updateTaskStatus(task, SpiderTaskStatus.FAILED);
                updateSnapshot(taskId, SpiderTaskStatus.FAILED, LocalDateTime.now(),
                        totalSuccess, totalFailure, lastError);
                return;
            }

            // REPEAT_N 间隔等待
            if (isRepeatN && round < repeatTimes - 1 && policy.getRepeatInterval() != null) {
                sleepQuietly(policy.getRepeatInterval() * 1000);
            }
        }

        // 9.5 REPEAT_N 达到次数后变更为 FINISHED
        SpiderTaskStatus finalStatus = isRepeatN ? SpiderTaskStatus.FINISHED : SpiderTaskStatus.FINISHED;
        updateTaskStatus(task, finalStatus);
        updateSnapshot(taskId, finalStatus, LocalDateTime.now(), totalSuccess, totalFailure);
        log.info("[Spider] 任务执行完成, taskId={}, status={}", taskId, finalStatus);
    }

    // ── 转换：平台层 → 底层 ───────────────────────────────────────────────────

    com.chua.spider.support.model.SpiderTaskDefinition toUtilsDefinition(
            SpiderTaskDefinition task,
            SpiderExecutionPolicy policy,
            SpiderFlowDefinition flow) {

        com.chua.spider.support.model.SpiderTaskDefinition def =
                new com.chua.spider.support.model.SpiderTaskDefinition();

        def.setTaskName(task.getTaskName());
        def.setUrl(task.getEntryUrl());

        if (policy != null) {
            // 9.3 重试策略
            if (policy.getRetryPolicy() != null) {
                def.setRetryTimes(policy.getRetryPolicy().getMaxRetries());
                def.setRetrySleepTime((int) policy.getRetryPolicy().getRetryIntervalMs());
            }
            if (policy.getThreadCount() != null) {
                def.setThreadNum(policy.getThreadCount());
            }

            // 执行模式映射
            SpiderExecutionDefinition execDef = new SpiderExecutionDefinition();
            execDef.setEnabled(true);
            execDef.setMode(mapExecutionMode(task.getExecutionType()));
            if (policy.getCron() != null) {
                execDef.setCron(policy.getCron());
            }
            if (policy.getRepeatInterval() != null) {
                execDef.setPeriodMillis(policy.getRepeatInterval() * 1000);
            }
            def.setExecution(execDef);
        }

        return def;
    }

    private SpiderExecutionMode mapExecutionMode(SpiderExecutionType type) {
        if (type == null) return SpiderExecutionMode.ONCE;
        return switch (type) {
            case ONCE -> SpiderExecutionMode.ONCE;
            case REPEAT_N -> SpiderExecutionMode.FIXED_RATE;
            case SCHEDULED -> SpiderExecutionMode.CRON;
        };
    }

    // ── 快照 & 状态更新 ───────────────────────────────────────────────────────

    private void updateTaskStatus(SpiderTaskDefinition task, SpiderTaskStatus status) {
        task.setStatus(status);
        taskRepository.updateById(task);
    }

    private void updateSnapshot(Long taskId, SpiderTaskStatus status,
                                LocalDateTime lastExecuteTime,
                                Long successCount, Long failureCount) {
        updateSnapshot(taskId, status, lastExecuteTime, successCount, failureCount, null);
    }

    private void updateSnapshot(Long taskId, SpiderTaskStatus status,
                                LocalDateTime lastExecuteTime,
                                Long successCount, Long failureCount,
                                String lastError) {
        SpiderRuntimeSnapshot snapshot = snapshotRepository.findByTaskId(taskId)
                .orElse(SpiderRuntimeSnapshot.builder().taskId(taskId).build());

        snapshot.setStatus(status);
        if (lastExecuteTime != null) snapshot.setLastExecuteTime(lastExecuteTime);
        if (successCount != null) snapshot.setSuccessCount(successCount);
        if (failureCount != null) snapshot.setFailureCount(failureCount);
        if (lastError != null) snapshot.setLastErrorSummary(
                lastError.length() > 512 ? lastError.substring(0, 512) : lastError);

        snapshotRepository.saveOrUpdate(snapshot);
    }

    // ── 节点日志 ──────────────────────────────────────────────────────────────

    /** 9.2 构建节点级日志摘要 */
    private List<String> buildNodeLog(SpiderFlowDefinition flow, int round) {
        List<String> logs = new ArrayList<>();
        if (flow == null || flow.getNodes() == null) return logs;

        for (SpiderFlowNode node : flow.getNodes()) {
            logs.add(String.format("[round=%d][%s][%s] 执行时间=%s",
                    round + 1,
                    node.getNodeType(),
                    node.getNodeId(),
                    LocalDateTime.now()));
        }
        return logs;
    }

    private SpiderExecutionPolicy parsePolicy(SpiderTaskDefinition task) {
        if (task.getExecutionPolicy() == null || task.getExecutionPolicy().isBlank()) return null;
        try {
            return JSON.parseObject(task.getExecutionPolicy(), SpiderExecutionPolicy.class);
        } catch (Exception e) {
            log.warn("[Spider] 解析执行策略失败, taskId={}", task.getId(), e);
            return null;
        }
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
