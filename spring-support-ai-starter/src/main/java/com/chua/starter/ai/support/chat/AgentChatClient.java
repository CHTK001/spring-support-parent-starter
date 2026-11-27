// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.agent.Agent;
import com.chua.common.support.ai.agent.AgentResponse;
import com.chua.common.support.ai.agent.AgentStreamEvent;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ChatSyncResponse;
import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.proxy.ProxyProvider;
import com.chua.common.support.reflection.ReflectUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;
import com.chua.common.support.utils.StringUtils;

/**
 * Agent 对话客户端 —— 将 Agent 运行时包装为 对话客户端。
 *
 * <p>用于将已注册的 Agent 实例暴露为可被对话链路调用的 ChatClient，
 * 注册到 {@link com.chua.starter.ai.support.registry.ChatClientRegistry} 后
 * 即可在模型管理页中选择使用。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class AgentChatClient implements ChatClient {

    /**
     * 默认模型名称
     */
    private static final String DEFAULT_MODEL = "agent-router";

    /**
     * 内部 Agent 实例
     */
    private final Agent agent;

    /**
     * 模型名称
     */
    private String model = DEFAULT_MODEL;

    /**
     * 关闭钩子：释放 Agent 运行时私有的资源（如 MCP 连接），由构建方注入
     */
    private Runnable closeHook;

    /** 仅短生命周期包装器拥有 Agent；注册表持有的实例由注册表关闭。 */
    private boolean closeAgent;

    /** 防止流结束与调用方收尾路径重复释放同一组私有资源。 */
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 工具观察者：TOOL_START 时旁路回调 {@code (toolName, toolInput)}，可空。
     * 由构建方注入（如会话待办镜像），只旁路消费、不影响对话主链路。
     */
    private transient BiConsumer<String, String> toolObserver;

    /**
     * 当前正在跑的流订阅；{@link #interrupt()} 用它立刻掐断本客户端的执行。
     */
    private volatile reactor.core.Disposable currentStream;

    /**
     * 创建 Agent对话客户端 实例
     *
     * @param agent Agent 运行时实例
     */
    public AgentChatClient(Agent agent) {
        this.agent = agent;
    }

    /**
     * 注入关闭钩子并返回自身（链式）。
     *
     * @param hook 关闭动作，用于释放 MCP 等私有资源，可空
     * @return 当前客户端
     */
    public AgentChatClient withCloseHook(Runnable hook) {
        Runnable previous = this.closeHook;
        this.closeHook = previous == null ? hook : () -> {
            try {
                previous.run();
            } finally {
                if (hook != null) hook.run();
            }
        };
        return this;
    }

    /**
     * 将 Agent 的模型客户端设为借用视图，Agent 关闭时不关闭被共享的底层客户端。
     * @param client 被借用的模型客户端
     * @return 不拥有底层客户端的代理
     */
    public static ChatClient borrowedClient(ChatClient client) {
        return ProxyProvider.of(ChatClient.class).target(client).tryAsm(false).tryJavassist(false)
                .enableAnnotationScan(false).enableArround(false)
                .methodIntercept((target, method, args, proxy) -> {
                    if ("close".equals(method.getName()) && method.getParameterCount() == 0) return null;
                    Object result = ReflectUtils.invokeProxy(client, method, args);
                    return result == client ? proxy : result;
                }).build();
    }

    /**
     * 取得短生命周期 Agent 的关闭权，始终先关闭 Agent，再执行私有资源钩子。
     * 调用方需先保护其借用的 ChatClient；重复调用不重复登记关闭动作。
     * @return 当前客户端
     */
    public AgentChatClient withAgentClose() {
        closeAgent = true;
        return this;
    }

    /**
     * 注入工具观察者并返回自身（链式）。
     *
     * @param observer TOOL_START 时回调 {@code (toolName, toolInput)}，可空
     * @return 当前客户端
     */
    public AgentChatClient withToolObserver(BiConsumer<String, String> observer) {
        this.toolObserver = observer;
        return this;
    }

    /**
     * 旁路通知工具观察者（仅 TOOL_START 时调用）。
     *
     * <p>观察者异常只记日志、不外抛，保证工具事件不影响对话流。</p>
     *
     * @param event 工具开始事件
     */
    private void notifyToolObserver(AgentStreamEvent event) {
        BiConsumer<String, String> observer = this.toolObserver;
        if (observer == null || event.getToolName() == null) {
            return;
        }
        try {
            observer.accept(event.getToolName(), event.getToolInput());
        } catch (Exception e) {
            log.warn("[AgentChatClient] 工具观察者回调失败 tool={}: {}", event.getToolName(), e.getMessage());
        }
    }

    /**
     * 获取内部 Agent 实例
     * @return 获取Agent的结果
     */
    public Agent getAgent() {
        return agent;
    }

    @Override
    public ChatClient model(String model) {
        this.model = model != null ? model : DEFAULT_MODEL;
        return this;
    }

    @Override
    public String chatSync(String prompt) {
        try {
            AgentResponse response = agent.run(prompt);
            return response != null ? response.getOutput() : "";
        } catch (Exception e) {
            log.error("[AgentChatClient] 调用 Agent 失败: {}", e.getMessage(), e);
            return "Agent 调用失败: " + e.getMessage();
        }
    }

    @Override
    public ChatSyncResponse chatSyncWithResponse(String prompt) {
        try {
            AgentResponse response = agent.run(prompt);
            return ChatSyncResponse.builder()
                    .text(response != null ? response.getOutput() : "")
                    .usage(response != null ? response.getUsage() : null)
                    .build();
        } catch (Exception e) {
            log.error("[AgentChatClient] 同步调用 Agent 失败: {}", e.getMessage(), e);
            return ChatSyncResponse.builder()
                    .text("Agent 调用失败: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public void chat(String prompt, Consumer<ChatResponse> consumer) {
        chat(prompt, consumer, () -> {}, e -> log.error("[AgentChatClient] chat error: {}", e.getMessage(), e));
    }

    @Override
    public void chat(String prompt, Consumer<ChatResponse> consumer, Runnable onComplete, Consumer<Throwable> onError) {
        consumer.accept(ChatResponse.builder().state(ChatResponse.State.START).build());
        final StringBuilder fullOutput = new StringBuilder();
        final com.chua.common.support.ai.AiUsage[] usageHolder =
                new com.chua.common.support.ai.AiUsage[1];
        final java.util.concurrent.atomic.AtomicBoolean finished =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        // 直接订阅 Agent 的逐字事件流：正文/思考增量实时下发，COMPLETE 捕获聚合用量。
        // 不能用 runAsync().get() 等整段结束后再一次性回调，否则表现为长静默后整段爆发（伪流式）。
        reactor.core.Disposable disposable = agent.runStream(prompt).subscribe(
                event -> {
                    try {
                        if (event == null || event.getType() == null) {
                            return;
                        }
                        switch (event.getType()) {
                            case REASONING_DELTA: {
                                String delta = event.getDelta();
                                if (StringUtils.isNotEmpty(delta)) {
                                    fullOutput.append(delta);
                                    consumer.accept(ChatResponse.builder()
                                            .state(ChatResponse.State.STREAMING)
                                            .content(delta)
                                            .build());
                                }
                                break;
                            }
                            case THINKING_DELTA: {
                                String delta = event.getDelta();
                                if (StringUtils.isNotEmpty(delta)) {
                                    consumer.accept(ChatResponse.builder()
                                            .state(ChatResponse.State.STREAMING)
                                            .reasoningContent(delta)
                                            .build());
                                }
                                break;
                            }
                            case TRACE: {
                                // 执行轨迹（thinking/输出/轮次/工具/用量/边缘）透传给上层，
                                // 由 facade 作为独立 SSE 事件推到前端，不计入正文
                                if (event.getTrace() != null) {
                                    log.debug("[TraceDiag] 收到 TRACE kind={} spanId={} spanEnd={}",
                                            event.getTrace().getKind(),
                                            event.getTrace().getSpanId(),
                                            event.getTrace().isSpanEnd());
                                    consumer.accept(ChatResponse.builder()
                                            .state(ChatResponse.State.STREAMING)
                                            .trace(event.getTrace())
                                            .build());
                                }
                                break;
                            }
                            case CONFIRMATION_REQUEST: {
                                // 人工确认请求：透传给上层渲染批准/拒绝卡片；本轮随后正常 STOP
                                consumer.accept(ChatResponse.builder()
                                        .state(ChatResponse.State.STREAMING)
                                        .confirmationRequest(
                                                com.chua.common.support.ai.agent.ConfirmationRequest.builder()
                                                        .replyId(event.getReplyId())
                                                        .items(event.getConfirmations())
                                                        .build())
                                        .build());
                                break;
                            }
                            case TOOL_START:
                                // 工具步骤不注入正文，避免污染答案；观察者可旁路消费（如待办镜像）
                                notifyToolObserver(event);
                                break;
                            case TOOL_RESULT:
                                // 工具结果不注入正文；后续可由独立工具面板展示
                                break;
                            case COMPLETE:
                                if (event.getUsage() != null) {
                                    usageHolder[0] = event.getUsage();
                                }
                                break;
                            case ERROR:
                                if (finished.compareAndSet(false, true)) {
                                    String emsg = event.getToolOutput();
                                    Exception err = new IllegalStateException(
                                            emsg != null ? emsg : "Agent 执行错误");
                                    consumer.accept(ChatResponse.builder()
                                            .state(ChatResponse.State.ERROR)
                                            .errorMessage(err.getMessage())
                                            .build());
                                    onError.accept(err);
                                }
                                break;
                            default:
                                break;
                        }
                    } catch (Exception inner) {
                        log.warn("[AgentChatClient] 流式事件转发异常: {}", inner.getMessage());
                    }
                },
                err -> {
                    if (finished.compareAndSet(false, true)) {
                        consumer.accept(ChatResponse.builder()
                                .state(ChatResponse.State.ERROR)
                                .errorMessage(err.getMessage())
                                .build());
                        onError.accept(err);
                    }
                },
                () -> {
                    if (finished.compareAndSet(false, true)) {
                        consumer.accept(ChatResponse.builder()
                                .state(ChatResponse.State.STOP)
                                .usage(usageHolder[0])
                                .build());
                        onComplete.run();
                    }
                }
        );
        this.currentStream = disposable;
    }

    /**
     * 恢复执行：回传人工确认（human-in-the-loop）决策，订阅 Agent 恢复后的事件流。
     *
     * @param replyId    待确认请求回复 ID
     * @param decisions  各待确认工具的决策
     * @param consumer   流式响应回调
     * @param onComplete 完成回调
     * @param onError    异常回调
     */
    public void resume(String replyId,
                       List<com.chua.common.support.ai.agent.ConfirmationDecision> decisions,
                       Consumer<ChatResponse> consumer, Runnable onComplete,
                       Consumer<Throwable> onError) {
        resume(replyId, decisions, null, consumer, onComplete, onError);
    }

    /**
     * 恢复执行（携带用户修改意见）。
     *
     * @param replyId    待确认请求回复 ID
     * @param decisions  各待确认工具的决策
     * @param feedback   用户修改意见（规划模式「需要修改」），可空
     * @param consumer   流式响应回调
     * @param onComplete 完成回调
     * @param onError    异常回调
     */
    public void resume(String replyId,
                       List<com.chua.common.support.ai.agent.ConfirmationDecision> decisions,
                       String feedback,
                       Consumer<ChatResponse> consumer, Runnable onComplete,
                       Consumer<Throwable> onError) {
        consumer.accept(ChatResponse.builder().state(ChatResponse.State.START).build());
        final StringBuilder fullOutput = new StringBuilder();
        final com.chua.common.support.ai.AiUsage[] usageHolder =
                new com.chua.common.support.ai.AiUsage[1];
        final java.util.concurrent.atomic.AtomicBoolean finished =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        reactor.core.Disposable disposable = agent.resumeStream(replyId, decisions, feedback).subscribe(
                event -> {
                    try {
                        if (event == null || event.getType() == null) {
                            return;
                        }
                        switch (event.getType()) {
                            case REASONING_DELTA: {
                                String delta = event.getDelta();
                                if (StringUtils.isNotEmpty(delta)) {
                                    fullOutput.append(delta);
                                    consumer.accept(ChatResponse.builder()
                                            .state(ChatResponse.State.STREAMING)
                                            .content(delta)
                                            .build());
                                }
                                break;
                            }
                            case THINKING_DELTA: {
                                String delta = event.getDelta();
                                if (StringUtils.isNotEmpty(delta)) {
                                    consumer.accept(ChatResponse.builder()
                                            .state(ChatResponse.State.STREAMING)
                                            .reasoningContent(delta)
                                            .build());
                                }
                                break;
                            }
                            case TRACE: {
                                if (event.getTrace() != null) {
                                    consumer.accept(ChatResponse.builder()
                                            .state(ChatResponse.State.STREAMING)
                                            .trace(event.getTrace())
                                            .build());
                                }
                                break;
                            }
                            case CONFIRMATION_REQUEST: {
                                consumer.accept(ChatResponse.builder()
                                        .state(ChatResponse.State.STREAMING)
                                        .confirmationRequest(
                                                com.chua.common.support.ai.agent.ConfirmationRequest.builder()
                                                        .replyId(event.getReplyId())
                                                        .items(event.getConfirmations())
                                                        .build())
                                        .build());
                                break;
                            }
                            case TOOL_START:
                                // 工具步骤不注入正文；观察者可旁路消费（如待办镜像）
                                notifyToolObserver(event);
                                break;
                            case TOOL_RESULT:
                                // 工具结果不注入正文
                                break;
                            case COMPLETE:
                                if (event.getUsage() != null) {
                                    usageHolder[0] = event.getUsage();
                                }
                                break;
                            case ERROR:
                                if (finished.compareAndSet(false, true)) {
                                    String emsg = event.getToolOutput();
                                    Exception err = new IllegalStateException(
                                            emsg != null ? emsg : "Agent 执行错误");
                                    consumer.accept(ChatResponse.builder()
                                            .state(ChatResponse.State.ERROR)
                                            .errorMessage(err.getMessage())
                                            .build());
                                    onError.accept(err);
                                }
                                break;
                            default:
                                break;
                        }
                    } catch (Exception inner) {
                        log.warn("[AgentChatClient] resume事件转发异常: {}", inner.getMessage());
                    }
                },
                err -> {
                    if (finished.compareAndSet(false, true)) {
                        consumer.accept(ChatResponse.builder()
                                .state(ChatResponse.State.ERROR)
                                .errorMessage(err.getMessage())
                                .build());
                        onError.accept(err);
                    }
                },
                () -> {
                    if (finished.compareAndSet(false, true)) {
                        consumer.accept(ChatResponse.builder()
                                .state(ChatResponse.State.STOP)
                                .usage(usageHolder[0])
                                .build());
                        onComplete.run();
                    }
                }
        );
        this.currentStream = disposable;
    }

    @Override
    public List<ModelDefinition> models() {
        String agentName = agent.getDefinition() != null ? agent.getDefinition().getName() : "unknown";
        return List.of(ModelDefinition.builder()
                .id(model)
                .name("Agent: " + agentName)
                .provider("agent")
                .description("Agent 运行时模型，通过路由自动委派给合适的子 Agent")
                .capabilities(List.of("chat", "tools", "skills", "routing"))
                .build());
    }

    /**
     * 中断当前执行：这是「停止」按钮真正能停住的那一层。
     *
     * <p>只关闭帧槽（SSE/WS）不会让正在跑的 Agent 停下来——opencode 实现是一个
     * 子进程，帧槽关了它照样在后台调模型、写日志、占端口。这里做两件事：</p>
     * <ol>
     *   <li>{@link Agent#interrupt()}：把中断信号交给 Agent 实现——
     *       opencode 强杀子进程、agentscope 关闭当前 Harness、langchain4j 跳出 ReAct 循环</li>
     *   <li>取消本客户端持有的订阅：立刻停止向上层继续推送事件</li>
     * </ol>
     */
    public void interrupt() {
        Agent target = this.agent;
        if (target != null) {
            try {
                target.interrupt();
            } catch (Exception e) {
                log.warn("[AgentChatClient] 中断 Agent 失败: {}", e.getMessage());
            }
        }
        reactor.core.Disposable disposable = this.currentStream;
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    @Override
    public void close() {
        // 注册表实例默认仅释放钩子；临时实例显式取得关闭权后，先关闭 Agent，
        // 再释放 MCP 等私有资源。借用客户端代理负责保护底层共享模型客户端。
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            if (closeAgent) {
                agent.close();
            }
        } catch (Exception | Error e) {
            log.warn("[AgentChatClient] 关闭 Agent 失败", e);
        } finally {
            if (closeHook != null) {
                try {
                    closeHook.run();
                } catch (Exception | Error e) {
                    log.warn("[AgentChatClient] 执行关闭钩子失败", e);
                }
            }
        }
    }
}
