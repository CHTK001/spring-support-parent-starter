package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.agent.run.AgentRunQuery;
import com.chua.common.support.ai.agent.run.AgentRunRecord;
import com.chua.common.support.ai.agent.run.AgentRunStatus;
import com.chua.common.support.ai.agent.run.AgentStepRecord;
import com.chua.common.support.ai.agent.run.AgentStepStatus;
import com.chua.common.support.ai.agent.run.AgentStepType;

import java.util.List;

/**
 * Agent 运行记录服务：运行生命周期 + 步骤落库 + 会话 SSE 事件桥。
 *
 * <p>负责把一次 Agent 执行（会话内子代理调用 / 编排运行）记为一条
 * {@link AgentRunRecord} 及其步骤时间线，并在状态 / 汇总变化时向归属用户的
 * 会话 SSE 通道广播 {@code agent_run_update} 事件。存储经
 * {@link com.chua.common.support.ai.agent.run.AgentRunStore} SPI 完成。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
public interface AgentRunService {

    /**
     * 开启一条运行记录（状态 RUNNING）并广播 agent_run_update。
     *
     * @param sessionId    关联会话；null 表示后台运行
     * @param agentId      Agent 标识
     * @param triggerType  触发来源：chat / manual / orchestration / schedule / eval
     * @param mode         执行模式（SINGLE / ROUTER / PIPELINE 等，可空）
     * @param inputSummary 输入摘要（内部截断）
     * @return 已落库的运行记录
     */
    AgentRunRecord startRun(Long sessionId, String agentId, String triggerType, String mode, String inputSummary);

    /**
     * 追加一步并累加运行轮次 / 工具调用计数，广播 agent_run_update（含 lastStep）。
     *
     * @param runId         运行标识
     * @param type          步骤类型
     * @param name          工具名 / 子代理名 / 节点名
     * @param argumentsJson 参数摘要（内部截断）
     * @param resultSummary 结果摘要（内部截断；失败时存失败原因）
     * @param status        步骤执行状态
     * @param startedAt     步骤开始时间（epoch 毫秒）
     * @param endedAt       步骤结束时间（epoch 毫秒）
     * @return 已落库的步骤记录
     */
    AgentStepRecord appendStep(String runId, AgentStepType type, String name, String argumentsJson,
                               String resultSummary, AgentStepStatus status, long startedAt, Long endedAt);

    /**
     * 运行成功收尾（终态；已终态则忽略）并广播 agent_run_update。
     *
     * @param runId         运行标识
     * @param outputSummary 输出摘要（内部截断）
     */
    void completeRun(String runId, String outputSummary);

    /**
     * 运行失败收尾（终态；已终态则忽略）并广播 agent_run_update。
     *
     * @param runId 运行标识
     * @param error 失败原因
     */
    void failRun(String runId, String error);

    /**
     * 回填运行与会话 / 子代理记录的关联（会话侧调用入口在拿到运行标识后调用）。
     *
     * <p>运行在 Agent 服务内创建时尚无会话上下文（sessionId 为空），本方法补齐
     * {@code sessionId} / {@code subSessionId}，使运行可按会话过滤并进入会话 SSE 通道。
     * 已终态的运行只补关联，不改状态。</p>
     *
     * @param runId        运行标识
     * @param sessionId    关联会话标识
     * @param subSessionId 关联子代理运行记录（sys_ai_session_sub_agent），可空
     * @return 回填后的运行记录；运行不存在返回 null
     */
    AgentRunRecord linkSession(String runId, Long sessionId, Long subSessionId);

    /**
     * 取消运行（终态；已终态则原样返回）。权限：管理员或归属人。
     *
     * @param runId      运行标识
     * @param operatorId 操作人（当前登录用户）
     * @return 取消后的运行记录
     */
    AgentRunRecord cancelRun(String runId, String operatorId);

    /**
     * 读取运行记录；不存在抛业务异常。
     *
     * @param runId 运行标识
     * @return 运行记录
     */
    AgentRunRecord getRun(String runId);

    /**
     * 读取运行的完整步骤时间线（按 seq 升序）。
     *
     * @param runId 运行标识
     * @return 步骤记录列表
     */
    List<AgentStepRecord> listSteps(String runId);

    /**
     * 统计满足条件的运行数。
     *
     * @param query 查询条件
     * @return 运行数
     */
    long countRuns(AgentRunQuery query);

    /**
     * 分页查询运行列表（按开始时间倒序）。
     *
     * @param query 查询条件（含分页参数）
     * @return 运行记录列表
     */
    List<AgentRunRecord> listRuns(AgentRunQuery query);
}
