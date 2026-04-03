package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.bigmodel.BigModelMetadataView;
import com.chua.common.support.ai.bigmodel.BigModelSetting;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultScopeChatClientTest {

    @Test
    void shouldApplyScopeToFreshCommonClient() {
        List<StubCommonChatClient> clients = new CopyOnWriteArrayList<>();
        DefaultScopeChatClient chatClient = new DefaultScopeChatClient(
                settings(),
                (factory, setting) -> {
                    StubCommonChatClient client = new StubCommonChatClient();
                    client.factory = factory;
                    client.mcpEnabled = setting.isMcpEnabled();
                    clients.add(client);
                    return client;
                },
                setting -> new StubCatalogBigModelClient()
        );

        ChatContext context = new ChatContext();
        context.setHistory(List.of(
                ChatMessage.user("history-user"),
                ChatMessage.assistant("history-assistant")
        ));

        ChatResponse response = chatClient.chat(ChatScope.builder()
                .input("hello")
                .model("model-b")
                .systemPrompt("scope-system")
                .context(context)
                .imageUrls(List.of("https://image"))
                .build());

        assertThat(response.getText()).isEqualTo("model-b:hello");
        assertThat(clients).hasSize(1);
        StubCommonChatClient delegate = clients.get(0);
        assertThat(delegate.factory).isEqualTo("default");
        assertThat(delegate.model).isEqualTo("model-b");
        assertThat(delegate.systemPrompt).isEqualTo("scope-system");
        assertThat(delegate.userHistories).containsExactly("history-user");
        assertThat(delegate.assistantHistories).containsExactly("history-assistant");
        assertThat(delegate.images).containsExactly("https://image");
        assertThat(delegate.mcpEnabled).isTrue();
    }

    @Test
    void shouldOptimizeInputAndCompressContextWithCurrentAi() {
        List<StubCommonChatClient> clients = new CopyOnWriteArrayList<>();
        DefaultScopeChatClient chatClient = new DefaultScopeChatClient(
                settings(),
                (factory, setting) -> {
                    StubCommonChatClient client = new StubCommonChatClient();
                    clients.add(client);
                    return client;
                },
                setting -> new StubCatalogBigModelClient()
        );

        ChatContext context = new ChatContext();
        context.setHistory(List.of(
                ChatMessage.user("a"),
                ChatMessage.assistant("b"),
                ChatMessage.user("c")
        ));

        ChatResponse response = chatClient.chat(ChatScope.builder()
                .input("raw input")
                .context(context)
                .inputOptimizationEnabled(true)
                .contextCompressionEnabled(true)
                .contextCompressionThreshold(2)
                .contextCompressionRetainMessages(1)
                .build());

        assertThat(response.getText()).isEqualTo("model-a:optimized input");
        assertThat(clients).hasSize(3);
        StubCommonChatClient finalClient = clients.get(2);
        assertThat(finalClient.userHistories).containsExactly("c");
        assertThat(finalClient.assistantHistories.get(0)).contains("[Compressed Context]");
    }

    @Test
    void shouldListModelsFromProviderCatalog() {
        DefaultScopeChatClient chatClient = new DefaultScopeChatClient(
                settings(),
                (factory, setting) -> new StubCommonChatClient(),
                setting -> new StubCatalogBigModelClient()
        );

        assertThat(chatClient.listModels()).extracting(BigModelMetadataView::name)
                .containsExactly("model-a", "model-b");
    }

    private ChatClientSettings settings() {
        return ChatClientSettings.builder()
                .factory("default")
                .provider("mock")
                .defaultModel("model-a")
                .systemPrompt("system")
                .timeoutMillis(1_000L)
                .baseSetting(BigModelSetting.builder()
                        .provider("mock")
                        .mcpEnabled(true)
                        .build())
                .build();
    }
}
