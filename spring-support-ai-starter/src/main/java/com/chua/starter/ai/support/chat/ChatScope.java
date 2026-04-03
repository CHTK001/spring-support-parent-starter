package com.chua.starter.ai.support.chat;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单次聊天请求 scope。
 * <p>
 * scope 只属于一次调用，包含模型、请求参数、上下文和输入优化选项，
 * 不保存 session。
 *
 * @author CH
 * @since 2026/04/03
 */
@Getter
@Builder(toBuilder = true)
public class ChatScope {

    /**
     * 当前输入文本。
     */
    private final String input;

    /**
     * 本次调用使用的模型。
     */
    private final String model;

    /**
     * 本次调用使用的系统提示词。
     */
    private final String systemPrompt;

    /**
     * 本次调用的温度参数。
     */
    private final Double temperature;

    /**
     * 本次调用的最大输出 token 数。
     */
    private final Integer maxTokens;

    /**
     * 本次调用超时时间，单位毫秒。
     */
    private final Long timeoutMillis;

    /**
     * 当前 scope 的上下文。
     */
    private final ChatContext context;

    /**
     * 图片输入地址列表。
     */
    @Builder.Default
    private final List<String> imageUrls = List.of();

    /**
     * 是否启用 MCP。
     */
    @Builder.Default
    private final boolean mcpEnabled = true;

    /**
     * 是否启用输入美化。
     */
    @Builder.Default
    private final boolean inputOptimizationEnabled = false;

    /**
     * 是否启用上下文压缩。
     */
    @Builder.Default
    private final boolean contextCompressionEnabled = false;

    /**
     * 触发上下文压缩的历史消息阈值。
     */
    @Builder.Default
    private final int contextCompressionThreshold = 12;

    /**
     * 压缩后保留的尾部消息数量。
     */
    @Builder.Default
    private final int contextCompressionRetainMessages = 6;

    /**
     * 透传给实现层的请求参数。
     */
    @Builder.Default
    private final Map<String, Object> parameters = Map.of();

    /**
     * 扩展属性。
     */
    @Builder.Default
    private final Map<String, Object> attributes = Map.of();

    /**
     * 读取透传参数。
     *
     * @return 请求参数
     */
    public Map<String, Object> getParameters() {
        return parameters == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    /**
     * 读取扩展属性。
     *
     * @return 扩展属性
     */
    public Map<String, Object> getAttributes() {
        return attributes == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    /**
     * 读取图片输入地址。
     *
     * @return 图片地址列表
     */
    public List<String> getImageUrls() {
        return imageUrls == null ? List.of() : Collections.unmodifiableList(imageUrls);
    }

    /**
     * 创建只包含输入文本的 scope。
     *
     * @param input 输入文本
     * @return 聊天 scope
     */
    public static ChatScope of(String input) {
        return ChatScope.builder().input(input).build();
    }
}
