package com.chua.starter.ai.support.agent;

import com.chua.common.support.ai.agent.AgentMetadata;
import com.chua.common.support.ai.agent.AgentOptions;
import com.chua.common.support.ai.agent.AgentProvider;
import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.core.annotation.SpiDefault;
import com.chua.starter.ai.support.chat.ChatClient;

import java.util.Map;

/**
 * 默认 Spring Agent SPI 提供者。
 *
 * @author CH
 * @since 2026/04/03
 */
@Spi("default")
@SpiDefault
public class DefaultAgentProvider implements AgentProvider {

    /**
     * ChatClient 属性名。
     */
    public static final String ATTRIBUTE_CHAT_CLIENT = "starter.chatClient";

    /**
     * ChatClient 工厂属性名。
     */
    public static final String ATTRIBUTE_CHAT_FACTORY = "starter.chatFactory";

    /**
     * 返回默认 Agent SPI 的元数据。
     *
     * @return 元数据
     */
    @Override
    public AgentMetadata metadata() {
        return AgentMetadata.builder()
                .name("default")
                .capabilities(java.util.Set.of("session", "snapshot", "permission", "orchestration"))
                .build();
    }

    /**
     * 基于 starter ChatClient 创建默认 Agent。
     *
     * @param options Agent 运行选项
     * @return 默认 Agent
     */
    @Override
    public com.chua.common.support.ai.agent.Agent create(AgentOptions options) {
        Map<String, Object> attributes = options == null ? Map.of() : options.getAttributes();
        Object chatClientObject = attributes.get(ATTRIBUTE_CHAT_CLIENT);
        if (!(chatClientObject instanceof ChatClient chatClient)) {
            throw new IllegalStateException("AgentOptions.attributes 必须包含 starter ChatClient");
        }
        Object factoryValue = attributes.get(ATTRIBUTE_CHAT_FACTORY);
        return new DefaultAgent(chatClient, options, factoryValue == null ? chatClient.getFactory() : String.valueOf(factoryValue));
    }
}
