package com.chua.starter.ai.support.agent;

/**
 * Agent 操作需要人工审批时抛出的异常。
 *
 * @author CH
 * @since 2026/04/03
 */
final class AgentApprovalRequiredException extends IllegalStateException {

    AgentApprovalRequiredException(String message) {
        super(message);
    }
}
