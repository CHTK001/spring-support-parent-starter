package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.agent.AgentUsage;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 聊天响应。
 *
 * @author CH
 * @since 2026/04/03
 */
@Getter
@Builder(toBuilder = true)
public class ChatResponse {

    /**
     * 当前工厂名称。
     */
    private final String factory;

    /**
     * 当前提供商名称。
     */
    private final String provider;

    /**
     * 实际执行模型。
     */
    private final String model;

    /**
     * 文本输出。
     */
    private final String text;

    /**
     * 当前响应的 token / 费用信息。
     */
    private final AgentUsage usage;

    /**
     * 扩展元数据。
     */
    @Builder.Default
    private final Map<String, Object> metadata = Map.of();

    /**
     * 返回只读元数据。
     *
     * @return 元数据
     */
    public Map<String, Object> getMetadata() {
        return metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
