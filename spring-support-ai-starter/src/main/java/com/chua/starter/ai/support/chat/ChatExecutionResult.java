package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.agent.AgentUsage;

import java.util.Map;

/**
 * 单次聊天执行结果。
 *
 * @param text     文本输出
 * @param usage    token / 费用信息
 * @param metadata 扩展元数据
 * @author CH
 * @since 2026/04/03
 */
record ChatExecutionResult(String text, AgentUsage usage, Map<String, Object> metadata) {
}
