package com.chua.starter.ai.support.registry;

import com.chua.common.support.ai.agent.Agent;
import com.chua.common.support.ai.agent.AgentDefinition;
import com.chua.common.support.ai.agent.AgentHookEvent;
import com.chua.common.support.ai.agent.AgentResponse;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.skill.SkillDefinition;
import com.chua.common.support.ai.skill.SkillManager;
import com.chua.common.support.ai.mcp.McpManager;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 全局 Agent 注册中心。
 *
 * <p>管理所有 AI Agent 实例，支持按名称、能力、分组等维度查找。
 * 与 {@link ChatClientRegistry} 配合，Agent 通过 对话客户端 与大模型通信。
 *
 * <h2>注册体系</h2>
 * <pre>
 * AgentRegistry
 *   ├─ agentIndex        → "dev-agent" → Agent, "search-agent" → Agent
 *   ├─ groupIndex        → "code" → [Agent], "search" → [Agent]
 *   ├─ capabilityIndex   → "code-gen" → [Agent], "analysis" → [Agent]
 *   ├─ masterAgent       → 主 Agent（Router/Auto 模式）
 *   └─ agentDefinitions  → AgentDefinition 列表（未初始化 Agent）
 * </pre>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 *   // 注入注册中心
 *   @Autowired
 *   private AgentRegistry registry;
 *
 *   // 获取主 Agent 执行对话
 *   AgentResponse response = registry.getMaster().run("帮我写代码");
 *
 *   // 按名称查找 Agent
 *   Agent devAgent = registry.getByName("dev-agent");
 *
 *   // 注册新 Agent
 *   registry.register("dev-agent", "开发助手", "代码开发专家",
 *       chatClient, AgentDefinition.builder().build());
 * }</pre>      chatClient, AgentDefinition.builder().build());
 * }</pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class AgentRegistry {

    /**
     * 按名称索引的 Agent
     */
    private final Map<String, Agent> agentIndex = new ConcurrentHashMap<>();

    /**
     * 按分组索引的 Agent 列表
     */
    private final Map<String, List<Agent>> groupIndex = new ConcurrentHashMap<>();

    /**
     * 按能力索引的 Agent 列表
     */
    private final Map<String, List<Agent>> capabilityIndex = new ConcurrentHashMap<>();

    /**
     * 主 Agent（Router/Auto 模式）
     */
    private volatile Agent masterAgent;

    /**
     * Agent 定义列表（延迟初始化）
     */
    private final List<AgentDefinition> pendingDefinitions = new CopyOnWriteArrayList<>();

    /**
     * 所有已注册的 Agent 列表
     */
    private final List<Agent> allAgents = new CopyOnWriteArrayList<>();

    /**
     * 全局 skill管理器（可选）
     */
    private volatile SkillManager skillManager;

    /**
     * 全局 mcp管理器（可选）
     */
    private volatile McpManager mcpManager;

    /**
     * 注册 Agent。
     *
     * @param name          Agent 名称
     * @param displayName   Agent 显示名称
     * @param description   Agent 描述
     * @param chatClient    关联的 对话客户端
     * @param definition    Agent 定义
     * @param groups        分组标签
     * @param capabilities  能力标签
     * @return 注册后的 Agent 实例
     */
    public Agent register(String name, String displayName, String description,
                          ChatClient chatClient, AgentDefinition definition,
                          String[] groups, String[] capabilities) {
        if (name == null) {
            log.warn("[AgentRegistry] Agent 名称不能为空");
            return null;
        }

        Agent agent = Agent.create("agentscope")
                .chatClient(chatClient)
                .definition(definition != null ? definition : AgentDefinition.builder()
                        .id(name)
                        .name(displayName != null ? displayName : name)
                        .description(description != null ? description : "")
                        .build());

        if (skillManager != null) {
            agent.skillManager(skillManager);
        }
        if (mcpManager != null) {
            agent.mcpManager(mcpManager);
        }

        agentIndex.put(name, agent);
        allAgents.add(agent);

        if (groups != null) {
            for (String group : groups) {
                groupIndex.computeIfAbsent(group, k -> new CopyOnWriteArrayList<>()).add(agent);
            }
        }

        if (capabilities != null) {
            for (String capability : capabilities) {
                capabilityIndex.computeIfAbsent(capability, k -> new CopyOnWriteArrayList<>()).add(agent);
            }
        }

        log.info("[AgentRegistry] 注册 Agent: name={}, displayName={}", name, displayName);
        return agent;
    }

    /**
     * 注册 Agent（简化版）。
     *
     * @param name        Agent 名称
     * @param chatClient  关联的 对话客户端
     * @param definition  Agent 定义
     * @return 注册后的 Agent 实例
     */
    public Agent register(String name, ChatClient chatClient, AgentDefinition definition) {
        return register(name, name, "", chatClient, definition, null, null);
    }

    /**
     * 注册已构建的 Agent 实例（保留子 Agent 独立模型/MCP/Skill 配置）。
     *
     * @param name         Agent 名称
     * @param agent        已构建的 Agent 实例
     * @param groups       分组标签
     * @param capabilities 能力标签
     * @return 注册后的 Agent 实例
     */
    public Agent registerInstance(String name, Agent agent, String[] groups, String[] capabilities) {
        if (name == null || agent == null) {
            log.warn("[AgentRegistry] 注册参数不完整: name={}, agent={}", name, agent);
            return null;
        }
 // 注入全局 skill管理器 / mcp管理器
        if (skillManager != null) {
            try {
                agent.skillManager(skillManager);
            } catch (Exception e) {
                log.warn("[AgentRegistry] 注入 SkillManager 失败: {}", e.getMessage());
            }
        }
        if (mcpManager != null) {
            try {
                agent.mcpManager(mcpManager);
            } catch (Exception e) {
                log.warn("[AgentRegistry] 注入 McpManager 失败: {}", e.getMessage());
            }
        }

        agentIndex.put(name, agent);
        allAgents.add(agent);

        if (groups != null) {
            for (String group : groups) {
                groupIndex.computeIfAbsent(group, k -> new CopyOnWriteArrayList<>()).add(agent);
            }
        }
        if (capabilities != null) {
            for (String capability : capabilities) {
                capabilityIndex.computeIfAbsent(capability, k -> new CopyOnWriteArrayList<>()).add(agent);
            }
        }

        log.info("[AgentRegistry] 注册 Agent 实例: name={}", name);
        return agent;
    }

    /**
     * 批量注册 Agent 定义（延迟初始化）。
     * <p>在 ChatClient 就绪后统一初始化。</p>
     *
     * @param definitions Agent 定义列表
     */
    public void registerDefinitions(List<AgentDefinition> definitions) {
        if (definitions != null) {
            pendingDefinitions.addAll(definitions);
            log.info("[AgentRegistry] 添加 {} 个 Agent 定义（待初始化）", definitions.size());
        }
    }

    /**
     * 设置主 Agent。
     * <p>主 Agent 负责路由和分发，支持 Router/Auto/Pipeline/FanOut 等模式。</p>
     *
     * @param agent 主 Agent
     */
    public void setMaster(Agent agent) {
        this.masterAgent = agent;
        log.info("[AgentRegistry] 设置主 Agent: {}", agent.getClass().getSimpleName());
    }

    /**
     * 设置全局 skill管理器。
     *
     * @param skillManager 技能管理器
     */
    public void setSkillManager(SkillManager skillManager) {
        this.skillManager = skillManager;
 // 为所有已注册 Agent 注入 skill管理器
        for (Agent agent : allAgents) {
            agent.skillManager(skillManager);
        }
        log.info("[AgentRegistry] 设置全局 SkillManager");
    }

    /**
     * 设置全局 mcp管理器。
     *
     * @param mcpManager MCP 管理器
     */
    public void setMcpManager(McpManager mcpManager) {
        this.mcpManager = mcpManager;
        for (Agent agent : allAgents) {
            agent.mcpManager(mcpManager);
        }
        log.info("[AgentRegistry] 设置全局 McpManager");
    }

    // ==================== 查询方法 ====================

    /**
     * 获取主 Agent。
     *
     * @return 主 Agent，未设置时返回 空
     */
    public Agent getMaster() {
        return masterAgent;
    }

    /**
     * 按名称获取 Agent。
     *
     * @param name Agent 名称
     * @return Agent，未找到返回 空
     */
    public Agent getByName(String name) {
        return agentIndex.get(name);
    }

    /**
     * 按分组获取 Agent 列表。
     *
     * @param group 分组标签
     * @return Agent 列表，未找到返回空列表
     */
    public List<Agent> getByGroup(String group) {
        return groupIndex.getOrDefault(group, Collections.emptyList());
    }

    /**
     * 按能力获取 Agent 列表。
     *
     * @param capability 能力标签
     * @return Agent 列表，未找到返回空列表
     */
    public List<Agent> getByCapability(String capability) {
        return capabilityIndex.getOrDefault(capability, Collections.emptyList());
    }

    /**
     * 获取所有已注册的 Agent。
     *
     * @return Agent 列表
     */
    public List<Agent> getAll() {
        return Collections.unmodifiableList(allAgents);
    }

    /**
     * 获取所有已注册的 提供者 名称。
     *
     * @return provider 名称集合
     */
    public Set<String> getNames() {
        return Collections.unmodifiableSet(agentIndex.keySet());
    }

    /**
     * 获取待初始化的 Agent 定义。
     *
     * @return Agent 定义列表
     */
    public List<AgentDefinition> getPendingDefinitions() {
        return Collections.unmodifiableList(pendingDefinitions);
    }

    /**
     * 判断 Agent 是否已注册。
     *
     * @param name Agent 名称
     * @return true 表示已注册
     */
    public boolean hasAgent(String name) {
        return agentIndex.containsKey(name);
    }

    /**
     * 注销 Agent（移除所有索引并关闭）。
     *
     * @param name Agent 名称
     * @return 是否注销成功（原已注册）
     */
    public boolean unregister(String name) {
        Agent agent = agentIndex.remove(name);
        if (agent == null) {
            return false;
        }
        allAgents.remove(agent);
        if (masterAgent == agent) {
            masterAgent = null;
        }
        groupIndex.values().forEach(list -> list.remove(agent));
        capabilityIndex.values().forEach(list -> list.remove(agent));
        try {
            agent.close();
        } catch (Exception e) {
            log.warn("[AgentRegistry] 关闭 Agent 失败: name={}", name, e);
        }
        log.info("[AgentRegistry] 注销 Agent: name={}", name);
        return true;
    }

    /**
     * 获取已注册的 Agent 数量。
     *
     * @return 数量
     */
    public int size() {
        return allAgents.size();
    }

    /**
     * 获取全局 skill管理器。
     *
     * @return SkillManager，未设置返回 空
     */
    public SkillManager getSkillManager() {
        return skillManager;
    }

    /**
     * 获取全局 mcp管理器。
     *
     * @return McpManager，未设置返回 空
     */
    public McpManager getMcpManager() {
        return mcpManager;
    }

    // ==================== 生命周期 ====================

    /**
     * 关闭所有 Agent，释放资源。
     */
    public void closeAll() {
        for (Agent agent : allAgents) {
            try {
                agent.close();
            } catch (Exception e) {
                log.warn("[AgentRegistry] 关闭 Agent 失败: {}", e.getMessage());
            }
        }
        if (masterAgent != null) {
            try {
                masterAgent.close();
            } catch (Exception e) {
                log.warn("[AgentRegistry] 关闭主 Agent 失败: {}", e.getMessage());
            }
        }
        agentIndex.clear();
        groupIndex.clear();
        capabilityIndex.clear();
        allAgents.clear();
        pendingDefinitions.clear();
        masterAgent = null;
        log.info("[AgentRegistry] 所有 Agent 已关闭");
    }
}
