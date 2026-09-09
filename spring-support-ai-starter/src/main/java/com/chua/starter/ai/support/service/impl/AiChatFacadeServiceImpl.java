package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.ChatMessage;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ChatSyncResponse;
import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.starter.ai.support.entity.SysAiGroup;
import com.chua.starter.ai.support.entity.SysAiGroupModel;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.mapper.SysAiGroupMapper;
import com.chua.starter.ai.support.mapper.SysAiGroupModelMapper;
import com.chua.starter.ai.support.interceptor.UsageRecorderInterceptor;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.ai.support.service.SysAiPricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * AI 对话门面服务实现。
 *
 * <p>通过 {@link ChatClientRegistry} 路由到对应的 {@link ChatClient}，
 * 完成同步/流式对话、模型列表查询与模型定义查询。</p>
 *
 * <p>当请求指定的模型/提供商在模型管理（DB provider）中配置时，优先按 DB
 * 中的平台/地址/凭证/代理动态构建 ChatClient，使每个提供商可独立配置代理
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

    private static final long SSE_TIMEOUT_MILLIS = 60_000L;

    private final ChatClientRegistry chatClientRegistry;
    private final SysAiGroupMapper sysAiGroupMapper;
    private final SysAiGroupModelMapper sysAiGroupModelMapper;
    private final SysAiPricingService sysAiPricingService;
    private final SysAiProviderMapper sysAiProviderMapper;
    private final SysAiProviderModelMapper sysAiProviderModelMapper;
    private final UsageRecorderInterceptor usageRecorderInterceptor;

    // ==================== 同步对话 ====================

    @Override
    public Map<String, Object> call(AiChatRequest request) {
        ChatClient client = resolveChatClient(request);
        if (client == null) {
            throw new IllegalStateException("No available ChatClient for model: " + request.getModel());
        }
        ChatClient configured = configureClient(client, request);
        try {
            long start = System.currentTimeMillis();
            ChatSyncResponse response = configured.chatSyncWithResponse(promptOf(request));
            long end = System.currentTimeMillis();
            enrichLatency(response != null ? response.getUsage() : null, start, end, 0L);
            recordUsage(configured, "chat", response);
            return buildResult(response);
        } catch (Exception e) {
            log.error("[AiChat] call failed: model={}", request.getModel(), e);
            return buildErrorResult(e);
        }
    }

    // ==================== 流式对话 ====================

    @Override
    public SseEmitter stream(AiChatRequest request, Consumer<String> onChunk, Consumer<String> onComplete) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);

        ChatClient client = resolveChatClient(request);
        if (client == null) {
            emitter.completeWithError(new IllegalStateException("No available ChatClient for model: " + request.getModel()));
            return emitter;
        }

        ChatClient configured = configureClient(client, request);
        StringBuilder fullResponse = new StringBuilder();
        final com.chua.common.support.ai.AiUsage[] lastUsage = new com.chua.common.support.ai.AiUsage[1];
        final long start = System.currentTimeMillis();
        final long[] firstTokenAt = new long[] { 0L };

        CompletableFuture.runAsync(() -> {
            try {
                configured.chat(promptOf(request),
                        response -> {
                            if (response.getState() == com.chua.common.support.ai.chat.ChatResponse.State.STOP) {
                                lastUsage[0] = response.getUsage();
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
                                } catch (Exception ignored) {
                                }
                            }
                        },
                        () -> {
                            if (lastUsage[0] != null) {
                                enrichLatency(lastUsage[0], start, System.currentTimeMillis(), firstTokenAt[0]);
                                recordUsage(lastUsage[0]);
                            }
                            String reply = fullResponse.toString();
                            if (onComplete != null) {
                                try {
                                    onComplete.accept(reply);
                                } catch (Exception ignored) {
                                }
                            }
                            emitter.complete();
                        },
                        error -> {
                            log.error("[AiChat] stream error: model={}", request.getModel(), error);
                            emitter.completeWithError(error);
                        }
                );
            } catch (Exception e) {
                log.error("[AiChat] stream failed: model={}", request.getModel(), e);
                emitter.completeWithError(e);
            }
        });

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
     * 解析对话 ChatClient。
     *
     * <p>优先按请求模型名从 DB 动态构建（支持每个提供商独立代理），
     * DB 未命中时回退注册中心按模型/提供商/默认顺序查找。</p>
     *
     * @param request 对话请求
     * @return 底层 ChatClient，找不到返回 null
     */
    private ChatClient resolveChatClient(AiChatRequest request) {
        String model = request.getModel();
        String provider = request.getProvider();

        if (model != null && !model.isBlank()) {
            SysAiProvider dbProvider = resolveDbProviderByModel(model);
            if (dbProvider != null) {
                ChatClient dbClient = buildProviderChatClient(dbProvider, model);
                if (dbClient != null) {
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
     * 按模型名查询 DB 中启用的 provider 配置。
     *
     * @param model 模型名称
     * @return DB 中启用的 provider，未命中返回 null
     */
    private SysAiProvider resolveDbProviderByModel(String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        try {
            SysAiProviderModel pm = sysAiProviderModelMapper.selectOne(
                    new LambdaQueryWrapper<SysAiProviderModel>()
                            .eq(SysAiProviderModel::getModel, model)
                            .eq(SysAiProviderModel::getEnabled, true)
                            .last("LIMIT 1"));
            if (pm == null) {
                return null;
            }
            SysAiProvider provider = sysAiProviderMapper.selectById(pm.getProviderId());
            return provider != null && Boolean.TRUE.equals(provider.getEnabled()) ? provider : null;
        } catch (Exception e) {
            log.warn("[AiChat] 解析 DB provider 失败 model={}: {}", model, e.getMessage());
            return null;
        }
    }

    /**
     * 从 DB provider 动态构建带代理的 ChatClient。
     *
     * @param provider DB provider 配置
     * @param model    模型名称
     * @return 底层 ChatClient，构建失败返回 null
     */
    private ChatClient buildProviderChatClient(SysAiProvider provider, String model) {
        try {
            String apiKey = extractApiKey(provider.getCredentials());
            Map<String, String> extraHeaders = extractExtraHeaders(provider.getExtra());
            ChatClientSetting setting = ChatClientSetting.builder()
                    .provider(provider.getPlatform())
                    .appKey(apiKey)
                    .baseUrl(provider.getBaseUrl())
                    .extraHeaders(extraHeaders)
                    .proxy(provider.getProxy())
                    .build();
            ChatClient raw = ChatClient.create(setting);
            if (raw == null) {
                return null;
            }
            return raw.model(model);
        } catch (Exception e) {
            log.warn("[AiChat] 构建 DB ChatClient 失败 provider={} model={}: {}",
                    provider.getProviderId(), model, e.getMessage());
            return null;
        }
    }

    /**
     * 应用请求级参数（模型/系统提示词/温度/最大 tokens/历史/思考/搜索）到客户端。
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
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            c = c.system(request.getSystemPrompt());
        }
        if (request.getTemperature() != null) {
            c = c.temperature(request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            c = c.maxTokens(request.getMaxTokens());
        }
        if (Boolean.TRUE.equals(request.getThink())) {
            c = c.thinking(true);
            if (request.getParameters() != null && request.getParameters().get("thinking_effort") != null) {
                c = c.thinkingEffort(String.valueOf(request.getParameters().get("thinking_effort")));
            }
        }
        if (Boolean.TRUE.equals(request.getSearch())) {
            c = c.smartSearch(true);
        }
        if (request.getImageUrls() != null) {
            for (String imageUrl : request.getImageUrls()) {
                if (imageUrl != null && !imageUrl.isBlank()) {
                    c = c.addImage(imageUrl);
                }
            }
        }
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            List<ChatMessage> history = new ArrayList<>();
            for (Map<String, Object> msg : request.getMessages()) {
                String role = msg.get("role") != null ? msg.get("role").toString() : "user";
                String content = msg.get("content") != null ? msg.get("content").toString() : "";
                history.add(new ChatMessage(role, content));
            }
            c = c.history(history);
        }
        return c;
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

    private void recordUsage(ChatClient client, String callType, ChatSyncResponse response) {
        recordUsage(client, callType, response != null ? response.getUsage() : null);
    }

    private void recordUsage(ChatClient client, String callType, com.chua.common.support.ai.AiUsage usage) {
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
                    null
            );
        } catch (Exception e) {
            log.warn("[UsageRecorder] 记录失败: {}", e.getMessage());
        }
    }

    private void recordUsage(com.chua.common.support.ai.AiUsage usage) {
        recordUsage(usage, "chat_stream");
    }

    private void recordUsage(com.chua.common.support.ai.AiUsage usage, String callType) {
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
                    null
            );
        } catch (Exception e) {
            log.warn("[UsageRecorder] 记录失败: {}", e.getMessage());
        }
    }

    /**
     * 回填调用时延（总耗时 / 首字时延）到 {@link com.chua.common.support.ai.AiUsage}，
     * 使持久化的 sys_ai_usage 携带 first_token_latency / total_latency。
     *
     * @param usage       待回填的用量（可为 null）
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
            // 非流式（同步）无独立首字时刻：以整体耗时近似，避免统计被 null 拉低
            usage.setFirstTokenLatencyMillis(duration);
        }
    }

    /**
     * 从 provider 凭证中提取 API Key（兼容 {@code api_keys} 数组与 {@code apiKey} 键）。
     */
    private static String extractApiKey(Map<String, Object> credentials) {
        if (credentials == null) {
            return null;
        }
        Object keys = credentials.get("api_keys");
        if (keys instanceof List<?> list && !list.isEmpty() && list.get(0) != null) {
            return list.get(0).toString();
        }
        Object key = credentials.get("apiKey");
        return key != null ? key.toString() : null;
    }

    /**
     * 从 provider extra 中提取 {@code custom_headers}。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> extractExtraHeaders(Map<String, Object> extra) {
        if (extra == null) {
            return null;
        }
        Object headers = extra.get("custom_headers");
        if (headers instanceof Map<?, ?> map) {
            Map<String, String> result = new HashMap<>();
            map.forEach((k, v) -> {
                if (k != null && v != null) {
                    result.put(k.toString(), v.toString());
                }
            });
            return result.isEmpty() ? null : result;
        }
        return null;
    }

    private Map<String, Object> buildResult(ChatSyncResponse response) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", response.getText() != null ? response.getText() : "");
        if (response.getUsage() != null) {
            result.put("promptTokens", response.getUsage().getInputTokens());
            result.put("completionTokens", response.getUsage().getOutputTokens());
            result.put("totalTokens", response.getUsage().getTotalTokens());
        } else {
            result.put("promptTokens", 0);
            result.put("completionTokens", 0);
            result.put("totalTokens", 0);
        }
        return result;
    }

    private Map<String, Object> buildErrorResult(Exception e) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", "");
        result.put("error", Map.of("message", e.getMessage() != null ? e.getMessage() : "Chat failed"));
        result.put("promptTokens", 0);
        result.put("completionTokens", 0);
        result.put("totalTokens", 0);
        return result;
    }
}
