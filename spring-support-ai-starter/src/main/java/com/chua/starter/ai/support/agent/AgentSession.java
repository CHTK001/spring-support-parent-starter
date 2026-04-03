package com.chua.starter.ai.support.agent;

import com.chua.common.support.ai.agent.AgentMessage;
import com.chua.common.support.ai.agent.AgentResponse;
import com.chua.common.support.ai.persistence.AgentSessionState;
import com.chua.common.support.ai.persistence.AgentSnapshot;

import java.util.List;

/**
 * Agent 会话句柄。
 *
 * @author CH
 * @since 2026/04/03
 */
public interface AgentSession {

    /**
     * 当前会话标识。
     *
     * @return 会话标识
     */
    String getSessionId();

    /**
     * 当前会话使用的模型。
     *
     * @return 模型名称
     */
    String getModel();

    /**
     * 当前会话系统提示词。
     *
     * @return 系统提示词
     */
    String getSystemPrompt();

    /**
     * 当前会话历史。
     *
     * @return 历史消息
     */
    List<AgentMessage> getHistoryMessages();

    /**
     * 切换当前会话模型。
     *
     * @param model 模型名称
     * @return 当前会话
     */
    AgentSession useModel(String model);

    /**
     * 更新当前会话系统提示词。
     *
     * @param systemPrompt 系统提示词
     * @return 当前会话
     */
    AgentSession system(String systemPrompt);

    /**
     * 清空当前会话历史。
     *
     * @return 当前会话
     */
    AgentSession clearHistory();

    /**
     * 返回当前会话状态。
     *
     * @return 会话状态
     */
    AgentSessionState state();

    /**
     * 创建会话快照。
     *
     * @param label       快照标签
     * @param description 快照描述
     * @return 快照对象
     */
    AgentSnapshot snapshot(String label, String description);

    /**
     * 在当前会话执行请求。
     *
     * @param request Agent 请求
     * @return Agent 响应
     */
    AgentResponse execute(AgentRequest request);
}
