package com.chua.starter.ai.support.agent;

import com.chua.common.support.ai.agent.AgentResponse;
import com.chua.common.support.ai.bigmodel.BigModelMetadataView;
import com.chua.starter.ai.support.chat.ChatClient;

import java.util.List;

/**
 * Spring 层 Agent。
 * <p>
 * Agent 负责 session、snapshot、权限和任务编排。
 *
 * @author CH
 * @since 2026/04/03
 */
public interface Agent extends com.chua.common.support.ai.agent.Agent {

    /**
     * 当前 Agent 对应的 ChatClient 工厂名称。
     *
     * @return 工厂名称
     */
    String getFactory();

    /**
     * 当前 Agent 固定 provider。
     *
     * @return provider 名称
     */
    String getProvider();

    /**
     * 当前 Agent 默认模型。
     *
     * @return 默认模型
     */
    String getDefaultModel();

    /**
     * 返回底层 ChatClient。
     *
     * @return ChatClient
     */
    ChatClient getChatClient();

    /**
     * 返回当前 provider 支持的模型目录。
     *
     * @return 模型目录
     */
    List<BigModelMetadataView> listModels();

    /**
     * 切换 Agent 默认模型。
     *
     * @param model 模型名称
     * @return 当前 Agent
     */
    Agent useModel(String model);

    /**
     * 返回指定 session。
     *
     * @param sessionId session 标识
     * @return session 句柄
     */
    AgentSession session(String sessionId);

    /**
     * 执行 Spring 层 Agent 请求。
     *
     * @param request Agent 请求
     * @return Agent 响应
     */
    AgentResponse execute(com.chua.starter.ai.support.agent.AgentRequest request);

    /**
     * 执行默认 session 请求。
     *
     * @param input 输入文本
     * @return Agent 响应
     */
    default AgentResponse execute(String input) {
        return execute(com.chua.starter.ai.support.agent.AgentRequest.builder().input(input).build());
    }
}
