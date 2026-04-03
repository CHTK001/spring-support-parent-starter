package com.chua.starter.ai.support.agent;

import com.chua.common.support.ai.agent.AgentContext;
import com.chua.common.support.ai.agent.AgentMessage;
import com.chua.common.support.ai.agent.AgentResponse;
import com.chua.common.support.ai.persistence.AgentSessionState;
import com.chua.common.support.ai.persistence.AgentSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认 Agent 会话实现。
 *
 * @author CH
 * @since 2026/04/03
 */
final class DefaultAgentSession implements AgentSession {

    private final DefaultAgent owner;
    private final String sessionId;
    private final List<AgentMessage> historyMessages = new ArrayList<>();
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private volatile String model;
    private volatile String systemPrompt;
    private volatile String snapshotId;

    /**
     * 创建 Agent 会话。
     *
     * @param owner        所属 Agent
     * @param sessionId    会话标识
     * @param model        当前模型
     * @param systemPrompt 当前系统提示词
     */
    DefaultAgentSession(DefaultAgent owner, String sessionId, String model, String systemPrompt) {
        this.owner = owner;
        this.sessionId = sessionId;
        this.model = model;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public String getSystemPrompt() {
        return systemPrompt;
    }

    @Override
    public List<AgentMessage> getHistoryMessages() {
        synchronized (historyMessages) {
            return List.copyOf(historyMessages);
        }
    }

    @Override
    public AgentSession useModel(String model) {
        owner.assertModelSupported(model);
        this.model = model;
        return this;
    }

    @Override
    public AgentSession system(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    @Override
    public AgentSession clearHistory() {
        synchronized (historyMessages) {
            historyMessages.clear();
        }
        return this;
    }

    @Override
    public AgentSessionState state() {
        return AgentSessionState.builder()
                .sessionId(sessionId)
                .context(AgentContext.builder()
                        .sessionId(sessionId)
                        .systemPrompt(systemPrompt)
                        .historyMessages(getHistoryMessages())
                        .attributes(new LinkedHashMap<>(attributes))
                        .build())
                .historyMessages(getHistoryMessages())
                .snapshotId(snapshotId)
                .attributes(new LinkedHashMap<>(attributes))
                .build();
    }

    @Override
    public AgentSnapshot snapshot(String label, String description) {
        return owner.createSnapshot(this, label, description, null);
    }

    @Override
    public AgentResponse execute(AgentRequest request) {
        return owner.execute(request == null
                ? AgentRequest.builder().sessionId(sessionId).build()
                : request.toBuilder().sessionId(sessionId).build());
    }

    /**
     * 追加单条历史消息。
     *
     * @param message 消息内容
     */
    void appendHistory(AgentMessage message) {
        if (message == null) {
            return;
        }
        synchronized (historyMessages) {
            historyMessages.add(message);
        }
    }

    /**
     * 批量追加历史消息。
     *
     * @param messages 消息列表
     */
    void appendHistory(List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        synchronized (historyMessages) {
            historyMessages.addAll(messages);
        }
    }

    /**
     * 使用快照或外部状态覆盖当前会话。
     *
     * @param state 会话状态
     */
    void applyState(AgentSessionState state) {
        if (state == null) {
            return;
        }
        AgentContext context = state.getContext();
        this.systemPrompt = context == null ? null : context.getSystemPrompt();
        this.snapshotId = state.getSnapshotId();
        synchronized (historyMessages) {
            historyMessages.clear();
            historyMessages.addAll(state.getHistoryMessages());
        }
        attributes.clear();
        attributes.putAll(state.getAttributes());
    }

    /**
     * 更新当前会话绑定的最新快照标识。
     *
     * @param snapshotId 快照标识
     */
    void updateSnapshot(String snapshotId) {
        this.snapshotId = snapshotId;
    }
}
