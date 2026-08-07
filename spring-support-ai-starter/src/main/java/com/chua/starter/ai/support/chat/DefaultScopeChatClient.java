package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.AiTool;
import com.chua.common.support.ai.AiToolCall;
import com.chua.common.support.ai.AiToolResult;
import com.chua.common.support.ai.agent.AgentUsage;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ChatResponse.State;
import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * 默认 Spring ChatClient 实现。
 * <p>
 * 该实现将 session 从接口层移除，每次调用都基于 scope 创建新的底层 common ChatClient。
 *
 * @author CH
 * @since 2026/04/03
 */
@Slf4j
public class DefaultScopeChatClient implements ChatClient {

    /**
     * 输入优化提示词模板
     */
    private static final String INPUT_OPTIMIZATION_PROMPT = """
            你是输入优化器。
            请在不改变用户真实意图的前提下，把下面输入改写成更清晰、可执行、适合模型理解的版本。
            不要解释，不要扩写无关内容，只输出优化后的文本。

            输入：
            %s
            """;

    /**
     * 上下文压缩提示词模板
     */
    private static final String CONTEXT_COMPRESSION_PROMPT = """
            你是上下文压缩器。
            请把下面历史消息压缩成短工作记忆，保留任务目标、约束、关键决定、未完成事项和重要实体。
            只输出压缩结果，不要解释。
            压缩结果字数不要超过 %d 字。

            历史：
            %s
            """;

    /**
     * 上下文压缩最大字符数
     */
    private static final int COMPRESSION_MAX_CHARS = 2000;

    /**
     * ChatClient 设置
     */
    private final ChatClientSettings settings;

    /**
     * 底层 ChatClient 工厂函数（按模型与连接设置创建）
     */
    private final BiFunction<String, ChatClientSetting, com.chua.common.support.ai.chat.ChatClient> delegateFactory;

    /**
     * 缓存的模型列表
     */
    private volatile List<ModelDefinition> cachedModels = List.of();

    /**
     * 上下文压缩专用 ChatClient
     */
    private final ChatClient compressionChatClient;

    /**
     * 工具注册表
     */
    private final Map<String, AiTool> toolRegistry = new ConcurrentHashMap<>();

    /**
     * 简易构造。
     *
     * @param settings ChatClient 设置
     */
    public DefaultScopeChatClient(ChatClientSettings settings) {
        this(settings, null, CommonChatClientFactoryResolver::create);
    }

    /**
     * 构造（带压缩客户端）。
     *
     * @param settings              ChatClient 设置
     * @param compressionChatClient 压缩专用 ChatClient
     */
    public DefaultScopeChatClient(ChatClientSettings settings, ChatClient compressionChatClient) {
        this(settings, compressionChatClient, CommonChatClientFactoryResolver::create);
    }

    /**
     * 全参构造。
     *
     * @param settings             ChatClient 设置
     * @param compressionChatClient 压缩专用 ChatClient
     * @param delegateFactory      底层 ChatClient 工厂函数
     */
    DefaultScopeChatClient(ChatClientSettings settings,
                           ChatClient compressionChatClient,
                           BiFunction<String, ChatClientSetting, com.chua.common.support.ai.chat.ChatClient> delegateFactory) {
        this.settings = settings;
        this.compressionChatClient = compressionChatClient;
        this.delegateFactory = delegateFactory;
    }

    @Override
    public String getModelName() {
        return settings.getDefaultModel();
    }

    @Override
    public String getFactory() {
        return settings.getFactory();
    }

    @Override
    public String getProvider() {
        return settings.getProvider();
    }

    @Override
    public String getDefaultModel() {
        return settings.getDefaultModel();
    }

    @Override
    public String getEndpoint() {
        ChatClientSetting setting = settings.getBaseSetting();
        if (setting == null) {
            return null;
        }
        return StringUtils.defaultString(setting.getBaseUrl(), setting.getProvider());
    }

    @Override
    public List<ModelDefinition> listModels() {
        List<ModelDefinition> local = cachedModels;
        if (!local.isEmpty()) {
            return local;
        }
        try {
            com.chua.common.support.ai.chat.ChatClient delegate = delegateFactory.apply(settings.getFactory(), createDelegateSetting(false));
            List<ModelDefinition> resolved = delegate.models();
            cachedModels = resolved == null ? List.of() : List.copyOf(resolved);
            delegate.close();
        } catch (Exception ignored) {
            cachedModels = List.of();
        }
        return cachedModels;
    }

    @Override
    public ChatResponse chat(ChatScope scope) {
        ChatScope normalized = normalize(scope);
        ChatContext context = resolveContext(normalized);
        String input = resolveInput(normalized, context);
        com.chua.common.support.ai.chat.ChatClient delegate = null;
        try {
            delegate = createDelegate(normalized, context);
            if (delegate == null) {
                log.warn("[DefaultScopeChatClient] 底层 ChatClient 创建失败（未配置有效的 LLM provider/apiKey），返回提示响应");
                return ChatResponse.builder()
                        .state(State.STOP)
                        .content("[AI] 尚未配置可用的模型服务，请先在全局设置中配置 LLM provider 与 API Key。")
                        .build();
            }
            ChatExecutionResult execution = executeSync(delegate, normalized, input);
            return ChatResponse.builder()
                    .state(State.STOP)
                    .content(execution.text())
                    .build();
        } finally {
            closeQuietly(delegate);
        }
    }

    @Override
    public void chat(ChatScope scope, Consumer<String> consumer, Runnable onComplete, Consumer<Throwable> onError) {
        ChatScope normalized = normalize(scope);
        ChatContext context = resolveContext(normalized);
        String input = resolveInput(normalized, context);
        com.chua.common.support.ai.chat.ChatClient delegate = null;
        try {
            delegate = createDelegate(normalized, context);
            if (delegate == null) {
                log.warn("[DefaultScopeChatClient] 底层 ChatClient 创建失败（未配置有效的 LLM provider/apiKey），返回提示响应");
                if (consumer != null) {
                    consumer.accept("[AI] 尚未配置可用的模型服务，请先在全局设置中配置 LLM provider 与 API Key。");
                }
                if (onComplete != null) {
                    onComplete.run();
                }
                return;
            }
            Consumer<com.chua.common.support.ai.chat.ChatResponse> wrappedConsumer = response -> {
                if (response != null) {
                    consumer.accept(response.getContent());
                } else {
                    consumer.accept(null);
                }
            };
            delegate.chat(input, wrappedConsumer, onComplete, onError);
        } catch (Throwable throwable) {
            closeQuietly(delegate);
            onError.accept(throwable);
        }
    }

    @Override
    public List<AiTool> listTools(ChatScope scope) {
        if (toolRegistry.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(toolRegistry.values());
    }

    @Override
    public AiToolResult callTool(ChatScope scope, AiToolCall toolCall) {
        if (toolCall == null || toolCall.getName() == null) {
            return AiToolResult.builder()
                    .callId(toolCall != null ? toolCall.getId() : null)
                    .name("unknown")
                    .success(false)
                    .errorMessage("工具调用请求无效: 工具名称为空")
                    .build();
        }
        AiTool tool = toolRegistry.get(toolCall.getName());
        if (tool == null) {
            return AiToolResult.builder()
                    .callId(toolCall.getId())
                    .name(toolCall.getName())
                    .success(false)
                    .errorMessage("未找到工具: " + toolCall.getName())
                    .build();
        }
        try {
            return tool.call(toolCall.getArguments());
        } catch (Exception e) {
            return AiToolResult.builder()
                    .callId(toolCall.getId())
                    .name(toolCall.getName())
                    .success(false)
                    .errorMessage("工具执行异常: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 注册一个工具到该 ChatClient 实例。
     *
     * @param tool AI 工具
     */
    public void registerTool(AiTool tool) {
        if (tool != null && tool.name() != null) {
            toolRegistry.put(tool.name(), tool);
        }
    }

    /**
     * 批量注册工具。
     *
     * @param tools 工具列表
     */
    public void registerTools(List<AiTool> tools) {
        if (tools != null) {
            for (AiTool tool : tools) {
                registerTool(tool);
            }
        }
    }

    /**
     * 获取已注册的工具数量。
     */
    public int getToolCount() {
        return toolRegistry.size();
    }

    @Override
    public void close() {
    }

    @Override
    public void chat(String input, java.util.function.Consumer<ChatResponse> consumer,
                    Runnable onComplete, java.util.function.Consumer<Throwable> onError) {
        try {
            ChatScope scope = ChatScope.builder().input(input).build();
            ChatResponse response = chat(scope);
            if (consumer != null) {
                consumer.accept(response);
            }
            if (onComplete != null) {
                onComplete.run();
            }
        } catch (Throwable t) {
            if (onError != null) {
                onError.accept(t);
            }
        }
    }

    @Override
    public String chatSync(String input) {
        return chatSync(input, 0L);
    }

    @Override
    public String chatSync(String input, long timeoutMillis) {
        ChatScope scope = ChatScope.builder()
                .input(input)
                .timeoutMillis(timeoutMillis > 0 ? timeoutMillis : null)
                .build();
        ChatResponse response = chat(scope);
        return response != null ? response.getContent() : null;
    }

    public ChatResponse send(String message) {
        ChatScope scope = ChatScope.builder().input(message).build();
        return chat(scope);
    }

    public ChatResponse send(String systemPrompt, String message) {
        ChatScope scope = ChatScope.builder()
                .systemPrompt(systemPrompt)
                .input(message)
                .build();
        return chat(scope);
    }

    // ==================== 内部实现 ====================

    private ChatScope normalize(ChatScope scope) {
        ChatScope effective = scope == null ? ChatScope.builder().build() : scope;
        return effective.toBuilder()
                .systemPrompt(StringUtils.defaultString(effective.getSystemPrompt(), settings.getSystemPrompt()))
                .model(StringUtils.defaultString(effective.getModel(), settings.getDefaultModel()))
                .temperature(effective.getTemperature() == null ? settings.getTemperature() : effective.getTemperature())
                .maxTokens(effective.getMaxTokens() == null ? settings.getMaxTokens() : effective.getMaxTokens())
                .timeoutMillis(effective.getTimeoutMillis() == null ? settings.getTimeoutMillis() : effective.getTimeoutMillis())
                .inputOptimizationEnabled(effective.isInputOptimizationEnabled() || settings.isInputOptimizationEnabled())
                .contextCompressionEnabled(effective.isContextCompressionEnabled() || settings.isContextCompressionEnabled())
                .contextCompressionThreshold(effective.getContextCompressionThreshold() <= 0
                        ? settings.getContextCompressionThreshold()
                        : effective.getContextCompressionThreshold())
                .contextCompressionRetainMessages(effective.getContextCompressionRetainMessages() <= 0
                        ? settings.getContextCompressionRetainMessages()
                        : effective.getContextCompressionRetainMessages())
                .build();
    }

    private ChatContext resolveContext(ChatScope scope) {
        ChatContext source = copyContext(scope.getContext());
        if (!scope.isContextCompressionEnabled() || source.getHistory() == null
                || source.getHistory().size() < scope.getContextCompressionThreshold()) {
            return source;
        }
        int retain = Math.max(0, scope.getContextCompressionRetainMessages());
        List<ChatMessage> history = source.getHistory();
        int split = Math.max(0, history.size() - retain);
        List<ChatMessage> head = new ArrayList<>(history.subList(0, split));
        List<ChatMessage> tail = new ArrayList<>(history.subList(split, history.size()));
        String compressionPrompt = String.format(CONTEXT_COMPRESSION_PROMPT, COMPRESSION_MAX_CHARS, formatMessages(head));
        String summary;
        if (compressionChatClient != null) {
            summary = compressionChatClient.chatSync(compressionPrompt);
        } else {
            summary = runHelperPrompt(compressionPrompt, scope);
        }
        if (StringUtils.isBlank(summary)) {
            return source;
        }
        List<ChatMessage> compressed = new ArrayList<>();
        compressed.add(ChatMessage.assistant("[Compressed Context]\n" + summary));
        compressed.addAll(tail);
        source.setHistory(compressed);
        source.attribute("contextCompressed", true);
        return source;
    }

    private String resolveInput(ChatScope scope, ChatContext context) {
        String input = StringUtils.defaultString(scope.getInput(), "");
        if (!scope.isInputOptimizationEnabled() || StringUtils.isBlank(input)) {
            return input;
        }
        String optimized = runHelperPrompt(String.format(INPUT_OPTIMIZATION_PROMPT, input), scope);
        if (StringUtils.isNotBlank(optimized)) {
            context.attribute("inputOptimized", true);
            return optimized;
        }
        return input;
    }

    private String runHelperPrompt(String helperPrompt, ChatScope scope) {
        ChatScope helperScope = scope.toBuilder()
                .input(helperPrompt)
                .mcpEnabled(false)
                .inputOptimizationEnabled(false)
                .contextCompressionEnabled(false)
                .context(new ChatContext())
                .build();
        com.chua.common.support.ai.chat.ChatClient helper = null;
        try {
            helper = createDelegate(helperScope, new ChatContext());
            return helper.chatSync(helperPrompt, resolveTimeout(helperScope));
        } catch (Exception ignored) {
            return null;
        } finally {
            if (helper != null) {
                try {
                    helper.close();
                } catch (Exception ignored2) {
                }
            }
        }
    }

    private ChatExecutionResult executeSync(com.chua.common.support.ai.chat.ChatClient delegate,
                                            ChatScope scope,
                                            String input) {
        long timeout = resolveTimeout(scope);
        String text = timeout > 0 ? delegate.chatSync(input, timeout) : delegate.chatSync(input);
        return new ChatExecutionResult(text, null, Map.of());
    }

    private com.chua.common.support.ai.chat.ChatClient createDelegate(ChatScope scope, ChatContext context) {
        ChatClientSetting setting = createDelegateSetting(scope.isMcpEnabled());
        com.chua.common.support.ai.chat.ChatClient delegate = delegateFactory.apply(settings.getFactory(), setting);
        if (resolveModel(scope) != null && !resolveModel(scope).isBlank()) {
            if (delegate instanceof ExtensibleChatClient ec) {
                ec.model(resolveModel(scope));
            }
        }
        String systemPrompt = scope.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            if (delegate instanceof ExtensibleChatClient ec) {
                ec.system(systemPrompt);
            }
        }
        Double temperature = scope.getTemperature();
        if (temperature != null) {
            if (delegate instanceof ExtensibleChatClient ec) {
                ec.temperature(temperature);
            }
        }
        Integer maxTokens = scope.getMaxTokens();
        if (maxTokens != null) {
            if (delegate instanceof ExtensibleChatClient ec) {
                ec.maxTokens(maxTokens);
            }
        }
        if (context != null && context.getHistory() != null) {
            for (ChatMessage message : context.getHistory()) {
                if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                    continue;
                }
                if ("assistant".equalsIgnoreCase(message.getRole())) {
                    if (delegate instanceof ExtensibleChatClient ec) {
                        ec.addAssistantHistory(message.getContent());
                    }
                } else {
                    if (delegate instanceof ExtensibleChatClient ec) {
                        ec.addUserHistory(message.getContent());
                    }
                }
            }
        }
        if (scope.getImageUrls() != null) {
            for (String imageUrl : scope.getImageUrls()) {
                if (imageUrl != null && !imageUrl.isBlank()) {
                    if (delegate instanceof ExtensibleChatClient ec) {
                        ec.addImage(imageUrl);
                    }
                }
            }
        }
        return delegate;
    }

    private ChatClientSetting createDelegateSetting(boolean mcpEnabled) {
        ChatClientSetting base = settings.getBaseSetting();
        if (base == null) {
            return ChatClientSetting.builder().provider(settings.getProvider()).build();
        }
        return ChatClientSetting.builder()
                .provider(base.getProvider())
                .baseUrl(base.getBaseUrl())
                .appKey(base.getAppKey())
                .appSecret(base.getAppSecret())
                .build();
    }

    private ChatContext copyContext(ChatContext context) {
        ChatContext target = new ChatContext();
        if (context == null) {
            return target;
        }
        target.setUserId(context.getUserId());
        target.setHistory(context.getHistory() == null ? new ArrayList<>() : new ArrayList<>(context.getHistory()));
        target.setVariables(context.getVariables() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(context.getVariables()));
        target.setAttributes(context.getAttributes() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(context.getAttributes()));
        return target;
    }

    private long resolveTimeout(ChatScope scope) {
        return scope.getTimeoutMillis() == null ? settings.getTimeoutMillis() : scope.getTimeoutMillis();
    }

    private String resolveModel(ChatScope scope) {
        return StringUtils.defaultString(scope.getModel(), settings.getDefaultModel());
    }

    private String formatMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : messages) {
            if (message == null || StringUtils.isBlank(message.getContent())) {
                continue;
            }
            builder.append(message.getRole()).append(": ").append(message.getContent()).append('\n');
        }
        return builder.toString();
    }

    private void closeQuietly(com.chua.common.support.ai.chat.ChatClient delegate) {
        if (delegate == null) {
            return;
        }
        try {
            delegate.close();
        } catch (Exception ignored) {
        }
    }
}