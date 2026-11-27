// [P3C A20 豁免] A20/A30/A31: 含日志字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.ai.agent.run.AgentRunQuery;
import com.chua.common.support.ai.agent.run.AgentRunRecord;
import com.chua.common.support.ai.agent.run.AgentRunStatus;
import com.chua.common.support.ai.agent.run.AgentRunStore;
import com.chua.common.support.ai.agent.run.AgentStepRecord;
import com.chua.common.support.ai.agent.run.AgentStepStatus;
import com.chua.common.support.ai.agent.run.AgentStepType;
import com.chua.common.support.utils.IdUtils;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.service.AgentRunService;
import com.chua.starter.ai.support.service.AiSessionEventBroadcaster;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.chua.common.support.utils.ObjectUtils;

/**
 * Agent 运行记录服务实现：落库 + 汇总维护 + 会话 SSE 广播。
 *
 * <p>运行状态机在此维护：终态（COMPLETED/FAILED/CANCELED）不可覆盖；
 * 步骤级事件按「每步一条 agent_run_update」节流，步骤明细经 REST 拉取（设计方案 §9 事件风暴口径）。</p>
 *
 * <p><b>失败隔离</b>：执行链路上的落库方法（{@link #startRun} / {@link #appendStep} /
 * {@link #completeRun} / {@link #failRun}）均为 best-effort——存储异常只告警不抛出、
 * runId 为空直接跳过，保证「运行观测」不阻断 Agent 主链路；管理面读写方法
 * （{@link #getRun} / {@link #listSteps} / {@link #cancelRun}）保持严格语义
 * （不存在抛 NOT_FOUND）。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunServiceImpl implements AgentRunService {

    /**
     * 摘要字段截断上限（设计方案 §9：参数/结果单条 ≤2KB）
     */
    private static final int SUMMARY_MAX_LENGTH = 2048;

    /**
     * 运行存储（SPI，JDBC 实现）
     */
    private final AgentRunStore agentRunStore;

    /**
     * 会话事件广播器
     */
    private final AiSessionEventBroadcaster eventBroadcaster;

    /** 本进程同步执行线程，取消时发出中断；数据库状态负责跨进程的边界检查。 */
    private final Map<String, Thread> executions = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public AgentRunRecord startRun(Long sessionId, String agentId, String triggerType, String mode,
                                   String inputSummary) {
        try {
            AgentRunRecord run = new AgentRunRecord(IdUtils.uuidv7(), sessionId, agentId, triggerType, mode,
                    AgentRunStatus.RUNNING, truncate(inputSummary), null, null, 0L, 0, 0, null,
                    AiUserContext.currentUserId(), System.currentTimeMillis(), null);
            agentRunStore.insertRun(run);
            executions.put(run.runId(), Thread.currentThread());
            publishRunUpdate(run, null);
            return run;
        } catch (Exception e) {
            log.warn("[AgentRun] 运行记录落库失败（不影响主链路）: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public AgentStepRecord appendStep(String runId, AgentStepType type, String name, String argumentsJson,
                                      String resultSummary, AgentStepStatus status, long startedAt, Long endedAt) {
        if (runId == null || runId.isBlank()) {
            return null;
        }
        try {
            AgentRunRecord run = agentRunStore.findRun(runId);
            if (run == null) {
                log.warn("[AgentRun] 运行不存在，忽略步骤落库: {}", runId);
                return null;
            }
            int seq = (int) Math.min(Integer.MAX_VALUE, agentRunStore.countSteps(runId));
            AgentStepRecord step = new AgentStepRecord(IdUtils.uuidv7(), runId, seq, type, name,
                    truncate(argumentsJson), truncate(resultSummary), status, 0L, startedAt, endedAt);
            agentRunStore.insertStep(step);
            AgentRunRecord updated = withCounters(run, run.rounds() + 1,
                    run.toolCalls() + (type == AgentStepType.TOOL_CALL ? 1 : 0));
            agentRunStore.updateRun(updated);
            publishRunUpdate(updated, step);
            return step;
        } catch (Exception e) {
            log.warn("[AgentRun] 步骤落库失败（不影响主链路）runId={}: {}", runId, e.getMessage());
            return null;
        }
    }

    @Override
    public void completeRun(String runId, String outputSummary) {
        try {
            transitionRun(runId, AgentRunStatus.COMPLETED, truncate(outputSummary), null);
        } finally {
            if (runId != null) executions.remove(runId);
        }
    }

    @Override
    public void failRun(String runId, String error) {
        try {
            transitionRun(runId, AgentRunStatus.FAILED, null, error);
        } finally {
            if (runId != null) executions.remove(runId);
        }
    }

    @Override
    public void checkCancelled(String runId) {
        if (runId == null) return;
        AgentRunRecord run = agentRunStore.findRun(runId);
        if (run != null && run.status() == AgentRunStatus.CANCELED) {
            throw new java.util.concurrent.CancellationException("运行已取消");
        }
    }

    @Override
    public AgentRunRecord linkSession(String runId, Long sessionId, Long subSessionId) {
        AgentRunRecord run = agentRunStore.findRun(runId);
        if (run == null) {
            log.warn("[AgentRun] 运行不存在，忽略会话关联回填: {}", runId);
            return null;
        }
        AgentRunRecord updated = new AgentRunRecord(run.runId(), sessionId, run.agentId(), run.triggerType(),
                run.mode(), run.status(), run.inputSummary(), run.outputSummary(), run.error(), run.tokensUsed(),
                run.rounds(), run.toolCalls(), subSessionId, run.userId(), run.startedAt(), run.endedAt());
        if (agentRunStore.updateRun(updated) > 0) publishRunUpdate(updated, null);
        return agentRunStore.findRun(runId);
    }

    @Override
    public AgentRunRecord cancelRun(String runId, String operatorId) {
        AgentRunRecord run = requireRun(runId);
        boolean owner = ObjectUtils.nullSafeEquals(operatorId, run.userId());
        if (!AiUserContext.isAdmin() && !owner) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "无权取消该运行");
        }
        if (run.isTerminal()) {
            return run;
        }
        AgentRunRecord updated = new AgentRunRecord(run.runId(), run.sessionId(), run.agentId(), run.triggerType(),
                run.mode(), AgentRunStatus.CANCELED, run.inputSummary(), run.outputSummary(), "手动取消",
                run.tokensUsed(), run.rounds(), run.toolCalls(), run.subSessionId(), run.userId(),
                run.startedAt(), System.currentTimeMillis());
        if (agentRunStore.updateRun(updated) > 0) {
            Thread execution = executions.remove(runId);
            if (execution != null) execution.interrupt();
            publishRunUpdate(updated, null);
        }
        return requireRun(runId);
    }

    @Override
    public AgentRunRecord getRun(String runId) {
        return requireRun(runId);
    }

    @Override
    public List<AgentStepRecord> listSteps(String runId) {
        requireRun(runId);
        return agentRunStore.listSteps(runId);
    }

    @Override
    public long countRuns(AgentRunQuery query) {
        return agentRunStore.countRuns(query);
    }

    @Override
    public List<AgentRunRecord> listRuns(AgentRunQuery query) {
        return agentRunStore.listRuns(query);
    }

    /**
     * 运行状态流转到目标态（终态不可覆盖；运行不存在 / runId 为空 / 落库失败均静默跳过），
     * 并广播 agent_run_update。
     *
     * @param runId         运行标识
     * @param target        目标状态
     * @param outputSummary 输出摘要
     * @param error         失败原因
     */
    private void transitionRun(String runId, AgentRunStatus target, String outputSummary, String error) {
        if (runId == null || runId.isBlank()) {
            return;
        }
        try {
            AgentRunRecord run = agentRunStore.findRun(runId);
            if (run == null) {
                log.warn("[AgentRun] 运行不存在，忽略状态变更 {} -> {}: {}", runId, target, error);
                return;
            }
            if (run.isTerminal()) {
                return;
            }
            AgentRunRecord updated = new AgentRunRecord(run.runId(), run.sessionId(), run.agentId(),
                    run.triggerType(), run.mode(), target, run.inputSummary(), outputSummary, error,
                    run.tokensUsed(), run.rounds(), run.toolCalls(), run.subSessionId(), run.userId(),
                    run.startedAt(), System.currentTimeMillis());
            if (agentRunStore.updateRun(updated) > 0) publishRunUpdate(updated, null);
        } catch (Exception e) {
            log.warn("[AgentRun] 运行状态落库失败（不影响主链路）runId={}: {}", runId, e.getMessage());
        }
    }

    /**
     * 复制运行记录并更新轮次 / 工具调用计数。
     *
     * @param run       原记录
     * @param rounds    新轮次
     * @param toolCalls 新工具调用次数
     * @return 新记录
     */
    private static AgentRunRecord withCounters(AgentRunRecord run, int rounds, int toolCalls) {
        return new AgentRunRecord(run.runId(), run.sessionId(), run.agentId(), run.triggerType(), run.mode(),
                run.status(), run.inputSummary(), run.outputSummary(), run.error(), run.tokensUsed(), rounds,
                toolCalls, run.subSessionId(), run.userId(), run.startedAt(), run.endedAt());
    }

    /**
     * 读取运行记录；不存在抛业务异常。
     *
     * @param runId 运行标识
     * @return 运行记录
     */
    private AgentRunRecord requireRun(String runId) {
        AgentRunRecord run = agentRunStore.findRun(runId);
        if (run == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "运行记录不存在: " + runId);
        }
        return run;
    }

    /**
     * 向归属用户的会话 SSE 通道广播 agent_run_update（无会话 / 无归属人则跳过）。
     *
     * @param run      运行记录
     * @param lastStep 最近一步（可为 null）
     */
    private void publishRunUpdate(AgentRunRecord run, AgentStepRecord lastStep) {
        if (run.sessionId() == null || run.userId() == null || run.userId().isBlank()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>(10);
        payload.put("runId", run.runId());
        payload.put("sessionId", run.sessionId());
        payload.put("agentId", run.agentId());
        payload.put("triggerType", run.triggerType());
        payload.put("mode", run.mode());
        payload.put("status", run.status().name());
        payload.put("tokensUsed", run.tokensUsed());
        payload.put("rounds", run.rounds());
        payload.put("toolCalls", run.toolCalls());
        if (run.error() != null) {
            payload.put("error", run.error());
        }
        if (lastStep != null) {
            Map<String, Object> step = new LinkedHashMap<>(6);
            step.put("seq", lastStep.seq());
            step.put("type", lastStep.type().name());
            step.put("name", lastStep.name());
            step.put("status", lastStep.status().name());
            payload.put("lastStep", step);
        }
        eventBroadcaster.publish(AiSessionEventBroadcaster.TYPE_AGENT_RUN_UPDATE, run.userId(),
                String.valueOf(run.sessionId()), payload);
    }

    /**
     * 摘要截断（超长以 ... 结尾）。
     *
     * @param value 原文
     * @return 截断后的摘要
     */
    private static String truncate(String value) {
        if (value == null || value.length() <= SUMMARY_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, SUMMARY_MAX_LENGTH - 3) + "...";
    }
}
