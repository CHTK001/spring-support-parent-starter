package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeGraphSettings {

    private String provider;
    private String model;
    private Double temperature;
    private Integer maxTokens;
    private String extractionPromptTemplate;
    private Boolean relationExtractionEnabled;
    private Integer maxEntitiesPerDoc;
    private ChatClientSetting baseSetting;

    public static KnowledgeGraphSettingsBuilder builder() {
        return new KnowledgeGraphSettingsBuilder();
    }

    public ChatClientSettings toChatClientSettings() {
        return ChatClientSettings.builder()
                .provider(provider)
                .defaultModel(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .baseSetting(baseSetting)
                .build();
    }

    public static class KnowledgeGraphSettingsBuilder {
        private String provider;
        private String model;
        private Double temperature;
        private Integer maxTokens;
        private String extractionPromptTemplate;
        private Boolean relationExtractionEnabled;
        private Integer maxEntitiesPerDoc;
        private ChatClientSetting baseSetting;

        public KnowledgeGraphSettingsBuilder provider(String provider) { this.provider = provider; return this; }
        public KnowledgeGraphSettingsBuilder model(String model) { this.model = model; return this; }
        public KnowledgeGraphSettingsBuilder temperature(Double temperature) { this.temperature = temperature; return this; }
        public KnowledgeGraphSettingsBuilder maxTokens(Integer maxTokens) { this.maxTokens = maxTokens; return this; }
        public KnowledgeGraphSettingsBuilder extractionPromptTemplate(String extractionPromptTemplate) { this.extractionPromptTemplate = extractionPromptTemplate; return this; }
        public KnowledgeGraphSettingsBuilder relationExtractionEnabled(Boolean relationExtractionEnabled) { this.relationExtractionEnabled = relationExtractionEnabled; return this; }
        public KnowledgeGraphSettingsBuilder maxEntitiesPerDoc(Integer maxEntitiesPerDoc) { this.maxEntitiesPerDoc = maxEntitiesPerDoc; return this; }
        public KnowledgeGraphSettingsBuilder baseSetting(ChatClientSetting baseSetting) { this.baseSetting = baseSetting; return this; }

        public KnowledgeGraphSettings build() {
            return new KnowledgeGraphSettings(provider, model, temperature, maxTokens,
                    extractionPromptTemplate, relationExtractionEnabled, maxEntitiesPerDoc, baseSetting);
        }
    }
}
