package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.ai.AiUsage;
import com.chua.common.support.ai.agent.Agent;
import com.chua.common.support.ai.agent.AgentDefinition;
import com.chua.common.support.ai.agent.AgentMode;
import com.chua.common.support.ai.skill.SkillDefinition;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.ChatMessage;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ChatSyncResponse;
import com.chua.common.support.ai.chat.ChatToolCallData;
import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.ai.chat.aggregate.AggregateChatClient;
import com.chua.common.support.ai.chat.aggregate.AggregateChatClientSetting;
import com.chua.starter.ai.support.chat.ChatSkillSupport;
import com.chua.starter.ai.support.configuration.runtime.AgentProviderFactory;
import com.chua.starter.ai.support.chat.AgentChatClient;
import com.chua.starter.ai.support.entity.SysAiGroup;
import com.chua.starter.ai.support.entity.SysAiGroupModel;
import com.chua.starter.ai.support.entity.settings.SysAiModel;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderConnectionMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderConnection;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.manage.api.AgentMcpRuntimeService;
import com.chua.starter.ai.support.mapper.SysAiGroupMapper;
import com.chua.starter.ai.support.mapper.settings.AiModelMapper;
import com.chua.starter.ai.support.mapper.SysAiGroupModelMapper;
import com.chua.starter.ai.support.interceptor.UsageRecorderInterceptor;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.model.AiChatResult;
import com.chua.starter.ai.support.model.AiClientProbe;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.service.SysAiPricingService;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.ai.support.util.ProviderCredentials;
import com.chua.starter.ai.support.util.ProviderRouteTargets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * AI 对话门面服务实现。
 *
 * <p>通过 {@link ChatClientRegistry} 路由到对应的 {@link ChatClient}，
 * 完成同步/流式对话、模型列表查询与模型定义查询。</p>
 *
 * <p>当请求指定的模型/提供商在模型管理（DB provider）中配置时，优先按 DB
 * 中的平台/地址/凭证/代理动态构建 对话客户端，使每个提供商可独立配置代理
 * （如 Google 需要单独走代理）。DB 未命中时回退全局注册中心。</p>
 *
 * <p>对话统一走底层 {@link com.chua.common.support.ai.chat.ChatClient} 的
 * {@code chatSyncWithResponse} / {@code chat} API，保证任意 SPI 客户端可被调用。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatFacadeServiceImpl implements AiChatFacadeService {

    private static final long SSE_TIMEOUT_MILLIS = 60_000L; // sse超时millis

    private final ChatClientRegistry chatClientRegistry; // 对话客户端registry
    private final SysAiGroupMapper sysAiGroupMapper; // sysAI群体映射器
    private final SysAiGroupModelMapper sysAiGroupModelMapper; // sysAI群体模型映射器
    private final SysAiPricingService sysAiPricingService; // sysaipricing服务
    private final SysAiProviderMapper sysAiProviderMapper; // sysAI提供者映射器
    private final SysAiProviderModelMapper sysAiProviderModelMapper; // sysAI提供者模型映射器
    private final SysAiProviderConnectionMapper sysAiProviderConnectionMapper; // sysAI提供者connection映射器
    private final UsageRecorderInterceptor usageRecorderInterceptor; // usagerecorder拦截器
    private final AiModelMapper aiModelMapper; // 模型定义映射器（加载别名→主模型映射）
    private final ChatSkillSupport chatSkillSupport; // 已安装技能注入
    private final AgentMcpRuntimeService agentMcpRuntimeService; // 管理员 Agent 运行时 MCP 构建
    private final SysAiGlobalSettingService globalSettingService; // 全局设置（工作目录基路径等）
    private final AgentProviderFactory agentProviderFactory;

    /**
     * DB 提供者 多 appKey 聚合客户端缓存（提供者标识::模型 → 客户端，5 分钟 TTL）。
     * 避免每次对话都重建多客户端聚合实例；凭证变更后最多延迟 5 分钟生效。
     */
    private static final long DB_CLIENT_CACHE_TTL_MILLIS = 5 * 60 * 1000L;
    /**
     * 别名映射缓存 TTL（5 分钟）：别名 → 主模型 code，从 sys_ai_model.sys_ai_model_aliases 加载。
     */
    private static final long ALIAS_CACHE_TTL_MILLIS = 5 * 60 * 1000L;
    /**
     * 别名映射缓存，**按账号分桶**。
     *
     * <p>{@code sys_ai_model} 已按 {@code sys_ai_model_user_id} 隔离，别名是「某个账号自己
     * 的模型清单」的一部分。若沿用单份全局缓存，别名会跨账号串味 —— A 给自己模型起的别名
     * 会被拿来改写 B 发出的模型名。故这里按 userId 分桶；无身份上下文（系统线程）用
     * {@link #SYSTEM_ALIAS_BUCKET} 单独一桶，只放无归属的历史行。</p>
     */
    private final Map<String, CachedAliasMap> aliasCacheByUser = new ConcurrentHashMap<>();
    /**
     * 无身份线程使用的桶名。刻意与任何真实 userId 区分开（真实 userId 不会等于此常量）。
     */
    private static final String SYSTEM_ALIAS_BUCKET = "\u0000system";
    private final Map<String, CachedDbClient> dbClientCache = new ConcurrentHashMap<>(); // db客户端缓存

    /**
     * DB 提供者 客户端缓存项。
     * @param client 客户端
     * @param builtAt builtat
     * @return 缓存db客户端的结果
     */
    private record CachedDbClient(ChatClient client, long builtAt) {
    }

    // ==================== 同步对话 ====================

    @Override
    public Map<String, Object> call(AiChatRequest request) {
        return toLegacyMap(callForResult(request));
    }

    @Override
    public AiChatResult callForResult(AiChatRequest request) {
        ChatClient client = resolveChatClient(request);
        if (client == null) {
            return AiChatResult.failed(request.getModel(),
                    "No available ChatClient for model: " + request.getModel());
        }
        ChatClient configured = maybeWrapAgent(configureClient(client, request), request);
        try {
            long start = System.currentTimeMillis();
            ChatSyncResponse response = configured.chatSyncWithResponse(promptOf(request));
            long end = System.currentTimeMillis();
            /**
             * enrich延迟。
             * @param response.getUsage( 响应.获取usage(
             */
            AiUsage usage = response != null ? response.getUsage() : null;
            enrichLatency(usage, start, end, 0L);
            /**
             * recordusage。
             * @param configured configured
             * @param "chat" "对话"
             * @param response 响应
             */
            recordUsage(configured, callTypeOf(request), response, AiUserContext.currentUserId());
            String text = response != null && response.getText() != null ? response.getText() : "";
            return AiChatResult.ok(text, resolveUsedModel(configured, request, usage), usage);
        } catch (Exception e) {
            log.error("[AiChat] call failed: model={}", request.getModel(), e);
            return AiChatResult.failed(request.getModel(), e.getMessage());
        } finally {
            // 释放 Agent 运行时私有的 MCP 连接；直连客户端不在此关闭（可能为缓存共享实例）
            if (configured instanceof AgentChatClient agentClient) {
                agentClient.close();
            }
        }
    }

    /**
     * 取本次调用的用量记账来源。
     *
     * @param request 对话请求
     * @return 调用方声明的 callType，未声明时返回 chat
     */
    private String callTypeOf(AiChatRequest request) {
        String callType = request.getCallType();
        return callType == null || callType.isBlank() ? "chat" : callType.trim();
    }

    /**
     * 解析本次对话实际使用的模型名称。
     *
     * @param configured 已配置的客户端
     * @param request    对话请求
     * @param usage      模型回传用量，可为空
     * @return 模型名称，无法确定时为空
     */
    private String resolveUsedModel(ChatClient configured, AiChatRequest request, AiUsage usage) {
        String model = configured.getModel();
        if (model == null || model.isBlank()) {
            model = usage != null ? usage.getModel() : null;
        }
        return model == null || model.isBlank() ? request.getModel() : model;
    }

    /**
     * 探测模型是否可对话：与真实对话共用解析链路，只建客户端不发请求。
     *
     * @param model 模型名称，可为空
     * @return 探测结果
     */
    @Override
    public AiClientProbe probeChatClient(String model) {
        AiChatRequest request = new AiChatRequest();
        request.setModel(model);
        ChatClient client;
        try {
            client = resolveChatClient(request);
        } catch (Exception e) {
            log.warn("[AiChat] 探测对话客户端失败 model={}: {}", model, e.getMessage());
            return AiClientProbe.unavailable(model, "解析对话客户端失败: " + e.getMessage());
        }
        if (client == null) {
            return AiClientProbe.unavailable(request.getModel(),
                    "未找到模型 " + (model == null || model.isBlank() ? "默认客户端" : model) + " 对应的对话客户端");
        }
        if (client instanceof AggregateChatClient aggregate && aggregate.clientCount() == 0) {
            return AiClientProbe.unavailable(request.getModel(),
                    "聚合客户端未挂载任何子客户端，请在模型管理中配置提供商与密钥");
        }
        ChatClient configured = configureClient(client, request);
        ChatClientSetting setting = configured.getSetting();
        return AiClientProbe.available(configured.getModel(),
                setting != null ? setting.getProvider() : null,
                setting != null ? setting.getBaseUrl() : null,
                configured.getClass().getSimpleName());
    }

    /**
     * 把强类型结果投影为历史 Map 结构，键与顺序保持与旧实现一致。
     *
     * @param result 强类型对话结果
     * @return 兼容旧调用方的 Map
     */
    private Map<String, Object> toLegacyMap(AiChatResult result) {
        Map<String, Object> map = new LinkedHashMap<>(5);
        AiUsage usage = result.usage();
        map.put("text", result.text());
        if (!result.success()) {
            map.put("error", Map.of("message", result.errorMessage()));
        }
        map.put("promptTokens", usage != null && usage.getInputTokens() != null ? usage.getInputTokens() : 0);
        map.put("completionTokens", usage != null && usage.getOutputTokens() != null ? usage.getOutputTokens() : 0);
        map.put("totalTokens", usage != null && usage.getTotalTokens() != null ? usage.getTotalTokens() : 0);
        return map;
    }

    // ==================== 流式对话 ====================

    @Override
    public SseEmitter stream(AiChatRequest request, Consumer<String> onChunk, Consumer<String> onComplete,
                             Consumer<Throwable> onError) {
        return stream(request, onChunk, onComplete, onError, null);
    }

    @Override
    public SseEmitter stream(AiChatRequest request, Consumer<String> onChunk, Consumer<String> onComplete,
                             Consumer<Throwable> onError, Consumer<String> onThinkingChunk) {
        return streamWithUsage(request, onChunk,
                onComplete == null ? null : (reply, usage) -> onComplete.accept(reply), onError, onThinkingChunk);
    }

    @Override
    public SseEmitter streamWithUsage(AiChatRequest request, Consumer<String> onChunk,
                                      BiConsumer<String, com.chua.common.support.ai.AiUsage> onCompleteWithUsage,
                                      Consumer<Throwable> onError, Consumer<String> onThinkingChunk) {
        return streamWithUsage(request, onChunk, onCompleteWithUsage, onError, onThinkingChunk, null);
    }

    @Override
    public SseEmitter streamWithUsage(AiChatRequest request, Consumer<String> onChunk,
                                      BiConsumer<String, com.chua.common.support.ai.AiUsage> onCompleteWithUsage,
                                      Consumer<Throwable> onError, Consumer<String> onThinkingChunk,
                                      Consumer<com.chua.common.support.ai.agent.AgentTrace> onTrace) {
        return streamWithUsage(request, onChunk, onCompleteWithUsage, onError, onThinkingChunk, onTrace, null);
    }

    @Override
    public SseEmitter streamWithUsage(AiChatRequest request, Consumer<String> onChunk,
                                      BiConsumer<String, com.chua.common.support.ai.AiUsage> onCompleteWithUsage,
                                      Consumer<Throwable> onError, Consumer<String> onThinkingChunk,
                                      Consumer<com.chua.common.support.ai.agent.AgentTrace> onTrace,
                                      Consumer<List<ChatToolCallData>> onToolCall) {
        return streamWithUsage(request, onChunk, onCompleteWithUsage, onError,
                onThinkingChunk, onTrace, onToolCall, null);
    }

    @Override
    public SseEmitter streamWithUsage(AiChatRequest request, Consumer<String> onChunk,
                                      BiConsumer<String, com.chua.common.support.ai.AiUsage> onCompleteWithUsage,
                                      Consumer<Throwable> onError, Consumer<String> onThinkingChunk,
                                      Consumer<com.chua.common.support.ai.agent.AgentTrace> onTrace,
                                      Consumer<List<ChatToolCallData>> onToolCall,
                                      Consumer<com.chua.common.support.ai.agent.ConfirmationRequest> onConfirmation) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);

        ChatClient configured;
        try {
            ChatClient client = resolveChatClient(request);
            if (client == null) {
                emitter.completeWithError(new IllegalStateException("No available ChatClient for model: " + request.getModel()));
                return emitter;
            }
            configured = maybeWrapAgent(configureClient(client, request), request);
        } catch (Throwable e) {
            // 组装阶段（含 Agent 包装 / SPI 实例化）异常必须显式记录并经错误回调下发，
            // 否则外层 CompletableFuture 会静默吞掉，前端永远停在“工作中”
            log.error("[AiChat] 组装 Agent/客户端失败 model={}", request.getModel(), e);
            if (onError != null) {
                try {
                    onError.accept(e);
                } catch (Exception cbEx) {
                    log.debug("[ai] 错误回调执行失败，已忽略: {}", cbEx.getMessage());
                }
            }
            try {
                emitter.completeWithError(e);
            } catch (Exception cbEx) {
                log.debug("[ai] 下发错误失败，已忽略: {}", cbEx.getMessage());
            }
            return emitter;
        }
        StringBuilder fullResponse = new StringBuilder(256); // 回复长度不可预估
        final com.chua.common.support.ai.AiUsage[] lastUsage = new com.chua.common.support.ai.AiUsage[1];
        final long start = System.currentTimeMillis();
        final long[] firstTokenAt = new long[] { 0L };
        // 请求线程内捕获身份：流式回调整时在别的线程执行，届时取不到上下文
        final String usageUserId = AiUserContext.currentUserId();
        // 思考开关在请求线程快照：推理模型可能默认产出 reasoning，
        // 未开启深度思考时必须在回调里丢弃，避免前端仍渲染思考块
        final boolean forwardThinking = Boolean.TRUE.equals(request.getThink());

        CompletableFuture.runAsync(AiUserContext.capturing(() -> {
            try {
                configured.chat(promptOf(request),
                        response -> {
                            if (response.getTrace() != null) {
                                com.chua.common.support.ai.agent.AgentTrace trace = response.getTrace();
                                // 经回调转发给调用方（控制器），由其下发到前端
                                if (onTrace != null) {
                                    try {
                                        onTrace.accept(trace);
                                    } catch (Exception e) {
                                        log.warn("[AiChat] trace 回调失败: {}", e.getMessage());
                                    }
                                }
                                try {
                                    emitter.send(SseEmitter.event().name("trace").data(trace));
                                } catch (Exception e) {
                                    log.warn("[AiChat] SSE trace send failed: {}", e.getMessage());
                                }
                                return;
                            }
                            if (response.getConfirmationRequest() != null) {
                                // 人工确认请求：作为独立 SSE 事件下发，前端渲染批准/拒绝卡片
                                com.chua.common.support.ai.agent.ConfirmationRequest creq =
                                        response.getConfirmationRequest();
                                if (onConfirmation != null) {
                                    try {
                                        onConfirmation.accept(creq);
                                    } catch (Exception e) {
                                        log.warn("[AiChat] confirmation 回调失败: {}", e.getMessage());
                                    }
                                }
                                try {
                                    emitter.send(SseEmitter.event()
                                            .name("confirmation")
                                            .data(creq));
                                } catch (Exception e) {
                                    log.warn("[AiChat] SSE confirmation send failed: {}", e.getMessage());
                                }
                                return;
                            }
                            if (response.getState() == com.chua.common.support.ai.chat.ChatResponse.State.STOP
                                    && lastUsage[0] == null && response.getUsage() != null) {
                                // 聚合客户端会在底层客户端的带量 STOP 之后补发一个空 STOP，
                                // 无条件覆盖会把真实用量清空，导致 sys_ai_usage 恒为 0 行
                                lastUsage[0] = response.getUsage();
                            }
                            // 思考内容分流（深度思考），不受正文为空影响；未开启时丢弃
                            String reasoning = response.getReasoningContent();
                            if (forwardThinking && reasoning != null && !reasoning.isEmpty()
                                    && onThinkingChunk != null) {
                                try {
                                    onThinkingChunk.accept(reasoning);
                                } catch (Exception e) {
                                    log.debug("[ai] 回调执行失败，已忽略: {}", e.getMessage());
                                }
                            }
                            // 工具调用增量分流（函数调用），与正文相互独立
                            List<ChatToolCallData> toolCalls = response.getToolCalls();
                            if (toolCalls != null && !toolCalls.isEmpty() && onToolCall != null) {
                                try {
                                    onToolCall.accept(toolCalls);
                                } catch (Exception e) {
                                    log.warn("[AiChat] toolCall 回调失败: {}", e.getMessage());
                                }
                            }
                            String chunk = response.getContent() != null ? response.getContent() : "";
                            if (chunk.isEmpty()) {
                                return;
                            }
                            if (firstTokenAt[0] == 0L) {
                                firstTokenAt[0] = System.currentTimeMillis();
                            }
                            fullResponse.append(chunk);
                            try {
                                emitter.send(SseEmitter.event().name("message").data(chunk));
                            } catch (Exception e) {
                                log.warn("[AiChat] SSE send failed: {}", e.getMessage());
                            }
                            if (onChunk != null) {
                                try {
                                    onChunk.accept(chunk);
                                } catch (Exception e) {
                                    log.debug("[ai] 回调执行失败，已忽略: {}", e.getMessage());
                                }
                            }
                        },
                        () -> {
                            String reply = fullResponse.toString();
                            if (lastUsage[0] != null) {
                                try {
                                    enrichLatency(lastUsage[0], start, System.currentTimeMillis(), firstTokenAt[0]);
                                    if (lastUsage[0].getModel() == null) {
                                        lastUsage[0].setModel(request.getModel());
                                    }
                                    if (lastUsage[0].getProvider() == null) {
                                        lastUsage[0].setProvider(request.getProvider());
                                    }
                                    recordStreamUsage(lastUsage[0], usageUserId, request.getUsageSource());
                                } catch (Throwable e) {
                                    // 计量落库失败不得连带丢弃回复：本轮答案已经生成
                                    log.error("[AiChat] 流式计量落库失败: model={}, userId={}",
                                            request.getModel(), usageUserId, e);
                                }
                            }
                            if (onCompleteWithUsage != null) {
                                try {
                                    onCompleteWithUsage.accept(reply, lastUsage[0]);
                                } catch (Exception e) {
                                    log.error("[AiChat] 完成回调异常（回复已生成但未落库/未推送）: model={}",
                                            request.getModel(), e);
                                }
                            }
                            // Agent 运行时私有 MCP 连接在流结束后释放
                            if (configured instanceof AgentChatClient agentClient) {
                                agentClient.close();
                            }
                            emitter.complete();
                        },
                        error -> {
                            log.error("[AiChat] stream error: model={}", request.getModel(), error);
                            if (onError != null) {
                                try {
                                    onError.accept(error);
                                } catch (Exception e) {
                                    log.debug("[ai] 回调执行失败，已忽略: {}", e.getMessage());
                                }
                            }
                            // 出错同样释放 Agent 私有连接，避免 stdio 进程泄漏
                            if (configured instanceof AgentChatClient agentClient) {
                                agentClient.close();
                            }
                            emitter.completeWithError(error);
                        }
                );
            } catch (Throwable e) {
                log.error("[AiChat] stream failed: model={}", request.getModel(), e);
                if (onError != null) {
                    try {
                        onError.accept(e);
                    } catch (Exception ignored) {
                    }
                }
                // 兜底：启动/订阅阶段异常也要释放 Agent 私有连接
                if (configured instanceof AgentChatClient agentClient) {
                    agentClient.close();
                }
                emitter.completeWithError(e);
            }
        }));

        return emitter;
    }

    @Override
    public ChatClient prepareConfigured(AiChatRequest request) {
        ChatClient base = resolveChatClient(request);
        if (base == null) {
            return null;
        }
        return maybeWrapAgent(configureClient(base, request), request);
    }

    @Override
    public SseEmitter resume(AiChatRequest request, String replyId,
                             List<com.chua.common.support.ai.agent.ConfirmationDecision> decisions) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);

        ChatClient configured;
        try {
            ChatClient client = resolveChatClient(request);
            if (client == null) {
                emitter.completeWithError(new IllegalStateException(
                        "No available ChatClient for model: " + request.getModel()));
                return emitter;
            }
            configured = maybeWrapAgent(configureClient(client, request), request);
        } catch (Throwable e) {
            log.error("[AiChat] resume组装失败 model={}", request.getModel(), e);
            emitter.completeWithError(e);
            return emitter;
        }

        if (!(configured instanceof AgentChatClient wrapper)) {
            emitter.completeWithError(new IllegalStateException(
                    "当前会话不是 Agent 运行时，无法处理人工确认恢复"));
            return emitter;
        }

        final StringBuilder fullResponse = new StringBuilder(256);
        final AiUsage[] lastUsage = new AiUsage[1];
        final long start = System.currentTimeMillis();
        final long[] firstTokenAt = {0L};
        final String usageUserId = AiUserContext.currentUserId();
        final boolean forwardThinking = Boolean.TRUE.equals(request.getThink());

        CompletableFuture.runAsync(AiUserContext.capturing(() -> {
            try {
                wrapper.resume(replyId, decisions,
                        response -> {
                            if (response.getTrace() != null) {
                                try {
                                    emitter.send(SseEmitter.event()
                                            .name("trace").data(response.getTrace()));
                                } catch (Exception e) {
                                    log.warn("[AiChat] resume trace send failed: {}", e.getMessage());
                                }
                                return;
                            }
                            if (response.getConfirmationRequest() != null) {
                                try {
                                    emitter.send(SseEmitter.event()
                                            .name("confirmation")
                                            .data(response.getConfirmationRequest()));
                                } catch (Exception e) {
                                    log.warn("[AiChat] resume confirmation send failed: {}", e.getMessage());
                                }
                                return;
                            }
                            if (response.getState() == ChatResponse.State.STOP
                                    && lastUsage[0] == null && response.getUsage() != null) {
                                lastUsage[0] = response.getUsage();
                            }
                            String reasoning = response.getReasoningContent();
                            if (forwardThinking && reasoning != null && !reasoning.isEmpty()) {
                                try {
                                    emitter.send(SseEmitter.event().name("thinking").data(reasoning));
                                } catch (Exception e) {
                                    log.debug("[ai] 回调执行失败，已忽略: {}", e.getMessage());
                                }
                            }
                            String chunk = response.getContent() != null ? response.getContent() : "";
                            if (chunk.isEmpty()) {
                                return;
                            }
                            if (firstTokenAt[0] == 0L) {
                                firstTokenAt[0] = System.currentTimeMillis();
                            }
                            fullResponse.append(chunk);
                            try {
                                emitter.send(SseEmitter.event().name("message").data(chunk));
                            } catch (Exception e) {
                                log.warn("[AiChat] resume message send failed: {}", e.getMessage());
                            }
                        },
                        () -> {
                            if (lastUsage[0] != null) {
                                try {
                                    enrichLatency(lastUsage[0], start,
                                            System.currentTimeMillis(), firstTokenAt[0]);
                                    if (lastUsage[0].getModel() == null) {
                                        lastUsage[0].setModel(request.getModel());
                                    }
                                    if (lastUsage[0].getProvider() == null) {
                                        lastUsage[0].setProvider(request.getProvider());
                                    }
                                    recordStreamUsage(lastUsage[0], usageUserId, request.getUsageSource());
                                } catch (Throwable e) {
                                    log.error("[AiChat] resume计量落库失败: model={}", request.getModel(), e);
                                }
                            }
                            wrapper.close();
                            emitter.complete();
                        },
                        error -> {
                            log.error("[AiChat] resume error: model={}", request.getModel(), error);
                            wrapper.close();
                            emitter.completeWithError(error);
                        });
            } catch (Throwable e) {
                log.error("[AiChat] resume failed: model={}", request.getModel(), e);
                wrapper.close();
                emitter.completeWithError(e);
            }
        }));

        return emitter;
    }

    // ==================== 模型列表 ====================

    @Override
    public List<String> models(Integer groupId) {
        if (groupId == null) {
            return new ArrayList<>(chatClientRegistry.getModels());
        }

        SysAiGroup group = sysAiGroupMapper.selectById(groupId);
        if (group == null || group.getStatus() == null || group.getStatus() != 1) {
            return Collections.emptyList();
        }

        List<SysAiGroupModel> groupModels = sysAiGroupModelMapper.selectList(
                new LambdaQueryWrapper<SysAiGroupModel>()
                        .eq(SysAiGroupModel::getSysAiGroupId, groupId));

        if (groupModels.isEmpty()) {
            return new ArrayList<>(chatClientRegistry.getModels());
        }

        return chatClientRegistry.getModels().stream()
                .collect(Collectors.toList());
    }

    // ==================== 模型定义 ====================

    @Override
    public List<ModelDefinition> modelDefinitions(Integer groupId) {
        List<ModelDefinition> allDefs = chatClientRegistry.getModelDefinitions();
        if (groupId == null) {
            return allDefs;
        }

        List<String> groupModels = models(groupId);
        if (groupModels.isEmpty()) {
            return allDefs;
        }

        Set<String> modelSet = new HashSet<>(groupModels);
        return allDefs.stream()
                .filter(d -> d.getId() != null && modelSet.contains(d.getId()))
                .collect(Collectors.toList());
    }

    // ==================== 内部方法 ====================

    /**
     * 解析对话 对话客户端。
     *
     * <p>优先按请求模型名从 DB 动态构建（支持每个提供商独立代理），
     * DB 未命中时回退注册中心按模型/提供商/默认顺序查找。</p>
     *
     * @param request 对话请求
     * @return 底层 对话客户端，找不到返回 空
     */
    private ChatClient resolveChatClient(AiChatRequest request) {
        String model = resolveModelAlias(request.getModel());
        if (model != null && !model.equals(request.getModel())) {
            log.info("[AiChat] 模型别名映射: {} -> {}", request.getModel(), model);
            request.setModel(model);
        }
        // 安全网：请求里若还带着 <providerId>:<model> 限定名（系统默认模型就是这种存法，
        // 也有调用方直接透传前端 model.id），在此拆开。拆不出供应商时保持原样，
        // 交给后面的按名反查 + 告警处理，不要静默丢前缀。
        String modelProviderId = request.getProviderId();
        if (model != null && model.contains(":")) {
            int colon = model.indexOf(':');
            String prefix = model.substring(0, colon).trim();
            String bare = model.substring(colon + 1).trim();
            if (!prefix.isEmpty() && !bare.isEmpty()) {
                if (modelProviderId == null || modelProviderId.isBlank()) {
                    modelProviderId = prefix;
                }
                request.setProviderId(modelProviderId);
                log.info("[AiChat] 限定名拆分 {} -> 供应商={} 模型={}", model, modelProviderId, bare);
                model = bare;
                request.setModel(bare);
            }
        }
        String provider = request.getProvider();

        if (model != null && !model.isBlank()) {
            SysAiProvider dbProvider = resolveDbProviderByModel(model, modelProviderId);
            if (dbProvider != null) {
                ChatClient dbClient = getCachedDbClient(dbProvider, model);
                if (dbClient != null) {
                    if (request.getProvider() == null || request.getProvider().isBlank()) {
                        request.setProvider(dbProvider.getProviderId());
                    }
                    return dbClient;
                }
            }
        }

        if (model != null && !model.isBlank()) {
            ChatClient client = chatClientRegistry.getByModel(model);
            if (client != null) {
                return client;
            }
        }
        if (provider != null && !provider.isBlank()) {
            ChatClient client = chatClientRegistry.getByProvider(provider);
            if (client != null) {
                return client;
            }
        }
        return chatClientRegistry.getDefault();
    }

    /**
     * 按模型名查询 DB 中启用的 提供者 配置。
     *
     * @param model 模型名称
     * @return DB 中启用的 提供者，未命中返回 空
     */
    /**
     * 解析模型别名：若请求模型名命中 sys_ai_model.sys_ai_model_aliases 中的某个别名，
     * 则返回其对应的主模型 code；否则原样返回。
     *
     * <p>映射表带 5 分钟 TTL 缓存，避免每次对话都全表扫描模型定义。</p>
     * @param model 请求模型名（可为空）
     * @return 主模型 code 或原模型名
     */
    private String resolveModelAlias(String model) {
        if (model == null || model.isBlank()) {
            return model;
        }
        Map<String, String> map = loadAliasMap();
        String resolved = map.get(model);
        return resolved != null && !resolved.isBlank() ? resolved : model;
    }

    /**
     * 加载别名→主模型映射（**按账号分桶** + TTL 缓存）。
     *
     * <p>桶的取法：管理员与普通账号都只取自己名下的模型（管理员也不跨账号取别名 ——
     * 别名参与的是「改写谁发出的模型名」，跨账号取只会互相污染）；
     * 无身份上下文（系统线程/异步线程丢上下文）走独立桶，只加载无归属的历史行。</p>
     *
     * @return 只读映射，永不返回空
     */
    private Map<String, String> loadAliasMap() {
        String bucket = resolveAliasBucket();
        long now = System.currentTimeMillis();
        CachedAliasMap cached = aliasCacheByUser.get(bucket);
        if (cached != null && (now - cached.loadedAt) < ALIAS_CACHE_TTL_MILLIS) {
            return cached.map;
        }
        synchronized (aliasCacheByUser) {
            cached = aliasCacheByUser.get(bucket);
            if (cached != null && (System.currentTimeMillis() - cached.loadedAt) < ALIAS_CACHE_TTL_MILLIS) {
                return cached.map;
            }
            Map<String, String> map = new HashMap<>(64); // 别名数量不可预估
            boolean loadFailed = false;
            try {
                String owner = SYSTEM_ALIAS_BUCKET.equals(bucket) ? null : bucket;
                List<SysAiModel> models = aiModelMapper.selectList(
                        new LambdaQueryWrapper<SysAiModel>()
                                .eq(SysAiModel::getModelEnabled, true)
                                // owner 为 null（系统桶）时只取无归属历史行；
                                // 否则用 isNull 明确排除，避免把无主行混进任何人的别名表
                                .eq(owner != null, SysAiModel::getUserId, owner)
                                .isNull(owner == null, SysAiModel::getUserId));
                for (SysAiModel m : models) {
                    String code = m.getModelCode();
                    if (code == null || code.isBlank()) {
                        continue;
                    }
                    List<String> aliases = parseAliases(m.getModelAliases());
                    if (aliases != null) {
                        for (String alias : aliases) {
                            if (alias != null && !alias.isBlank()) {
                                map.putIfAbsent(alias.trim(), code.trim());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                loadFailed = true;
                log.warn("[AiChat] 加载模型别名映射失败: bucket={}, {}", bucket, e.getMessage());
            }
            // 加载失败时宁可继续用旧值，也不要把一份空表当成功写进去（否则别名会「凭空消失」）
            if (loadFailed && cached != null) {
                return cached.map;
            }
            aliasCacheByUser.put(bucket, new CachedAliasMap(map, now));
            log.info("[AiChat] 已加载模型别名映射 bucket={} 共 {} 条", bucket, map.size());
            return map;
        }
    }

    /**
     * 解析当前别名缓存桶：无身份上下文时返回系统桶。
     */
    private String resolveAliasBucket() {
        try {
            String userId = AiUserContext.currentUserId();
            return (userId == null || userId.isBlank()) ? SYSTEM_ALIAS_BUCKET : userId;
        } catch (Exception e) {
            return SYSTEM_ALIAS_BUCKET;
        }
    }

    /**
     * 别名映射缓存项（按账号分桶）。
     *
     * @param map      别名 → 主模型 code
     * @param loadedAt 加载时刻（毫秒）
     */
    private record CachedAliasMap(Map<String, String> map, long loadedAt) {
    }

    /**
     * 解析Aliases。
     *
     * @param content 内容，不允许为 null
     * @return 结果列表，无数据时为空列表
     */
    private List<String> parseAliases(String content) {
        if (content == null || content.isBlank()) {
            return new java.util.ArrayList<>();
        }
        try {
            if (content.trim().startsWith("[")) {
                @SuppressWarnings("unchecked")
                List<String> list = Json.fromJson(content, List.class);
                return list != null ? list : new java.util.ArrayList<>();
            }
            return java.util.Arrays.stream(content.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        } catch (Exception e) {
            return java.util.Arrays.stream(content.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    /**
     * 解析Db提供者By模型。
     *
     * <p>{@code providerIdHint} 来自请求里已限定的供应商（前端模型下拉的
     * {@code <providerId>:<model>} 拆分而来）。有它就只认这一家；没有它才按模型名反查。
     * 不同供应商允许同名模型，反查命中哪一行由数据库决定，所以命中多行时必须显式告警 ——
     * 否则用户选了 A 家却发到 B 家接口，B 家的配额/订阅报错会原样冒进 A 家的会话。</p>
     *
     * @param model          模型，不允许为 null
     * @param providerIdHint 供应商标识（可空）
     * @return SysAi提供者 对象
     */
    private SysAiProvider resolveDbProviderByModel(String model, String providerIdHint) {
        if (model == null || model.isBlank()) {
            return null;
        }
        try {
            List<SysAiProviderModel> candidates;
            if (providerIdHint != null && !providerIdHint.isBlank()) {
                candidates = sysAiProviderModelMapper.selectList(
                        new LambdaQueryWrapper<SysAiProviderModel>()
                                .eq(SysAiProviderModel::getProviderId, providerIdHint)
                                .eq(SysAiProviderModel::getModel, model)
                                .eq(SysAiProviderModel::getEnabled, true)
                                .last("LIMIT 1"));
                if (candidates.isEmpty()) {
                    log.warn("[AiChat] 供应商 {} 下未找到启用的模型 {}，回退按模型名反查", providerIdHint, model);
                }
            } else {
                candidates = sysAiProviderModelMapper.selectList(
                        new LambdaQueryWrapper<SysAiProviderModel>()
                                .eq(SysAiProviderModel::getModel, model)
                                .eq(SysAiProviderModel::getEnabled, true)
                                .orderByAsc(SysAiProviderModel::getProviderId));
                if (candidates.size() > 1) {
                    log.warn("[AiChat] 模型 {} 在 {} 个供应商下同名（{}），请求未指定供应商，取第一家；"
                                    + "若这不符合预期，请让调用方传 <providerId>:<model> 限定名",
                            model, candidates.size(),
                            candidates.stream().map(SysAiProviderModel::getProviderId).toList());
                }
            }
            for (SysAiProviderModel pm : candidates) {
                SysAiProvider provider = sysAiProviderMapper.selectById(pm.getProviderId());
                if (provider != null && Boolean.TRUE.equals(provider.getEnabled())) {
                    log.info("[AiChat] 路由 model={} -> 供应商={}({}) baseUrl={}",
                            model, provider.getProviderId(), provider.getPlatform(), provider.getBaseUrl());
                    return provider;
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("[AiChat] 解析 DB provider 失败 model={}: {}", model, e.getMessage());
            return null;
        }
    }

    /**
     * 从 DB 提供者 动态构建带代理的 对话客户端。
     *
     * <p>支持多 AppKey：聚合 provider 级凭证（{@code api_keys} 数组 / {@code apiKey}）
     * 与其所有 connection 级凭证中的全部可用密钥。单密钥时直接构建普通客户端；
     * 多密钥时每个密钥构建一个客户端并以 {@link AggregateChatClient}（failover 策略）
     * 聚合，某密钥 401/429/额度耗尽时自动切换下一密钥。</p>
     *
     * @param provider DB 提供者 配置
     * @param model    模型名称
     * @return 底层 对话客户端，构建失败返回 空
     */
    private ChatClient buildProviderChatClient(SysAiProvider provider, String model) {
        try {
            List<String> keys = collectAllApiKeys(provider);
            Map<String, String> extraHeaders = extractExtraHeaders(provider.getExtra());
            if (keys.isEmpty()) {
 // 无任何可用密钥：保留原行为（appKey 为 空），由下游/兜底处理
                ChatClientSetting setting = buildSetting(provider, null, extraHeaders);
                ChatClient raw = ChatClient.create(setting);
                return raw == null ? null : raw.model(model);
            }
            if (keys.size() == 1) {
                ChatClientSetting setting = buildSetting(provider, keys.getFirst(), extraHeaders);
                ChatClient raw = ChatClient.create(setting);
                if (raw == null) {
                    return null;
                }
                return raw.model(model);
            }
        // 多 appKey：逐密钥构建客户端，按 提供者 负载均衡算法聚合（默认 failover = 首个 Key 优先）
        String strategy = resolveLoadStrategy(provider);
        log.info("[AiChat] 构建多 AppKey 聚合客户端: provider={} model={} keys={} strategy={}",
                provider.getProviderId(), model, keys.size(), strategy);
        // 创建聚合客户端，先设置策略
        AggregateChatClientSetting agg = new AggregateChatClientSetting();
        agg.setStrategy(strategy);
        AggregateChatClient aggregateClient = new AggregateChatClient(agg);
        // 逐个创建底层客户端，设置 stream(true)
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            ChatClientSetting setting = buildSetting(provider, key, extraHeaders);
            ChatClient raw = ChatClient.create(setting);
            if (raw != null) {
                raw = raw.model(model);
                raw = raw.stream(true); // 启用流式
                aggregateClient.addClient("client-" + i, provider.getPlatform(), 1, raw);
            }
        }
        return aggregateClient.model(model);
        } catch (Exception e) {
            log.warn("[AiChat] 构建 DB ChatClient 失败 provider={} model={}: {}",
                    provider.getProviderId(), model, e.getMessage());
            return null;
        }
    }

    /**
     * 解析 提供者 的多 Key 负载均衡算法。
     *
     * <p>读 {@code extra.load_strategy}，缺省或非法时回退 {@code failover}（按序取第一个可用 key）。
     * 可选值：{@code failover} / {@code round_robin} / {@code weighted} / {@code latency} / {@code cost} / {@code hybrid}。</p>
     * @param provider 提供者
     * @return resolve加载strategy的结果
     */
    private String resolveLoadStrategy(SysAiProvider provider) {
        String s = null;
        try {
            Map<String, Object> extra = provider.getExtra();
            if (extra != null && extra.get("load_strategy") != null) {
                s = String.valueOf(extra.get("load_strategy")).trim().toLowerCase();
            }
        } catch (Exception e) {
            log.debug("[ai] 操作失败，已忽略: {}", e.getMessage());
        }
        if (s == null || s.isEmpty()) {
            return "failover";
        }
        // 归一化别名：roundrobin → round_robin
        if (s.equals("roundrobin") || s.equals("round-robin")) {
            return "round_robin";
        }
        return s;
    }

    /**
     * 收集 提供者 全部可用 appKey（去重、保序）：
     * 提供者 级凭证（{@code api_keys} 数组 + {@code apiKey} 标量）+ 各 connection 级凭证。
     *
     * @param provider DB 提供者
     * @return 全部可用密钥列表（可能为空）
     */
    private List<String> collectAllApiKeys(SysAiProvider provider) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String k : extractApiKeys(provider.getCredentials())) {
            keys.add(k);
        }
        List<SysAiProviderConnection> connections;
        try {
            connections = sysAiProviderConnectionMapper.selectList(
                    new LambdaQueryWrapper<SysAiProviderConnection>()
                            .eq(SysAiProviderConnection::getProviderId, provider.getProviderId()));
        } catch (Exception e) {
            log.debug("[AiChat] 查询 provider connections 失败: {}", e.getMessage());
            connections = new ArrayList<>(4); // 连接数量不可预估
        }
        for (SysAiProviderConnection conn : connections) {
            for (String k : extractApiKeys(conn.getCredentials())) {
                keys.add(k);
            }
        }
        return new ArrayList<>(keys);
    }

    /**
     * 从凭证中提取全部 API Key（形态兼容见 {@link ProviderCredentials}）。
     * @param credentials 凭证
     * @return extractapiKey的结果
     */
    private static List<String> extractApiKeys(Map<String, Object> credentials) {
        return ProviderCredentials.apiKeys(credentials);
    }

    /**
     * 应用请求级参数（模型/系统提示词/温度/最大 令牌/历史/思考/搜索）到客户端。
     *
     * @param client  客户端
     * @param request 对话请求
     * @return 配置后的客户端
     */
    private ChatClient configureClient(ChatClient client, AiChatRequest request) {
        ChatClient c = client;
        if (request.getModel() != null && !request.getModel().isBlank()) {
            c = c.model(request.getModel());
        }
        String system = request.getSystemPrompt();
        if (system != null && !system.isBlank()) {
            c = c.system(system);
        }
        // 技能不再在 facade 手动拼 system：解析出正文块后交由客户端内部并入 system
        String skills = "";
        if (Boolean.TRUE.equals(request.getUseSkill())) {
            List<String> pickedNames = request.getSkillNames();
            skills = (pickedNames != null && !pickedNames.isEmpty())
                    ? chatSkillSupport.promptBlock(pickedNames)
                    : chatSkillSupport.promptBlock();
        }
        // 客户端按 提供者::模型 缓存复用，技能块是实例字段：每轮必须显式写入，空串即清除上一轮残留
        c = c.skillPrompt(skills);
        if (!skills.isBlank()) {
            log.info("[ChatSkill] 技能交由客户端内部注入，长度={} 字符，显式选中={}",
                    skills.length(), request.getSkillNames() != null && !request.getSkillNames().isEmpty());
        }
        if (request.getTemperature() != null) {
            c = c.temperature(request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            c = c.maxTokens(request.getMaxTokens());
        }
         if (request.getTopP() != null) {
             c = c.topP(request.getTopP());
         }
         if (request.getFrequencyPenalty() != null) {
             c = c.extraBody(java.util.Map.of("frequency_penalty", request.getFrequencyPenalty()));
         }
        // 思考开关必须显式下发：客户端按 提供者::模型 缓存复用，
        // 只在开启时写 true 会让上一轮的思考状态残留到未开启的请求
        boolean thinkOn = Boolean.TRUE.equals(request.getThink());
        c = c.thinking(thinkOn);
        if (thinkOn && request.getParameters() != null && request.getParameters().get("thinking_effort") != null) {
            c = c.thinkingEffort(String.valueOf(request.getParameters().get("thinking_effort")));
        }
        if (Boolean.TRUE.equals(request.getSearch())) {
            c = c.smartSearch(true);
        }
        // 工具（函数调用）定义与选择策略透传
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            c = c.tools(request.getTools());
        }
        if (request.getToolChoice() != null && !request.getToolChoice().isBlank()) {
            c = c.toolChoice(request.getToolChoice());
        }
        // 启用流式
        c = c.stream(Boolean.TRUE.equals(request.getStream()));
        if (request.getImageUrls() != null) {
            for (String imageUrl : request.getImageUrls()) {
                if (imageUrl != null && !imageUrl.isBlank()) {
                    c = c.addImage(imageUrl);
                }
            }
        }
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            List<ChatMessage> history = new ArrayList<>(8); // 历史消息数量不可预估
            for (Map<String, Object> msg : request.getMessages()) {
                String role = msg.get("role") != null ? msg.get("role").toString() : "user";
                String content = msg.get("content") != null ? msg.get("content").toString() : "";
                ChatMessage.ChatMessageBuilder messageBuilder =
                        ChatMessage.builder().role(role).content(content);
                // tool 角色关联 tool_call_id
                if (msg.get("tool_call_id") != null) {
                    messageBuilder.toolCallId(msg.get("tool_call_id").toString());
                }
                // assistant 携带的 tool_calls（OpenAI 形态：{id,function:{name,arguments}}）
                Object rawToolCalls = msg.get("tool_calls");
                if (rawToolCalls instanceof List<?> toolCallList && !toolCallList.isEmpty()) {
                    List<com.chua.common.support.ai.chat.ChatToolCallData> toolCalls = new ArrayList<>();
                    for (Object item : toolCallList) {
                        if (item instanceof Map<?, ?> toolCallMap) {
                            com.chua.common.support.ai.chat.ChatToolCallData.ChatToolCallDataBuilder tb =
                                    com.chua.common.support.ai.chat.ChatToolCallData.builder();
                            if (toolCallMap.get("id") != null) {
                                tb.id(toolCallMap.get("id").toString());
                            }
                            Object fn = toolCallMap.get("function");
                            if (fn instanceof Map<?, ?> functionMap) {
                                if (functionMap.get("name") != null) {
                                    tb.name(functionMap.get("name").toString());
                                }
                                if (functionMap.get("arguments") != null) {
                                    tb.arguments(functionMap.get("arguments").toString());
                                }
                            }
                            toolCalls.add(tb.build());
                        }
                    }
                    if (!toolCalls.isEmpty()) {
                        messageBuilder.toolCalls(toolCalls);
                    }
                }
                history.add(messageBuilder.build());
            }
            c = c.history(history);
        }
        return c;
    }

    /**
     * 会话统一包装为 Agent 运行时客户端。
     *
     * <p>管理员与普通账号都走 Agent —— 统一之后大家才有各自的工作间
     * （{@code <基路径>/<账号>/agent}）。差别只在工具：</p>
     * <ul>
     *   <li><b>管理员</b>：按角色 settings.plugins 启用 MCP / 子代理 / 本地工具，
     *       且<b>不受</b>「普通账号工具权限」开关影响；</li>
     *   <li><b>普通账号</b>：开关关闭（默认）时插件集合整体清空，只建 agent、不给工具；
     *       开关打开后才按角色配置启用。</li>
     * </ul>
     * <p>请求 mode=plan 时首轮即进入只读规划。</p>
     *
     * @param configured 已完成参数配置的直连客户端
     * @param request    对话请求（携带 roleSettings、mode）
     * @return Agent 包装客户端或原直连客户端
     */
    private ChatClient maybeWrapAgent(ChatClient configured, AiChatRequest request) {
        boolean admin = AiUserContext.isAdmin();

        Map<String, Object> roleSettings = request.getRoleSettings();
        Map<?, ?> plugins = roleSettings != null && roleSettings.get("plugins") instanceof Map<?, ?> p
                ? p : null;

        // 工具权限总闸：默认只对管理员开放。
        // 关闸时把插件集合整体清空，等价于「建 agent 但不启用任何工具」——
        // 普通账号仍能得到 agent 运行时（从而拿到自己的工作间），只是没有工具。
        // 开关本身是管理员专属配置项，普通账号看不到也改不了（见 SettingsController 的三处过滤）。
        boolean userToolAccess = isUserToolAccessAllowed();
        if (!admin && !userToolAccess) {
            plugins = null;
        }
        if (!admin && !userToolAccess && (request.getRoleSettings() != null && request.getRoleSettings().get("plugins") != null)) {
            log.info("[AiChat] 普通账号工具权限未开启，本次不启用任何工具 userId={}", AiUserContext.currentUserId());
        }

        boolean mcpOn = false;
        boolean subAgentOn = false;
        if (plugins != null) {
            mcpOn = isEnabledNode(plugins.get("mcp"));
            subAgentOn = isEnabledNode(plugins.get("sub_agent"));
        }

        // Plan 只读模式（Build 为默认）；仅管理员经 Agent 生效
        boolean planOn = "plan".equalsIgnoreCase(request.getMode());

        // 会话工作间：只要有工作间就必须建 Agent —— HarnessAgent 的文件类工具以
        // workspace 为根，管理员若在"无能力"时早退，就拿不到 agent+workspace，
        // AI 只能回「我当前没有可用的文件工具来创建文件」（用户实测 2026-10-08：
        // 三个模型一致复现）。因此把 hasWorkspace 纳入早退判据。
        String agentWorkspace = resolveSessionWorkspace(request.getSessionId());
        boolean hasWorkspace = agentWorkspace != null && !agentWorkspace.isBlank();

        // MCP / 子代理 / Plan 均未开启：
        //  - 管理员且**本会话没有工作间**时保持原行为（直连，等价普通直答），避免给纯聊天凭空加一层 Agent 开销；
        //  - 普通账号、以及任何有工作间的会话仍然构建 Agent —— 他们要的正是「有自己的工作间」+ 文件工具。
        if (!mcpOn && !subAgentOn && !planOn && admin && !hasWorkspace) {
            log.debug("[AiChat] 管理员会话未启用任何 Agent 能力且无工作间，保持标准非 Agent 客户端 model={}",
                    request.getModel());
            return configured;
        }

        // MCP 启用时实时连接，构建私有运行时：
        //  - 会话级 serverIds 优先；否则按角色的 allow/deny 名单收敛（null=连接全部启用服务器）
        McpSelection mcpSelection = plugins != null ? extractMcpSelection(plugins.get("mcp")) : McpSelection.none();
        java.util.Collection<String> catalogIds = mcpOn ? mcpSelection.only() : java.util.List.of();
        java.util.Collection<String> excludeIds = mcpOn ? mcpSelection.exclude() : null;
        AgentMcpRuntimeService.McpRuntime runtime = mcpOn
                ? agentMcpRuntimeService.build(catalogIds, excludeIds) : null;

        // Agent 标识按会话稳定：初始轮与人工确认恢复轮（可能跨重启）必须落到同一
        // agent 目录 / 状态键，否则恢复时读不到待确认请求。String.hashCode 跨 JVM 稳定。
        String agentId;
        if (request.getSessionId() != null && !request.getSessionId().isBlank()) {
            agentId = "role-" + Integer.toHexString(request.getSessionId().hashCode());
        } else {
            agentId = "role-" + Integer.toHexString(System.identityHashCode(configured));
        }
        String instruction = request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()
                ? request.getSystemPrompt()
                : "You are a helpful assistant.";
        AgentDefinition definition = AgentDefinition.builder()
                .id(agentId)
                .name("Assistant")
                .instruction(instruction)
                .leader(subAgentOn)
                .build();
        Agent agent = agentProviderFactory.createAgent()
                .chatClient(configured)
                .definition(definition)
                // 子代理编排存在时 AUTO 路由，否则 SINGLE（主 Agent 直接调用工具）
                .mode(subAgentOn ? AgentMode.AUTO : AgentMode.SINGLE)
                .mcp(mcpOn);
        // 业务会话标识作为 defaultSessionId：进入 Plan / 读取 AgentState 必需，缺失会抛 NPE
        if (request.getSessionId() != null && !request.getSessionId().isBlank()) {
            agent.sessionId(request.getSessionId());
        }
        // 工作目录：以系统设置「通用设置 - 工作目录基路径」为根，每会话独立子目录，避免跨会话文件冲突
        // （复用上方已解析的 agentWorkspace：早退判据与这里必须看同一个值）
        if (agentWorkspace != null) {
            agent.workspace(agentWorkspace);
        }
        // shell 通道只留给管理员。
        // 文件工具由框架的路径白名单挡住越界（默认 ROOTED，实测 .. 与绝对路径都被拒），
        // 但 shell 是一条命令字符串，`cat /etc/passwd`、`curl` 外发这类都绕得过去——
        // 对「不准出工作间」的账号，只能把整条通道关掉，不能指望路径规则兜住。
        if (!admin) {
            agent.shellTool(false);
        }
        if (runtime != null) {
            agent.mcpManager(runtime.getManager());
        }
        // 角色技能必须注册成 Agent 的技能工具：Agent 运行时以 definition 的 system prompt
        // 为准，不会继承底层 ChatClient 的 skillPrompt，只靠提示词注入等于没生效。
        applySessionSkills(agent, request);
        if (planOn) {
            // 注册 Plan 能力并在首轮即进入只读，不产生文件写入/命令执行等副作用
            agent.plan(true).startInPlanMode(true);
            // 规划模式需要「只读调研若干轮 + plan_write 记录计划 + plan_exit 请求批准」，
            // 默认 5 轮会在 plan_write 当轮触发 ExceedMaxIters、把 plan_exit 挤掉。
            // 这里给规划模式独立的迭代预算（10），确保能走到 plan_exit 等待用户批准。
            agent.maxToolIterations(10);
        }
        AgentChatClient wrapper = new AgentChatClient(agent)
                .withCloseHook(runtime != null ? runtime::close : null);
        // 日志文案不能写死「管理员会话」：统一之后普通账号也走这条路径，
        // 写死会在排查时把人引到「这一定是管理员在操作」的错误结论上。
        log.info("[AiChat] 会话切换到 Agent 运行时 userId={}, admin={}, agentId={}, mode={}, mcp={}, subAgent={}, plan={}",
                AiUserContext.currentUserId(), admin, agentId, subAgentOn ? "AUTO" : "SINGLE",
                mcpOn, subAgentOn, planOn);
        return wrapper;
    }

    /**
     * 解析工作目录基路径：读取系统设置「通用设置 - 工作目录基路径」，确保其存在后返回。
     *
     * <p>注意：HarnessAgent 会在该基路径下再按会话标识（defaultSessionId）自动建立每会话
     * 独立的工作子目录（{@code <基路径>/<sessionId>/}），故此处<b>只返回基路径</b>，
     * 不再额外拼接 sessionId，避免出现 {@code base/<id>/<id>} 双重嵌套。</p>
     *
     * @param sessionId 业务会话标识（保留参数以便日志/扩展，当前不参与路径拼接）
     * @return 工作目录基路径绝对路径；未配置或创建失败时返回 null（回退底层默认目录）
     */
    private String resolveSessionWorkspace(String sessionId) {
        String base;
        try {
            base = globalSettingService.getValue("system", "workspaceBaseDir").orElse(null);
        } catch (Exception e) {
            log.warn("[AiChat] 读取工作目录基路径失败: {}", e.getMessage());
            return null;
        }
        if (base == null || base.isBlank()) {
            return null;
        }
        try {
            Path basePath = Paths.get(base.trim()).toAbsolutePath().normalize();
            // 账号段用于数据隔离：同一基路径下每个账号各自一棵子树，互不可见。
            // 账号标识必须净化成单个目录名，否则一个形如 ../../etc 的账号标识就能把
            // 工作间指到基路径之外——隔离在这里就要挡住，不能指望下游的路径拼接。
            Path accountRoot = basePath.resolve(safeSegment(AiUserContext.currentUserId()));
            /*
             * 这里**不要**拼会话段：会话隔离由 harness 自己负责。
             * harness 的 WorkspaceManager#getSessionDir(ctx, sessionId) 实际执行的是
             * `workspace.resolve("<sessionId>/")`，也就是说会话目录 = workspace/<会话id>。
             * 若这里再补一层会话段，workspace 变成 `<root>/agent/147` 后 harness 又会
             * 在其下建 `147/`，实测得到 `<root>/agent/147/147/acc-check.txt`（双嵌套）。
             *
             * 正确分工：
             *  - workspace（本方法返回值）= 账号级根 `<root>/<账号>/agent`，交给 agent.workspace()；
             *  - shell 的执行目录由 AgentScopeAgent 用 `workspace + 会话id` 单独设定
             *    （LocalFilesystemSpec.project），与 harness 的会话目录**重合**，因此不会双嵌套。
             */
            Path workspace = accountRoot.resolve("agent").normalize();
            // 二次确认：即便净化逻辑今后被改动，也不允许解析结果越出基路径
            if (!workspace.startsWith(basePath)) {
                log.warn("[AiChat] 工作间解析越界，已拒绝 userId={} 解析结果={}", AiUserContext.currentUserId(), workspace);
                return null;
            }
            Files.createDirectories(workspace);
            return workspace.toString();
        } catch (Exception e) {
            log.warn("[AiChat] 解析/创建工作目录基路径失败 base={}: {}", base, e.getMessage());
            return null;
        }
    }

    /**
     * 把账号标识净化为可安全用作单个目录名的片段。
     *
     * <p>与知识库上传目录同一套口径：只留字母数字与 {@code . _ -}，其余替换为下划线；
     * 空值回落 {@code anonymous}。这样账号标识不可能携带路径分隔符或 {@code ..}。</p>
     *
     * @param raw 原始账号标识
     * @return 安全的目录名片段
     */
    private static String safeSegment(String raw) {
        if (raw == null || raw.isBlank()) {
            return "anonymous";
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isEmpty() ? "anonymous" : cleaned;
    }

    /**
     * 普通账号是否被允许调用工具。
     *
     * <p>读「对话路由」分组的 {@code user_tool_access}（管理员专属配置项，默认 false）。
     * 读取失败一律按 false 处理：闸门出故障时应偏向收紧，不能偏向放开。</p>
     *
     * @return true 表示普通账号可用工具
     */
    private boolean isUserToolAccessAllowed() {
        try {
            String value = globalSettingService
                    .getValue("runtime", "user_tool_access")
                    .orElse(null);
            return Boolean.parseBoolean(String.valueOf(value).trim());
        } catch (Exception e) {
            log.warn("[AiChat] 读取 user_tool_access 失败，按关闭处理: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 判断插件节点是否启用：布尔节点直接取值；对象节点读取 enabled（缺省 false）。
     *
     * @param node 插件节点（Boolean 或 Map）
     * @return 是否启用
     */
    private static boolean isEnabledNode(Object node) {
        if (node instanceof Boolean b) {
            return b;
        }
        if (node instanceof Map<?, ?> m) {
            Object enabled = m.get("enabled");
            return enabled != null && Boolean.parseBoolean(String.valueOf(enabled));
        }
        return false;
    }

    /**
     * 角色 MCP 选择结果。
     *
     * <p>{@code only} 非 null 表示白名单（只连这些服务器）；{@code exclude} 非 null 表示
     * 黑名单（在「全部启用」基础上排除这些服务器）。二者互斥，最多一个非 null。</p>
     *
     * @param only    白名单服务器ID；null 表示不限定
     * @param exclude 黑名单服务器ID；null 表示不排除
     */
    private record McpSelection(java.util.Collection<String> only, java.util.Collection<String> exclude) {
        static McpSelection none() {
            return new McpSelection(null, null);
        }
    }

    /**
     * 解析角色 MCP 节点的服务器选择。
     *
     * <p>口径与角色配置面板一致：会话级 {@code serverIds} 优先；否则读角色写入的
     * {@code toolkits_filter=allow|deny} + {@code toolkits_allow|toolkits_deny}
     * （ID 带 {@code mcp_} 前缀，读取时剥掉）。都不满足时返回「不限定」，即连接全部启用服务器。</p>
     *
     * @param node MCP 插件节点
     * @return MCP 选择
     */
    private static McpSelection extractMcpSelection(Object node) {
        if (!(node instanceof Map<?, ?> m)) {
            return McpSelection.none();
        }
        // 会话级显式选择优先于角色默认
        java.util.Collection<String> sessionIds = toIdSet(m.get("serverIds"), null);
        if (sessionIds != null && !sessionIds.isEmpty()) {
            return new McpSelection(sessionIds, null);
        }
        String filter = m.get("toolkits_filter") != null
                ? String.valueOf(m.get("toolkits_filter")).trim().toLowerCase() : "";
        if ("allow".equals(filter)) {
            java.util.Collection<String> allow = toIdSet(m.get("toolkits_allow"), "mcp_");
            // 白名单模式：列表为空即「一个都不连」，绝不能退回「连全部」
            return new McpSelection(allow != null ? allow : java.util.List.of(), null);
        }
        if ("deny".equals(filter)) {
            java.util.Collection<String> deny = toIdSet(m.get("toolkits_deny"), "mcp_");
            if (deny != null && !deny.isEmpty()) {
                return new McpSelection(null, deny);
            }
        }
        return McpSelection.none();
    }

    /**
     * 把 JSON 数组节点转成字符串ID集合。
     *
     * @param node        JSON 节点
     * @param stripPrefix 需要剥离的前缀（可为 null）
     * @return ID 集合；节点不是数组时返回 null
     */
    private static java.util.Collection<String> toIdSet(Object node, String stripPrefix) {
        if (!(node instanceof List<?> list)) {
            return null;
        }
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        for (Object o : list) {
            if (o == null) {
                continue;
            }
            String v = String.valueOf(o).trim();
            if (stripPrefix != null && !stripPrefix.isEmpty() && v.startsWith(stripPrefix)) {
                v = v.substring(stripPrefix.length());
            }
            if (!v.isEmpty()) {
                ids.add(v);
            }
        }
        return ids;
    }

    /**
     * 把角色启用/会话选中的技能注册到 Agent 上，成为可被大模型调用的技能工具。
     *
     * <p>技能正文来自 {@link ChatSkillSupport#skills()}（磁盘 builtin/user 目录，受全局
     * 禁用名单约束）。{@code request.getSkillNames()} 已由编排器合并了即时选择与角色默认技能。</p>
     *
     * @param agent   目标 Agent 实例
     * @param request 对话请求（携带 skillNames）
     */
    private void applySessionSkills(Agent agent, AiChatRequest request) {
        List<String> names = request.getSkillNames();
        if (names == null || names.isEmpty()) {
            return;
        }
        java.util.Set<String> wanted = new java.util.LinkedHashSet<>();
        for (String n : names) {
            if (n != null && !n.isBlank()) {
                wanted.add(n.trim());
            }
        }
        if (wanted.isEmpty()) {
            return;
        }
        java.util.Map<String, SkillDefinition> defs = new java.util.LinkedHashMap<>();
        for (ChatSkillSupport.InstalledSkill skill : chatSkillSupport.skills()) {
            if (!skill.enabled() || !wanted.contains(skill.name()) || defs.containsKey(skill.name())) {
                continue;
            }
            defs.put(skill.name(), SkillDefinition.text(skill.name(), skill.description(), skill.content()));
        }
        for (SkillDefinition def : defs.values()) {
            agent.skill(def.getName(), def.getDescription(), def.getHandler());
        }
        if (!defs.isEmpty()) {
            log.info("[AiChat] 会话已注册角色技能 {} 个（选择 {} 个）: {}",
                    defs.size(), wanted.size(), defs.keySet());
        } else {
            log.warn("[AiChat] 角色选择了 {} 个技能，但本机未找到可用技能正文（可能未安装或已被全局禁用）: {}",
                    wanted.size(), wanted);
        }
    }

    /**
     * 提取用户本次输入文本。
     *
     * @param request 对话请求
     * @return 用户输入（可为空字符串）
     */
    private static String promptOf(AiChatRequest request) {
        return request.getPrompt() != null ? request.getPrompt() : "";
    }

    /**
     * recordusage。
     * @param client 客户端
     * @param callType call类型
     * @param response 响应
     */
    private void recordUsage(ChatClient client, String callType, ChatSyncResponse response, String userId) {
        recordUsage(client, callType, response != null ? response.getUsage() : null, userId);
    }

    /**
     * recordusage。
     * @param client 客户端
     * @param callType call类型
     * @param usage usage
     * @param userId 归属用户标识
     */
    private void recordUsage(ChatClient client, String callType, com.chua.common.support.ai.AiUsage usage, String userId) {
        if (usage == null) {
            return;
        }
        try {
            usageRecorderInterceptor.record(
                    "chat",
                    usage.getProvider(),
                    usage.getModel() != null ? usage.getModel() : client.getModel(),
                    callType,
                    usage,
                    UUID.randomUUID().toString(),
                    userId
            );
        } catch (Exception e) {
            log.warn("[UsageRecorder] 记录失败: {}", e.getMessage());
        }
    }

    /**
     * 记录流式对话用量。
     *
     * @param usage  用量
     * @param userId 请求线程捕获的归属用户标识
     */
    private void recordStreamUsage(com.chua.common.support.ai.AiUsage usage, String userId, String source) {
        recordUsage(usage, "chat_stream", userId, source);
    }

    /**
     * recordusage。
     * @param usage usage
     * @param callType call类型
     * @param userId 归属用户标识
     */
    private void recordUsage(com.chua.common.support.ai.AiUsage usage, String callType, String userId) {
        recordUsage(usage, callType, userId, null);
    }

    /**
     * recordusage，并显式标注用量来源。
     * @param source 用量来源 service/third_party/self，为空按默认口径
     */
    private void recordUsage(com.chua.common.support.ai.AiUsage usage, String callType, String userId, String source) {
        if (usage == null) {
            return;
        }
        try {
            usageRecorderInterceptor.record(
                    "chat",
                    usage.getProvider(),
                    usage.getModel(),
                    callType,
                    usage,
                    UUID.randomUUID().toString(),
                    userId,
                    source
            );
        } catch (Exception e) {
            log.warn("[UsageRecorder] 记录失败: {}", e.getMessage());
        }
    }

    /**
     * 回填调用时延（总耗时 / 首字时延）到 {@link com.chua.common.support.ai.AiUsage}，
     * 使持久化的 sys_AI_usage 携带 第一个_令牌_延迟 / total_延迟。
     *
     * @param usage       待回填的用量（可为 空）
     * @param start       调用开始时间戳（ms）
     * @param end         调用结束时间戳（ms）
     * @param firstTokenAt 首个内容 chunk 到达时间戳（ms）；0 表示未知（同步调用）
     */
    private void enrichLatency(com.chua.common.support.ai.AiUsage usage, long start, long end, long firstTokenAt) {
        if (usage == null) {
            return;
        }
        long duration = Math.max(0L, end - start);
        usage.setDurationMillis(duration);
        if (firstTokenAt > 0) {
            usage.setFirstTokenLatencyMillis(Math.max(0L, firstTokenAt - start));
        } else if (usage.getFirstTokenLatencyMillis() == null) {
 // 非流式（同步）无独立首字时刻：以整体耗时近似，避免统计被 空 拉低
            usage.setFirstTokenLatencyMillis(duration);
        }
    }

    /**
     * 获取（带缓存的）DB 提供者 对话客户端。
     *
     * @param provider DB 提供者
     * @param model    模型名称
     * @return 底层 对话客户端，构建失败返回 空
     */
    private ChatClient getCachedDbClient(SysAiProvider provider, String model) {
        String cacheKey = provider.getProviderId() + "::" + model;
        long now = System.currentTimeMillis();
        CachedDbClient cached = dbClientCache.get(cacheKey);
        if (cached != null && now - cached.builtAt() < DB_CLIENT_CACHE_TTL_MILLIS) {
            return cached.client();
        }
        ChatClient built = buildProviderChatClient(provider, model);
        if (built != null) {
            dbClientCache.put(cacheKey, new CachedDbClient(built, now));
        }
        return built;
    }

    /**
     * 从 提供者 extra 中提取 {@code custom_headers}。
     * @param extra extra
     * @return extractextra头部的结果
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> extractExtraHeaders(Map<String, Object> extra) {
        if (extra == null) {
            return null;
        }
        Object headers = extra.get("custom_headers");
        if (headers instanceof Map<?, ?> map) {
            Map<String, String> result = new HashMap<>(8); // 自定义头部数量不可预估
            map.forEach((k, v) -> {
                if (k != null && v != null) {
                    result.put(k.toString(), v.toString());
                }
            });
            return result.isEmpty() ? null : result;
        }
        return null;
    }

    /**
     * 组装 提供者 的对话客户端配置，含认证方案。
     *
     * <p>{@code auth_scheme} 决定是否发送认证头，{@code extra.auth_header} /
     * {@code extra.auth_prefix} 决定头名与前缀。例如 Dots Studio 只认
     * {@code api-key: <key>}，若仍发标准的 {@code Authorization: Bearer} 会被 401。</p>
     *
     * @param provider     DB 提供者
     * @param apiKey       API Key，可为 null
     * @param extraHeaders 自定义请求头
     * @return 客户端配置
     */
    private static ChatClientSetting buildSetting(SysAiProvider provider, String apiKey, Map<String, String> extraHeaders) {
        Map<String, Object> extra = provider.getExtra();
        return ChatClientSetting.builder()
                .provider(provider.getPlatform())
                .appKey(apiKey)
                .baseUrl(provider.getBaseUrl())
                .extraHeaders(extraHeaders)
                .authScheme(provider.getAuthScheme())
                .authHeader(extraString(extra, "auth_header"))
                .authPrefix(extraString(extra, "auth_prefix"))
                .proxy(provider.getProxy())
                .routes(ProviderRouteTargets.fromExtra(extra))
                .build();
    }

    /**
     * 读取 extra 中的字符串值。
     *
     * @param extra 扩展配置
     * @param key   键名
     * @return 字符串值；缺失返回 null
     */
    private static String extraString(Map<String, Object> extra, String key) {
        if (extra == null) {
            return null;
        }
        Object value = extra.get(key);
        return value == null ? null : value.toString();
    }
}
