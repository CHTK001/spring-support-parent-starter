package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.agent.Agent;
import com.chua.common.support.ai.agent.AgentResponse;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ModelDefinition;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Consumer;

/**
 * Agent 对话客户端 —— 将 Agent 运行时包装为 对话客户端。
 *
 * <p>用于将已注册的 Agent 实例暴露为可被对话链路调用的 ChatClient，
 * 注册到 {@link com.chua.starter.ai.support.registry.ChatClientRegistry} 后
 * 即可在模型管理页中选择使用。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class AgentChatClient implements ChatClient {

    /** 默认模型名称 */
    private static final String DEFAULT_MODEL = "agent-router";

    /** 内部 Agent 实例 */
    private final Agent agent;

    /** 模型名称 */
    private String model = DEFAULT_MODEL;

    /**
     * 创建 Agent对话客户端 实例
     *
     * @param agent Agent 运行时实例
     */
    public AgentChatClient(Agent agent) {
        this.agent = agent;
    }

    /**
     * 获取内部 Agent 实例
     * @return 获取Agent的结果
     */
    public Agent getAgent() {
        return agent;
    }

    @Override
    public ChatClient model(String model) {
        this.model = model != null ? model : DEFAULT_MODEL;
        return this;
    }

    @Override
    public String chatSync(String prompt) {
        try {
            AgentResponse response = agent.run(prompt);
            return response != null ? response.getOutput() : "";
        } catch (Exception e) {
            log.error("[AgentChatClient] 调用 Agent 失败: {}", e.getMessage(), e);
            return "Agent 调用失败: " + e.getMessage();
        }
    }

    @Override
    public void chat(String prompt, Consumer<ChatResponse> consumer) {
        chat(prompt, consumer, () -> {}, e -> log.error("[AgentChatClient] chat error: {}", e.getMessage(), e));
    }

    @Override
    public void chat(String prompt, Consumer<ChatResponse> consumer, Runnable onComplete, Consumer<Throwable> onError) {
        try {
            consumer.accept(ChatResponse.builder().state(ChatResponse.State.START).build());
            String output = chatSync(prompt);
            if (output != null && !output.isEmpty()) {
                consumer.accept(ChatResponse.builder()
                        .state(ChatResponse.State.STREAMING)
                        .content(output)
                        .build());
            }
            consumer.accept(ChatResponse.builder()
                    .state(ChatResponse.State.STOP)
                    .usage(null)
                    .build());
            onComplete.run();
        } catch (Exception e) {
            consumer.accept(ChatResponse.builder()
                    .state(ChatResponse.State.ERROR)
                    .errorMessage(e.getMessage())
                    .build());
            onError.accept(e);
        }
    }

    @Override
    public List<ModelDefinition> models() {
        String agentName = agent.getDefinition() != null ? agent.getDefinition().getName() : "unknown";
        return List.of(ModelDefinition.builder()
                .id(model)
                .name("Agent: " + agentName)
                .provider("agent")
                .description("Agent 运行时模型，通过路由自动委派给合适的子 Agent")
                .capabilities(List.of("chat", "tools", "skills", "routing"))
                .build());
    }

    @Override
    public void close() {
        try {
            agent.close();
        } catch (Exception e) {
            log.warn("[AgentChatClient] 关闭 Agent 失败: {}", e.getMessage());
        }
    }
}
