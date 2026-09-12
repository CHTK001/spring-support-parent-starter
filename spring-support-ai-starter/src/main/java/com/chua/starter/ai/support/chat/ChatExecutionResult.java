package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.agent.AgentUsage;

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
}
