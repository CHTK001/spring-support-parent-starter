package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.AiTool;
import com.chua.common.support.ai.AiToolCall;
import com.chua.common.support.ai.AiToolResult;
import com.chua.common.support.ai.agent.AgentUsage;
import com.chua.common.support.ai.bigmodel.BigModelCallback;
import com.chua.common.support.ai.bigmodel.BigModelClient;
import com.chua.common.support.ai.bigmodel.BigModelMetadataView;
import com.chua.common.support.ai.bigmodel.BigModelRequest;
import com.chua.common.support.ai.bigmodel.BigModelResponse;
import com.chua.common.support.ai.bigmodel.BigModelSetting;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.deeplearning.support.ml.bigmodel.DefaultChatClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 默认 Spring ChatClient 实现。
 * <p>
 * 该实现将 session 从接口层移除，每次调用都基于 scope 创建新的底层 common ChatClient。
 *
 * @author CH
 * @since 2026/04/03
 */
public class DefaultScopeChatClient implements ChatClient {

    private static final String INPUT_OPTIMIZATION_PROMPT = """
            你是输入优化器。
            请在不改变用户真实意图的前提下，把下面输入改写成更清晰、可执行、适合模型理解的版本。
            不要解释，不要扩写无关内容，只输出优化后的文本。

            输入：
            %s
            """;

    private static final String CONTEXT_COMPRESSION_PROMPT = """
            你是上下文压缩器。
            请把下面历史消息压缩成短工作记忆，保留任务目标、约束、关键决定、未完成事项和重要实体。
            只输出压缩结果，不要解释。

            历史：
            %s
            """;

    private final ChatClientSettings settings;
    private final BiFunction<String, BigModelSetting, com.chua.common.support.ai.ChatClient> delegateFactory;
    private final Function<BigModelSetting, BigModelClient> catalogFactory;
    private volatile List<BigModelMetadataView> cachedModels = List.of();

    /**
     * 创建默认聊天客户端。
     *
     * @param settings 聊天客户端静态配置
     */
    public DefaultScopeChatClient(ChatClientSettings settings) {
        this(settings,
                CommonChatClientFactoryResolver::create,
                BigModelClient::create);
    }

    /**
     * 创建可测试的聊天客户端实例。
     *
     * @param settings        聊天静态配置
     * @param delegateFactory common ChatClient 创建函数
     * @param catalogFactory  模型目录查询函数
     */
    DefaultScopeChatClient(ChatClientSettings settings,
                           BiFunction<String, BigModelSetting, com.chua.common.support.ai.ChatClient> delegateFactory,
                           Function<BigModelSetting, BigModelClient> catalogFactory) {
        this.settings = settings;
        this.delegateFactory = delegateFactory;
        this.catalogFactory = catalogFactory;
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
        BigModelSetting setting = settings.getBaseSetting();
        if (setting == null) {
            return null;
        }
        return StringUtils.defaultString(setting.getBaseUrl(), setting.getHost());
    }

    @Override
    public List<BigModelMetadataView> listModels() {
        List<BigModelMetadataView> local = cachedModels;
        if (!local.isEmpty()) {
            return local;
        }
        try {
            List<BigModelMetadataView> resolved = catalogFactory.apply(settings.createSetting(false)).listModelViews();
            cachedModels = resolved == null ? List.of() : List.copyOf(resolved);
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
        try (com.chua.common.support.ai.ChatClient delegate = createDelegate(normalized, context)) {
            ChatExecutionResult execution = executeSync(delegate, normalized, input);
            return ChatResponse.builder()
                    .factory(getFactory())
                    .provider(getProvider())
                    .model(resolveModel(normalized))
                    .text(execution.text())
                    .usage(execution.usage())
                    .metadata(responseMetadata(normalized, context, execution))
                    .build();
        }
    }

    @Override
    public void chat(ChatScope scope, Consumer<String> consumer, Runnable onComplete, Consumer<Throwable> onError) {
        ChatScope normalized = normalize(scope);
        ChatContext context = resolveContext(normalized);
        String input = resolveInput(normalized, context);
        try (com.chua.common.support.ai.ChatClient delegate = createDelegate(normalized, context)) {
            if (!executeStream(delegate, normalized, input, consumer, onComplete, onError)) {
                delegate.chat(input, consumer, onComplete, onError);
            }
        } catch (Throwable throwable) {
            onError.accept(throwable);
        }
    }

    @Override
    public List<AiTool> listTools(ChatScope scope) {
        ChatScope normalized = normalize(scope);
        try (com.chua.common.support.ai.ChatClient delegate = createDelegate(normalized, resolveContext(normalized))) {
            return delegate.listMcpTools();
        }
    }

    @Override
    public AiToolResult callTool(ChatScope scope, AiToolCall toolCall) {
        ChatScope normalized = normalize(scope);
        try (com.chua.common.support.ai.ChatClient delegate = createDelegate(normalized, resolveContext(normalized))) {
            return delegate.callMcpTool(toolCall);
        }
    }

    @Override
    public void close() {
    }

    /**
     * 合并 scope 与静态配置，得到一次请求的最终参数。
     *
     * @param scope 原始 scope
     * @return 归一化后的 scope
     */
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

    /**
     * 在当前 scope 内执行上下文压缩。
     *
     * @param scope 归一化后的 scope
     * @return 实际发送给底层客户端的上下文
     */
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
        String summary = runHelperPrompt(String.format(CONTEXT_COMPRESSION_PROMPT, formatMessages(head)), scope);
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

    /**
     * 在当前 scope 内执行输入优化。
     *
     * @param scope   归一化后的 scope
     * @param context 当前上下文
     * @return 实际发送给模型的输入
     */
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

    /**
     * 使用当前模型执行辅助提示词。
     *
     * 该方法用于输入优化和上下文压缩，不参与 MCP 调度。
     *
     * @param helperPrompt 辅助提示词
     * @param scope        当前 scope
     * @return 模型输出
     */
    private String runHelperPrompt(String helperPrompt, ChatScope scope) {
        ChatScope helperScope = scope.toBuilder()
                .input(helperPrompt)
                .mcpEnabled(false)
                .inputOptimizationEnabled(false)
                .contextCompressionEnabled(false)
                .context(new ChatContext())
                .build();
        try (com.chua.common.support.ai.ChatClient helper = createDelegate(helperScope, new ChatContext())) {
            return helper.chatSync(helperPrompt, resolveTimeout(helperScope));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 使用底层 common ChatClient 执行同步聊天，并在可用时回填 usage。
     *
     * @param delegate 底层聊天客户端
     * @param scope    当前 scope
     * @param input    实际输入
     * @return 执行结果
     */
    private ChatExecutionResult executeSync(com.chua.common.support.ai.ChatClient delegate,
                                            ChatScope scope,
                                            String input) {
        ChatExecutionResult advanced = tryExecuteSync(delegate, scope, input);
        if (advanced != null) {
            return advanced;
        }
        long timeout = resolveTimeout(scope);
        String text = timeout > 0 ? delegate.chatSync(input, timeout) : delegate.chatSync(input);
        return new ChatExecutionResult(text, null, Map.of());
    }

    /**
     * 尝试复用底层公开的 `chat(BigModelRequest, BigModelCallback)`。
     *
     * @param delegate 底层聊天客户端
     * @param scope    当前 scope
     * @param input    实际输入
     * @return 执行结果；底层不支持时返回 null
     */
    private ChatExecutionResult tryExecuteSync(com.chua.common.support.ai.ChatClient delegate,
                                               ChatScope scope,
                                               String input) {
        if (!(delegate instanceof DefaultChatClient advancedClient)) {
            return null;
        }
        CountDownLatch latch = new CountDownLatch(1);
        long startedAt = System.currentTimeMillis();
        StringBuilder buffer = new StringBuilder();
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<AgentUsage> usage = new AtomicReference<>();
        AtomicReference<Map<String, Object>> metadata = new AtomicReference<>(Map.of());
        AtomicReference<Long> firstTokenAt = new AtomicReference<>();
        try {
            advancedClient.chat(buildRequest(scope, input), new BigModelCallback() {
                @Override
                public void accept(BigModelResponse response) {
                    if (response == null) {
                        return;
                    }
                    if (StringUtils.isNotBlank(response.getOutput())) {
                        firstTokenAt.compareAndSet(null, System.currentTimeMillis());
                        buffer.append(response.getOutput());
                    }
                    usage.set(ChatUsageSupport.merge(usage.get(), ChatUsageSupport.from(response)));
                    metadata.set(extractResponseMetadata(response));
                    if (response.isDone()) {
                        latch.countDown();
                    }
                }

                @Override
                public void exception(Throwable throwable) {
                    error.set(throwable);
                    latch.countDown();
                }

                @Override
                public void onComplete() {
                    latch.countDown();
                }
            });
            await(latch, resolveTimeout(scope));
        } catch (Exception ex) {
            return null;
        }
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
        Map<String, Object> resultMetadata = new LinkedHashMap<>(metadata.get());
        if (firstTokenAt.get() != null) {
            resultMetadata.put("firstTokenLatencyMillis", firstTokenAt.get() - startedAt);
        }
        resultMetadata.put("durationMillis", System.currentTimeMillis() - startedAt);
        return new ChatExecutionResult(buffer.toString(), usage.get(), resultMetadata);
    }

    /**
     * 尝试走带 `BigModelResponse` 的流式路径。
     *
     * @param delegate   底层聊天客户端
     * @param scope      当前 scope
     * @param input      实际输入
     * @param consumer   输出消费者
     * @param onComplete 完成回调
     * @param onError    错误回调
     * @return 是否已处理
     */
    private boolean executeStream(com.chua.common.support.ai.ChatClient delegate,
                                  ChatScope scope,
                                  String input,
                                  Consumer<String> consumer,
                                  Runnable onComplete,
                                  Consumer<Throwable> onError) {
        if (!(delegate instanceof DefaultChatClient advancedClient)) {
            return false;
        }
        try {
            advancedClient.chat(buildRequest(scope, input), new BigModelCallback() {
                @Override
                public void accept(BigModelResponse response) {
                    if (response != null && StringUtils.isNotBlank(response.getOutput())) {
                        consumer.accept(response.getOutput());
                    }
                }

                @Override
                public void exception(Throwable throwable) {
                    onError.accept(throwable);
                }

                @Override
                public void onComplete() {
                    onComplete.run();
                }
            });
            return true;
        } catch (Exception ex) {
            onError.accept(ex);
            return true;
        }
    }

    /**
     * 为一次请求创建底层 common ChatClient，并注入模型、系统词和历史。
     *
     * @param scope   当前 scope
     * @param context 当前上下文
     * @return 底层聊天客户端
     */
    private com.chua.common.support.ai.ChatClient createDelegate(ChatScope scope, ChatContext context) {
        BigModelSetting setting = settings.createSetting(scope.isMcpEnabled());
        com.chua.common.support.ai.ChatClient delegate = delegateFactory.apply(settings.getFactory(), setting);
        if (StringUtils.isNotBlank(resolveModel(scope))) {
            delegate.model(resolveModel(scope));
        }
        if (StringUtils.isNotBlank(scope.getSystemPrompt())) {
            delegate.system(scope.getSystemPrompt());
        }
        if (scope.getTemperature() != null) {
            delegate.temperature(scope.getTemperature());
        }
        if (scope.getMaxTokens() != null) {
            delegate.maxTokens(scope.getMaxTokens());
        }
        if (context != null && context.getHistory() != null) {
            for (ChatMessage message : context.getHistory()) {
                if (message == null || StringUtils.isBlank(message.getContent())) {
                    continue;
                }
                if ("assistant".equalsIgnoreCase(message.getRole())) {
                    delegate.addAssistantHistory(message.getContent());
                } else {
                    delegate.addUserHistory(message.getContent());
                }
            }
        }
        for (String imageUrl : scope.getImageUrls()) {
            if (StringUtils.isNotBlank(imageUrl)) {
                delegate.addImage(imageUrl);
            }
        }
        return delegate;
    }

    /**
     * 构造底层大模型请求。
     *
     * @param scope 当前 scope
     * @param input 实际输入
     * @return 大模型请求
     */
    private BigModelRequest buildRequest(ChatScope scope, String input) {
        return BigModelRequest.builder()
                .prompt(input)
                .system(scope.getSystemPrompt())
                .model(resolveModel(scope))
                .tokens(scope.getMaxTokens())
                .temperature(scope.getTemperature())
                .timeout(resolveTimeout(scope))
                .build();
    }

    /**
     * 拷贝 scope 上下文，避免请求间共享可变对象。
     *
     * @param context 原始上下文
     * @return 拷贝后的上下文
     */
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

    /**
     * 生成响应元数据，便于上层查看实际执行参数。
     *
     * @param scope   当前 scope
     * @param context 当前上下文
     * @return 响应元数据
     */
    private Map<String, Object> responseMetadata(ChatScope scope, ChatContext context, ChatExecutionResult execution) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("factory", getFactory());
        metadata.put("provider", getProvider());
        metadata.put("mcpEnabled", scope.isMcpEnabled());
        metadata.put("historySize", context == null || context.getHistory() == null ? 0 : context.getHistory().size());
        metadata.put("inputOptimized", Boolean.TRUE.equals(context == null ? null : context.getAttributes().get("inputOptimized")));
        metadata.put("contextCompressed", Boolean.TRUE.equals(context == null ? null : context.getAttributes().get("contextCompressed")));
        metadata.put("parameters", scope.getParameters());
        if (execution != null && execution.metadata() != null && !execution.metadata().isEmpty()) {
            metadata.putAll(execution.metadata());
        }
        return metadata;
    }

    /**
     * 解析本次请求超时时间。
     *
     * @param scope 当前 scope
     * @return 超时毫秒数
     */
    private long resolveTimeout(ChatScope scope) {
        return scope.getTimeoutMillis() == null ? settings.getTimeoutMillis() : scope.getTimeoutMillis();
    }

    /**
     * 解析本次请求实际模型。
     *
     * @param scope 当前 scope
     * @return 模型名称
     */
    private String resolveModel(ChatScope scope) {
        return StringUtils.defaultString(scope.getModel(), settings.getDefaultModel());
    }

    /**
     * 将历史消息格式化为压缩提示词输入。
     *
     * @param messages 历史消息
     * @return 格式化后的文本
     */
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

    /**
     * 等待带 usage 的底层执行完成。
     *
     * @param latch   同步器
     * @param timeout 超时时间
     * @throws InterruptedException 中断异常
     */
    private void await(CountDownLatch latch, long timeout) throws InterruptedException {
        if (timeout > 0) {
            latch.await(timeout, TimeUnit.MILLISECONDS);
            return;
        }
        latch.await();
    }

    /**
     * 提取底层响应元数据。
     *
     * @param response 底层响应
     * @return 元数据
     */
    private Map<String, Object> extractResponseMetadata(BigModelResponse response) {
        if (response == null) {
            return Map.of();
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("requestId", response.getRequestId());
        metadata.put("currency", response.getCurrency());
        metadata.put("estimatedUsage", Boolean.TRUE.equals(response.getEstimated()));
        return metadata;
    }
}
