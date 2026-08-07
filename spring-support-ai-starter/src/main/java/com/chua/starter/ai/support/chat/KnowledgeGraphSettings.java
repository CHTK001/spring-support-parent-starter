package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatClientSetting;
import lombok.Builder;
import lombok.Data;

/**
 * 知识图谱 LLM 设置。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Builder
public class KnowledgeGraphSettings {

    /**
     * 厂商提供者
     */
    private String provider;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 采样温度
     */
    private Double temperature;

    /**
     * 最大生成 tokens
     */
    private Integer maxTokens;

    /**
     * 关系抽取提示词模板
     */
    private String extractionPromptTemplate;

    /**
     * 是否启用关系抽取
     */
    private Boolean relationExtractionEnabled;

    /**
     * 单文档最大实体数
     */
    private Integer maxEntitiesPerDoc;

    /**
     * 基础连接设置
     */
    private ChatClientSetting baseSetting;

    /**
     * 创建 Builder。
     *
     * @return Builder 实例
     */
    public static KnowledgeGraphSettingsBuilder builder() {
        return new KnowledgeGraphSettingsBuilder();
    }

    /**
     * 转换为 ChatClient 创建设置。
     *
     * @return ChatClientSettings 实例
     */
    public ChatClientSettings toChatClientSettings() {
        return ChatClientSettings.builder()
                .provider(provider)
                .defaultModel(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .baseSetting(baseSetting)
                .build();
    }

    /**
     * KnowledgeGraphSettings Builder。
     *
     * @author CH
     * @since 4.0.0.42
     */
    public static class KnowledgeGraphSettingsBuilder {

        /**
         * 厂商提供者
         */
        private String provider;

        /**
         * 模型名称
         */
        private String model;

        /**
         * 采样温度
         */
        private Double temperature;

        /**
         * 最大生成 tokens
         */
        private Integer maxTokens;

        /**
         * 关系抽取提示词模板
         */
        private String extractionPromptTemplate;

        /**
         * 是否启用关系抽取
         */
        private Boolean relationExtractionEnabled;

        /**
         * 单文档最大实体数
         */
        private Integer maxEntitiesPerDoc;

        /**
         * 基础连接设置
         */
        private ChatClientSetting baseSetting;

        /**
         * 设置厂商提供者。
         *
         * @param provider 厂商提供者
         * @return 当前 Builder
         */
        public KnowledgeGraphSettingsBuilder provider(String provider) {
            this.provider = provider;
            return this;
        }

        /**
         * 设置模型名称。
         *
         * @param model 模型名称
         * @return 当前 Builder
         */
        public KnowledgeGraphSettingsBuilder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * 设置采样温度。
         *
         * @param temperature 采样温度
         * @return 当前 Builder
         */
        public KnowledgeGraphSettingsBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * 设置最大生成 tokens。
         *
         * @param maxTokens 最大生成 tokens
         * @return 当前 Builder
         */
        public KnowledgeGraphSettingsBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * 设置关系抽取提示词模板。
         *
         * @param extractionPromptTemplate 关系抽取提示词模板
         * @return 当前 Builder
         */
        public KnowledgeGraphSettingsBuilder extractionPromptTemplate(String extractionPromptTemplate) {
            this.extractionPromptTemplate = extractionPromptTemplate;
            return this;
        }

        /**
         * 设置是否启用关系抽取。
         *
         * @param relationExtractionEnabled 是否启用关系抽取
         * @return 当前 Builder
         */
        public KnowledgeGraphSettingsBuilder relationExtractionEnabled(Boolean relationExtractionEnabled) {
            this.relationExtractionEnabled = relationExtractionEnabled;
            return this;
        }

        /**
         * 设置单文档最大实体数。
         *
         * @param maxEntitiesPerDoc 单文档最大实体数
         * @return 当前 Builder
         */
        public KnowledgeGraphSettingsBuilder maxEntitiesPerDoc(Integer maxEntitiesPerDoc) {
            this.maxEntitiesPerDoc = maxEntitiesPerDoc;
            return this;
        }

        /**
         * 设置基础连接设置。
         *
         * @param baseSetting 基础连接设置
         * @return 当前 Builder
         */
        public KnowledgeGraphSettingsBuilder baseSetting(ChatClientSetting baseSetting) {
            this.baseSetting = baseSetting;
            return this;
        }

        /**
         * 构建 KnowledgeGraphSettings 实例。
         *
         * @return KnowledgeGraphSettings 实例
         */
        public KnowledgeGraphSettings build() {
            return new KnowledgeGraphSettings(provider, model, temperature, maxTokens,
                    extractionPromptTemplate, relationExtractionEnabled, maxEntitiesPerDoc, baseSetting);
        }
    }
}
