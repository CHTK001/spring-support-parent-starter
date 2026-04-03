package com.chua.starter.ai.support.agent;

import com.chua.common.support.ai.agent.AgentContext;
import com.chua.common.support.ai.agent.AgentMessage;
import com.chua.common.support.ai.agent.AgentMetadata;
import com.chua.common.support.ai.agent.AgentOptions;
import com.chua.common.support.ai.agent.AgentResponse;
import com.chua.common.support.ai.agent.AgentRuntimeState;
import com.chua.common.support.ai.agent.AgentRuntimeStatus;
import com.chua.common.support.ai.agent.AgentStreamChunk;
import com.chua.common.support.ai.agent.AgentUsage;
import com.chua.common.support.ai.bigmodel.BigModelMetadataView;
import com.chua.common.support.ai.bigmodel.BigModelPricing;
import com.chua.common.support.ai.callback.AgentActionType;
import com.chua.common.support.ai.callback.AgentCallback;
import com.chua.common.support.ai.callback.AgentCallbacks;
import com.chua.common.support.ai.callback.AgentEventType;
import com.chua.common.support.ai.persistence.AgentExecutionRecord;
import com.chua.common.support.ai.persistence.AgentForkRequest;
import com.chua.common.support.ai.persistence.AgentReplayRequest;
import com.chua.common.support.ai.persistence.AgentRestoreRequest;
import com.chua.common.support.ai.persistence.AgentSessionState;
import com.chua.common.support.ai.persistence.AgentSnapshot;
import com.chua.common.support.ai.policy.AgentPolicies;
import com.chua.common.support.ai.policy.AgentPolicy;
import com.chua.common.support.ai.policy.AgentPolicyContext;
import com.chua.common.support.ai.policy.AgentPolicyDecision;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.ai.support.agent.telemetry.AgentCostMetrics;
import com.chua.starter.ai.support.agent.telemetry.AgentRequestStorage;
import com.chua.starter.ai.support.agent.telemetry.AgentRequestTelemetry;
import com.chua.starter.ai.support.agent.telemetry.AgentTokenMetrics;
import com.chua.starter.ai.support.chat.ChatClient;
import com.chua.starter.ai.support.chat.ChatContext;
import com.chua.starter.ai.support.chat.ChatMessage;
import com.chua.starter.ai.support.chat.ChatResponse;
import com.chua.starter.ai.support.chat.ChatScope;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 默认 Spring Agent 实现。
 *
 * @author CH
 * @since 2026/04/03
 */
public class DefaultAgent implements Agent {

    private static final String DEFAULT_SESSION_ID = "default";

    private final ChatClient chatClient;
    private final AgentOptions options;
    private final String factory;
    private final String provider;
    private final AgentMetadata metadata;
    private final AgentPermissionEvaluator permissionEvaluator;
    private final List<AgentPolicy> policies;
    private final List<AgentCallback> callbacks;
    private final List<AgentRequestStorage> requestStorages;
    private final Map<String, DefaultAgentSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, List<AgentSnapshot>> snapshots = new ConcurrentHashMap<>();
    private final Map<String, AgentRuntimeState> runtimeStates = new ConcurrentHashMap<>();
    private final Map<String, AgentExecutionRecord> records = new ConcurrentHashMap<>();
    private volatile String defaultModel;

    /**
     * 创建默认 Agent。
     *
     * @param chatClient 底层聊天客户端
     * @param options    Agent 运行选项
     * @param factory    ChatClient 工厂名称
     */
    public DefaultAgent(ChatClient chatClient, AgentOptions options, String factory) {
        this.chatClient = chatClient;
        this.options = options;
        this.factory = StringUtils.defaultString(factory, "default");
        this.provider = StringUtils.defaultString(options.getProvider(), chatClient.getProvider());
        this.defaultModel = StringUtils.defaultString(options.getModel(), chatClient.getDefaultModel());
        this.permissionEvaluator = new AgentPermissionEvaluator(options.getPermissions());
        this.policies = new CopyOnWriteArrayList<>(AgentPolicies.find(options.getPolicyNames()));
        this.callbacks = resolveCallbacks(options);
        this.requestStorages = resolveStorages();
        this.metadata = AgentMetadata.builder()
                .name(this.factory)
                .aliases(java.util.Set.of("spring-agent", this.provider))
                .capabilities(java.util.Set.of("session", "snapshot", "permission", "scope-chat"))
                .attributes(Map.of("provider", this.provider, "factory", this.factory))
                .build();
        session(DEFAULT_SESSION_ID);
    }

    @Override
    public String getFactory() {
        return factory;
    }

    @Override
    public String getProvider() {
        return provider;
    }

    @Override
    public String getDefaultModel() {
        return defaultModel;
    }

    @Override
    public ChatClient getChatClient() {
        return chatClient;
    }

    @Override
    public AgentMetadata metadata() {
        return metadata;
    }

    @Override
    public List<BigModelMetadataView> listModels() {
        return chatClient.listModels();
    }

    @Override
    public Agent useModel(String model) {
        assertModelSupported(model);
        evaluateOrThrow(permissionEvaluator.evaluateAction("MODEL_SWITCH"), "MODEL_SWITCH");
        this.defaultModel = model;
        return this;
    }

    @Override
    public AgentSession session(String sessionId) {
        String actualSessionId = StringUtils.defaultString(sessionId, DEFAULT_SESSION_ID);
        return sessions.computeIfAbsent(actualSessionId, key -> new DefaultAgentSession(this, key, defaultModel, null));
    }

    @Override
    public AgentResponse execute(com.chua.starter.ai.support.agent.AgentRequest request) {
        com.chua.starter.ai.support.agent.AgentRequest effective = request == null
                ? com.chua.starter.ai.support.agent.AgentRequest.builder().build()
                : request;
        DefaultAgentSession session = (DefaultAgentSession) session(effective.getSessionId());
        String requestId = StringUtils.isBlank(effective.getRequestId()) ? UUID.randomUUID().toString() : effective.getRequestId();
        String model = resolveModel(effective, session);
        AgentTaskMode taskMode = resolveTaskMode(effective);
        long startedAt = System.currentTimeMillis();
        AgentExecutionTrace trace = new AgentExecutionTrace(
                metadata.getName(),
                requestId,
                session.getSessionId(),
                provider,
                model,
                taskMode,
                callbacks
        );
        trace.event(AgentEventType.REQUEST_RECEIVED, AgentActionType.REQUEST, com.chua.common.support.ai.callback.AgentActionPhase.STARTED,
                "REQUEST", provider, "request received", true, requestInput(effective), Map.of(), requestMetadata(taskMode, effective));
        updateState(requestId, session.getSessionId(), AgentRuntimeStatus.CREATED, "created");
        try {
            assertModelSupported(model);
            applyDecision(trace, permissionEvaluator.evaluateAction("REQUEST"), "REQUEST", AgentActionType.PERMISSION);
            applyDecision(trace, permissionEvaluator.evaluateMcp(isMcpEnabled(effective)), "MCP", AgentActionType.PERMISSION);
            trace.event(AgentEventType.SESSION_LOADED, AgentActionType.SESSION, com.chua.common.support.ai.callback.AgentActionPhase.COMPLETED,
                    "SESSION", session.getSessionId(), "session loaded", true, Map.of(), Map.of("sessionId", session.getSessionId()), Map.of());
            evaluatePolicies(requestId, effective, session, AgentActionType.REQUEST, trace);
            updateState(requestId, session.getSessionId(), AgentRuntimeStatus.RUNNING, "running");
            trace.event(AgentEventType.MODEL_CALL_STARTED, AgentActionType.MODEL_CALL, com.chua.common.support.ai.callback.AgentActionPhase.STARTED,
                    "MODEL_CALL", model, "model call started", true, Map.of("model", model), Map.of(), Map.of());
            ChatResponse response = chatClient.chat(buildScope(effective, session, model));
            emitChatResponseEvents(trace, response, model);
            AgentResponse agentResponse = buildSuccessResponse(requestId, model, response, updateHistory(session, effective, response.getText()), taskMode);
            if (options.isSnapshotEnabled()) {
                AgentSnapshot snapshot = createSnapshot(session, "auto", "auto snapshot after request", requestId);
                trace.snapshot(snapshot.getSnapshotId());
                trace.event(AgentEventType.SNAPSHOT_CREATED, AgentActionType.SNAPSHOT, com.chua.common.support.ai.callback.AgentActionPhase.COMPLETED,
                        "SNAPSHOT", snapshot.getSnapshotId(), "snapshot created", true, Map.of(), Map.of("snapshotId", snapshot.getSnapshotId()), snapshot.getMetadata());
            }
            trace.event(AgentEventType.REQUEST_COMPLETED, AgentActionType.REQUEST, com.chua.common.support.ai.callback.AgentActionPhase.COMPLETED,
                    "REQUEST", provider, "request completed", true, requestInput(effective), responseOutput(agentResponse),
                    requestCompletedMetadata(taskMode, response, startedAt, null));
            recordExecution(session, effective, agentResponse, trace);
            updateState(requestId, session.getSessionId(), AgentRuntimeStatus.COMPLETED, "completed");
            storeTelemetry(buildTelemetry(requestId, session, effective, "REQUEST", model, response.getUsage(), response.getMetadata(), startedAt, null, true, null));
            return agentResponse;
        } catch (AgentApprovalRequiredException ex) {
            updateState(requestId, session.getSessionId(), AgentRuntimeStatus.AWAITING_APPROVAL, ex.getMessage());
            trace.event(AgentEventType.APPROVAL_REQUIRED, AgentActionType.PERMISSION, com.chua.common.support.ai.callback.AgentActionPhase.FAILED,
                    "APPROVAL", provider, ex.getMessage(), false, requestInput(effective), Map.of(), requestMetadata(taskMode, effective));
            storeTelemetry(buildTelemetry(requestId, session, effective, "REQUEST", model, null, Map.of(), startedAt, null, false, ex.getMessage()));
        } catch (Exception ex) {
            updateState(requestId, session.getSessionId(), AgentRuntimeStatus.FAILED, ex.getMessage());
            trace.event(AgentEventType.ERROR, AgentActionType.REQUEST, com.chua.common.support.ai.callback.AgentActionPhase.FAILED,
                    "ERROR", provider, ex.getMessage(), false, requestInput(effective), Map.of(), requestMetadata(taskMode, effective));
            storeTelemetry(buildTelemetry(requestId, session, effective, "REQUEST", model, null, Map.of(), startedAt, null, false, ex.getMessage()));
        }
        return failureResponse(requestId, model, runtimeStates.get(requestId));
    }

    @Override
    public AgentResponse execute(com.chua.common.support.ai.agent.AgentRequest request) {
        return execute(AgentRequestAdapter.from(request));
    }

    @Override
    public void executeStream(com.chua.common.support.ai.agent.AgentRequest request, Consumer<AgentStreamChunk> consumer) {
        executeStream(AgentRequestAdapter.from(request), consumer);
    }

    /**
     * 以流式方式执行 Spring 层 Agent 请求。
     *
     * @param request  Spring 层请求
     * @param consumer 分片消费者
     */
    public void executeStream(com.chua.starter.ai.support.agent.AgentRequest request, Consumer<AgentStreamChunk> consumer) {
        com.chua.starter.ai.support.agent.AgentRequest effective = request == null
                ? com.chua.starter.ai.support.agent.AgentRequest.builder().build()
                : request;
        DefaultAgentSession session = (DefaultAgentSession) session(effective.getSessionId());
        String requestId = StringUtils.isBlank(effective.getRequestId()) ? UUID.randomUUID().toString() : effective.getRequestId();
        String model = resolveModel(effective, session);
        AgentTaskMode taskMode = resolveTaskMode(effective);
        long startedAt = System.currentTimeMillis();
        StringBuilder buffer = new StringBuilder();
        long[] firstTokenLatencyMillis = new long[]{-1L};
        AgentExecutionTrace trace = new AgentExecutionTrace(
                metadata.getName(),
                requestId,
                session.getSessionId(),
                provider,
                model,
                taskMode,
                callbacks
        );
        trace.event(AgentEventType.REQUEST_RECEIVED, AgentActionType.STREAM, com.chua.common.support.ai.callback.AgentActionPhase.STARTED,
                "STREAM", provider, "stream request received", true, requestInput(effective), Map.of(), requestMetadata(taskMode, effective));
        updateState(requestId, session.getSessionId(), AgentRuntimeStatus.CREATED, "created");
        try {
            assertModelSupported(model);
            applyDecision(trace, permissionEvaluator.evaluateAction("REQUEST"), "REQUEST", AgentActionType.PERMISSION);
            applyDecision(trace, permissionEvaluator.evaluateMcp(isMcpEnabled(effective)), "MCP", AgentActionType.PERMISSION);
            trace.event(AgentEventType.SESSION_LOADED, AgentActionType.SESSION, com.chua.common.support.ai.callback.AgentActionPhase.COMPLETED,
                    "SESSION", session.getSessionId(), "session loaded", true, Map.of(), Map.of("sessionId", session.getSessionId()), Map.of());
            evaluatePolicies(requestId, effective, session, AgentActionType.STREAM, trace);
            updateState(requestId, session.getSessionId(), AgentRuntimeStatus.RUNNING, "streaming");
            trace.event(AgentEventType.MODEL_CALL_STARTED, AgentActionType.MODEL_CALL, com.chua.common.support.ai.callback.AgentActionPhase.STARTED,
                    "MODEL_CALL", model, "stream model call started", true, Map.of("model", model), Map.of(), Map.of());
            chatClient.chat(buildScope(effective, session, model), chunk -> {
                if (firstTokenLatencyMillis[0] < 0 && StringUtils.isNotBlank(chunk)) {
                    firstTokenLatencyMillis[0] = System.currentTimeMillis() - startedAt;
                }
                buffer.append(chunk);
                trace.event(AgentEventType.STREAM_CHUNK, AgentActionType.STREAM, com.chua.common.support.ai.callback.AgentActionPhase.PROGRESS,
                        "STREAM_CHUNK", model, "stream chunk", true, Map.of(), Map.of("chunk", chunk), Map.of());
                consumer.accept(AgentStreamChunk.builder()
                        .requestId(requestId)
                        .content(chunk)
                        .done(false)
                        .build());
            }, () -> {
                AgentResponse response = AgentResponse.builder()
                        .requestId(requestId)
                        .success(true)
                        .provider(provider)
                        .model(model)
                        .text(buffer.toString())
                        .messages(updateHistory(session, effective, buffer.toString()))
                        .metadata(new LinkedHashMap<>(requestMetadata(taskMode, effective)))
                        .build();
                trace.event(AgentEventType.MODEL_CALL_COMPLETED, AgentActionType.MODEL_CALL, com.chua.common.support.ai.callback.AgentActionPhase.COMPLETED,
                        "MODEL_CALL", model, "stream model call completed", true, Map.of("model", model), responseOutput(response),
                        metadataOf("firstTokenLatencyMillis", firstTokenLatencyMillis[0] < 0 ? null : firstTokenLatencyMillis[0]));
                if (options.isSnapshotEnabled()) {
                    AgentSnapshot snapshot = createSnapshot(session, "auto", "auto snapshot after stream", requestId);
                    trace.snapshot(snapshot.getSnapshotId());
                    trace.event(AgentEventType.SNAPSHOT_CREATED, AgentActionType.SNAPSHOT, com.chua.common.support.ai.callback.AgentActionPhase.COMPLETED,
                            "SNAPSHOT", snapshot.getSnapshotId(), "snapshot created", true, Map.of(), Map.of("snapshotId", snapshot.getSnapshotId()), snapshot.getMetadata());
                }
                trace.event(AgentEventType.REQUEST_COMPLETED, AgentActionType.STREAM, com.chua.common.support.ai.callback.AgentActionPhase.COMPLETED,
                        "STREAM", provider, "stream request completed", true, requestInput(effective), responseOutput(response),
                        metadataOf("taskMode", taskMode.name(), "firstTokenLatencyMillis", firstTokenLatencyMillis[0] < 0 ? null : firstTokenLatencyMillis[0], "durationMillis", System.currentTimeMillis() - startedAt));
                recordExecution(session, effective, response, trace);
                updateState(requestId, session.getSessionId(), AgentRuntimeStatus.COMPLETED, "completed");
                storeTelemetry(buildTelemetry(requestId, session, effective, "STREAM", model, null,
                        metadataOf("firstTokenLatencyMillis", firstTokenLatencyMillis[0] < 0 ? null : firstTokenLatencyMillis[0]),
                        startedAt, firstTokenLatencyMillis[0] < 0 ? null : firstTokenLatencyMillis[0], true, null));
                consumer.accept(AgentStreamChunk.builder()
                        .requestId(requestId)
                        .content("")
                        .done(true)
                        .response(response)
                        .build());
            }, throwable -> {
                updateState(requestId, session.getSessionId(), AgentRuntimeStatus.FAILED, throwable.getMessage());
                trace.event(AgentEventType.ERROR, AgentActionType.STREAM, com.chua.common.support.ai.callback.AgentActionPhase.FAILED,
                        "ERROR", provider, throwable.getMessage(), false, requestInput(effective), Map.of(), requestMetadata(taskMode, effective));
                storeTelemetry(buildTelemetry(requestId, session, effective, "STREAM", model, null,
                        metadataOf("firstTokenLatencyMillis", firstTokenLatencyMillis[0] < 0 ? null : firstTokenLatencyMillis[0]),
                        startedAt, firstTokenLatencyMillis[0] < 0 ? null : firstTokenLatencyMillis[0], false, throwable.getMessage()));
                publishStreamFailure(consumer, requestId, throwable.getMessage());
            });
        } catch (AgentApprovalRequiredException ex) {
            updateState(requestId, session.getSessionId(), AgentRuntimeStatus.AWAITING_APPROVAL, ex.getMessage());
            trace.event(AgentEventType.APPROVAL_REQUIRED, AgentActionType.PERMISSION, com.chua.common.support.ai.callback.AgentActionPhase.FAILED,
                    "APPROVAL", provider, ex.getMessage(), false, requestInput(effective), Map.of(), requestMetadata(taskMode, effective));
            storeTelemetry(buildTelemetry(requestId, session, effective, "STREAM", model, null, Map.of(), startedAt, null, false, ex.getMessage()));
            publishStreamFailure(consumer, requestId, ex.getMessage());
        } catch (Exception ex) {
            updateState(requestId, session.getSessionId(), AgentRuntimeStatus.FAILED, ex.getMessage());
            trace.event(AgentEventType.ERROR, AgentActionType.STREAM, com.chua.common.support.ai.callback.AgentActionPhase.FAILED,
                    "ERROR", provider, ex.getMessage(), false, requestInput(effective), Map.of(), requestMetadata(taskMode, effective));
            storeTelemetry(buildTelemetry(requestId, session, effective, "STREAM", model, null, Map.of(), startedAt, null, false, ex.getMessage()));
            publishStreamFailure(consumer, requestId, ex.getMessage());
        }
    }

    @Override
    public List<AgentSnapshot> snapshots(String sessionId) {
        return List.copyOf(snapshots.getOrDefault(StringUtils.defaultString(sessionId, DEFAULT_SESSION_ID), List.of()));
    }

    @Override
    public AgentSessionState restore(AgentRestoreRequest request) {
        if (request == null || StringUtils.isBlank(request.getSessionId())) {
            throw new IllegalArgumentException("restore 请求必须包含 sessionId");
        }
        evaluateOrThrow(permissionEvaluator.evaluateAction("RESTORE"), "RESTORE");
        DefaultAgentSession session = (DefaultAgentSession) session(request.getSessionId());
        AgentSnapshot snapshot = findSnapshot(request.getSessionId(), request.getSnapshotId(), request.getRestoreAt());
        if (snapshot == null || snapshot.getState() == null) {
            throw new IllegalStateException("未找到可恢复的快照");
        }
        session.applyState(snapshot.getState());
        session.updateSnapshot(snapshot.getSnapshotId());
        return session.state();
    }

    @Override
    public AgentSessionState fork(AgentForkRequest request) {
        if (request == null || StringUtils.isBlank(request.getSourceSessionId()) || StringUtils.isBlank(request.getTargetSessionId())) {
            throw new IllegalArgumentException("fork 请求必须包含 sourceSessionId 和 targetSessionId");
        }
        evaluateOrThrow(permissionEvaluator.evaluateAction("FORK"), "FORK");
        DefaultAgentSession source = (DefaultAgentSession) session(request.getSourceSessionId());
        DefaultAgentSession target = (DefaultAgentSession) session(request.getTargetSessionId());
        AgentSnapshot snapshot = request.getSnapshotId() == null ? null
                : findSnapshot(request.getSourceSessionId(), request.getSnapshotId(), request.getRestoreAt());
        AgentSessionState state = snapshot != null && snapshot.getState() != null ? snapshot.getState() : source.state();
        target.useModel(source.getModel());
        target.system(source.getSystemPrompt());
        target.applyState(state.toBuilder()
                .sessionId(request.getTargetSessionId())
                .historyMessages(request.isCopyHistory() ? state.getHistoryMessages() : List.of())
                .build());
        if (request.isCreateSnapshot()) {
            createSnapshot(target, "fork", "forked from " + request.getSourceSessionId(), null);
        }
        return target.state();
    }

    @Override
    public List<AgentExecutionRecord> records(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return List.copyOf(records.values());
        }
        return records.values().stream()
                .filter(record -> sessionId.equals(record.getSessionId()))
                .toList();
    }

    @Override
    public AgentResponse replay(AgentReplayRequest request) {
        if (request == null || StringUtils.isBlank(request.getRecordId())) {
            throw new IllegalArgumentException("replay 请求必须包含 recordId");
        }
        AgentExecutionRecord record = records.get(request.getRecordId());
        if (record == null) {
            throw new IllegalStateException("未找到要回放的执行记录");
        }
        if (request.isRestoreSessionState() && StringUtils.isNotBlank(record.getSnapshotId())) {
            restore(AgentRestoreRequest.builder()
                    .sessionId(record.getSessionId())
                    .snapshotId(record.getSnapshotId())
                    .build());
        }
        return execute(com.chua.starter.ai.support.agent.AgentRequest.builder()
                .sessionId(record.getSessionId())
                .input(record.getInput())
                .model(record.getModel())
                .build());
    }

    @Override
    public AgentRuntimeState state(String requestId) {
        return runtimeStates.get(requestId);
    }

    @Override
    public List<AgentRuntimeState> activeStates() {
        return runtimeStates.values().stream().filter(state -> state != null && !state.isTerminal()).toList();
    }

    @Override
    public AgentRuntimeState pause(String requestId) {
        return updateRuntimeStatus(requestId, AgentRuntimeStatus.PAUSED, "paused");
    }

    @Override
    public AgentRuntimeState resume(String requestId) {
        return updateRuntimeStatus(requestId, AgentRuntimeStatus.RUNNING, "resumed");
    }

    @Override
    public AgentRuntimeState cancel(String requestId) {
        return updateRuntimeStatus(requestId, AgentRuntimeStatus.CANCELLED, "cancelled");
    }

    @Override
    public AgentRuntimeState approve(String requestId) {
        return updateRuntimeStatus(requestId, AgentRuntimeStatus.RUNNING, "approved");
    }

    @Override
    public AgentRuntimeState reject(String requestId, String reason) {
        return updateRuntimeStatus(requestId, AgentRuntimeStatus.FAILED, StringUtils.defaultString(reason, "rejected"));
    }

    @Override
    public void close() {
        chatClient.close();
    }

    /**
     * 为当前会话创建快照并回写最新快照标识。
     *
     * @param session     会话
     * @param label       快照标签
     * @param description 快照描述
     * @param requestId   关联请求标识
     * @return 新创建的快照
     */
    AgentSnapshot createSnapshot(DefaultAgentSession session, String label, String description, String requestId) {
        AgentSnapshot snapshot = AgentSnapshot.builder()
                .snapshotId(UUID.randomUUID().toString())
                .sessionId(session.getSessionId())
                .requestId(requestId)
                .label(label)
                .description(description)
                .state(session.state())
                .metadata(Map.of("provider", provider, "model", session.getModel()))
                .build();
        snapshots.computeIfAbsent(session.getSessionId(), key -> new CopyOnWriteArrayList<>()).add(snapshot);
        session.updateSnapshot(snapshot.getSnapshotId());
        return snapshot;
    }

    /**
     * 校验模型是否属于当前固定 provider 的模型目录。
     *
     * @param model 模型名称
     */
    void assertModelSupported(String model) {
        if (StringUtils.isBlank(model)) {
            return;
        }
        List<BigModelMetadataView> models = listModels();
        if (models.isEmpty()) {
            return;
        }
        boolean supported = models.stream().anyMatch(item -> item != null && model.equalsIgnoreCase(item.name()));
        if (!supported) {
            throw new IllegalArgumentException("模型不属于当前 provider: " + model);
        }
    }

    /**
     * 将 Agent 请求压平为一次 ChatClient scope 调用。
     *
     * @param request 当前请求
     * @param session 当前会话
     * @param model   实际执行模型
     * @return scope 聊天参数
     */
    private ChatScope buildScope(com.chua.starter.ai.support.agent.AgentRequest request, DefaultAgentSession session, String model) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(request.getReasoningEffort())) {
            parameters.put("reasoningEffort", request.getReasoningEffort());
        }
        if (StringUtils.isNotBlank(request.getUserAgent())) {
            parameters.put("userAgent", request.getUserAgent());
        }
        if (request.getTaskMode() != null) {
            parameters.put("taskMode", request.getTaskMode().name());
        }
        return ChatScope.builder()
                .input(request.getInput())
                .model(model)
                .systemPrompt(StringUtils.defaultString(request.getSystemPrompt(), session.getSystemPrompt()))
                .temperature(request.getTemperature() == null ? options.getTemperature() : request.getTemperature())
                .maxTokens(request.getMaxTokens())
                .timeoutMillis(request.getTimeoutMillis() == null ? options.getTimeoutMillis() : request.getTimeoutMillis())
                .context(toChatContext(session, request))
                .mcpEnabled(isMcpEnabled(request))
                .inputOptimizationEnabled(request.isInputOptimizationEnabled()
                        || (options.getInputOptimization() != null && options.getInputOptimization().isEnabled()))
                .contextCompressionEnabled(request.isContextCompressionEnabled() || options.isContextCompressionEnabled())
                .contextCompressionThreshold(options.getContextCompressionThreshold())
                .contextCompressionRetainMessages(options.getContextCompressionRetainMessages())
                .parameters(parameters)
                .attributes(request.getAttributes())
                .build();
    }

    /**
     * 合并会话历史、请求上下文和本次消息，构造一次调用需要的上下文。
     *
     * @param session 当前会话
     * @param request 当前请求
     * @return 聊天上下文
     */
    private ChatContext toChatContext(DefaultAgentSession session, com.chua.starter.ai.support.agent.AgentRequest request) {
        ChatContext context = new ChatContext();
        List<ChatMessage> history = new ArrayList<>();
        for (AgentMessage message : session.getHistoryMessages()) {
            history.add(toChatMessage(message));
        }
        if (request.getContext() != null && request.getContext().getHistoryMessages() != null) {
            for (AgentMessage message : request.getContext().getHistoryMessages()) {
                history.add(toChatMessage(message));
            }
        }
        if (request.getMessages() != null) {
            for (AgentMessage message : request.getMessages()) {
                history.add(toChatMessage(message));
            }
        }
        context.setHistory(history);
        context.setAttributes(new LinkedHashMap<>(request.getAttributes()));
        context.setVariables(request.getContext() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(request.getContext().getVariables()));
        return context;
    }

    private ChatMessage toChatMessage(AgentMessage message) {
        if (message == null) {
            return new ChatMessage();
        }
        return new ChatMessage(message.getRole(), message.getText());
    }

    /**
     * 将本次输入和输出追加进会话历史，并返回新增的消息列表。
     *
     * @param session      当前会话
     * @param request      当前请求
     * @param responseText 模型输出
     * @return 本次追加的消息
     */
    private List<AgentMessage> updateHistory(DefaultAgentSession session,
                                             com.chua.starter.ai.support.agent.AgentRequest request,
                                             String responseText) {
        List<AgentMessage> appended = new ArrayList<>();
        if (StringUtils.isNotBlank(request.getInput())) {
            AgentMessage userMessage = AgentMessage.builder()
                    .role("user")
                    .text(request.getInput())
                    .attributes(request.getAttributes())
                    .build();
            session.appendHistory(userMessage);
            appended.add(userMessage);
        }
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            session.appendHistory(request.getMessages());
            appended.addAll(request.getMessages());
        }
        AgentMessage assistantMessage = AgentMessage.builder()
                .role("assistant")
                .text(responseText)
                .build();
        session.appendHistory(assistantMessage);
        appended.add(assistantMessage);
        return appended;
    }

    /**
     * 记录一次执行结果，供回放和审计使用。
     *
     * @param session  当前会话
     * @param request  当前请求
     * @param response 当前响应
     */
    private void recordExecution(DefaultAgentSession session,
                                 com.chua.starter.ai.support.agent.AgentRequest request,
                                 AgentResponse response,
                                 AgentExecutionTrace trace) {
        if (!options.isExecutionRecordEnabled()) {
            return;
        }
        AgentExecutionRecord record = AgentExecutionRecord.builder()
                .recordId(UUID.randomUUID().toString())
                .requestId(response.getRequestId())
                .sessionId(session.getSessionId())
                .snapshotId(session.state().getSnapshotId())
                .agentName(metadata.getName())
                .provider(provider)
                .model(response.getModel())
                .input(request.getInput())
                .context(AgentRequestAdapter.toCommon(request, options, request.getContext()).getContext())
                .requestMessages(request.getMessages())
                .requestAttributes(request.getAttributes())
                .success(response.isSuccess())
                .responseText(response.getText())
                .error(response.getError())
                .responseMessages(response.getMessages())
                .steps(trace == null ? List.of() : trace.steps())
                .metadata(response.getMetadata())
                .build();
        records.put(record.getRecordId(), record);
        if (trace != null) {
            trace.event(AgentEventType.RECORD_PERSISTED, AgentActionType.RECORD, com.chua.common.support.ai.callback.AgentActionPhase.COMPLETED,
                    "RECORD", record.getRecordId(), "execution record persisted", true, Map.of(), Map.of("recordId", record.getRecordId()), Map.of());
        }
    }

    /**
     * 执行全部 AgentPolicy。
     *
     * @param requestId  请求标识
     * @param request    当前请求
     * @param session    当前会话
     * @param actionType 当前动作类型
     */
    private void evaluatePolicies(String requestId,
                                  com.chua.starter.ai.support.agent.AgentRequest request,
                                  DefaultAgentSession session,
                                  AgentActionType actionType,
                                  AgentExecutionTrace trace) {
        AgentPolicyContext context = AgentPolicyContext.builder()
                .agentName(metadata.getName())
                .requestId(requestId)
                .sessionId(session.getSessionId())
                .action(com.chua.common.support.ai.callback.AgentAction.builder()
                        .actionId(UUID.randomUUID().toString())
                        .type(actionType)
                        .phase(com.chua.common.support.ai.callback.AgentActionPhase.STARTED)
                        .name(actionType.name())
                        .target(provider)
                        .summary(request == null ? null : request.getInput())
                        .build())
                .request(AgentRequestAdapter.toCommon(request, options, request == null ? null : request.getContext()))
                .context(AgentContext.builder()
                        .sessionId(session.getSessionId())
                        .systemPrompt(session.getSystemPrompt())
                        .historyMessages(session.getHistoryMessages())
                        .build())
                .options(options)
                .build();
        for (AgentPolicy policy : policies) {
            if (policy == null || !policy.supports(context)) {
                continue;
            }
            AgentPolicyDecision decision = policy.evaluate(context);
            if (decision == null || decision.isAllowed()) {
                if (trace != null) {
                    trace.event(AgentEventType.POLICY_APPROVED, AgentActionType.POLICY, com.chua.common.support.ai.callback.AgentActionPhase.COMPLETED,
                            policy.metadata().getName(), provider, "policy approved", true, Map.of(), Map.of(), Map.of());
                }
                continue;
            }
            if (trace != null) {
                trace.event(decision.isApprovalRequired() ? AgentEventType.APPROVAL_REQUIRED : AgentEventType.POLICY_DENIED,
                        AgentActionType.POLICY,
                        com.chua.common.support.ai.callback.AgentActionPhase.FAILED,
                        policy.metadata().getName(),
                        provider,
                        StringUtils.defaultString(decision.getReason(), "policy denied"),
                        false,
                        Map.of(),
                        Map.of(),
                        Map.of());
            }
            evaluateOrThrow(decision, policy.metadata().getName());
        }
    }

    /**
     * 将策略决策转换为运行时异常。
     *
     * @param decision 决策结果
     * @param target   被评估对象
     */
    private void evaluateOrThrow(AgentPolicyDecision decision, String target) {
        if (decision == null || decision.isAllowed()) {
            return;
        }
        String reason = StringUtils.defaultString(decision.getReason(), "Agent policy rejected: " + target);
        if (decision.isApprovalRequired()) {
            throw new AgentApprovalRequiredException(reason);
        }
        throw new AgentExecutionRejectedException(reason);
    }

    /**
     * 判断本次请求是否启用 MCP。
     *
     * @param request 当前请求
     * @return 是否启用 MCP
     */
    private boolean isMcpEnabled(com.chua.starter.ai.support.agent.AgentRequest request) {
        if (request == null) {
            return true;
        }
        Object value = request.getAttributes().get("mcpEnabled");
        return !(value instanceof Boolean bool) || bool;
    }

    /**
     * 解析本次执行模型。
     *
     * 优先级: request.model > session.model > agent.defaultModel
     *
     * @param request 当前请求
     * @param session 当前会话
     * @return 实际执行模型
     */
    private String resolveModel(com.chua.starter.ai.support.agent.AgentRequest request, DefaultAgentSession session) {
        if (StringUtils.isNotBlank(request.getModel())) {
            return request.getModel();
        }
        if (StringUtils.isNotBlank(session.getModel())) {
            return session.getModel();
        }
        return defaultModel;
    }

    /**
     * 按快照标识或时间点查找会话快照。
     *
     * @param sessionId 会话标识
     * @param snapshotId 快照标识
     * @param restoreAt 恢复时间点
     * @return 命中的快照
     */
    private AgentSnapshot findSnapshot(String sessionId, String snapshotId, Long restoreAt) {
        List<AgentSnapshot> items = snapshots(sessionId);
        if (StringUtils.isNotBlank(snapshotId)) {
            return items.stream().filter(item -> snapshotId.equals(item.getSnapshotId())).findFirst().orElse(null);
        }
        if (restoreAt == null) {
            return items.isEmpty() ? null : items.get(items.size() - 1);
        }
        return items.stream()
                .filter(item -> item.getCreatedAt() <= restoreAt)
                .reduce((left, right) -> right)
                .orElse(null);
    }

    /**
     * 更新请求运行态。
     *
     * @param requestId 请求标识
     * @param sessionId 会话标识
     * @param status    新状态
     * @param message   状态说明
     * @return 最新运行态
     */
    private AgentRuntimeState updateState(String requestId, String sessionId, AgentRuntimeStatus status, String message) {
        AgentRuntimeState state = AgentRuntimeState.builder()
                .requestId(requestId)
                .sessionId(sessionId)
                .status(status)
                .timeoutMillis(options.getTimeoutMillis())
                .message(message)
                .updatedAt(System.currentTimeMillis())
                .build();
        runtimeStates.put(requestId, state);
        return state;
    }

    /**
     * 在已有运行态上切换状态。
     *
     * @param requestId 请求标识
     * @param status    新状态
     * @param message   状态说明
     * @return 最新运行态
     */
    private AgentRuntimeState updateRuntimeStatus(String requestId, AgentRuntimeStatus status, String message) {
        AgentRuntimeState current = runtimeStates.get(requestId);
        if (current == null) {
            throw new IllegalStateException("未找到运行时状态: " + requestId);
        }
        AgentRuntimeState updated = current.toBuilder()
                .status(status)
                .message(message)
                .updatedAt(System.currentTimeMillis())
                .completedAt(status.isTerminal() ? System.currentTimeMillis() : current.getCompletedAt())
                .build();
        runtimeStates.put(requestId, updated);
        return updated;
    }

    private List<AgentCallback> resolveCallbacks(AgentOptions options) {
        List<AgentCallback> result = new ArrayList<>(AgentCallbacks.find(options == null ? List.of() : options.getCallbackNames()));
        if (options != null && options.getCallbacks() != null) {
            result.addAll(options.getCallbacks());
        }
        return List.copyOf(result);
    }

    private List<AgentRequestStorage> resolveStorages() {
        ServiceProvider<AgentRequestStorage> provider = ServiceProvider.of(AgentRequestStorage.class);
        List<AgentRequestStorage> result = new ArrayList<>();
        provider.getExtensions().forEach(name -> {
            AgentRequestStorage storage = provider.getExtension(name);
            if (storage != null) {
                result.add(storage);
            }
        });
        AgentRequestStorage defaultStorage = provider.getDefault();
        if (defaultStorage != null && !result.contains(defaultStorage)) {
            result.add(defaultStorage);
        }
        return List.copyOf(result);
    }

    private AgentTaskMode resolveTaskMode(com.chua.starter.ai.support.agent.AgentRequest request) {
        if (request != null && request.getTaskMode() != null) {
            return request.getTaskMode();
        }
        if (request != null) {
            Object value = request.getAttributes().get("taskMode");
            if (value != null) {
                try {
                    return AgentTaskMode.valueOf(String.valueOf(value).trim().toUpperCase());
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        Object value = options.getAttributes().get("taskMode");
        if (value != null) {
            try {
                return AgentTaskMode.valueOf(String.valueOf(value).trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return AgentTaskMode.DEFAULT;
    }

    private void applyDecision(AgentExecutionTrace trace,
                               AgentPolicyDecision decision,
                               String target,
                               AgentActionType actionType) {
        if (decision == null || decision.isAllowed()) {
            if (trace != null) {
                trace.event(AgentEventType.PERMISSION_APPROVED, actionType, com.chua.common.support.ai.callback.AgentActionPhase.COMPLETED,
                        target, provider, target + " approved", true, Map.of(), Map.of(), Map.of());
            }
            return;
        }
        String reason = StringUtils.defaultString(decision.getReason(), "Agent policy rejected: " + target);
        if (trace != null) {
            trace.event(decision.isApprovalRequired() ? AgentEventType.APPROVAL_REQUIRED : AgentEventType.PERMISSION_DENIED,
                    actionType,
                    com.chua.common.support.ai.callback.AgentActionPhase.FAILED,
                    target,
                    provider,
                    reason,
                    false,
                    Map.of(),
                    Map.of(),
                    Map.of());
        }
        if (decision.isApprovalRequired()) {
            throw new AgentApprovalRequiredException(reason);
        }
        throw new AgentExecutionRejectedException(reason);
    }

    private void emitChatResponseEvents(AgentExecutionTrace trace, ChatResponse response, String model) {
        if (trace == null || response == null) {
            return;
        }
        if (Boolean.TRUE.equals(response.getMetadata().get("inputOptimized"))) {
            trace.event(AgentEventType.INPUT_OPTIMIZATION_COMPLETED, AgentActionType.INPUT_OPTIMIZATION,
                    com.chua.common.support.ai.callback.AgentActionPhase.COMPLETED,
                    "INPUT_OPTIMIZATION", model, "input optimized", true, Map.of(), Map.of(), response.getMetadata());
        }
        if (Boolean.TRUE.equals(response.getMetadata().get("contextCompressed"))) {
            trace.event(AgentEventType.CONTEXT_COMPRESSION_COMPLETED, AgentActionType.CONTEXT_COMPRESSION,
                    com.chua.common.support.ai.callback.AgentActionPhase.COMPLETED,
                    "CONTEXT_COMPRESSION", model, "context compressed", true, Map.of(), Map.of(), response.getMetadata());
        }
        trace.event(AgentEventType.MODEL_CALL_COMPLETED, AgentActionType.MODEL_CALL,
                com.chua.common.support.ai.callback.AgentActionPhase.COMPLETED,
                "MODEL_CALL", model, "model call completed", true, Map.of("model", model),
                Map.of("text", response.getText()), response.getMetadata());
        if (response.getUsage() != null) {
            trace.event(AgentEventType.USAGE_RECORDED, AgentActionType.MODEL_CALL,
                    com.chua.common.support.ai.callback.AgentActionPhase.COMPLETED,
                    "USAGE", model, "usage recorded", true, Map.of(), usageOutput(response.getUsage()), response.getMetadata());
        }
    }

    private AgentResponse buildSuccessResponse(String requestId,
                                               String model,
                                               ChatResponse response,
                                               List<AgentMessage> messages,
                                               AgentTaskMode taskMode) {
        Map<String, Object> metadata = new LinkedHashMap<>(response == null ? Map.of() : response.getMetadata());
        metadata.put("taskMode", taskMode.name());
        metadata.put("endpoint", chatClient.getEndpoint());
        return AgentResponse.builder()
                .requestId(requestId)
                .success(true)
                .provider(provider)
                .model(model)
                .text(response == null ? null : response.getText())
                .usage(response == null ? null : response.getUsage())
                .messages(messages)
                .metadata(metadata)
                .build();
    }

    private Map<String, Object> requestInput(com.chua.starter.ai.support.agent.AgentRequest request) {
        Map<String, Object> input = new LinkedHashMap<>();
        if (request != null) {
            input.put("input", request.getInput());
            input.put("sessionId", request.getSessionId());
            input.put("reasoningEffort", request.getReasoningEffort());
            input.put("userAgent", request.getUserAgent());
        }
        return input;
    }

    private Map<String, Object> responseOutput(AgentResponse response) {
        Map<String, Object> output = new LinkedHashMap<>();
        if (response != null) {
            output.put("text", response.getText());
            output.put("success", response.isSuccess());
            if (response.getUsage() != null) {
                output.putAll(usageOutput(response.getUsage()));
            }
        }
        return output;
    }

    private Map<String, Object> usageOutput(AgentUsage usage) {
        if (usage == null) {
            return Map.of();
        }
        return metadataOf(
                "promptTokens", usage.getPromptTokens(),
                "completionTokens", usage.getCompletionTokens(),
                "totalTokens", usage.getTotalTokens(),
                "inputCost", usage.getInputCost(),
                "outputCost", usage.getOutputCost(),
                "totalCost", usage.getTotalCost(),
                "currency", usage.getCurrency()
        );
    }

    private Map<String, Object> requestMetadata(AgentTaskMode taskMode,
                                                com.chua.starter.ai.support.agent.AgentRequest request) {
        return metadataOf(
                "taskMode", taskMode == null ? null : taskMode.name(),
                "reasoningEffort", request == null ? null : request.getReasoningEffort(),
                "userAgent", request == null ? null : request.getUserAgent(),
                "endpoint", chatClient.getEndpoint()
        );
    }

    private Map<String, Object> requestCompletedMetadata(AgentTaskMode taskMode,
                                                         ChatResponse response,
                                                         long startedAt,
                                                         Long firstTokenLatencyMillis) {
        Map<String, Object> metadata = new LinkedHashMap<>(response == null ? Map.of() : response.getMetadata());
        metadata.put("taskMode", taskMode.name());
        metadata.put("durationMillis", System.currentTimeMillis() - startedAt);
        if (firstTokenLatencyMillis != null) {
            metadata.put("firstTokenLatencyMillis", firstTokenLatencyMillis);
        }
        if (response != null && response.getUsage() != null) {
            metadata.putAll(usageOutput(response.getUsage()));
        }
        return metadata;
    }

    private AgentRequestTelemetry buildTelemetry(String requestId,
                                                 DefaultAgentSession session,
                                                 com.chua.starter.ai.support.agent.AgentRequest request,
                                                 String requestType,
                                                 String model,
                                                 AgentUsage usage,
                                                 Map<String, Object> responseMetadata,
                                                 long startedAt,
                                                 Long firstTokenLatencyMillis,
                                                 boolean success,
                                                 String error) {
        BigModelMetadataView modelView = findModelMetadata(model);
        BigDecimal inputUnitPrice = modelView == null || modelView.inputPricing() == null ? null : modelView.inputPricing().effectiveUnitCostPerMillion();
        BigDecimal outputUnitPrice = modelView == null || modelView.outputPricing() == null ? null : modelView.outputPricing().effectiveUnitCostPerMillion();
        BigDecimal multiplier = modelView == null || modelView.inputPricing() == null ? null : modelView.inputPricing().multiplier();
        Integer cacheTokens = integerValue(responseMetadata == null ? null : responseMetadata.get("cacheTokens"));
        BigDecimal cacheCost = decimalValue(responseMetadata == null ? null : responseMetadata.get("cacheCost"));
        long completedAt = System.currentTimeMillis();
        return AgentRequestTelemetry.builder()
                .requestId(requestId)
                .sessionId(session == null ? null : session.getSessionId())
                .agentName(metadata.getName())
                .requestType(requestType)
                .provider(provider)
                .model(model)
                .reasoningEffort(request == null ? null : request.getReasoningEffort())
                .endpoint(chatClient.getEndpoint())
                .clientType(chatClient.getFactory())
                .userAgent(resolveUserAgent(request, responseMetadata))
                .tokens(AgentTokenMetrics.builder()
                        .inputTokens(usage == null ? null : usage.getPromptTokens())
                        .outputTokens(usage == null ? null : usage.getCompletionTokens())
                        .cacheTokens(cacheTokens)
                        .build())
                .costs(AgentCostMetrics.builder()
                        .inputCost(usage == null ? null : usage.getInputCost())
                        .outputCost(usage == null ? null : usage.getOutputCost())
                        .cacheCost(cacheCost)
                        .totalCost(totalCost(usage, cacheCost))
                        .inputUnitPrice(inputUnitPrice)
                        .outputUnitPrice(outputUnitPrice)
                        .multiplier(multiplier)
                        .currency(usage == null ? null : usage.getCurrency())
                        .build())
                .firstTokenLatencyMillis(firstTokenLatencyMillis == null
                        ? longValue(responseMetadata == null ? null : responseMetadata.get("firstTokenLatencyMillis"))
                        : firstTokenLatencyMillis)
                .durationMillis(completedAt - startedAt)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .success(success)
                .error(error)
                .build();
    }

    private void storeTelemetry(AgentRequestTelemetry telemetry) {
        for (AgentRequestStorage storage : requestStorages) {
            if (storage == null || telemetry == null) {
                continue;
            }
            try {
                storage.store(telemetry);
            } catch (Exception ignored) {
            }
        }
    }

    private String resolveUserAgent(com.chua.starter.ai.support.agent.AgentRequest request, Map<String, Object> responseMetadata) {
        if (request != null && StringUtils.isNotBlank(request.getUserAgent())) {
            return request.getUserAgent();
        }
        Object value = responseMetadata == null ? null : responseMetadata.get("userAgent");
        return value == null ? null : String.valueOf(value);
    }

    private BigModelMetadataView findModelMetadata(String model) {
        if (StringUtils.isBlank(model)) {
            return null;
        }
        return listModels().stream()
                .filter(item -> item != null && model.equalsIgnoreCase(item.name()))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal totalCost(AgentUsage usage, BigDecimal cacheCost) {
        BigDecimal total = usage == null ? null : usage.getTotalCost();
        if (total == null) {
            return cacheCost;
        }
        return cacheCost == null ? total : total.add(cacheCost);
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Map<String, Object> metadataOf(Object... items) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (items == null) {
            return metadata;
        }
        for (int i = 0; i + 1 < items.length; i += 2) {
            Object key = items[i];
            Object value = items[i + 1];
            if (key != null && value != null) {
                metadata.put(String.valueOf(key), value);
            }
        }
        return metadata;
    }

    /**
     * 构造失败响应，统一复用当前 provider 与错误消息。
     *
     * @param requestId 请求标识
     * @param model     模型名称
     * @param state     最新运行态
     * @return 失败响应
     */
    private AgentResponse failureResponse(String requestId, String model, AgentRuntimeState state) {
        return AgentResponse.failure(requestId, provider, model, state == null ? null : state.getMessage());
    }

    /**
     * 发布失败的流式分片。
     *
     * @param consumer  分片消费者
     * @param requestId 请求标识
     * @param message   错误信息
     */
    private void publishStreamFailure(Consumer<AgentStreamChunk> consumer, String requestId, String message) {
        consumer.accept(AgentStreamChunk.builder()
                .requestId(requestId)
                .error(message)
                .done(true)
                .build());
    }
}
