package com.chua.starter.ai.support.registry;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.ai.chat.aggregate.AggregateChatClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 全局 ChatClient 注册中心。
 *
 * <p>作为 Spring Boot 容器的单例 Bean，管理所有 AI 大模型 ChatClient 实例。
 * 支持按 provider、model、capability 等多种维度索引和查找。
 *
 * <h2>注册体系</h2>
 * <pre>
 * ChatClientRegistry
 *   ├─ providerIndex    → "openai" → ChatClient, "zhipu" → ChatClient
 *   ├─ modelIndex       → "gpt-4o" → ChatClient, "glm-4" → ChatClient
 *   ├─ capabilityIndex  → "chat" → [ChatClient], "vision" → [ChatClient]
 *   ├─ defaultClient    → 默认（聚合）ChatClient
 *   └─ aggregateClient  → AggregateChatClient（多模型聚合路由）
 * </pre>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 *   // 注入注册中心
 *   @Autowired
 *   private ChatClientRegistry registry;
 *
 *   // 使用默认客户端（聚合）
 *   String answer = registry.getDefault().chatSync("你好");
 *
 *   // 按模型名查找
 *   ChatClient gpt4 = registry.getByModel("gpt-4o");
 *
 *   // 按 provider 查找
 *   ChatClient zhipu = registry.getByProvider("zhipu");
 *
 *   // 获取聚合客户端进行多模型路由
 *   AggregateChatClient aggregate = registry.getAggregate();
 *   aggregate.withTokenGroup("vip").chatSync("复杂问题");
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class ChatClientRegistry {

    /**
     * 按 provider 名称索引的 ChatClient
     */
    private final Map<String, ChatClient> providerIndex = new ConcurrentHashMap<>();

    /**
     * 按模型名称索引的 ChatClient
     */
    private final Map<String, ChatClient> modelIndex = new ConcurrentHashMap<>();

    /**
     * 按能力索引的 ChatClient 列表
     */
    private final Map<String, List<ChatClient>> capabilityIndex = new ConcurrentHashMap<>();

    /**
     * 默认 ChatClient（聚合客户端）
     */
    private volatile ChatClient defaultClient;

    /**
     * 聚合 ChatClient（多模型路由）
     */
    private volatile AggregateChatClient aggregateClient;

    /**
     * 已注册的模型定义列表（含 capabilities）
     */
    private final List<ModelDefinition> modelDefinitions = new CopyOnWriteArrayList<>();

    /**
     * 所有已注册的 ChatClient 列表
     */
    private final List<ChatClient> allClients = new CopyOnWriteArrayList<>();

    /**
     * 注册 ChatClient。
     *
     * @param provider   提供者标识（如 "openai", "zhipu", "hunyuan"）
     * @param modelName  模型名称（如 "gpt-4o", "glm-4"）
     * @param client     ChatClient 实例
     * @param capabilities 能力标签（如 "chat", "vision", "code"）
     */
    public void register(String provider, String modelName, ChatClient client, String... capabilities) {
        if (provider == null || client == null) {
            log.warn("[ChatClientRegistry] 注册参数不完整: provider={}, client={}", provider, client);
            return;
        }

        providerIndex.put(provider.toLowerCase(), client);

        if (modelName != null) {
            modelIndex.put(modelName.toLowerCase(), client);
        }

        if (capabilities != null) {
            for (String capability : capabilities) {
                capabilityIndex.computeIfAbsent(capability.toLowerCase(), k -> new CopyOnWriteArrayList<>())
                        .add(client);
            }
        }

        allClients.add(client);
        log.info("[ChatClientRegistry] 注册 ChatClient: provider={}, model={}, capabilities={}",
                provider, modelName, Arrays.toString(capabilities));
    }

    /**
     * 注册 ChatClient（简化版）。
     *
     * @param provider 提供者标识
     * @param client   ChatClient 实例
     */
    public void register(String provider, ChatClient client) {
        register(provider, null, client);
    }

    /**
     * 注册模型定义（带能力标签，供 /v1/models 与前端能力开关使用）。
     *
     * @param definition 模型定义，id 重复时覆盖
     */
    public void registerModel(ModelDefinition definition) {
        if (definition == null || definition.getId() == null) {
            return;
        }
        String id = definition.getId().toLowerCase();
        modelDefinitions.removeIf(d -> id.equals(d.getId().toLowerCase()));
        modelDefinitions.add(definition);
        log.info("[ChatClientRegistry] 注册模型定义: id={}, name={}, capabilities={}",
                definition.getId(), definition.getName(), definition.getCapabilities());
    }

    /**
     * 获取全部已注册的模型定义。
     *
     * @return 模型定义列表
     */
    public List<ModelDefinition> getModelDefinitions() {
        return Collections.unmodifiableList(modelDefinitions);
    }

    /**
     * 设置默认 ChatClient。
     *
     * @param client 默认 ChatClient
     */
    public void setDefault(ChatClient client) {
        this.defaultClient = client;
        log.info("[ChatClientRegistry] 设置默认 ChatClient: {}", client.getClass().getSimpleName());
    }

    /**
     * 设置聚合 ChatClient。
     *
     * @param client 聚合 ChatClient
     */
    public void setAggregate(AggregateChatClient client) {
        this.aggregateClient = client;
        log.info("[ChatClientRegistry] 设置聚合 ChatClient");
    }

    // ==================== 查询方法 ====================

    /**
     * 获取默认 ChatClient。
     *
     * @return 默认 ChatClient，未设置时返回 null
     */
    public ChatClient getDefault() {
        return defaultClient;
    }

    /**
     * 获取聚合 ChatClient。
     *
     * @return 聚合 ChatClient，未设置时返回 null
     */
    public AggregateChatClient getAggregate() {
        return aggregateClient;
    }

    /**
     * 按 provider 查找 ChatClient。
     *
     * @param provider 提供者标识
     * @return ChatClient，未找到返回 null
     */
    public ChatClient getByProvider(String provider) {
        return providerIndex.get(provider.toLowerCase());
    }

    /**
     * 按模型名称查找 ChatClient。
     *
     * @param modelName 模型名称
     * @return ChatClient，未找到返回 null
     */
    public ChatClient getByModel(String modelName) {
        return modelIndex.get(modelName.toLowerCase());
    }

    /**
     * 按能力查找 ChatClient 列表。
     *
     * @param capability 能力标签
     * @return ChatClient 列表，未找到返回空列表
     */
    public List<ChatClient> getByCapability(String capability) {
        return capabilityIndex.getOrDefault(capability.toLowerCase(), Collections.emptyList());
    }

    /**
     * 获取所有已注册的 ChatClient。
     *
     * @return ChatClient 列表
     */
    public List<ChatClient> getAll() {
        return Collections.unmodifiableList(allClients);
    }

    /**
     * 获取所有已注册的 provider 名称。
     *
     * @return provider 名称集合
     */
    public Set<String> getProviders() {
        return Collections.unmodifiableSet(providerIndex.keySet());
    }

    /**
     * 获取所有已注册的模型名称。
     *
     * @return 模型名称集合
     */
    public Set<String> getModels() {
        return Collections.unmodifiableSet(modelIndex.keySet());
    }

    /**
     * 判断某个 provider 是否已注册。
     *
     * @param provider 提供者标识
     * @return true 表示已注册
     */
    public boolean hasProvider(String provider) {
        return providerIndex.containsKey(provider.toLowerCase());
    }

    /**
     * 获取已注册的 ChatClient 数量。
     *
     * @return 数量
     */
    public int size() {
        return allClients.size();
    }

    // ==================== 生命周期 ====================

    /**
     * 关闭所有 ChatClient，释放资源。
     */
    public void closeAll() {
        for (ChatClient client : allClients) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("[ChatClientRegistry] 关闭 ChatClient 失败: {}", e.getMessage());
            }
        }
        providerIndex.clear();
        modelIndex.clear();
        capabilityIndex.clear();
        allClients.clear();
        defaultClient = null;
        aggregateClient = null;
        log.info("[ChatClientRegistry] 所有 ChatClient 已关闭");
    }
}
