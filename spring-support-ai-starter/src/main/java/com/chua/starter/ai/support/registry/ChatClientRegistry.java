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
* 全局 对话客户端 注册中心。
*
* <p>作为 Spring Boot 容器的单例 Bean，管理所有 AI 大模型 ChatClient 实例。
* 支持按 提供者、模型、capability 等多种维度索引和查找。
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
* }</pre>*   aggregate.withTokenGroup("vip").chatSync("复杂问题");
* }</pre>
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
public class ChatClientRegistry {

    /**
    * 按 提供者 名称索引的 对话客户端
     */
    private final Map<String, ChatClient> providerIndex = new ConcurrentHashMap<>();

    /**
    * 按模型名称索引的 对话客户端
     */
    private final Map<String, ChatClient> modelIndex = new ConcurrentHashMap<>();

    /**
    * 按能力索引的 对话客户端 列表
     */
    private final Map<String, List<ChatClient>> capabilityIndex = new ConcurrentHashMap<>();

    /**
    * 默认 对话客户端（聚合客户端）
     */
    private volatile ChatClient defaultClient;

    /**
    * 聚合 对话客户端（多模型路由）
     */
    private volatile AggregateChatClient aggregateClient;

    /**
    * 已注册的模型定义列表（含 capabilities）
     */
    private final List<ModelDefinition> modelDefinitions = new CopyOnWriteArrayList<>();

    /**
    * 所有已注册的 对话客户端 列表
     */
    private final List<ChatClient> allClients = new CopyOnWriteArrayList<>();

    /**
    * 已注册为 智能体 模型的名称集合（提供者="智能体" 时的专用追踪）
     */
    private final Set<String> registeredAgentModelNames = ConcurrentHashMap.newKeySet();

    /**
    * 注册 对话客户端。
    *
    * @param provider   提供者标识（如 "openai", "zhipu", "hunyuan"）
    * @param modelName  模型名称（如 "gpt-4o", "glm-4"）
    * @param client     对话客户端 实例
    * @param capabilities 能力标签（如 "对话", "vision", "编码"）
     */
    public void register(String provider, String modelName, ChatClient client, String... capabilities) {
        if (provider == null || client == null) {
            log.warn("[ChatClientRegistry] 注册参数不完整: provider={}, client={}", provider, client);
            return;
        }

 // 幂等：同一 提供者（热更新/重复注册）时先注销旧 客户端，避免 全部客户端 无限膨胀
        ChatClient previous = providerIndex.put(provider.toLowerCase(), client);
        if (previous != null && previous != client) {
            allClients.remove(previous);
 // 同时清理旧 客户端 的模型索引（该模型现在指向新 客户端）
            modelIndex.entrySet().removeIf(e -> e.getValue() == previous);
            capabilityIndex.values().forEach(list -> list.remove(previous));
        }

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
    * 注册 对话客户端（简化版）。
    *
    * @param provider 提供者标识
    * @param client   对话客户端 实例
     */
    public void register(String provider, ChatClient client) {
        register(provider, null, client);
    }

    /**
    * 注册 智能体 模型（提供者="智能体"），同时追踪到 注册智能体模型名称。
    *
    * @param modelName    模型名称
    * @param client       对话客户端 实例
    * @param capabilities 能力标签
     */
    public void registerAgentModel(String modelName, ChatClient client, String... capabilities) {
        if (modelName == null || client == null) {
            log.warn("[ChatClientRegistry] 注册 Agent 模型参数不完整: modelName={}, client={}", modelName, client);
            return;
        }
        // 先幂等移除旧注册
        unregisterAgentModel(modelName);
        // 注册到通用索引
        register("agent", modelName, client, capabilities);
 // 追踪 智能体 模型名称
        registeredAgentModelNames.add(modelName);
        log.info("[ChatClientRegistry] 注册 Agent 模型: {}", modelName);
    }

    /**
    * 注销 智能体 模型（从所有索引 + 追踪集合中移除）。
    *
    * @param modelName 模型名称
     */
    public void unregisterAgentModel(String modelName) {
        if (modelName == null) {
            return;
        }
        String key = modelName.toLowerCase();
        registeredAgentModelNames.remove(modelName);
 // 通过 模型索引 找到要移除的 客户端
        ChatClient target = modelIndex.get(key);
        if (target != null) {
            // 从所有索引中移除
            providerIndex.values().remove(target);
            modelIndex.entrySet().removeIf(e -> e.getValue() == target);
            capabilityIndex.values().forEach(list -> list.remove(target));
            allClients.remove(target);
        }
        log.info("[ChatClientRegistry] 注销 Agent 模型: {}", modelName);
    }

    /**
    * 判断某个模型名称是否已注册为 智能体 模型。
    * @param modelName 模型名称
    * @return 是否智能体模型注册的结果
     */
    public boolean isAgentModelRegistered(String modelName) {
        if (modelName == null) {
            return false;
        }
        return registeredAgentModelNames.contains(modelName);
    }

    /**
    * 获取所有已注册的 智能体 模型名称。
    * @return 获取注册智能体模型的结果
     */
    public Set<String> getRegisteredAgentModels() {
        return Collections.unmodifiableSet(registeredAgentModelNames);
    }

    /**
    * 注册模型定义（带能力标签，供 /v1/模型 与前端能力开关使用）。
    *
    * @param definition 模型定义，标识 重复时覆盖
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
    * 设置默认 对话客户端。
    *
    * @param client 默认 对话客户端
     */
    public void setDefault(ChatClient client) {
        this.defaultClient = client;
        log.info("[ChatClientRegistry] 设置默认 ChatClient: {}", client.getClass().getSimpleName());
    }

    /**
    * 设置聚合 对话客户端。
    *
    * @param client 聚合 对话客户端
     */
    public void setAggregate(AggregateChatClient client) {
        this.aggregateClient = client;
        log.info("[ChatClientRegistry] 设置聚合 ChatClient");
    }

    // ==================== 查询方法 ====================

    /**
    * 获取默认 对话客户端。
    *
    * @return 默认 对话客户端，未设置时返回 空
     */
    public ChatClient getDefault() {
        return defaultClient;
    }

    /**
    * 获取聚合 对话客户端。
    *
    * @return 聚合 对话客户端，未设置时返回 空
     */
    public AggregateChatClient getAggregate() {
        return aggregateClient;
    }

    /**
    * 按 提供者 查找 对话客户端。
    *
    * @param provider 提供者标识
    * @return ChatClient，未找到返回 空
     */
    public ChatClient getByProvider(String provider) {
        return providerIndex.get(provider.toLowerCase());
    }

    /**
    * 按模型名称查找 对话客户端。
    *
    * @param modelName 模型名称
    * @return ChatClient，未找到返回 空
     */
    public ChatClient getByModel(String modelName) {
        return modelIndex.get(modelName.toLowerCase());
    }

    /**
    * 按能力查找 对话客户端 列表。
    *
    * @param capability 能力标签
    * @return ChatClient 列表，未找到返回空列表
     */
    public List<ChatClient> getByCapability(String capability) {
        return capabilityIndex.getOrDefault(capability.toLowerCase(), Collections.emptyList());
    }

    /**
    * 获取所有已注册的 对话客户端。
    *
    * @return ChatClient 列表
     */
    public List<ChatClient> getAll() {
        return Collections.unmodifiableList(allClients);
    }

    /**
    * 获取所有已注册的 提供者 名称。
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
    * 判断某个 提供者 是否已注册。
    *
    * @param provider 提供者标识
    * @return true 表示已注册
     */
    public boolean hasProvider(String provider) {
        return providerIndex.containsKey(provider.toLowerCase());
    }

    /**
    * 获取已注册的 对话客户端 数量。
    *
    * @return 数量
     */
    public int size() {
        return allClients.size();
    }

    // ==================== 生命周期 ====================

    /**
    * 关闭所有 对话客户端，释放资源。
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
