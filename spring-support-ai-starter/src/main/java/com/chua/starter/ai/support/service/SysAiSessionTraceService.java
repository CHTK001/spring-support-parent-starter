package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.agent.AgentTrace;
import com.chua.starter.ai.support.entity.SysAiSessionTrace;

import java.util.List;

/**
 * AI 会话执行轨迹 服务。
 *
 * <p>会话编排层在收到 Agent 的执行轨迹时调用 {@link #append} 入队，
 * 由本服务异步批量落库——写库不能阻塞流式输出的节奏。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public interface SysAiSessionTraceService {

    /**
     * 异步记录一条执行轨迹。
     *
     * <p>入队即返回，不等待落库。队列满时丢弃该条并告警，
     * 不阻塞、不抛异常——轨迹是观测数据，不值得让业务流为它停下。</p>
     *
     * @param sessionId     所属会话ID（字符串形式，与会话编排层一致）；为空或非法则不记会话归属
     * @param chatRequestId 本次运行的请求ID，可为空
     * @param trace         Agent 执行轨迹，不可为空
     */
    void append(String sessionId, String chatRequestId, AgentTrace trace);

    /**
     * 立即把队列中已入队的轨迹刷库。
     *
     * <p>供服务停止前调用，避免最后一批轨迹滞留内存丢失。</p>
     *
     * @return 本次实际刷库的条数
     */
    int flush();

    /**
     * 查询某会话的执行轨迹，按发生顺序返回。
     *
     * @param sessionId 会话ID
     * @param limit     最多返回条数；≤0 表示不限制
     * @return 轨迹列表
     */
    List<SysAiSessionTrace> listBySession(Long sessionId, int limit);
}
