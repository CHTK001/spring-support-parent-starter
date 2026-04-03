package com.chua.starter.ai.support.agent;

import com.chua.common.support.ai.callback.AgentAction;
import com.chua.common.support.ai.callback.AgentActionPhase;
import com.chua.common.support.ai.callback.AgentActionType;
import com.chua.common.support.ai.callback.AgentCallback;
import com.chua.common.support.ai.callback.AgentEvent;
import com.chua.common.support.ai.callback.AgentEventType;
import com.chua.common.support.ai.persistence.AgentExecutionStep;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 单次执行的事件追踪器。
 *
 * @author CH
 * @since 2026/04/03
 */
final class AgentExecutionTrace {

    private final String agentName;
    private final String requestId;
    private final String sessionId;
    private final String provider;
    private final String model;
    private final AgentTaskMode taskMode;
    private final List<AgentCallback> callbacks;
    private final List<AgentExecutionStep> steps = new ArrayList<>();
    private volatile String snapshotId;

    AgentExecutionTrace(String agentName,
                        String requestId,
                        String sessionId,
                        String provider,
                        String model,
                        AgentTaskMode taskMode,
                        List<AgentCallback> callbacks) {
        this.agentName = agentName;
        this.requestId = requestId;
        this.sessionId = sessionId;
        this.provider = provider;
        this.model = model;
        this.taskMode = taskMode == null ? AgentTaskMode.DEFAULT : taskMode;
        this.callbacks = callbacks == null ? List.of() : List.copyOf(callbacks);
    }

    /**
     * 更新当前请求关联的快照标识。
     *
     * @param snapshotId 快照标识
     */
    void snapshot(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    /**
     * 记录并分发一次事件。
     *
     * @param eventType   事件类型
     * @param actionType  动作类型
     * @param actionPhase 动作阶段
     * @param name        动作名称
     * @param target      目标对象
     * @param message     事件消息
     * @param success     是否成功
     * @param input       输入数据
     * @param output      输出数据
     * @param metadata    扩展元数据
     */
    void event(AgentEventType eventType,
               AgentActionType actionType,
               AgentActionPhase actionPhase,
               String name,
               String target,
               String message,
               boolean success,
               Map<String, Object> input,
               Map<String, Object> output,
               Map<String, Object> metadata) {
        if (!taskMode.exposes(eventType)) {
            return;
        }
        Map<String, Object> safeMetadata = metadata == null ? Map.of() : new LinkedHashMap<>(metadata);
        AgentAction action = AgentAction.builder()
                .actionId(UUID.randomUUID().toString())
                .type(actionType)
                .phase(actionPhase)
                .name(name)
                .target(target)
                .summary(message)
                .success(success)
                .timestamp(System.currentTimeMillis())
                .input(input == null ? Map.of() : new LinkedHashMap<>(input))
                .output(output == null ? Map.of() : new LinkedHashMap<>(output))
                .metadata(safeMetadata)
                .build();
        AgentEvent event = AgentEvent.builder()
                .type(eventType)
                .action(action)
                .agentName(agentName)
                .requestId(requestId)
                .sessionId(sessionId)
                .snapshotId(snapshotId)
                .provider(provider)
                .model(model)
                .message(message)
                .success(success)
                .timestamp(System.currentTimeMillis())
                .metadata(safeMetadata)
                .build();
        steps.add(AgentExecutionStep.builder()
                .stepId(UUID.randomUUID().toString())
                .requestId(requestId)
                .eventType(eventType)
                .actionType(actionType)
                .actionPhase(actionPhase)
                .name(name)
                .target(target)
                .message(message)
                .success(success)
                .timestamp(event.getTimestamp())
                .input(action.getInput())
                .output(action.getOutput())
                .metadata(safeMetadata)
                .build());
        for (AgentCallback callback : callbacks) {
            if (callback == null) {
                continue;
            }
            try {
                callback.onEvent(event);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 返回当前请求已记录的可见步骤。
     *
     * @return 步骤列表
     */
    List<AgentExecutionStep> steps() {
        return List.copyOf(steps);
    }
}
