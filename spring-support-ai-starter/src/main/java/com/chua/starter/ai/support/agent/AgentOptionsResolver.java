package com.chua.starter.ai.support.agent;

import com.chua.common.support.ai.agent.AgentOptions;
import com.chua.common.support.ai.config.AgentProperties;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.ai.support.chat.ChatClient;
import com.chua.starter.ai.support.properties.AiProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 选项解析器。
 *
 * @author CH
 * @since 2026/04/03
 */
public final class AgentOptionsResolver {

    private AgentOptionsResolver() {
    }

    /**
     * 从 Spring 配置解析 Agent 运行选项。
     *
     * @param properties Spring AI 配置
     * @param chatClient 已创建的聊天客户端
     * @return Agent 运行选项
     */
    public static AgentOptions resolve(AiProperties properties, ChatClient chatClient) {
        AgentProperties agentProperties = properties == null ? new AgentProperties() : properties.getAgent();
        String provider = firstNonBlank(agentProperties.getProvider(), chatClient.getProvider());
        if (!provider.equalsIgnoreCase(chatClient.getProvider())) {
            throw new IllegalStateException("spring.ai.agent.provider 必须与 spring.ai.llm.provider 保持一致，当前实现只支持单 provider Agent");
        }
        Map<String, Object> attributes = new LinkedHashMap<>(agentProperties.getAttributes());
        attributes.put(DefaultAgentProvider.ATTRIBUTE_CHAT_CLIENT, chatClient);
        attributes.put(DefaultAgentProvider.ATTRIBUTE_CHAT_FACTORY, chatClient.getFactory());
        return agentProperties.toOptions().toBuilder()
                .agentType(firstNonBlank(agentProperties.getType(), "default"))
                .provider(provider)
                .model(firstNonBlank(agentProperties.getModel(), chatClient.getDefaultModel()))
                .attributes(attributes)
                .build();
    }

    /**
     * 返回第一个非空白字符串。
     *
     * @param values 候选值列表
     * @return 第一个非空白值
     */
    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
