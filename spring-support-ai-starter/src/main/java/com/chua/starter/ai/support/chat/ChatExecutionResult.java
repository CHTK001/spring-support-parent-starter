package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.agent.AgentUsage;

import java.util.Map;

public record ChatExecutionResult(String text, AgentUsage usage, Map<String, Object> metadata) {
}
