package com.chua.starter.ai.support.agent;

import com.chua.common.support.ai.agent.AgentContext;
import com.chua.common.support.ai.agent.AgentMessage;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring 层 Agent 请求。
 * <p>
 * 该请求显式支持 session 和 model 覆盖。
 *
 * @author CH
 * @since 2026/04/03
 */
@Getter
@Builder(toBuilder = true)
public class AgentRequest {

    /**
     * 请求标识。
     */
    private final String requestId;

    /**
     * 目标会话标识。
     */
    private final String sessionId;

    /**
     * 当前输入文本。
     */
    private final String input;

    /**
     * 本次执行覆盖模型。
     */
    private final String model;

    /**
     * 本次执行覆盖系统提示词。
     */
    private final String systemPrompt;

    /**
     * 本次执行温度参数。
     */
    private final Double temperature;

    /**
     * 本次执行最大输出 token 数。
     */
    private final Integer maxTokens;

    /**
     * 本次执行超时时间。
     */
    private final Long timeoutMillis;

    /**
     * 本次执行推理强度。
     */
    private final String reasoningEffort;

    /**
     * 本次执行 User-Agent。
     */
    private final String userAgent;

    /**
     * 任务可见性模式。
     */
    private final AgentTaskMode taskMode;

    /**
     * 结构化上下文。
     */
    private final AgentContext context;

    /**
     * 附加消息。
     */
    @Builder.Default
    private final List<AgentMessage> messages = List.of();

    /**
     * 是否启用输入美化。
     */
    @Builder.Default
    private final boolean inputOptimizationEnabled = false;

    /**
     * 是否启用上下文压缩。
     */
    @Builder.Default
    private final boolean contextCompressionEnabled = false;

    /**
     * 扩展属性。
     */
    @Builder.Default
    private final Map<String, Object> attributes = Map.of();

    /**
     * 返回只读消息列表。
     *
     * @return 消息列表
     */
    public List<AgentMessage> getMessages() {
        return messages == null ? List.of() : Collections.unmodifiableList(messages);
    }

    /**
     * 返回只读扩展属性。
     *
     * @return 扩展属性
     */
    public Map<String, Object> getAttributes() {
        return attributes == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
