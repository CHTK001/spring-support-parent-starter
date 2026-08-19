package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.mapper.SysAiKnowledgeLibraryMapper;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 知识图谱 LLM 客户端注册中心，管理每个知识库独立的 LLM 客户端实例。
 * <p>
 * 所有知识库共享全局 runtime 组的 LLM 配置，但每个知识库拥有独立的 LLM 会话（Session）。
 * 当全局配置变更时，调用 {@link #rebuildAll()} 重建所有知识库的客户端。
 * </p>
 *
 * @author CH
 */
@Slf4j
@Component
public class KnowledgeGraphClientRegistry {

    /**
     * 配置分组
     */
    private static final String RUNTIME_GROUP = "runtime";

    /**
     * 全局设置服务
     */
    private final SysAiGlobalSettingService globalSettingService;

    /**
     * 知识库 Mapper
     */
    private final SysAiKnowledgeLibraryMapper libraryMapper;

    /**
     * ChatClient 提供者
     */
    private final ObjectProvider<ChatClient> chatClientProvider;

    /**
     * 知识库 LLM 客户端缓存
     */
    private final ConcurrentMap<Integer, KnowledgeGraphLlmClient> clientCache = new ConcurrentHashMap<>();

    /**
     * 全参构造。
     *
     * @param globalSettingService 全局设置服务
     * @param libraryMapper        知识库 Mapper
     * @param chatClientProvider   ChatClient 提供者
     */
    public KnowledgeGraphClientRegistry(SysAiGlobalSettingService globalSettingService,
                                        SysAiKnowledgeLibraryMapper libraryMapper,
                                        ObjectProvider<ChatClient> chatClientProvider) {
        this.globalSettingService = globalSettingService;
        this.libraryMapper = libraryMapper;
        this.chatClientProvider = chatClientProvider;
    }

    /**
     * 获取指定知识库的 LLM 客户端。
     *
     * @param libraryId 知识库ID，null 或 0 时使用全局默认客户端
     * @return LLM 客户端
     * @throws IllegalStateException 当 ChatClient 或配置不可用时
     */
    public KnowledgeGraphLlmClient getClient(Integer libraryId) {
        if (libraryId == null || libraryId <= 0) {
            return getDefaultClient();
        }
        return clientCache.computeIfAbsent(libraryId, this::buildClient);
    }

    /**
     * 重建指定知识库的 LLM 客户端（配置变更后调用）。
     */
    public void rebuild(Integer libraryId) {
        if (libraryId != null && libraryId > 0) {
            KnowledgeGraphLlmClient old = clientCache.remove(libraryId);
            closeQuietly(old);
            log.debug("知识库[{}]的 LLM 客户端已重建", libraryId);
        }
    }

    /**
     * 重建所有知识库的 LLM 客户端。
     */
    public void rebuildAll() {
        closeQuietly(defaultClient);
        defaultClient = null;
        clientCache.values().forEach(c -> closeQuietly(c));
        clientCache.clear();
        log.debug("所有知识库的 LLM 客户端已重建");
    }

    // ── private ─────────────────────────────────────────────────────

    /**
     * 全局默认知识图谱 LLM 客户端（懒构建）
     */
    private volatile KnowledgeGraphLlmClient defaultClient;

    /** 获取DefaultClient */
    private KnowledgeGraphLlmClient getDefaultClient() {
        if (defaultClient == null) {
            synchronized (this) {
                if (defaultClient == null) {
                    defaultClient = buildGlobalClient();
                }
            }
        }
        return defaultClient;
    }

    /** 构建GlobalClient */
    private KnowledgeGraphLlmClient buildGlobalClient() {
        KnowledgeGraphSettings settings = resolveRuntimeSettings();
        ChatClient chatClient = createChatClientSafe(settings);
        if (chatClient == null) {
            throw new IllegalStateException("[KnowledgeGraphClientRegistry] 无法创建 ChatClient, provider=" + settings.getProvider());
        }
        log.info("[KnowledgeGraphClientRegistry] 构建全局 LLM 客户端: model={}, provider={}", settings.getModel(), settings.getProvider());
        return new DefaultKnowledgeGraphLlmClient(settings, chatClient);
    }

    /** 构建Client */
    private KnowledgeGraphLlmClient buildClient(Integer libraryId) {
        KnowledgeGraphSettings settings = resolveRuntimeSettings();
        ChatClient chatClient = createChatClientSafe(settings);
        if (chatClient == null) {
            throw new IllegalStateException("[KnowledgeGraphClientRegistry] 无法创建 ChatClient for 知识库[" + libraryId + "], provider=" + settings.getProvider());
        }
        log.info("[KnowledgeGraphClientRegistry] 构建知识库[{}]的 LLM 客户端: model={}, provider={}", libraryId, settings.getModel(), settings.getProvider());
        return new DefaultKnowledgeGraphLlmClient(settings, chatClient);
    }

    /**
     * 安全创建 ChatClient：先尝试 chatClientProvider，失败则直接用 common SPI。
     * 如果 settings 指定了明确的 provider（非 openai），则优先用 common SPI 创建适配的客户端。
     */
    private ChatClient createChatClientSafe(KnowledgeGraphSettings settings) {
        // 如果知识库指定了特定的 provider（非默认 openai），优先走 common SPI
        String provider = settings.getProvider();
        if (provider != null && !provider.isBlank() && !"openai".equalsIgnoreCase(provider)) {
            log.info("[KnowledgeGraphClientRegistry] 知识图谱使用自定义 provider={}，走 common SPI 创建", provider);
            try {
                ChatClient client = createCommonChatClient(settings);
                if (client != null) return client;
            } catch (Exception e) {
                log.warn("[KnowledgeGraphClientRegistry] common SPI 创建失败 provider={}: {}", provider, e.getMessage());
            }
        }
        // 与全局 model 比较，如果不同则也用 common SPI
        try {
            ChatClient global = chatClientProvider.getObject();
            String globalModel = global != null ? global.getDefaultModel() : "";
            String targetModel = settings.getModel();
            if (targetModel != null && !targetModel.isBlank() && globalModel != null && !globalModel.equals(targetModel)) {
                log.info("[KnowledgeGraphClientRegistry] 知识图谱 model={} 与全局 model={} 不同，走 common SPI 创建", targetModel, globalModel);
                ChatClient client = createCommonChatClient(settings);
                if (client != null) return client;
            }
        } catch (Exception e) {
            log.debug("[KnowledgeGraphClientRegistry] 检查全局 model 失败: {}", e.getMessage());
        }
        // 回退到全局 chatClientProvider
        try {
            return chatClientProvider.getObject();
        } catch (Exception e) {
            log.warn("[KnowledgeGraphClientRegistry] chatClientProvider 获取失败: {}, 尝试直接创建", e.getMessage());
            try {
                return createCommonChatClient(settings);
            } catch (Exception e2) {
                log.error("[KnowledgeGraphClientRegistry] 直接创建 ChatClient 也失败: {}", e2.getMessage());
                return null;
            }
        }
    }

    /**
     * 绕过 DefaultChatClient（deeplearning），直接用 common SPI 创建 ChatClient，
     * 然后包装为 Spring ChatClient。
     */
    private ChatClient createCommonChatClient(KnowledgeGraphSettings settings) {
        if (settings.getBaseSetting() == null) {
            return null;
        }
        com.chua.common.support.ai.chat.ChatClientSetting baseSetting = settings.getBaseSetting();
        ChatClient.Factory factory = null;
        try {
            factory = (p, s) -> null;
        } catch (Exception e) {
            log.warn("[KnowledgeGraphClientRegistry] 找不到 ChatClient.Factory SPI 实现");
        }
        if (factory == null) {
            log.warn("[KnowledgeGraphClientRegistry] 找不到 ChatClient.Factory SPI 实现");
            return null;
        }
        com.chua.common.support.ai.chat.ChatClient commonClient = factory.create(settings.getProvider(), baseSetting);
        // 包装为 DefaultScopeChatClient（Spring ChatClient）
        com.chua.starter.ai.support.chat.ChatClientSettings cs = settings.toChatClientSettings();
        return new com.chua.starter.ai.support.chat.DefaultScopeChatClient(cs);
    }

    /**
     * 从全局设置解析 LLM 配置。
     * 优先读 knowledge_graph 组的 llm_provider，然后 fallback 到 runtime 组。
     */
    private KnowledgeGraphSettings resolveRuntimeSettings() {
        String provider = firstNonBlank(
                getGlobalSetting("knowledge_graph", "llm_provider"),
                getGlobalSetting(RUNTIME_GROUP, "provider"),
                "openai"
        );
        String model = firstNonBlank(
                getGlobalSetting("knowledge_graph", "llm_model"),
                getGlobalSetting(RUNTIME_GROUP, "default_model"),
                ""
        );

        ChatClientSetting baseSetting = buildRuntimeSetting();
        baseSetting = ChatClientSetting.builder()
                .provider(provider)
                .baseUrl(baseSetting.getBaseUrl())
                .appKey(baseSetting.getAppKey())
                .appSecret(baseSetting.getAppSecret())
                .build();

        return KnowledgeGraphSettings.builder()
                .provider(provider)
                .model(model)
                .temperature(getGlobalDouble(RUNTIME_GROUP, "temperature"))
                .maxTokens(parseNullableInt(getGlobalSetting(RUNTIME_GROUP, "max_tokens")))
                .extractionPromptTemplate(firstNonBlank(
                        getGlobalSetting("knowledge_graph", "extraction_prompt_template"),
                        ""
                ))
                .relationExtractionEnabled(getGlobalBoolean("knowledge_graph", "relation_extraction_enabled", true))
                .maxEntitiesPerDoc(getGlobalInt("knowledge_graph", "max_entities_per_doc", 50))
                .baseSetting(baseSetting)
                .build();
    }

    /** 构建RuntimeSetting */
    private ChatClientSetting buildRuntimeSetting() {
        return ChatClientSetting.builder()
                .provider("")
                .baseUrl(firstNonBlank(
                        getGlobalSetting(RUNTIME_GROUP, "provider_base_url"),
                        getGlobalSetting(RUNTIME_GROUP, "provider_api_address")
                ))
                .appKey(firstNonBlank(
                        getGlobalSetting(RUNTIME_GROUP, "provider_api_key"),
                        getGlobalSetting(RUNTIME_GROUP, "provider_app_key")
                ))
                .appSecret(firstNonBlank(
                        getGlobalSetting(RUNTIME_GROUP, "provider_app_secret"),
                        getGlobalSetting(RUNTIME_GROUP, "provider_secret_key")
                ))
                .build();
    }

    /** 获取GlobalSetting */
    private String getGlobalSetting(String group, String field) {
        return globalSettingService.getValue(group, field).orElse(null);
    }

    /** 获取GlobalBoolean */
    private boolean getGlobalBoolean(String group, String field, boolean defaultValue) {
        return globalSettingService.getBoolean(group, field, defaultValue);
    }

    /** 获取GlobalInt */
    private int getGlobalInt(String group, String field, int defaultValue) {
        String value = getGlobalSetting(group, field);
        if (StringUtils.isBlank(value)) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 获取GlobalDouble */
    private Double getGlobalDouble(String group, String field) {
        String value = getGlobalSetting(group, field);
        if (StringUtils.isBlank(value)) return null;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 解析NullableInt */
    private static Integer parseNullableInt(String value) {
        if (StringUtils.isBlank(value)) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** FirstNonBlank */
    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) return value;
        }
        return null;
    }

    /** 关闭Quietly */
    private static void closeQuietly(KnowledgeGraphLlmClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }
}
