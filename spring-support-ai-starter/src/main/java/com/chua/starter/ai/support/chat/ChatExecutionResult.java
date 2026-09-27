package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.agent.AgentUsage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对话执行结果。
 *
 * @param text     回复文本
 * @param usage    用量统计
 * @param metadata 扩展元数据
 * @since 4.0.0.42
 * @author CH
 * @return 对话执行结果的结果
 */
public record ChatExecutionResult(String text, AgentUsage usage, Map<String, Object> metadata) {
    /**
     * 规范构造器：对扩展元数据做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变；扩展元数据允许携带 空 值，
     * 故保留 空 语义并采用可空安全包装而非 {@code Map.copyOf}。</p>
     *
     * @param text     回复文本
     * @param usage    用量统计
     * @param metadata 扩展元数据
     */
    public ChatExecutionResult {
        metadata = metadata == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
