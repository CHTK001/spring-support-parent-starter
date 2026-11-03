// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.agent.Agent;
import com.chua.common.support.ai.agent.AgentResponse;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ChatSyncResponse;
import com.chua.common.support.ai.chat.ModelDefinition;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Consumer;

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
        this.closeHook = hook;
        return this;
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
                                if (delta != null && !delta.isEmpty()) {
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
                                if (delta != null && !delta.isEmpty()) {
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
                            case TOOL_RESULT:
                                // 工具步骤不注入正文，避免污染答案；后续可由独立工具面板展示
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
     * @param feedback 用户修改意见（规划模式「需要修改」），可空
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
                                if (delta != null && !delta.isEmpty()) {
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
                                if (delta != null && !delta.isEmpty()) {
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
                            case TOOL_RESULT:
                                // 工具步骤不注入正文
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
        // 仅释放 Agent 运行时私有的资源（MCP 连接等）；
        // 不调用 agent.close()：其内部会关闭主 ChatClient，而主客户端是缓存/共享实例，
        // 关闭会影响后续请求复用。模型注册为确定性 id，重复注册会被框架忽略，无需在此清理。
        if (closeHook != null) {
            try {
                closeHook.run();
            } catch (Exception e) {
                log.warn("[AgentChatClient] 执行关闭钩子失败: {}", e.getMessage());
            }
        }
    }
}
