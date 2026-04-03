package com.chua.starter.ai.support.properties;

import lombok.Data;

/**
 * LLM 配置属性
 *
 * @author CH
 * @since 2025/01/XX
 */
@Data
public class LlmProperties {
    /**
     * ChatClient SPI 工厂名称。
     * <p>
     * 用于在默认实现、langchain4j 实现等不同聊天客户端之间切换。
     */
    private String factory = "default";

    /**
     * 是否启用
     */
    private boolean enabled = true;

    /**
     * 提供商
     */
    private String provider;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 默认系统提示词。
     */
    private String system;

    /**
     * 温度参数
     */
    private Double temperature;

    /**
     * 最大 token 数
     */
    private Integer maxTokens;

    /**
     * 超时时间（毫秒）
     */
    private Integer timeout;

    /**
     * 是否默认启用输入美化。
     */
    private boolean inputOptimizationEnabled;

    /**
     * 是否默认启用上下文压缩。
     */
    private boolean contextCompressionEnabled;

    /**
     * 触发上下文压缩的历史阈值。
     */
    private int contextCompressionThreshold = 12;

    /**
     * 压缩后保留的尾部消息数量。
     */
    private int contextCompressionRetainMessages = 6;

    /**
     * MCP 配置
     */
    private McpProperties mcp = new McpProperties();
}
