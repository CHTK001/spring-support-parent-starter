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
     * MCP 配置
     */
    private McpProperties mcp = new McpProperties();
}
