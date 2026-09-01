package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.ai.agent.Agent;
import com.chua.common.support.ai.agent.AgentDefinition;
import com.chua.common.support.ai.agent.AgentMode;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.utils.IdUtils;
import com.chua.starter.ai.support.manage.mapper.SysAiAgentMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.store.SysAiAgent;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.registry.AgentRegistry;
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

/**
 * AI Agent 管理服务实现。
 *
 * <p>管理 {@code sys_ai_agent} 表中的主 Agent 与子 Agent，提供 CRUD、
 * 编排图保存/加载，以及将 DB 配置动态构建为底层 {@link Agent} 并注册到
 * {@link AgentRegistry} 的能力。每个 Agent 可独立配置模型、MCP、Skill、
 * SystemPrompt 与策略。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiAgentServiceImpl implements SysAiAgentService {

    private final SysAiAgentMapper agentMapper;
    private final SysAiProviderMapper providerMapper;
    private final AgentRegistry agentRegistry;

    private static final String STRATEGY_KEY_MODE = "mode";
    private static final String STRATEGY_KEY_PLANNING = "planning";
    private static final String STRATEGY_KEY_MEMORY = "memory";
    private static final String STRATEGY_KEY_MAX_TOOL_ITERATIONS = "max_tool_iterations";
    private static final String STRATEGY_KEY_TEMPERATURE = "temperature";
    private static final String STRATEGY_KEY_MAX_TOKENS = "max_tokens";

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

    // ==================== 主 Agent CRUD ====================

    @Override
    @Transactional
    public Map<String, Object> createAgent(Map<String, Object> body) {
        long now = System.currentTimeMillis();
        SysAiAgent entity = new SysAiAgent();
        entity.setAgentId(IdUtils.uuidv7());
        applyBody(entity, body, true);
        entity.setEnabled(bool(body, "enabled", true));
        entity.setSortOrder(intv(body, "sort_order", agentMapper.selectCount(null).intValue()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        agentMapper.insert(entity);
        rebuildAgent(entity);
        return toResponse(entity, true);
    }

    @Override
    @Transactional
    public Map<String, Object> updateAgent(String agentId, Map<String, Object> body) {
        SysAiAgent entity = requireAgent(agentId);
        applyBody(entity, body, false);
        if (body.containsKey("enabled")) {
            entity.setEnabled(bool(body, "enabled", true));
        }
        if (body.containsKey("sort_order")) {
            entity.setSortOrder(intv(body, "sort_order", entity.getSortOrder()));
        }
        entity.setUpdatedAt(System.currentTimeMillis());
        agentMapper.updateById(entity);
        rebuildAgent(entity);
        return toResponse(entity, true);
    }

    @Override
    @Transactional
    public void deleteAgent(String agentId) {
        SysAiAgent agent = requireAgent(agentId);
        agentRegistry.unregister(agent.getName());
        agentMapper.delete(new LambdaQueryWrapper<SysAiAgent>()
                .eq(SysAiAgent::getParentId, agentId));
        agentMapper.deleteById(agentId);
    }

    // ==================== 子 Agent CRUD ====================

    @Override
    @Transactional
    public Map<String, Object> createSubAgent(String agentId, Map<String, Object> body) {
        requireAgent(agentId);
        long now = System.currentTimeMillis();
        SysAiAgent entity = new SysAiAgent();
        entity.setAgentId(IdUtils.uuidv7());
        entity.setParentId(agentId);
        applyBody(entity, body, true);
        entity.setEnabled(bool(body, "enabled", true));
        entity.setSortOrder(intv(body, "sort_order",
                Math.toIntExact(agentMapper.selectCount(new LambdaQueryWrapper<SysAiAgent>()
                        .eq(SysAiAgent::getParentId, agentId)))));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        agentMapper.insert(entity);
        SysAiAgent master = requireAgent(agentId);
        rebuildAgent(master);
        return toResponse(entity, false);
    }

    @Override
    @Transactional
    public Map<String, Object> updateSubAgent(String agentId, String subId, Map<String, Object> body) {
        requireAgent(agentId);
        SysAiAgent entity = requireAgent(subId);
        if (entity.getParentId() == null || !entity.getParentId().equals(agentId)) {
            throw new IllegalArgumentException("子 Agent " + subId + " 不属于主 Agent " + agentId);
        }
        applyBody(entity, body, false);
        entity.setUpdatedAt(System.currentTimeMillis());
        agentMapper.updateById(entity);
        SysAiAgent master = requireAgent(agentId);
        rebuildAgent(master);
        return toResponse(entity, false);
    }

    @Override
    @Transactional
    public void deleteSubAgent(String agentId, String subId) {
        requireAgent(agentId);
        SysAiAgent entity = requireAgent(subId);
        if (entity.getParentId() == null || !entity.getParentId().equals(agentId)) {
            throw new IllegalArgumentException("子 Agent " + subId + " 不属于主 Agent " + agentId);
        }
        agentMapper.deleteById(subId);
        SysAiAgent master = requireAgent(agentId);
        rebuildAgent(master);
    }

    // ==================== 编排图 ====================

    @Override
    public Map<String, Object> saveOrchestration(String agentId, Map<String, Object> body) {
        // 编排图由子 Agent 的增删改与 X6 拖拽位置/连线组成。
        // 位置与连线由前端 X6 图在每次变更后通过本接口持久化（可选）。
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agentId", agentId);
        result.put("edgesCount", body.get("edges") instanceof List<?> l ? l.size() : 0);
        result.put("nodesCount", body.get("nodes") instanceof List<?> l ? l.size() : 0);
        result.put("status", "persisted");
        return result;
    }

    @Override
    public Map<String, Object> getOrchestration(String agentId) {
        SysAiAgent master = requireAgent(agentId);
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

    // ==================== Agent-as-Model ====================

    @Override
    public Map<String, Object> registerAsModel(String agentId, String modelName) {
        SysAiAgent agent = requireAgent(agentId);
        if (agent.getParentId() != null) {
            throw new IllegalArgumentException("只有主 Agent 可以注册为模型");
        }
        String resolvedModel = modelName != null && !modelName.isBlank()
                ? modelName : "agent-" + agent.getName();
        rebuildAgent(agent);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agentId", agentId);
        result.put("agentName", agent.getName());
        result.put("modelName", resolvedModel);
        result.put("status", "registered");
        return result;
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

            // 子 Agent
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
            Agent agentInstance = Agent.create("agentscope").definition(definition);
            if (masterClient != null) {
                agentInstance.chatClient(masterClient);
            }

            // 子 Agent 独立模型
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
        rebuildAgent(agent);
    }

    // ==================== 辅助方法 ====================

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
        if (includeSubs) {
            map.put("sub_agents", subAgentsOf(agent.getAgentId()).stream()
                    .map(sub -> toResponse(sub, false))
                    .toList());
        }
        return map;
    }

    private List<SysAiAgent> subAgentsOf(String parentId) {
        return agentMapper.selectList(new LambdaQueryWrapper<SysAiAgent>()
                .eq(SysAiAgent::getParentId, parentId)
                .orderByAsc(SysAiAgent::getSortOrder)
                .orderByAsc(SysAiAgent::getCreatedAt));
    }

    private SysAiAgent requireAgent(String agentId) {
        SysAiAgent agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("Agent 不存在: " + agentId);
        }
        return agent;
    }

    private static String strategyStr(SysAiAgent agent, String key, String def) {
        Object v = agent.getStrategy() != null ? agent.getStrategy().get(key) : null;
        return v != null && !v.toString().isBlank() ? v.toString() : def;
    }

    private static boolean strategyBool(SysAiAgent agent, String key, boolean def) {
        Object v = agent.getStrategy() != null ? agent.getStrategy().get(key) : null;
        return v != null ? Boolean.parseBoolean(v.toString()) : def;
    }

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

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static boolean bool(Map<String, Object> map, String key, boolean def) {
        Object v = map.get(key);
        return v != null ? Boolean.parseBoolean(v.toString()) : def;
    }

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
