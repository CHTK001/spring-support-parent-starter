package com.chua.starter.ai.support.npm;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.ChatClientType;
import com.chua.common.support.ai.chat.ChatMessage;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ChatSyncResponse;
import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * npm 中间件 ChatClient 装饰器。
 *
 * <p>包装任意 {@link ChatClient}，把流式回调中的正文/思考帧翻译成 OpenAI 形态
 * 事件交给角色配置的 npm 插件链（如 {@code @magpie-community/middleware-think-tags}）
 * 处理，再把处理结果回译成 {@link ChatResponse} 下发。工具调用/执行轨迹/人工确认帧
 * 不经过中间件，原样透传。</p>
 *
 * <p>降级口径：中间件初始化失败、单帧处理失败或侧车不可用时，该帧（及其后续帧）
 * 按原始内容直通，绝不阻断对话主链路。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
public class NpmMiddlewareChatClient implements ChatClient {

    private final ChatClient delegate;
    private final NpmPluginHostService hostService;
    private final List<NpmPluginSpec> plugins;
    private final long callTimeoutSeconds;

    /**
     * 创建 npm 中间件装饰器。
     *
     * @param delegate           被包装客户端
     * @param hostService        Node 侧车宿主
     * @param plugins            插件清单
     * @param callTimeoutSeconds 单次中间件调用超时秒数
     */
    public NpmMiddlewareChatClient(ChatClient delegate, NpmPluginHostService hostService,
                                   List<NpmPluginSpec> plugins, long callTimeoutSeconds) {
        this.delegate = delegate;
        this.hostService = hostService;
        this.plugins = plugins;
        this.callTimeoutSeconds = callTimeoutSeconds;
    }

    /**
     * 按角色配置包装客户端；未配置 npmPlugins 时原样返回。
     *
     * @param client      已组装好的客户端（可为 Agent 包装后的）
     * @param request     对话请求
     * @param hostService 侧车宿主
     * @param properties  npm 插件配置
     * @return 包装后客户端或原客户端
     */
    public static ChatClient wrapIfConfigured(ChatClient client, AiChatRequest request,
                                              NpmPluginHostService hostService,
                                              NpmPluginProperties properties) {
        if (client == null || request == null || hostService == null
                || properties == null || !properties.isEnabled()) {
            return client;
        }
        Object raw = request.getRoleSettings() == null ? null : request.getRoleSettings().get("npmPlugins");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return client;
        }
        List<NpmPluginSpec> specs = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object enabled = map.get("enabled");
            if (enabled != null && !Boolean.parseBoolean(String.valueOf(enabled))) {
                continue;
            }
            Object name = map.get("name");
            if (name == null || name.toString().isBlank()) {
                continue;
            }
            specs.add(new NpmPluginSpec(name.toString().trim(), map.get("options")));
        }
        if (specs.isEmpty()) {
            return client;
        }
        log.info("[NpmPlugin] 会话启用 npm 中间件: {}",
                specs.stream().map(NpmPluginSpec::name).toList());
        return new NpmMiddlewareChatClient(client, hostService, specs, properties.getCallTimeoutSeconds());
    }

    // ======================== 流式链路（唯一被增强的入口） ========================

    @Override
    public void chat(String prompt, Consumer<ChatResponse> consumer,
                     Runnable onComplete, Consumer<Throwable> onError) {
        String streamId = "npm-" + UUID.randomUUID();
        boolean active;
        try {
            active = hostService.begin(streamId, plugins);
        } catch (Exception e) {
            log.warn("[NpmPlugin] 中间件初始化失败，本次对话不经过 npm 中间件: {}", e.getMessage());
            active = false;
        }
        if (!active) {
            delegate.chat(prompt, consumer, onComplete, onError);
            return;
        }
        StreamState state = new StreamState();
        delegate.chat(prompt, raw -> {
            try {
                dispatch(raw, streamId, state, consumer);
            } catch (Exception e) {
                log.warn("[NpmPlugin] 中间件处理失败，回退原始帧: {}", e.getMessage());
                consumer.accept(raw);
            }
        }, () -> {
            hostService.endQuietly(streamId);
            if (onComplete != null) {
                onComplete.run();
            }
        }, t -> {
            hostService.endQuietly(streamId);
            if (onError != null) {
                onError.accept(t);
            }
        });
    }

    /**
     * 按帧类型分发。
     *
     * @param raw      原始帧
     * @param streamId 会话流标识
     * @param state    流内累积状态
     * @param consumer 下游消费者
     */
    private void dispatch(ChatResponse raw, String streamId, StreamState state,
                          Consumer<ChatResponse> consumer) {
        if (raw == null) {
            return;
        }
        if (raw.getToolCalls() != null || raw.getTrace() != null || raw.getConfirmationRequest() != null) {
            consumer.accept(raw);
            return;
        }
        ChatResponse.State st = raw.getState();
        if (st == ChatResponse.State.STREAMING) {
            consumer.accept(transformStreaming(raw, streamId, state));
        } else if (st == ChatResponse.State.STOP) {
            emitStop(raw, streamId, state, consumer);
        } else {
            consumer.accept(raw);
        }
    }

    /**
     * 处理 STREAMING 帧：翻译成 OpenAI delta 事件 → 中间件 → 回译。
     *
     * @param raw      原始帧
     * @param streamId 会话流标识
     * @param state    流内累积状态
     * @return 变换后帧（无变化时返回原帧）
     */
    private ChatResponse transformStreaming(ChatResponse raw, String streamId, StreamState state) {
        String content = raw.getContent();
        String reasoning = raw.getReasoningContent();
        boolean hasContent = content != null && !content.isEmpty();
        boolean hasReasoning = reasoning != null && !reasoning.isEmpty();
        if ((!hasContent && !hasReasoning) || state.degraded) {
            return raw;
        }
        try {
            Map<String, Object> delta = new LinkedHashMap<>();
            if (hasContent) {
                delta.put("content", content);
            }
            if (hasReasoning) {
                delta.put("reasoning_content", reasoning);
            }
            Map<String, Object> choice = new LinkedHashMap<>();
            choice.put("index", 0);
            choice.put("delta", delta);
            JsonNode out = hostService.event(streamId, Map.of("choices", List.of(choice)));
            JsonNode deltaOut = out.path("choices").path(0).path("delta");
            String newContent = textOrOriginal(deltaOut, "content", content);
            String newReasoning = textOrOriginal(deltaOut, "reasoning_content", reasoning);
            if (newContent != null) {
                state.content.append(newContent);
            }
            if (newReasoning != null) {
                state.reasoning.append(newReasoning);
            }
            if (Objects.equals(newContent, content) && Objects.equals(newReasoning, reasoning)) {
                return raw;
            }
            return copy(ChatResponse.State.STREAMING, raw, newContent, newReasoning, null);
        } catch (Exception e) {
            state.degraded = true;
            log.warn("[NpmPlugin] 中间件事件处理失败，后续帧直通: {}", e.getMessage());
            return raw;
        }
    }

    /**
     * 处理 STOP 帧：先发 finish chunk 冲刷插件 carry，再以聚合正文调用 onResponse，
     * 用处理结果覆盖 fullContent。
     *
     * @param raw      原始 STOP 帧
     * @param streamId 会话流标识
     * @param state    流内累积状态
     * @param consumer 下游消费者
     */
    private void emitStop(ChatResponse raw, String streamId, StreamState state,
                          Consumer<ChatResponse> consumer) {
        if (state.degraded) {
            consumer.accept(raw);
            return;
        }
        // 1) finish chunk：触发中间件冲刷 carry（如 think 块闭合时滞留的正文）
        try {
            Map<String, Object> finishChoice = new LinkedHashMap<>();
            finishChoice.put("index", 0);
            finishChoice.put("delta", Map.of());
            finishChoice.put("finish_reason", "stop");
            JsonNode out = hostService.event(streamId, Map.of("choices", List.of(finishChoice)));
            JsonNode delta = out.path("choices").path(0).path("delta");
            String flushedContent = nonBlankText(delta, "content");
            String flushedReasoning = nonBlankText(delta, "reasoning_content");
            if ((flushedContent != null && !flushedContent.isEmpty())
                    || (flushedReasoning != null && !flushedReasoning.isEmpty())) {
                if (flushedContent != null) {
                    state.content.append(flushedContent);
                }
                if (flushedReasoning != null) {
                    state.reasoning.append(flushedReasoning);
                }
                consumer.accept(copy(ChatResponse.State.STREAMING, raw, flushedContent, flushedReasoning, null));
            }
        } catch (Exception e) {
            log.warn("[NpmPlugin] finish chunk 冲刷失败: {}", e.getMessage());
        }
        // 2) onResponse：对聚合正文做收尾处理（如剥离残留 think 标签）
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("role", "assistant");
            message.put("content", state.content.toString());
            if (state.reasoning.length() > 0) {
                message.put("reasoning_content", state.reasoning.toString());
            }
            Map<String, Object> choice = new LinkedHashMap<>();
            choice.put("index", 0);
            choice.put("message", message);
            choice.put("finish_reason", "stop");
            JsonNode out = hostService.response(streamId, Map.of("choices", List.of(choice)));
            String finalContent = textOrDefault(out.path("choices").path(0).path("message"), "content",
                    state.content.toString());
            if (Objects.equals(finalContent, raw.getFullContent())) {
                consumer.accept(raw);
            } else {
                consumer.accept(copy(ChatResponse.State.STOP, raw, raw.getContent(),
                        raw.getReasoningContent(), finalContent));
            }
        } catch (Exception e) {
            log.warn("[NpmPlugin] onResponse 处理失败，保持原始完整内容: {}", e.getMessage());
            consumer.accept(raw);
        }
    }

    /**
     * 从节点取文本字段；字段缺失时返回原值，null 时返回 null。
     *
     * @param node       父节点
     * @param field      字段名
     * @param original   字段缺失时的兜底值
     * @return 字段值或兜底值
     */
    private String textOrOriginal(JsonNode node, String field, String original) {
        JsonNode f = node.path(field);
        if (f.isMissingNode()) {
            return original;
        }
        return f.isNull() ? null : f.asText();
    }

    /**
     * 从节点取文本字段；缺失时返回给定的默认值。
     *
     * @param node        父节点
     * @param field       字段名
     * @param defaultText 默认值
     * @return 字段值或默认值
     */
    private String textOrDefault(JsonNode node, String field, String defaultText) {
        JsonNode f = node.path(field);
        if (f.isMissingNode() || f.isNull()) {
            return defaultText;
        }
        return f.asText();
    }

    /**
     * 从节点取非空文本；缺失/null/空串统一返回 null。
     *
     * @param node  父节点
     * @param field 字段名
     * @return 非空文本或 null
     */
    private String nonBlankText(JsonNode node, String field) {
        JsonNode f = node.path(field);
        if (f.isMissingNode() || f.isNull()) {
            return null;
        }
        String text = f.asText();
        return text == null || text.isEmpty() ? null : text;
    }

    /**
     * 以指定状态重建一帧（未给出的字段沿用原帧）。
     *
     * @param state       新状态
     * @param raw         原帧
     * @param content     正文（null 表示沿用原帧）
     * @param reasoning   思考内容（null 表示沿用原帧）
     * @param fullContent 完整正文（null 表示沿用原帧）
     * @return 新帧
     */
    private ChatResponse copy(ChatResponse.State state, ChatResponse raw,
                              String content, String reasoning, String fullContent) {
        return ChatResponse.builder()
                .state(state)
                .content(content != null || raw.getContent() == null ? content : raw.getContent())
                .fullContent(fullContent != null ? fullContent : raw.getFullContent())
                .usage(raw.getUsage())
                .errorMessage(raw.getErrorMessage())
                .reasoningContent(reasoning != null || raw.getReasoningContent() == null
                        ? reasoning : raw.getReasoningContent())
                .toolCalls(raw.getToolCalls())
                .trace(raw.getTrace())
                .confirmationRequest(raw.getConfirmationRequest())
                .build();
    }

    // ======================== 直通方法（v1 不增强） ========================

    @Override
    public String chatSync(String prompt) {
        return delegate.chatSync(prompt);
    }

    @Override
    public ChatSyncResponse chatSyncWithResponse(String prompt) {
        return delegate.chatSyncWithResponse(prompt);
    }

    @Override
    public CompletableFuture<ChatSyncResponse> chatAsync(String prompt) {
        return delegate.chatAsync(prompt);
    }

    @Override
    public List<ModelDefinition> models() {
        return delegate.models();
    }

    @Override
    public ChatClient provider(String provider) {
        delegate.provider(provider);
        return this;
    }

    @Override
    public ChatClient model(String model) {
        delegate.model(model);
        return this;
    }

    @Override
    public ChatClient system(String system) {
        delegate.system(system);
        return this;
    }

    @Override
    public ChatClient temperature(double temperature) {
        delegate.temperature(temperature);
        return this;
    }

    @Override
    public ChatClient maxTokens(int maxTokens) {
        delegate.maxTokens(maxTokens);
        return this;
    }

    @Override
    public ChatClient addImage(String imageUrl) {
        delegate.addImage(imageUrl);
        return this;
    }

    @Override
    public ChatClient addUserHistory(String content) {
        delegate.addUserHistory(content);
        return this;
    }

    @Override
    public ChatClient addAssistantHistory(String content) {
        delegate.addAssistantHistory(content);
        return this;
    }

    @Override
    public ChatClient history(List<ChatMessage> messages) {
        delegate.history(messages);
        return this;
    }

    @Override
    public ChatClient addAttachment(String name, byte[] data, String mimeType) {
        delegate.addAttachment(name, data, mimeType);
        return this;
    }

    @Override
    public ChatClient addAttachmentUrl(String name, String url, String mimeType) {
        delegate.addAttachmentUrl(name, url, mimeType);
        return this;
    }

    @Override
    public ChatClient session(String sessionId) {
        delegate.session(sessionId);
        return this;
    }

    @Override
    public ChatClient newChat() {
        delegate.newChat();
        return this;
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public ChatClientSetting getSetting() {
        return delegate.getSetting();
    }

    @Override
    public ChatClientType type() {
        return delegate.type();
    }

    @Override
    public String getModel() {
        return delegate.getModel();
    }

    /**
     * 单个会话流内的累积状态。
     */
    private static final class StreamState {

        /**
         * 已下发的正文累积（经中间件变换后）。
         */
        final StringBuilder content = new StringBuilder();

        /**
         * 已下发的思考内容累积（经中间件变换后）。
         */
        final StringBuilder reasoning = new StringBuilder();

        /**
         * 中间件调用失败后置位：后续帧直通，不再调用侧车。
         */
        volatile boolean degraded;
    }
}
