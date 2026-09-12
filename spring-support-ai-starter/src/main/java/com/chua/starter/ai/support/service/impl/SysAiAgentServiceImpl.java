package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.ai.agent.Agent;
import com.chua.common.support.ai.agent.AgentDefinition;
import com.chua.common.support.ai.agent.AgentMode;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.starter.ai.support.chat.AgentChatClient;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.lang.json.Json;
import com.chua.starter.ai.support.manage.mapper.SysAiAgentMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.store.SysAiAgent;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.registry.AgentRegistry;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.service.SysAiAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

/**
   * AI 智能体 管理服务实现。
 *
 * <p>管理 {@code sys_ai_agent} 表中的主 Agent 与子 Agent，提供 CRUD、
 * 编排图保存/加载，以及将 DB 配置动态构建为底层 {@link Agent} 并注册到
 * {@link AgentRegistry} 的能力。每个 Agent 可独立配置模型、MCP、Skill、
   * 系统提示符 与策略。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiAgentServiceImpl implements SysAiAgentService {

    private final SysAiAgentMapper agentMapper; // 智能体映射器
    private final SysAiProviderMapper providerMapper; // 提供者映射器
    private final AgentRegistry agentRegistry; // 智能体registry
    private final ChatClientRegistry chatClientRegistry; // 对话客户端registry
    private final com.chua.starter.ai.support.service.SysAiGlobalSettingService globalSettingService; // 全局设置（持久化编排图）

    private static final String STRATEGY_KEY_MODE = "mode"; // strategy钥匙mode
    private static final String STRATEGY_KEY_PLANNING = "planning"; // strategy钥匙planning
    private static final String STRATEGY_KEY_MEMORY = "memory"; // strategy钥匙内存
    private static final String STRATEGY_KEY_MAX_TOOL_ITERATIONS = "max_tool_iterations"; // strategy钥匙最大tooliterations
    private static final String STRATEGY_KEY_TEMPERATURE = "temperature"; // strategy钥匙temperature
    private static final String STRATEGY_KEY_MAX_TOKENS = "max_tokens"; // strategy钥匙最大令牌

    // ==================== 查询 ====================

    @Override
    public List<Map<String, Object>> listAgents() {
        List<SysAiAgent> masters = agentMapper.selectList(
                new LambdaQueryWrapper<SysAiAgent>()
                        .isNull(SysAiAgent::getParentId)
                        .orderByAsc(SysAiAgent::getSortOrder)
                        .orderByAsc(SysAiAgent::getCreatedAt));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysAiAgent master : masters) {
            result.add(toResponse(master, true));
        }
        return result;
    }

    @Override
    public Map<String, Object> getAgent(String agentId) {
        SysAiAgent agent = requireAgent(agentId);
        return toResponse(agent, true);
    }

 // ==================== 主 智能体 CRUD ====================

    @Override
    @Transactional
    /**
     * 创建智能体。
     * @param body 主体
     * @return 创建智能体的结果
     */
    public Map<String, Object> createAgent(Map<String, Object> body) {
        long now = System.currentTimeMillis();
        SysAiAgent entity = new SysAiAgent();
        entity.setAgentId(IdUtils.uuidv7());
        /**
         * apply主体。
         * @param entity 实体
         * @param body 主体
         * @param true true
         */
        applyBody(entity, body, true);
        entity.setEnabled(bool(body, "enabled", true));
        entity.setSortOrder(intv(body, "sort_order", agentMapper.selectCount(null).intValue()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        agentMapper.insert(entity);
        /**
         * rebuild智能体。
         * @param entity 实体
         */
        rebuildAgent(entity);
        return toResponse(entity, true);
    }

    @Override
    @Transactional
    /**
     * 更新智能体。
     * @param agentId 智能体标识
     * @param body 主体
     * @return 更新智能体的结果
     */
    public Map<String, Object> updateAgent(String agentId, Map<String, Object> body) {
        SysAiAgent entity = requireAgent(agentId);
        /**
         * apply主体。
         * @param entity 实体
         * @param body 主体
         * @param false false
         */
        applyBody(entity, body, false);
        if (body.containsKey("enabled")) {
            entity.setEnabled(bool(body, "enabled", true));
        }
        if (body.containsKey("sort_order")) {
            entity.setSortOrder(intv(body, "sort_order", entity.getSortOrder()));
        }
        entity.setUpdatedAt(System.currentTimeMillis());
        agentMapper.updateById(entity);
        /**
         * rebuild智能体。
         * @param entity 实体
         */
        rebuildAgent(entity);
        return toResponse(entity, true);
    }

    @Override
    @Transactional
    /**
     * 删除智能体。
     * @param agentId 智能体标识
     */
    public void deleteAgent(String agentId) {
        SysAiAgent agent = requireAgent(agentId);
        agentRegistry.unregister(agent.getName());
        agentMapper.delete(new LambdaQueryWrapper<SysAiAgent>()
                .eq(SysAiAgent::getParentId, agentId));
        agentMapper.deleteById(agentId);
    }

 // ==================== 子 智能体 CRUD ====================

    @Override
    @Transactional
    /**
     * 创建sub智能体。
     * @param agentId 智能体标识
     * @param body 主体
     * @return 创建sub智能体的结果
     */
    public Map<String, Object> createSubAgent(String agentId, Map<String, Object> body) {
        /**
         * require智能体。
         * @param agentId 智能体标识
         */
        requireAgent(agentId);
        long now = System.currentTimeMillis();
        SysAiAgent entity = new SysAiAgent();
        entity.setAgentId(IdUtils.uuidv7());
        entity.setParentId(agentId);
        /**
         * apply主体。
         * @param entity 实体
         * @param body 主体
         * @param true true
         */
        applyBody(entity, body, true);
        entity.setEnabled(bool(body, "enabled", true));
        entity.setSortOrder(intv(body, "sort_order",
                Math.toIntExact(agentMapper.selectCount(new LambdaQueryWrapper<SysAiAgent>()
                        .eq(SysAiAgent::getParentId, agentId)))));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        agentMapper.insert(entity);
        SysAiAgent master = requireAgent(agentId);
        /**
         * rebuild智能体。
         * @param master master
         */
        rebuildAgent(master);
        return toResponse(entity, false);
    }

    @Override
    @Transactional
    /**
     * 更新sub智能体。
     * @param agentId 智能体标识
     * @param subId subid
     * @param body 主体
     * @return 更新sub智能体的结果
     */
    public Map<String, Object> updateSubAgent(String agentId, String subId, Map<String, Object> body) {
        /**
         * require智能体。
         * @param agentId 智能体标识
         */
        requireAgent(agentId);
        SysAiAgent entity = requireAgent(subId);
        if (entity.getParentId() == null || !entity.getParentId().equals(agentId)) {
            /**
             * illegal参数异常。
             * @param agentId 智能体标识
             * @return illegal参数异常的结果
             */
            throw new IllegalArgumentException("子 Agent " + subId + " 不属于主 Agent " + agentId);
        }
        /**
         * apply主体。
         * @param entity 实体
         * @param body 主体
         * @param false false
         */
        applyBody(entity, body, false);
        entity.setUpdatedAt(System.currentTimeMillis());
        agentMapper.updateById(entity);
        SysAiAgent master = requireAgent(agentId);
        /**
         * rebuild智能体。
         * @param master master
         */
        rebuildAgent(master);
        return toResponse(entity, false);
    }

    @Override
    @Transactional
    /**
     * 删除sub智能体。
     * @param agentId 智能体标识
     * @param subId subid
     */
    public void deleteSubAgent(String agentId, String subId) {
        /**
         * require智能体。
         * @param agentId 智能体标识
         */
        requireAgent(agentId);
        SysAiAgent entity = requireAgent(subId);
        if (entity.getParentId() == null || !entity.getParentId().equals(agentId)) {
            /**
             * illegal参数异常。
             * @param agentId 智能体标识
             * @return illegal参数异常的结果
             */
            throw new IllegalArgumentException("子 Agent " + subId + " 不属于主 Agent " + agentId);
        }
        agentMapper.deleteById(subId);
        SysAiAgent master = requireAgent(agentId);
        /**
         * rebuild智能体。
         * @param master master
         */
        rebuildAgent(master);
    }

    // ==================== 编排图 ====================

    @Override
    public Map<String, Object> saveOrchestration(String agentId, Map<String, Object> body) {
 // 编排图由子 智能体 的增删改与 X6 拖拽位置/连线组成。
        // 位置与连线由前端 X6 图在每次变更后通过本接口持久化。
        // 持久化 nodes + edges 到全局设置 orchestration 分组，供运行时编排路由读取。
        List<?> nodes = body.get("nodes") instanceof List<?> nl ? nl : List.of();
        List<?> edges = body.get("edges") instanceof List<?> el ? el : List.of();
        try {
            globalSettingService.upsertValue("orchestration", "graph_" + agentId + "_nodes", Json.toJson(nodes), "orchestration nodes");
            globalSettingService.upsertValue("orchestration", "graph_" + agentId + "_edges", Json.toJson(edges), "orchestration edges");
            Object mode = body.get("mode");
            if (mode != null) {
                globalSettingService.upsertValue("orchestration", "graph_" + agentId + "_mode", mode.toString(), "orchestration mode");
            }
        } catch (Exception e) {
            log.warn("[Agent] 持久化编排图失败 agentId={}: {}", agentId, e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agentId", agentId);
        result.put("edgesCount", edges.size());
        result.put("nodesCount", nodes.size());
        result.put("status", "persisted");
        return result;
    }

    @Override
    public Map<String, Object> getOrchestration(String agentId) {
        SysAiAgent master = requireAgent(agentId);
        // 优先返回前端 X6 保存的编排图（nodes + edges）；无持久化时回退到 DB 父子关系推导。
        try {
            Optional<String> persistedNodes = globalSettingService.getValue("orchestration", "graph_" + agentId + "_nodes");
            if (persistedNodes.isPresent() && persistedNodes.get() != null && !persistedNodes.get().isBlank()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("nodes", Json.fromJsonToList(persistedNodes.get(), Map.class));
                Optional<String> persistedEdges = globalSettingService.getValue("orchestration", "graph_" + agentId + "_edges");
                result.put("edges", persistedEdges.map(e -> Json.fromJsonToList(e, Map.class)).orElse(List.of()));
                result.put("agentId", agentId);
                Optional<String> persistedMode = globalSettingService.getValue("orchestration", "graph_" + agentId + "_mode");
                result.put("mode", persistedMode.orElseGet(() -> strategyStr(master, STRATEGY_KEY_MODE, "ROUTER")));
                result.put("persisted", true);
                return result;
            }
        } catch (Exception e) {
            log.warn("[Agent] 读取持久化编排图失败 agentId={}，回退 DB 推导: {}", agentId, e.getMessage());
        }
        List<SysAiAgent> subs = subAgentsOf(agentId);

        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, Object> masterNode = new LinkedHashMap<>();
        masterNode.put("id", master.getAgentId());
        masterNode.put("agent_id", master.getAgentId());
        masterNode.put("label", master.getName());
        masterNode.put("kind", "master");
        masterNode.put("position", Map.of("x", 400, "y", 100));
        nodes.add(masterNode);

        List<Map<String, Object>> edges = new ArrayList<>();
        int idx = 0;
        for (SysAiAgent sub : subs) {
            Map<String, Object> subNode = new LinkedHashMap<>();
            subNode.put("id", sub.getAgentId());
            subNode.put("agent_id", sub.getAgentId());
            subNode.put("label", sub.getName());
            subNode.put("kind", "sub-agent");
            subNode.put("position", Map.of("x", 200 + idx * 220, "y", 350));
            nodes.add(subNode);

            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("id", master.getAgentId() + "-" + sub.getAgentId());
            edge.put("source", master.getAgentId());
            edge.put("target", sub.getAgentId());
            edge.put("label", "routes to");
            edges.add(edge);
            idx++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("agentId", agentId);
        result.put("mode", strategyStr(master, STRATEGY_KEY_MODE, "ROUTER"));
        return result;
    }

    // ==================== 编排运行时路由（串行管线） ====================

    @Override
    public Map<String, Object> runOrchestration(String agentId, String input) {
        SysAiAgent master = requireAgent(agentId);
        String mode = strategyStr(master, STRATEGY_KEY_MODE, "ROUTER").toUpperCase();
        List<SysAiAgent> subs = subAgentsOf(agentId).stream()
                .filter(s -> Boolean.TRUE.equals(s.getEnabled()))
                .toList();
        Map<String, SysAiAgent> subById = new LinkedHashMap<>();
        for (SysAiAgent sub : subs) {
            subById.put(sub.getAgentId(), sub);
        }

        // 读取持久化编排图，按边做拓扑排序；无图时按 sort 顺序串行。
        List<String> order = new ArrayList<>();
        try {
            Optional<String> nodesJson = globalSettingService.getValue("orchestration", "graph_" + agentId + "_nodes");
            Optional<String> edgesJson = globalSettingService.getValue("orchestration", "graph_" + agentId + "_edges");
            if (nodesJson.isPresent() && edgesJson.isPresent()) {
                @SuppressWarnings({"rawtypes","unchecked"})
                List<Map<String, Object>> nodes = (List<Map<String, Object>>) (List) Json.fromJsonToList(nodesJson.get(), Map.class);
                @SuppressWarnings({"rawtypes","unchecked"})
                List<Map<String, Object>> edges = (List<Map<String, Object>>) (List) Json.fromJsonToList(edgesJson.get(), Map.class);
                order = topologicalOrder(nodes, edges, subById.keySet(), master.getAgentId());
            }
        } catch (Exception e) {
            log.warn("[Agent] 编排拓扑排序失败 agentId={}，回退 sort 顺序: {}", agentId, e.getMessage());
        }
        if (order.isEmpty()) {
            for (SysAiAgent sub : subs) {
                order.add(sub.getAgentId());
            }
        }

        Map<String, String> outputs = new LinkedHashMap<>();
        String current = input != null ? input : "";
        for (String subId : order) {
            SysAiAgent sub = subById.get(subId);
            if (sub == null) {
                continue;
            }
            ChatClient client = buildChatClient(sub);
            if (client == null) {
                log.warn("[Agent] 编排跳过子 Agent（无可用模型/凭证）: {}", subId);
                continue;
            }
            try {
                String prompt = current;
                if (sub.getSystemPrompt() != null && !sub.getSystemPrompt().isBlank()) {
                    client = client.system(sub.getSystemPrompt());
                }
                String out = client.chatSync(prompt);
                outputs.put(subId, out);
                // 简单串行模式：上一个子 Agent 的输出作为下一个子 Agent 的输入
                current = out != null ? out : "";
            } catch (Exception e) {
                log.warn("[Agent] 编排子 Agent 调用失败 subId={}: {}", subId, e.getMessage());
                outputs.put(subId, "ERROR: " + e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agentId", agentId);
        result.put("mode", mode);
        result.put("nodes", order.size());
        result.put("outputs", outputs);
        result.put("finalOutput", current);
        return result;
    }

    /**
      * Kahn 拓扑排序：按 edges(source→target) 对节点排序，跳过主节点与未注册子 Agent。
     */
    private static List<String> topologicalOrder(List<Map<String, Object>> nodes, List<Map<String, Object>> edges,
                                                 Set<String> validSubIds, String masterId) {
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> adj = new LinkedHashMap<>();
        for (Map<String, Object> node : nodes) {
            Object id = node.get("agent_id") != null ? node.get("agent_id") : node.get("id");
            if (id == null) {
                continue;
            }
            String sid = id.toString();
            if (!validSubIds.contains(sid) || sid.equals(masterId)) {
                continue;
            }
            indegree.putIfAbsent(sid, 0);
            adj.putIfAbsent(sid, new ArrayList<>());
        }
        for (Map<String, Object> edge : edges) {
            Object src = edge.get("source");
            Object tgt = edge.get("target");
            if (src == null || tgt == null) {
                continue;
            }
            String s = src.toString();
            String t = tgt.toString();
            if (!validSubIds.contains(t) || t.equals(masterId)) {
                continue;
            }
            adj.putIfAbsent(s, new ArrayList<>());
            if (!s.equals(masterId) && validSubIds.contains(s)) {
                adj.get(s).add(t);
                indegree.merge(t, 1, Integer::sum);
            } else {
                indegree.merge(t, 0, Integer::sum);
            }
            indegree.putIfAbsent(t, indegree.getOrDefault(t, 0));
        }
        List<String> queue = new ArrayList<>();
        for (Map.Entry<String, Integer> e : indegree.entrySet()) {
            if (e.getValue() == 0) {
                queue.add(e.getKey());
            }
        }
        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String cur = queue.remove(0);
            order.add(cur);
            for (String next : adj.getOrDefault(cur, List.of())) {
                int d = indegree.merge(next, -1, Integer::sum);
                if (d == 0) {
                    queue.add(next);
                }
            }
        }
        // 环或孤立节点兜底：追加尚未排序的合法子 Agent
        for (String id : indegree.keySet()) {
            if (!order.contains(id)) {
                order.add(id);
            }
        }
        return order;
    }

 // ==================== 智能体-as-模型 ====================

    @Override
    public Map<String, Object> registerAsModel(String agentId, String modelName) {
        SysAiAgent agent = requireAgent(agentId);
        if (agent.getParentId() != null) {
            /**
             * illegal参数异常。
             * @param 可以注册为模型" 可以注册为模型"
             * @return illegal参数异常的结果
             */
            throw new IllegalArgumentException("只有主 Agent 可以注册为模型");
        }
        String resolvedModel = modelName != null && !modelName.isBlank()
                ? modelName : "agent-" + agent.getName();
        /**
         * rebuild智能体。
         * @param agent 智能体
         */
        rebuildAgent(agent);

 // 智能体-as-模型：包装为 对话客户端 注册到对话注册中心
        Agent runtime = agentRegistry.getByName(agent.getName());
        if (runtime != null) {
            chatClientRegistry.registerAgentModel(resolvedModel, new AgentChatClient(runtime),
                    "chat", "tools", "skills", "routing");
            log.info("[Agent] Agent {} 已注册为模型 {}", agent.getName(), resolvedModel);
        } else {
            log.warn("[Agent] Agent 运行时不可用，模型 {} 未注册到对话链路", resolvedModel);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agentId", agentId);
        result.put("agentName", agent.getName());
        result.put("modelName", resolvedModel);
        result.put("status", "registered");
        return result;
    }

    @Override
    public void unregisterModel(String agentId) {
        SysAiAgent agent = requireAgent(agentId);
        String resolvedModel = agent.getName() != null ? "agent-" + agent.getName() : agentId;
        chatClientRegistry.unregisterAgentModel(resolvedModel);
        log.info("[Agent] Agent {} 已从模型注册中心注销", agent.getName());
    }

    @Override
    public Map<String, Object> getRegisteredModelInfo(String agentId) {
        SysAiAgent agent = requireAgent(agentId);
        String resolvedModel = "agent-" + agent.getName();
        boolean registered = chatClientRegistry.isAgentModelRegistered(resolvedModel);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("agentId", agentId);
        info.put("agentName", agent.getName());
        info.put("modelName", registered ? resolvedModel : null);
        info.put("registered", registered);
        return info;
    }

    @Override
    public Set<String> getRegisteredAgentModelNames() {
        return chatClientRegistry.getRegisteredAgentModels();
    }

    // ==================== 运行时构建 ====================

    @Override
    public void rebuildAgent(SysAiAgent agent) {
        if (agent == null || agent.getParentId() != null) {
            return;
        }
        try {
            String agentName = agent.getName();
            agentRegistry.unregister(agentName);

            ChatClient masterClient = buildChatClient(agent);
            AgentDefinition.Builder masterBuilder = AgentDefinition.builder()
                    .id(agent.getAgentId())
                    .name(agent.getName())
                    .description(agent.getDescription())
                    .role(agent.getRole())
                    .instruction(agent.getInstruction())
                    .leader(true)
                    .mcp(strategyBool(agent, "mcp", true))
                    .planning(strategyBool(agent, STRATEGY_KEY_PLANNING, false))
                    .memory(strategyBool(agent, STRATEGY_KEY_MEMORY, true));
            if (agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()) {
                masterBuilder.systemPrompt(agent.getSystemPrompt());
            }
            int maxToolIterations = strategyInt(agent, STRATEGY_KEY_MAX_TOOL_ITERATIONS, 5);
            if (maxToolIterations > 0) {
                masterBuilder.maxToolIterations(maxToolIterations);
            }

 // 子 智能体
            List<SysAiAgent> subs = subAgentsOf(agent.getAgentId());
            for (SysAiAgent sub : subs) {
                if (!Boolean.TRUE.equals(sub.getEnabled())) {
                    continue;
                }
                AgentDefinition.Builder subBuilder = AgentDefinition.builder()
                        .id(sub.getAgentId())
                        .name(sub.getName())
                        .description(sub.getDescription())
                        .role(sub.getRole())
                        .instruction(sub.getInstruction())
                        .leader(false)
                        .mcp(strategyBool(sub, "mcp", true))
                        .memory(strategyBool(sub, STRATEGY_KEY_MEMORY, true));
                if (sub.getSystemPrompt() != null && !sub.getSystemPrompt().isBlank()) {
                    subBuilder.systemPrompt(sub.getSystemPrompt());
                }
                masterBuilder.subAgent(subBuilder.build());
            }

            AgentDefinition definition = masterBuilder.build();
            Agent agentInstance = Agent.create("agentscope");
            if (agentInstance == null) {
                log.warn("[Agent] AgentScope 运行时不可用（缺少 agentscope SPI 实现），跳过运行时注册 name={}",
                        agentName);
                return;
            }
            agentInstance.definition(definition);
            if (masterClient != null) {
                agentInstance.chatClient(masterClient);
            }

 // 子 智能体 独立模型
            for (SysAiAgent sub : subs) {
                if (!Boolean.TRUE.equals(sub.getEnabled())) {
                    continue;
                }
                ChatClient subClient = buildChatClient(sub);
                if (subClient != null) {
                    agentInstance.chatClient(sub.getAgentId(), subClient);
                }
            }

            String mode = strategyStr(agent, STRATEGY_KEY_MODE, "ROUTER").toUpperCase();
            try {
                agentInstance.mode(AgentMode.valueOf(mode));
            } catch (IllegalArgumentException e) {
                log.warn("[Agent] 未知模式 {}，使用 AUTO", mode);
                agentInstance.mode(AgentMode.AUTO);
            }

            agentRegistry.registerInstance(agentName, agentInstance, new String[]{"custom"}, new String[]{"chat", "tools", "skills", "routing"});
            log.info("[Agent] 已构建并注册 Agent: name={}, mode={}, subAgents={}",
                    agentName, mode, subs.size());
        } catch (Exception e) {
            log.error("[Agent] 构建 Agent 失败: agentId={} name={}", agent.getAgentId(), agent.getName(), e);
        }
    }

    @Override
    public void rebuildAgentById(String agentId) {
        SysAiAgent agent = agentMapper.selectById(agentId);
        /**
         * rebuild智能体。
         * @param agent 智能体
         */
        rebuildAgent(agent);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建对话客户端。
     * @param agent 智能体
     * @return 构建对话客户端的结果
     */
    private ChatClient buildChatClient(SysAiAgent agent) {
        if (agent.getProviderId() == null || agent.getProviderId().isBlank()
                || agent.getModel() == null || agent.getModel().isBlank()) {
            return null;
        }
        SysAiProvider provider = providerMapper.selectById(agent.getProviderId());
        if (provider == null || provider.getBaseUrl() == null) {
            return null;
        }
        String apiKey = extractApiKey(provider.getCredentials());
        Map<String, String> extraHeaders = extractExtraHeaders(provider.getExtra());
        ChatClientSetting setting = ChatClientSetting.builder()
                .provider(provider.getPlatform())
                .appKey(apiKey)
                .baseUrl(provider.getBaseUrl())
                .extraHeaders(extraHeaders)
                .proxy(provider.getProxy())
                .build();
        return ChatClient.create(setting).model(agent.getModel());
    }

    /**
     * apply主体。
     * @param entity 实体
     * @param body 主体
     * @param create 创建
     */
    private void applyBody(SysAiAgent entity, Map<String, Object> body, boolean create) {
        if (body.containsKey("name")) {
            entity.setName(str(body, "name"));
        }
        if (body.containsKey("description")) {
            entity.setDescription(str(body, "description"));
        }
        if (body.containsKey("role")) {
            entity.setRole(str(body, "role"));
        }
        if (body.containsKey("instruction")) {
            entity.setInstruction(str(body, "instruction"));
        }
        if (body.containsKey("system_prompt")) {
            entity.setSystemPrompt(str(body, "system_prompt"));
        }
        if (body.containsKey("provider_id")) {
            entity.setProviderId(str(body, "provider_id"));
        }
        if (body.containsKey("model")) {
            entity.setModel(str(body, "model"));
        }
        if (body.containsKey("mcp_ids")) {
            entity.setMcpIds(stringList(body, "mcp_ids"));
        }
        if (body.containsKey("skill_ids")) {
            entity.setSkillIds(stringList(body, "skill_ids"));
        }
        if (body.containsKey("strategy")) {
            Object strategy = body.get("strategy");
            if (strategy instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> sm = (Map<String, Object>) m;
                entity.setStrategy(sm);
            } else {
                entity.setStrategy(null);
            }
        }
        if (create) {
            entity.setName(str(body, "name"));
        }
    }

    /**
     * 转为响应。
     * @param agent 智能体
     * @param includeSubs includesubs
     * @return 转为响应的结果
     */
    private Map<String, Object> toResponse(SysAiAgent agent, boolean includeSubs) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("agent_id", agent.getAgentId());
        map.put("parent_id", agent.getParentId());
        map.put("name", agent.getName());
        map.put("description", agent.getDescription());
        map.put("role", agent.getRole());
        map.put("instruction", agent.getInstruction());
        map.put("system_prompt", agent.getSystemPrompt());
        map.put("provider_id", agent.getProviderId());
        map.put("model", agent.getModel());
        map.put("mcp_ids", agent.getMcpIds() != null ? agent.getMcpIds() : List.of());
        map.put("skill_ids", agent.getSkillIds() != null ? agent.getSkillIds() : List.of());
        map.put("strategy", agent.getStrategy() != null ? agent.getStrategy() : Map.of());
        map.put("enabled", agent.getEnabled());
        map.put("sort_order", agent.getSortOrder());
        map.put("created_at", agent.getCreatedAt());
        map.put("updated_at", agent.getUpdatedAt());
        if (agent.getParentId() == null) {
            String resolvedModel = "agent-" + agent.getName();
            map.put("registered_model_name", chatClientRegistry.isAgentModelRegistered(resolvedModel) ? resolvedModel : null);
        }
        if (includeSubs) {
            map.put("sub_agents", subAgentsOf(agent.getAgentId()).stream()
                    .map(sub -> toResponse(sub, false))
                    .toList());
        }
        return map;
    }

    /**
     * sub智能体的。
     * @param parentId 父标识
     * @return sub智能体的的结果
     */
    private List<SysAiAgent> subAgentsOf(String parentId) {
        return agentMapper.selectList(new LambdaQueryWrapper<SysAiAgent>()
                .eq(SysAiAgent::getParentId, parentId)
                .orderByAsc(SysAiAgent::getSortOrder)
                .orderByAsc(SysAiAgent::getCreatedAt));
    }

    /**
     * require智能体。
     * @param agentId 智能体标识
     * @return require智能体的结果
     */
    private SysAiAgent requireAgent(String agentId) {
        SysAiAgent agent = agentMapper.selectById(agentId);
        if (agent == null) {
            /**
             * illegal参数异常。
             * @param agentId 智能体标识
             * @return illegal参数异常的结果
             */
            throw new IllegalArgumentException("Agent 不存在: " + agentId);
        }
        return agent;
    }

    /**
      * strategystr。
     * @param agent 智能体
     * @param key 钥匙
     * @param def def
     * @return strategyStr的结果
     */
    private static String strategyStr(SysAiAgent agent, String key, String def) {
        Object v = agent.getStrategy() != null ? agent.getStrategy().get(key) : null;
        return v != null && !v.toString().isBlank() ? v.toString() : def;
    }

    /**
      * strategybool。
     * @param agent 智能体
     * @param key 钥匙
     * @param def def
     * @return strategyBool的结果
     */
    private static boolean strategyBool(SysAiAgent agent, String key, boolean def) {
        Object v = agent.getStrategy() != null ? agent.getStrategy().get(key) : null;
        return v != null ? Boolean.parseBoolean(v.toString()) : def;
    }

    /**
      * strategyint。
     * @param agent 智能体
     * @param key 钥匙
     * @param def def
     * @return strategyInt的结果
     */
    private static int strategyInt(SysAiAgent agent, String key, int def) {
        Object v = agent.getStrategy() != null ? agent.getStrategy().get(key) : null;
        if (v == null) {
            return def;
        }
        try {
            return (int) Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * extractapi钥匙。
     * @param credentials 凭证
     * @return extractapi钥匙的结果
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

    @SuppressWarnings("unchecked")
    /**
     * extractextra头部。
     * @param extra extra
     * @return extractextra头部的结果
     */
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

    /**
     * str。
     * @param map 映射
     * @param key 钥匙
     * @return str的结果
     */
    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    /**
     * bool。
     * @param map 映射
     * @param key 钥匙
     * @param def def
     * @return bool的结果
     */
    private static boolean bool(Map<String, Object> map, String key, boolean def) {
        Object v = map.get(key);
        return v != null ? Boolean.parseBoolean(v.toString()) : def;
    }

    /**
     * intv。
     * @param map 映射
     * @param key 钥匙
     * @param def def
     * @return intv的结果
     */
    private static int intv(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        if (v == null) {
            return def;
        }
        try {
            return (int) Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * 字符串列表。
     * @param map 映射
     * @param key 钥匙
     * @return 字符串列表的结果
     */
    private static List<String> stringList(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (!(v instanceof List<?> list)) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o != null) {
                out.add(o.toString());
            }
        }
        return out;
    }
}
