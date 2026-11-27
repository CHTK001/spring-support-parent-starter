package com.chua.starter.ai.support.registry;

import com.chua.common.support.ai.agent.Agent;
import com.chua.common.support.ai.agent.AgentDefinition;
import com.chua.common.support.ai.agent.AgentHookEvent;
import com.chua.common.support.ai.agent.AgentResponse;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.skill.SkillDefinition;
import com.chua.common.support.ai.skill.SkillManager;
import com.chua.common.support.ai.mcp.McpManager;
import com.chua.deeplearning.support.agent.opencode.OpencodeServerAgent;
import com.chua.starter.ai.support.chat.ChatSkillSupport;
import com.chua.starter.ai.support.manage.api.AgentMcpRuntimeService;
import com.chua.starter.ai.support.configuration.runtime.AgentProviderFactory;
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

    /** 独立装配实例不参与后续全局管理器刷新，生命周期交给 AgentManager。 */
    private final Set<Agent> isolatedAgents = ConcurrentHashMap.newKeySet();

    /**
     * Agent 引擎工厂
     */
    private final AgentProviderFactory agentProviderFactory;

    /**
     * 构造 Agent 注册中心。
     *
     * @param agentProviderFactory Agent 引擎工厂
     */
    public AgentRegistry(AgentProviderFactory agentProviderFactory) {
        this.agentProviderFactory = agentProviderFactory;
    }

    /**
     * 全局 skill管理器（可选）
     */
    private volatile SkillManager skillManager;

    /**
     * 全局 mcp管理器（可选）
     */
    private volatile McpManager mcpManager;

    /** 原生 OpenCode Agent 的声明式 MCP 装配器。 */
    private volatile AgentMcpRuntimeService agentMcpRuntimeService;

    /** 原生 OpenCode Agent 的声明式 Skill 装配器。 */
    private volatile ChatSkillSupport chatSkillSupport;

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
    public synchronized Agent register(String name, String displayName, String description,
                          ChatClient chatClient, AgentDefinition definition,
                          String[] groups, String[] capabilities) {
        if (name == null) {
            log.warn("[AgentRegistry] Agent 名称不能为空");
            return null;
        }

        Agent agent = agentProviderFactory.createAgent()
                .chatClient(chatClient)
                .definition(definition != null ? definition : AgentDefinition.builder()
                        .id(name)
                        .name(displayName != null ? displayName : name)
                        .description(description != null ? description : "")
                        .build());
        try {
            return registerInstance(name, agent, groups, capabilities, true);
        } catch (RuntimeException e) {
            try {
                agent.close();
            } catch (Exception closeError) {
                log.warn("[AgentRegistry] 注册失败后的 Agent 清理失败: name={}", name, closeError);
            }
            throw e;
        }
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
        return registerInstance(name, agent, groups, capabilities, true);
    }

    /**
     * 登记已完成独立装配的实例时，可禁止全局管理器覆盖私有能力。
     * @param name 名称
     * @param agent 实例
     * @param groups 分组
     * @param capabilities 能力标签
     * @param inheritManagers 是否注入全局管理器
     * @return 实例
     */
    public synchronized Agent registerInstance(String name, Agent agent, String[] groups, String[] capabilities,
                                  boolean inheritManagers) {
        if (name == null || agent == null) {
            log.warn("[AgentRegistry] 注册参数不完整: name={}, agent={}", name, agent);
            return null;
        }
        if (agentIndex.get(name) == agent) {
            if (inheritManagers) {
                injectGlobalManagers(agent);
                configureNativeCapabilities(agent);
            }
            return agent;
        }
        // 注入全局能力；原生 OpenCode Agent 由独立配置入口声明式装配。
        if (inheritManagers) {
            injectGlobalManagers(agent);
            configureNativeCapabilities(agent);
        }

        // 替换仅关闭注册中心自有实例；独立托管实例仍由 AgentManager 释放。
        unregister(name, true);
        if (!inheritManagers) isolatedAgents.add(agent);
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
        configureNativeCapabilities(agent);
        log.info("[AgentRegistry] 设置主 Agent: {}", agent.getClass().getSimpleName());
    }

    /**
     * 设置全局 skill管理器。
     *
     * @param skillManager 技能管理器
     */
    public synchronized void setSkillManager(SkillManager skillManager) {
        this.skillManager = skillManager;
 // 为所有已注册 Agent 注入 skill管理器
        for (Agent agent : allAgents) {
            if (!isolatedAgents.contains(agent)) injectSkillManager(agent);
        }
        refreshNativeCapabilities();
        log.info("[AgentRegistry] 设置全局 SkillManager");
    }

    /**
     * 设置全局 mcp管理器。
     *
     * @param mcpManager MCP 管理器
     */
    public synchronized void setMcpManager(McpManager mcpManager) {
        this.mcpManager = mcpManager;
        for (Agent agent : allAgents) {
            if (!isolatedAgents.contains(agent)) injectMcpManager(agent);
        }
        refreshNativeCapabilities();
        log.info("[AgentRegistry] 设置全局 McpManager");
    }

    /**
     * 接入原生 Agent 的全局 MCP/Skill 配置入口，并刷新已注册的全局实例。
     * 独立托管实例不参与刷新，其配置及资源仍由 AgentManager 所有。
     * @param mcpRuntimeService 声明式 MCP 配置入口
     * @param skillSupport 声明式 Skill 配置入口
     */
    public synchronized void setNativeCapabilityServices(AgentMcpRuntimeService mcpRuntimeService,
                                                          ChatSkillSupport skillSupport) {
        this.agentMcpRuntimeService = mcpRuntimeService;
        this.chatSkillSupport = skillSupport;
        if (skillSupport != null) {
            skillSupport.setInvalidationListener(this::refreshGlobalCapabilities);
        }
        refreshNativeCapabilities();
    }

    /** 刷新全局原生 Agent 的声明式能力，不触碰独立实例。 */
    public synchronized void refreshGlobalCapabilities() {
        for (Agent agent : allAgents) {
            if (!isolatedAgents.contains(agent)) configureNativeCapabilities(agent);
        }
        Agent master = masterAgent;
        if (master != null && !allAgents.contains(master) && !isolatedAgents.contains(master)) {
            configureNativeCapabilities(master);
        }
    }

    private void refreshNativeCapabilities() {
        refreshGlobalCapabilities();
    }

    private void configureNativeCapabilities(Agent agent) {
        if (!(agent instanceof OpencodeServerAgent) || isolatedAgents.contains(agent)) {
            return;
        }
        ChatSkillSupport skills = chatSkillSupport;
        if (skills != null) {
            skills.configureAgent(agent, null);
        }
        AgentMcpRuntimeService mcp = agentMcpRuntimeService;
        if (mcp != null) {
            mcp.configure(agent, true, null, null);
        }
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
        return unregister(name, true);
    }

    /**
     * 移除名称索引；按 id 托管的实例由 AgentManager 统一关闭。
     * @param name 名称
     * @param close 是否关闭实例
     * @return 是否移除了索引
     */
    public synchronized boolean unregister(String name, boolean close) {
        Agent agent = agentIndex.remove(name);
        if (agent == null) {
            return false;
        }
        allAgents.remove(agent);
        boolean isolated = isolatedAgents.remove(agent);
        if (masterAgent == agent) {
            masterAgent = null;
        }
        groupIndex.values().forEach(list -> list.remove(agent));
        capabilityIndex.values().forEach(list -> list.remove(agent));
        try {
            if (close && !isolated) agent.close();
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

    /**
     * 向支持 Java 管理器的 Agent 注入全局能力。原生 opencode Agent 的 MCP/Skill
     * 必须通过其声明式配置入口装配，不能把 JVM 内管理器塞进 HTTP 进程。
     *
     * @param agent Agent 实例
     */
    private void injectGlobalManagers(Agent agent) {
        if (agent instanceof OpencodeServerAgent) {
            return;
        }
        injectSkillManager(agent);
        injectMcpManager(agent);
    }

    /**
     * 注入 SkillManager，并隔离不支持该能力的 Agent。
     *
     * @param agent Agent 实例
     */
    private void injectSkillManager(Agent agent) {
        if (skillManager == null || agent instanceof OpencodeServerAgent) {
            return;
        }
        try {
            agent.skillManager(skillManager);
        } catch (RuntimeException e) {
            log.warn("[AgentRegistry] 注入 SkillManager 失败: {}", e.getMessage());
        }
    }

    /**
     * 注入 McpManager，并隔离不支持该能力的 Agent。
     *
     * @param agent Agent 实例
     */
    private void injectMcpManager(Agent agent) {
        if (mcpManager == null || agent instanceof OpencodeServerAgent) {
            return;
        }
        try {
            agent.mcpManager(mcpManager);
        } catch (RuntimeException e) {
            log.warn("[AgentRegistry] 注入 McpManager 失败: {}", e.getMessage());
        }
    }

    // ==================== 生命周期 ====================

    /**
     * 关闭所有 Agent，释放资源。
     */
    public void closeAll() {
        for (Agent agent : allAgents) {
            try {
                if (!isolatedAgents.contains(agent)) agent.close();
            } catch (Exception e) {
                log.warn("[AgentRegistry] 关闭 Agent 失败: {}", e.getMessage());
            }
        }
        if (masterAgent != null && !allAgents.contains(masterAgent) && !isolatedAgents.contains(masterAgent)) {
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
        isolatedAgents.clear();
        pendingDefinitions.clear();
        masterAgent = null;
        log.info("[AgentRegistry] 所有 Agent 已关闭");
    }
}
