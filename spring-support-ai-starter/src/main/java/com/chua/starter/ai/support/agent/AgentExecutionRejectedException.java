package com.chua.starter.ai.support.agent;

/**
 * Agent 操作被权限或策略直接拒绝时抛出的异常。
 *
 * @author CH
 * @since 2026/04/03
 */
final class AgentExecutionRejectedException extends IllegalStateException {

    AgentExecutionRejectedException(String message) {
        super(message);
    }
}
