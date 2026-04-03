package com.chua.starter.ai.support.agent;

import com.chua.common.support.ai.agent.AgentUsage;
import com.chua.common.support.ai.bigmodel.BigModelMetadataView;
import com.chua.common.support.ai.bigmodel.BigModelPricing;
import com.chua.starter.ai.support.chat.ChatClient;
import com.chua.starter.ai.support.chat.ChatResponse;
import com.chua.starter.ai.support.chat.ChatScope;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Agent 测试用聊天客户端桩。
 */
final class StubAgentChatClient implements ChatClient {

    private final List<ChatScope> scopes = new ArrayList<>();

    @Override
    public String getFactory() {
        return "default";
    }

    @Override
    public String getProvider() {
        return "mock";
    }

    @Override
    public String getDefaultModel() {
        return "model-a";
    }

    @Override
    public String getEndpoint() {
        return "https://mock.example.com/v1/chat";
    }

    @Override
    public List<BigModelMetadataView> listModels() {
        return List.of(
                new BigModelMetadataView("model-a", "1", true, "mock", "a", false, false, freePricing(), freePricing()),
                new BigModelMetadataView("model-b", "1", true, "mock", "b", false, false, freePricing(), freePricing())
        );
    }

    @Override
    public ChatResponse chat(ChatScope scope) {
        scopes.add(scope);
        return ChatResponse.builder()
                .factory(getFactory())
                .provider(getProvider())
                .model(scope.getModel())
                .text(scope.getModel() + ":" + scope.getInput())
                .usage(AgentUsage.builder()
                        .promptTokens(12)
                        .completionTokens(8)
                        .totalTokens(20)
                        .inputCost(new BigDecimal("0.12"))
                        .outputCost(new BigDecimal("0.08"))
                        .totalCost(new BigDecimal("0.20"))
                        .currency("USD")
                        .estimated(false)
                        .build())
                .metadata(java.util.Map.of(
                        "firstTokenLatencyMillis", 15L,
                        "inputOptimized", false,
                        "contextCompressed", false
                ))
                .build();
    }

    @Override
    public void chat(ChatScope scope, Consumer<String> consumer, Runnable onComplete, Consumer<Throwable> onError) {
        consumer.accept(scope.getModel() + ":" + scope.getInput());
        onComplete.run();
    }

    /**
     * 返回测试期间捕获到的 scope。
     *
     * @return 调用 scope
     */
    List<ChatScope> getScopes() {
        return List.copyOf(scopes);
    }

    private BigModelPricing freePricing() {
        return new BigModelPricing("free", "USD", BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, true);
    }
}
