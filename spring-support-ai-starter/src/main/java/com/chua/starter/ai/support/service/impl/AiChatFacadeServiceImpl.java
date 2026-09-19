package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.ChatMessage;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.chat.ChatSyncResponse;
import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.ai.chat.aggregate.AggregateChatClient;
import com.chua.common.support.ai.chat.aggregate.AggregateChatClientSetting;
import com.chua.starter.ai.support.entity.SysAiGroup;
import com.chua.starter.ai.support.entity.SysAiGroupModel;
import com.chua.starter.ai.support.entity.settings.SysAiModel;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderConnectionMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderConnection;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.mapper.SysAiGroupMapper;
import com.chua.starter.ai.support.mapper.settings.AiModelMapper;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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

    /**
     * DB 提供者 多 appKey 聚合客户端缓存（提供者标识::模型 → 客户端，5 分钟 TTL）。
     * 避免每次对话都重建多客户端聚合实例；凭证变更后最多延迟 5 分钟生效。
     */
    private static final long DB_CLIENT_CACHE_TTL_MILLIS = 5 * 60 * 1000L;
    /**
     * 别名映射缓存 TTL（5 分钟）：别名 → 主模型 code，从 sys_ai_model.sys_ai_model_aliases 加载。
     */
    private static final long ALIAS_CACHE_TTL_MILLIS = 5 * 60 * 1000L;
    private volatile Map<String, String> aliasToModelCache;
    private volatile long aliasCacheLoadedAt;
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
        ChatClient client = resolveChatClient(request);
        if (client == null) {
            throw new IllegalStateException("No available ChatClient for model: " + request.getModel());
        }
        ChatClient configured = configureClient(client, request);
        try {
            long start = System.currentTimeMillis();
            ChatSyncResponse response = configured.chatSyncWithResponse(promptOf(request));
            long end = System.currentTimeMillis();
            /**
             * enrich延迟。
             * @param response.getUsage( 响应.获取usage(
             */
            enrichLatency(response != null ? response.getUsage() : null, start, end, 0L);
            /**
             * recordusage。
             * @param configured configured
             * @param "chat" "对话"
             * @param response 响应
             */
            recordUsage(configured, "chat", response);
            return buildResult(response);
        } catch (Exception e) {
            log.error("[AiChat] call failed: model={}", request.getModel(), e);
            return buildErrorResult(e);
        }
    }

    // ==================== 流式对话 ====================

    @Override
    public SseEmitter stream(AiChatRequest request, Consumer<String> onChunk, Consumer<String> onComplete,
                             Consumer<Throwable> onError) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);

        ChatClient client = resolveChatClient(request);
        if (client == null) {
            emitter.completeWithError(new IllegalStateException("No available ChatClient for model: " + request.getModel()));
            return emitter;
        }

        ChatClient configured = configureClient(client, request);
        StringBuilder fullResponse = new StringBuilder(256); // 回复长度不可预估
        final com.chua.common.support.ai.AiUsage[] lastUsage = new com.chua.common.support.ai.AiUsage[1];
        final long start = System.currentTimeMillis();
        final long[] firstTokenAt = new long[] { 0L };

        CompletableFuture.runAsync(() -> {
            try {
                log.info("[AiChat] DBG chat call begin, promptLen={}, class={}", promptOf(request)==null?-1:promptOf(request).length(), configured.getClass().getName());
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
                                /**
                                 * recordusage。
                                 * @param lastUsage[0] 最后一个usage[0]
                                 */
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
                            if (onError != null) {
                                try {
                                    onError.accept(error);
                                } catch (Exception ignored) {
                                }
                            }
                            emitter.completeWithError(error);
                        }
                );
                log.info("[AiChat] DBG configured.chat() returned normally");
            } catch (Throwable e) {
                log.error("[AiChat] stream failed: model={}", request.getModel(), e);
                if (onError != null) {
                    try {
                        onError.accept(e);
                    } catch (Exception ignored) {
                    }
                }
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
        String provider = request.getProvider();

        if (model != null && !model.isBlank()) {
            SysAiProvider dbProvider = resolveDbProviderByModel(model);
            if (dbProvider != null) {
                ChatClient dbClient = getCachedDbClient(dbProvider, model);
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
     * 加载别名→主模型映射（带 TTL 缓存）。
     * @return 只读映射，永不返回空
     */
    private Map<String, String> loadAliasMap() {
        long now = System.currentTimeMillis();
        Map<String, String> cached = aliasToModelCache;
        if (cached != null && (now - aliasCacheLoadedAt) < ALIAS_CACHE_TTL_MILLIS) {
            return cached;
        }
        synchronized (this) {
            if (aliasToModelCache != null && (System.currentTimeMillis() - aliasCacheLoadedAt) < ALIAS_CACHE_TTL_MILLIS) {
                return aliasToModelCache;
            }
            Map<String, String> map = new HashMap<>(64); // 别名数量不可预估
            try {
                List<SysAiModel> models = aiModelMapper.selectList(
                        new LambdaQueryWrapper<SysAiModel>().eq(SysAiModel::getModelEnabled, true));
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
                log.warn("[AiChat] 加载模型别名映射失败: {}", e.getMessage());
                if (aliasToModelCache != null) {
                    return aliasToModelCache;
                }
            }
            aliasToModelCache = map;
            aliasCacheLoadedAt = now;
            log.info("[AiChat] 已加载模型别名映射 {} 条", map.size());
            return map;
        }
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
     * @param model 模型，不允许为 null
     * @return SysAi提供者 对象
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
                ChatClientSetting setting = ChatClientSetting.builder()
                        .provider(provider.getPlatform())
                        .baseUrl(provider.getBaseUrl())
                        .extraHeaders(extraHeaders)
                        .proxy(provider.getProxy())
                        .build();
                ChatClient raw = ChatClient.create(setting);
                return raw == null ? null : raw.model(model);
            }
            if (keys.size() == 1) {
                ChatClientSetting setting = ChatClientSetting.builder()
                        .provider(provider.getPlatform())
                        .appKey(keys.getFirst())
                        .baseUrl(provider.getBaseUrl())
                        .extraHeaders(extraHeaders)
                        .proxy(provider.getProxy())
                        .build();
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
            ChatClientSetting setting = ChatClientSetting.builder()
                    .provider(provider.getPlatform())
                    .appKey(key)
                    .baseUrl(provider.getBaseUrl())
                    .extraHeaders(extraHeaders)
                    .proxy(provider.getProxy())
                    .build();
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
        } catch (Exception ignored) {
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
     * 从凭证中提取全部 API Key（兼容 {@code api_keys} 数组与 {@code apiKey} 键）。
     * @param credentials 凭证
     * @return extractapiKey的结果
     */
    private static List<String> extractApiKeys(Map<String, Object> credentials) {
        if (credentials == null) {
            return new ArrayList<>(1);
        }
        List<String> result = new ArrayList<>(2);
        Object keys = credentials.get("api_keys");
        if (keys instanceof List<?> list) {
            for (Object o : list) {
                if (o != null && !o.toString().isBlank()) {
                    result.add(o.toString());
                }
            }
        }
        Object key = credentials.get("apiKey");
        if (key != null && !key.toString().isBlank() && !result.contains(key.toString())) {
            result.add(key.toString());
        }
        return result;
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
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            c = c.system(request.getSystemPrompt());
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
        if (Boolean.TRUE.equals(request.getThink())) {
            c = c.thinking(true);
            if (request.getParameters() != null && request.getParameters().get("thinking_effort") != null) {
                c = c.thinkingEffort(String.valueOf(request.getParameters().get("thinking_effort")));
            }
        }
        if (Boolean.TRUE.equals(request.getSearch())) {
            c = c.smartSearch(true);
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

    /**
     * recordusage。
     * @param client 客户端
     * @param callType call类型
     * @param response 响应
     */
    private void recordUsage(ChatClient client, String callType, ChatSyncResponse response) {
        recordUsage(client, callType, response != null ? response.getUsage() : null);
    }

    /**
     * recordusage。
     * @param client 客户端
     * @param callType call类型
     * @param usage usage
     */
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

    /**
     * recordusage。
     * @param usage usage
     */
    private void recordUsage(com.chua.common.support.ai.AiUsage usage) {
        recordUsage(usage, "chat_stream");
    }

    /**
     * recordusage。
     * @param usage usage
     * @param callType call类型
     */
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
     * 构建结果。
     * @param response 响应
     * @return 构建结果的结果
     */
    private Map<String, Object> buildResult(ChatSyncResponse response) {
        Map<String, Object> result = new LinkedHashMap<>(4);
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

    /**
     * 构建错误结果。
     * @param e e
     * @return 构建错误结果的结果
     */
    private Map<String, Object> buildErrorResult(Exception e) {
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("text", "");
        result.put("error", Map.of("message", e.getMessage() != null ? e.getMessage() : "Chat failed"));
        result.put("promptTokens", 0);
        result.put("completionTokens", 0);
        result.put("totalTokens", 0);
        return result;
    }
}
