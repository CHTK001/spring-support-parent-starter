package com.chua.starter.ai.support.agent;

import com.chua.common.support.ai.callback.AgentEventType;

/**
 * Agent 任务可见性模式。
 *
 * @author CH
 * @since 2026/04/03
 */
public enum AgentTaskMode {

    /**
     * 默认模式，只暴露请求级事件。
     */
    DEFAULT,

    /**
     * 任务模式，暴露模型调用、工具调用、usage 等任务级事件。
     */
    TASK,

    /**
     * 调试模式，暴露全部事件。
     */
    DEBUG;

    /**
     * 判断当前模式是否应暴露该事件。
     *
     * @param eventType 事件类型
     * @return 是否暴露
     */
    public boolean exposes(AgentEventType eventType) {
        if (eventType == null || this == DEBUG) {
            return true;
        }
        if (this == TASK) {
            return switch (eventType) {
                case REQUEST_RECEIVED,
                     SESSION_LOADED,
                     SNAPSHOT_CREATED,
                     MODEL_CALL_STARTED,
                     MODEL_CALL_COMPLETED,
                     TOOL_EXECUTION_STARTED,
                     TOOL_EXECUTION_COMPLETED,
                     INPUT_OPTIMIZATION_COMPLETED,
                     CONTEXT_COMPRESSION_COMPLETED,
                     USAGE_RECORDED,
                     REQUEST_COMPLETED,
                     ERROR,
                     PERMISSION_APPROVED,
                     PERMISSION_DENIED,
                     APPROVAL_REQUIRED,
                     POLICY_APPROVED,
                     POLICY_DENIED,
                     RECORD_PERSISTED -> true;
                default -> false;
            };
        }
        return switch (eventType) {
            case REQUEST_RECEIVED,
                 SESSION_LOADED,
                 SNAPSHOT_CREATED,
                 REQUEST_COMPLETED,
                 ERROR,
                 APPROVAL_REQUIRED,
                 PERMISSION_DENIED -> true;
            default -> false;
        };
    }
}
