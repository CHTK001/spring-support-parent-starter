package com.chua.starter.server.support.config;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.embedding.EmbeddingClient;
import com.chua.common.support.ai.rag.RagClient;
import com.chua.common.support.ai.rag.RagClientSetting;
import com.chua.common.support.ai.rag.RagDocument;
import com.chua.common.support.ai.rag.RagResponse;
import com.chua.starter.ai.support.configuration.RagProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 当 {@code RagAutoConfiguration} 被排除时，提供空实现的 {@link RagClient} 与默认的 {@link RagProperties}，
 * 以满足 {@code RagController} 的依赖注入需求，避免启动失败。
 *
 * @author CH
 * @since 2026/7/31
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class StubRagClientConfig {

    @Bean
    @ConditionalOnMissingBean(RagClient.class)
    public RagClient ragClient() {
        return new RagClient() {
            @Override
            public RagResponse query(String query) {
                return new RagResponse("", Collections.<RagResponse.Source>emptyList(), Map.of());
            }

            @Override
            public RagResponse query(String query, int topK, double similarityThreshold) {
                return new RagResponse("", Collections.<RagResponse.Source>emptyList(), Map.of());
            }

            @Override
            public void queryStream(String query, Consumer<String> consumer) {
            }

            @Override
            public void queryStream(String query, int topK, double similarityThreshold, Consumer<String> consumer) {
            }

            @Override
            public RagDocument uploadDocument(String fileName, byte[] data) {
                return new RagDocument(
                        java.util.UUID.randomUUID().toString(),
                        fileName,
                        "",
                        data == null ? 0 : data.length,
                        0,
                        "FAILED",
                        "RAG 客户端未启用",
                        System.currentTimeMillis(),
                        Map.of()
                );
            }

            @Override
            public boolean deleteDocument(String docId) {
                return false;
            }

            @Override
            public java.util.List<RagDocument> listDocuments(int page, int pageSize) {
                return Collections.emptyList();
            }

            @Override
            public int documentCount() {
                return 0;
            }

            @Override
            public int reindex() {
                return 0;
            }

            @Override
            public String readDocumentContent(String docId) {
                return null;
            }

            @Override
            public RagClientSetting getSetting() {
                return RagClientSetting.builder().build();
            }

            @Override
            public void setChatClient(ChatClient chatClient) {
            }

            @Override
            public void setEmbeddingClient(EmbeddingClient embeddingClient) {
            }
        };
    }
}
