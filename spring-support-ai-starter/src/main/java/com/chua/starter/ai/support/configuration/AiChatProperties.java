package com.chua.starter.ai.support.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 对话客户端配置属性
 *
 * <p>前缀 {@code spring.ai.chat}，用于声明式注册 OpenAI 兼容协议
 * 的 ChatClient 与模型定义（含能力标签，供前端深度思考/智能搜索开关使用）。</p>
 *
 * <pre>
 * spring.ai.chat:
 *   provider: openai
 *   api-key: sk-xxx
 *   base-url: https://api.openai.com/v1
 *   models:
 *     - id: gpt-4o
 *       capabilities: [chat, vision]
 *     - id: o3-mini
 *       capabilities: [chat, think]
 * </pre>
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "spring.ai.chat")
public class AiChatProperties {

    /**
     * AI 服务商名称（openai/siliconflow/sensetime/github/gitee 等）
     */
    private String provider = "openai";

    /**
     * API Key
     */
    private String apiKey;

    /**
     * API 基础地址（可选，默认用 OpenAI 官方地址）
     */
    private String baseUrl;

    /**
     * 注册的模型列表
     */
    private List<ModelItem> models = new ArrayList<>();

    /**
     * 单个模型定义
     */
    @Data
    public static class ModelItem {

        /**
         * 模型 ID，如 gpt-4o
         */
        private String id;

        /**
         * 模型名称（可选，默认同 id）
         */
        private String name;

        /**
         * 模型描述（可选）
         */
        private String description;

        /**
         * 能力标签：chat / vision / think / search 等
         */
        private List<String> capabilities = new ArrayList<>();
    }
}
