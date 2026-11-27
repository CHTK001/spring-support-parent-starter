// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.ai.agent.Agent;
import com.chua.common.support.ai.agent.AgentApprovalGate;
import com.chua.common.support.ai.agent.AgentBudget;
import com.chua.common.support.ai.agent.AgentDefinition;
import com.chua.common.support.ai.agent.AgentMode;
import com.chua.common.support.ai.agent.run.AgentRunRecord;
import com.chua.common.support.ai.agent.run.AgentStepStatus;
import com.chua.common.support.ai.agent.run.AgentStepType;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.starter.ai.support.service.AgentOrchestrationGraph;
import com.chua.starter.ai.support.chat.AgentChatClient;
import com.chua.starter.ai.support.chat.ChatSkillSupport;
import com.chua.starter.ai.support.manage.api.AgentMcpRuntimeService;
import com.chua.deeplearning.support.agent.opencode.OpencodeServerAgent;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.lang.json.Json;
import com.chua.starter.ai.support.configuration.runtime.AgentProviderFactory;
import com.chua.starter.ai.support.configuration.agent.AgentRuntimeProfile;
import com.chua.starter.ai.support.manage.mapper.SysAiAgentMapper;
import com.chua.starter.ai.support.manage.store.SysAiAgent;
import com.chua.starter.ai.support.registry.AgentManager;
import com.chua.starter.ai.support.registry.AgentRegistry;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.service.AgentEvalRecorderProvider;
import com.chua.starter.ai.support.service.AgentRunService;
import com.chua.starter.ai.support.service.SysAiAgentService;
import com.chua.starter.ai.support.chat.ModelClientResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

/**
 * AI Agent 管理服务实现。
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

    private final SysAiAgentMapper agentMapper; // Agent映射器
    private final ModelClientResolver modelClientResolver; // 渠道路由解析器（多账号 429 failover）
    private final AgentRegistry agentRegistry; // Agentregistry
    private final AgentManager agentManager; // 运行时 Agent 管理器（按 id 运维）
    private final ChatClientRegistry chatClientRegistry; // 对话客户端registry
    private final com.chua.starter.ai.support.service.SysAiGlobalSettingService globalSettingService; // 全局设置（持久化编排图）
    private final AgentProviderFactory agentProviderFactory;
    private final AgentRunService agentRunService; // Agent 运行记录（Run/Step 落库 + 会话 SSE）
    private final AgentApprovalGate agentApprovalGate; // Agent 工具风险裁决门（切片四审批闭环）
    private final AgentEvalRecorderProvider agentEvalRecorderProvider; // Agent 评测记录器（懒加载单例）
    private final AgentMcpRuntimeService agentMcpRuntimeService;
    private final ChatSkillSupport chatSkillSupport;

    /** Agent 标识到公开模型名称，生命周期按业务 id 跟踪，支持自定义名称。 */
    private final Map<String, String> registeredModels = new java.util.concurrent.ConcurrentHashMap<>();

    private static final String STRATEGY_KEY_MODE = "mode"; // strategyKeymode
    private static final String STRATEGY_KEY_PLANNING = "planning"; // strategyKeyplanning
    private static final String STRATEGY_KEY_MEMORY = "memory"; // strategyKey内存
    private static final String STRATEGY_KEY_MAX_TOOL_ITERATIONS = "max_tool_iterations"; // strategyKey最大tooliterations
    private static final String STRATEGY_KEY_TEMPERATURE = "temperature"; // strategyKeytemperature
    private static final String STRATEGY_KEY_MAX_TOKENS = "max_tokens"; // strategyKey最大令牌

    // ==================== 查询 ====================

    @Override
    public SysAiAgent findAgentByName(String name) {
        if (name == null || name.isBlank()) return null;
        return agentMapper.selectOne(new LambdaQueryWrapper<SysAiAgent>()
                .eq(SysAiAgent::getName, name.trim()).isNull(SysAiAgent::getParentId));
    }

    @Override
    public List<Map<String, Object>> listAgents() {
        List<SysAiAgent> masters = agentMapper.selectList(
                new LambdaQueryWrapper<SysAiAgent>()
                        .isNull(SysAiAgent::getParentId)
                        .orderByAsc(SysAiAgent::getSortOrder)
                        .orderByAsc(SysAiAgent::getCreatedAt));
        List<Map<String, Object>> result = new ArrayList<>(8); // 主 Agent 数量不可预估
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
    @Transactional(rollbackFor = Exception.class)
    /**
     * 创建Agent。
     * @param body 主体
     * @return 创建Agent的结果
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
         * rebuildAgent。
         * @param entity 实体
         */
        rebuildAgent(entity);
        return toResponse(entity, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 更新Agent。
     * @param agentId Agent标识
     * @param body 主体
     * @return 更新Agent的结果
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
         * rebuildAgent。
         * @param entity 实体
         */
        rebuildAgent(entity);
        return toResponse(entity, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 删除Agent。
     * @param agentId Agent标识
     */
    public void deleteAgent(String agentId) {
        SysAiAgent agent = requireAgent(agentId);
        afterCommit(() -> removeRuntime(agent), () -> { });
        agentMapper.delete(new LambdaQueryWrapper<SysAiAgent>()
                .eq(SysAiAgent::getParentId, agentId));
        agentMapper.deleteById(agentId);
    }

 // ==================== 子 Agent CRUD ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 创建subAgent。
     * @param agentId Agent标识
     * @param body 主体
     * @return 创建subAgent的结果
     */
    public Map<String, Object> createSubAgent(String agentId, Map<String, Object> body) {
        /**
         * requireAgent。
         * @param agentId Agent标识
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
         * rebuildAgent。
         * @param master master
         */
        rebuildAgent(master);
        return toResponse(entity, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 更新subAgent。
     * @param agentId Agent标识
     * @param subId subid
     * @param body 主体
     * @return 更新subAgent的结果
     */
    public Map<String, Object> updateSubAgent(String agentId, String subId, Map<String, Object> body) {
        /**
         * requireAgent。
         * @param agentId Agent标识
         */
        requireAgent(agentId);
        SysAiAgent entity = requireAgent(subId);
        if (entity.getParentId() == null || !entity.getParentId().equals(agentId)) {
            /**
             * IllegalArgumentException。
             * @param agentId Agent标识
             * @return IllegalArgumentException的结果
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
        if (body.containsKey("enabled")) entity.setEnabled(bool(body, "enabled", true));
        if (body.containsKey("sort_order")) {
            entity.setSortOrder(intv(body, "sort_order", entity.getSortOrder()));
        }
        entity.setUpdatedAt(System.currentTimeMillis());
        agentMapper.updateById(entity);
        SysAiAgent master = requireAgent(agentId);
        /**
         * rebuildAgent。
         * @param master master
         */
        rebuildAgent(master);
        return toResponse(entity, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 删除subAgent。
     * @param agentId Agent标识
     * @param subId subid
     */
    public void deleteSubAgent(String agentId, String subId) {
        /**
         * requireAgent。
         * @param agentId Agent标识
         */
        requireAgent(agentId);
        SysAiAgent entity = requireAgent(subId);
        if (entity.getParentId() == null || !entity.getParentId().equals(agentId)) {
            /**
             * IllegalArgumentException。
             * @param agentId Agent标识
             * @return IllegalArgumentException的结果
             */
            throw new IllegalArgumentException("子 Agent " + subId + " 不属于主 Agent " + agentId);
        }
        agentMapper.deleteById(subId);
        SysAiAgent master = requireAgent(agentId);
        /**
         * rebuildAgent。
         * @param master master
         */
        rebuildAgent(master);
    }

    // ==================== 编排图 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveOrchestration(String agentId, Map<String, Object> body) {
        requireAgent(agentId);
        Set<String> allowed = new java.util.HashSet<>();
        allowed.add(agentId);
        subAgentsOf(agentId).forEach(sub -> allowed.add(sub.getAgentId()));
        AgentOrchestrationGraph graph = AgentOrchestrationGraph.validate(body, allowed);
        List<?> nodes = graph.nodes();
        List<?> edges = graph.edges();
        globalSettingService.upsertValue("orchestration", "graph_" + agentId + "_nodes", Json.toJson(nodes), "orchestration nodes");
        globalSettingService.upsertValue("orchestration", "graph_" + agentId + "_edges", Json.toJson(edges), "orchestration edges");
        Object mode = body.get("mode");
        if (mode != null) {
            globalSettingService.upsertValue("orchestration", "graph_" + agentId + "_mode", mode.toString(), "orchestration mode");
        }
        Map<String, Object> result = new LinkedHashMap<>(4);
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
                Map<String, Object> result = new LinkedHashMap<>(6);
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

        List<Map<String, Object>> nodes = new ArrayList<>(4); // 子 Agent 数量不可预估
        Map<String, Object> masterNode = new LinkedHashMap<>(8);
        masterNode.put("id", master.getAgentId());
        masterNode.put("agent_id", master.getAgentId());
        masterNode.put("label", master.getName());
        masterNode.put("kind", "master");
        masterNode.put("position", Map.of("x", 400, "y", 100));
        nodes.add(masterNode);

        List<Map<String, Object>> edges = new ArrayList<>(4); // 子 Agent 数量不可预估
        int idx = 0;
        for (SysAiAgent sub : subs) {
            Map<String, Object> subNode = new LinkedHashMap<>(8);
            subNode.put("id", sub.getAgentId());
            subNode.put("agent_id", sub.getAgentId());
            subNode.put("label", sub.getName());
            subNode.put("kind", "sub-agent");
            subNode.put("position", Map.of("x", 200 + idx * 220, "y", 350));
            nodes.add(subNode);

            Map<String, Object> edge = new LinkedHashMap<>(4);
            edge.put("id", master.getAgentId() + "-" + sub.getAgentId());
            edge.put("source", master.getAgentId());
            edge.put("target", sub.getAgentId());
            edge.put("label", "routes to");
            edges.add(edge);
            idx++;
        }

        Map<String, Object> result = new LinkedHashMap<>(4);
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
        // 运行记录（Run）落库：编排属后台运行，无会话上下文，sessionId 置空
        AgentRunRecord run = agentRunService.startRun(null, agentId, "orchestration", mode, input);
        long runStartAt = System.currentTimeMillis();
        try {
            Map<String, Object> result = doRunOrchestration(run, master, mode, agentId, input, runStartAt);
            agentRunService.completeRun(run == null ? null : run.runId(),
                    result.get("finalOutput") == null ? null : String.valueOf(result.get("finalOutput")));
            return result;
        } catch (Exception e) {
            agentRunService.failRun(run == null ? null : run.runId(), e.getMessage());
            throw e;
        }
    }

    /**
     * 编排执行主体（串行管线）：按拓扑序逐个调用子 Agent，并为每个子 Agent 落一条
     * {@code SUB_AGENT} 步骤（成功 / 失败 / 跳过）；运行收尾由 {@link #runOrchestration} 负责。
     *
     * @param run        运行记录（可能为 null：运行记录落库失败不影响主链路）
     * @param master     主 Agent
     * @param mode       执行模式（ROUTER / PIPELINE / FAN_OUT 等）
     * @param agentId    主 Agent 标识
     * @param input      输入
     * @param runStartAt 运行开始时间（epoch 毫秒）
     * @return 编排结果（含 runId）
     */
    private Map<String, Object> doRunOrchestration(AgentRunRecord run, SysAiAgent master, String mode,
                                                   String agentId, String input, long runStartAt) {
        List<SysAiAgent> subs = subAgentsOf(agentId).stream()
                .filter(s -> Boolean.TRUE.equals(s.getEnabled()))
                .toList();
        Map<String, SysAiAgent> subById = new LinkedHashMap<>(4); // 子 Agent 数量不可预估
        for (SysAiAgent sub : subs) {
            subById.put(sub.getAgentId(), sub);
        }

        // 读取持久化编排图，按边做拓扑排序；无图时按 sort 顺序串行。
        List<String> order = new ArrayList<>(4); // 编排节点数量不可预估
        boolean persisted = false;
        try {
            Optional<String> nodesJson = globalSettingService.getValue("orchestration", "graph_" + agentId + "_nodes");
            Optional<String> edgesJson = globalSettingService.getValue("orchestration", "graph_" + agentId + "_edges");
            if (nodesJson.isPresent() && edgesJson.isPresent()) {
                persisted = true;
                @SuppressWarnings({"rawtypes","unchecked"})
                List<Map<String, Object>> nodes = (List<Map<String, Object>>) (List) Json.fromJsonToList(nodesJson.get(), Map.class);
                @SuppressWarnings({"rawtypes","unchecked"})
                List<Map<String, Object>> edges = (List<Map<String, Object>>) (List) Json.fromJsonToList(edgesJson.get(), Map.class);
                order = topologicalOrder(nodes, edges, subById.keySet(), master.getAgentId());
            }
        } catch (Exception e) {
            throw new IllegalStateException("编排图无效，无法执行: " + e.getMessage(), e);
        }
        if (!persisted) {
            for (SysAiAgent sub : subs) {
                order.add(sub.getAgentId());
            }
        }
        if (order.isEmpty()) throw new IllegalStateException("编排中没有可执行的子 Agent");

        Map<String, String> outputs = new LinkedHashMap<>(4); // 子 Agent 数量不可预估
        String current = input != null ? input : "";
        for (String subId : order) {
            agentRunService.checkCancelled(run == null ? null : run.runId());
            SysAiAgent sub = subById.get(subId);
            if (sub == null) {
                throw new IllegalStateException("编排子 Agent 不可用: " + subId);
            }
            ChatClient client = buildChatClient(sub);
            if (client == null) {
                log.warn("[Agent] 编排跳过子 Agent（无可用模型/凭证）: {}", subId);
                agentRunService.appendStep(run == null ? null : run.runId(), AgentStepType.SUB_AGENT, subId,
                        null, "跳过：无可用模型/凭证", AgentStepStatus.SKIPPED, runStartAt, System.currentTimeMillis());
                throw new IllegalStateException("子 Agent 无可用模型/凭证: " + sub.getName());
            }
            long stepStartAt = System.currentTimeMillis();
            try {
                String prompt = current;
                if (sub.getSystemPrompt() != null && !sub.getSystemPrompt().isBlank()) {
                    client = client.system(sub.getSystemPrompt());
                }
                String out = client.chatSync(prompt);
                agentRunService.checkCancelled(run == null ? null : run.runId());
                outputs.put(subId, out);
                // 简单串行模式：上一个子 Agent 的输出作为下一个子 Agent 的输入
                current = out != null ? out : "";
                agentRunService.appendStep(run == null ? null : run.runId(), AgentStepType.SUB_AGENT, subId,
                        prompt, out, AgentStepStatus.OK, stepStartAt, System.currentTimeMillis());
            } catch (Exception e) {
                log.warn("[Agent] 编排子 Agent 调用失败 subId={}: {}", subId, e.getMessage());
                outputs.put(subId, "ERROR: " + e.getMessage());
                agentRunService.appendStep(run == null ? null : run.runId(), AgentStepType.SUB_AGENT, subId,
                        null, e.getMessage(), AgentStepStatus.FAILED, stepStartAt, System.currentTimeMillis());
                throw new IllegalStateException("子 Agent 执行失败: " + sub.getName(), e);
            } finally {
                client.close();
            }
        }

        Map<String, Object> result = new LinkedHashMap<>(6);
        result.put("agentId", agentId);
        result.put("mode", mode);
        result.put("nodes", order.size());
        result.put("outputs", outputs);
        result.put("finalOutput", current);
        if (run != null) {
            result.put("runId", run.runId());
        }
        return result;
    }

    @Override
    public Map<String, Object> invokeSubAgent(String subAgentId, String input) {
        SysAiAgent sub = requireAgent(subAgentId);
        if (sub.getParentId() == null || sub.getParentId().isBlank()) {
            throw new IllegalArgumentException("仅支持调用子 Agent: " + subAgentId);
        }
        if (!Boolean.TRUE.equals(sub.getEnabled())) {
            throw new IllegalStateException("子 Agent 已停用: " + sub.getName());
        }
        ChatClient client = buildChatClient(sub);
        if (client == null) {
            throw new IllegalStateException("子 Agent 无可用模型/凭证: " + sub.getName());
        }
        String prompt = input != null ? input : "";
        // 运行记录（Run）落库：手动调用，无会话上下文（会话侧入口拿到结果后回填 sessionId/subSessionId）
        AgentRunRecord run = agentRunService.startRun(null, subAgentId, "manual", "SINGLE", prompt);
        long startedAt = System.currentTimeMillis();
        try {
            if (sub.getSystemPrompt() != null && !sub.getSystemPrompt().isBlank()) {
                client = client.system(sub.getSystemPrompt());
            }
            String output = client.chatSync(prompt);
            agentRunService.checkCancelled(run == null ? null : run.runId());
            agentRunService.appendStep(run == null ? null : run.runId(), AgentStepType.SUB_AGENT, sub.getName(),
                    prompt, output, AgentStepStatus.OK, startedAt, System.currentTimeMillis());
            agentRunService.completeRun(run == null ? null : run.runId(), output);
            Map<String, Object> result = new LinkedHashMap<>(8);
            result.put("agentId", subAgentId);
            result.put("name", sub.getName());
            result.put("masterId", sub.getParentId());
            result.put("input", prompt);
            result.put("output", output != null ? output : "");
            if (run != null) {
                result.put("runId", run.runId());
            }
            return result;
        } catch (Exception e) {
            log.warn("[Agent] 子 Agent 调用失败 subId={}: {}", subAgentId, e.getMessage());
            agentRunService.appendStep(run == null ? null : run.runId(), AgentStepType.SUB_AGENT, sub.getName(),
                    prompt, e.getMessage(), AgentStepStatus.FAILED, startedAt, System.currentTimeMillis());
            agentRunService.failRun(run == null ? null : run.runId(), e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>(8);
            result.put("agentId", subAgentId);
            result.put("name", sub.getName());
            result.put("masterId", sub.getParentId());
            result.put("input", prompt);
            result.put("output", "");
            result.put("error", e.getMessage());
            if (run != null) {
                result.put("runId", run.runId());
            }
            return result;
        } finally {
            client.close();
        }
    }

    /**
     * Kahn 拓扑排序：按 edges(source→target) 对节点排序，跳过主节点与未注册子 Agent。
     *
     * @param nodes        子 Agent 节点列表
     * @param edges        依赖边列表（source→target）
     * @param validSubIds 有效的子 Agent 标识集合
     * @param masterId    主 Agent 标识（不参与排序）
     * @return 拓扑序的子 Agent 标识列表
     */
    private static List<String> topologicalOrder(List<Map<String, Object>> nodes, List<Map<String, Object>> edges,
                                                 Set<String> validSubIds, String masterId) {
        Map<String, Integer> indegree = new LinkedHashMap<>(4); // 节点数量不可预估
        Map<String, List<String>> adj = new LinkedHashMap<>(4); // 节点数量不可预估
        Map<String, String> nodeAgents = new HashMap<>();
        for (Map<String, Object> node : nodes) {
            Object id = node.get("agent_id") != null ? node.get("agent_id")
                    : node.get("agentId") != null ? node.get("agentId") : node.get("id");
            if (id == null) {
                continue;
            }
            String sid = id.toString();
            nodeAgents.put(String.valueOf(node.get("id")), sid);
            if (!validSubIds.contains(sid) || sid.equals(masterId)) {
                continue;
            }
            indegree.putIfAbsent(sid, 0);
            adj.putIfAbsent(sid, new ArrayList<>(2));
        }
        for (Map<String, Object> edge : edges) {
            Object src = edge.get("source");
            Object tgt = edge.get("target");
            if (src == null || tgt == null) {
                continue;
            }
            String s = nodeAgents.getOrDefault(src.toString(), src.toString());
            String t = nodeAgents.getOrDefault(tgt.toString(), tgt.toString());
            if (!validSubIds.contains(t) || t.equals(masterId)) {
                continue;
            }
            adj.putIfAbsent(s, new ArrayList<>(2));
            if (!s.equals(masterId) && validSubIds.contains(s)) {
                adj.get(s).add(t);
                indegree.merge(t, 1, Integer::sum);
            } else {
                indegree.merge(t, 0, Integer::sum);
            }
            indegree.putIfAbsent(t, indegree.getOrDefault(t, 0));
        }
        List<String> queue = new ArrayList<>(4);
        for (Map.Entry<String, Integer> e : indegree.entrySet()) {
            if (e.getValue() == 0) {
                queue.add(e.getKey());
            }
        }
        List<String> order = new ArrayList<>(4);
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
        if (order.size() != indegree.size()) {
            throw new IllegalArgumentException("编排存在循环依赖，禁止执行");
        }
        return order;
    }

 // ==================== Agent-as-模型 ====================

    @Override
    public synchronized Map<String, Object> registerAsModel(String agentId, String modelName) {
        SysAiAgent agent = requireAgent(agentId);
        if (agent.getParentId() != null) {
            /**
             * IllegalArgumentException。
             * @param 可以注册为模型" 可以注册为模型"
             * @return IllegalArgumentException的结果
             */
            throw new IllegalArgumentException("只有主 Agent 可以注册为模型");
        }
        String resolvedModel = modelName != null && !modelName.isBlank()
                ? modelName.trim() : "agent-" + agent.getName();
        if (!Boolean.TRUE.equals(agent.getEnabled())) throw new IllegalArgumentException("已停用的 Agent 不能注册为模型");
        String existing = registeredModels.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(agentId) && entry.getValue().equalsIgnoreCase(resolvedModel))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        if (existing != null) {
            throw new IllegalArgumentException("模型名称已被其他 Agent 使用: " + resolvedModel);
        }
        /**
         * rebuildAgent。
         * @param agent Agent
         */
        if (agentManager.get(agentId) == null) rebuildAgent(agent);

 // Agent-as-模型：包装为 对话客户端 注册到对话注册中心
        Agent runtime = agentRegistry.getByName(agent.getName());
        if (runtime != null) {
            chatClientRegistry.registerAgentModel(resolvedModel, new AgentChatClient(runtime),
                    "chat", "tools", "skills", "routing");
            String oldModel = registeredModels.put(agentId, resolvedModel);
            if (oldModel != null && !oldModel.equalsIgnoreCase(resolvedModel)) {
                chatClientRegistry.unregisterAgentModel(oldModel);
            }
            log.info("[Agent] Agent {} 已注册为模型 {}", agent.getName(), resolvedModel);
        } else {
            // 运行时没建起来（构建异常已在 rebuildAgent 记 ERROR），必须如实失败：
            // 若仍返回 status=registered，前端会误报成功，而 GET /registered-model 立刻穿帮。
            log.warn("[Agent] Agent 运行时不可用，模型 {} 未注册到对话链路", resolvedModel);
            throw new IllegalStateException("Agent 运行时构建失败，无法注册为模型（详见服务端日志）");
        }

        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("agentId", agentId);
        result.put("agentName", agent.getName());
        result.put("modelName", resolvedModel);
        result.put("status", "registered");
        return result;
    }

    @Override
    public synchronized void unregisterModel(String agentId) {
        SysAiAgent agent = requireAgent(agentId);
        removeRegisteredModel(agent);
        log.info("[Agent] Agent {} 已从模型注册中心注销", agent.getName());
    }

    @Override
    public Map<String, Object> getRegisteredModelInfo(String agentId) {
        SysAiAgent agent = requireAgent(agentId);
        String resolvedModel = registeredModels.get(agentId);
        boolean registered = resolvedModel != null && chatClientRegistry.isAgentModelRegistered(resolvedModel);
        Map<String, Object> info = new LinkedHashMap<>(4);
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

    /**
     * 应用就绪后恢复已启用的主 Agent，供业务 Bean 通过管理器按 id 取用。
     *
     * <p>复用保存时的完整装配路径；已托管的记录不重复构建。目录读取失败会明确记录，
     * 单条配置失败只影响该记录，管理员可修正配置后重新保存或重建。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void restoreManagedAgents() {
        List<SysAiAgent> records;
        try {
            records = agentMapper.selectList(new LambdaQueryWrapper<SysAiAgent>()
                    .isNull(SysAiAgent::getParentId)
                    .eq(SysAiAgent::getEnabled, true)
                    .orderByAsc(SysAiAgent::getSortOrder)
                    .orderByAsc(SysAiAgent::getCreatedAt));
        } catch (Exception failure) {
            log.error("[Agent] 读取已启用托管配置失败，启动恢复未执行", failure);
            return;
        }
        for (SysAiAgent record : records) {
            if (agentManager.contains(record.getAgentId())) {
                continue;
            }
            try {
                rebuildAgent(record);
            } catch (Exception failure) {
                log.error("[Agent] 启动恢复失败 agentId={} name={}", record.getAgentId(), record.getName(), failure);
            }
        }
    }

    @Override
    public void rebuildAgent(SysAiAgent agent) {
        if (agent == null || agent.getParentId() != null) {
            return;
        }
        if (!Boolean.TRUE.equals(agent.getEnabled())) {
            afterCommit(() -> removeRuntime(agent), () -> { });
            return;
        }
        AgentRuntimeProfile profile = AgentRuntimeProfile.fromManaged(agent);
        boolean mcpEnabled = AgentRuntimeProfile.resolve(profile.mcp(), false, false);
        boolean delegation = AgentRuntimeProfile.resolve(profile.subAgent(), false, false);
        Agent instance = null;
        List<AutoCloseable> resources = new ArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean released = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            boolean opencodeRuntime = AgentProviderFactory.OPENCODE_SERVER_PROVIDER
                    .equals(agentProviderFactory.resolveProvider());

            ChatClient masterClient = buildChatClient(agent);
            if (masterClient == null) throw new IllegalArgumentException("启用 Agent 前请选择渠道和模型");
            resources.add(masterClient);
            AgentDefinition.Builder masterBuilder = AgentDefinition.builder()
                    .id(agent.getAgentId())
                    .name(agent.getName())
                    .description(agent.getDescription())
                    .role(agent.getRole())
                    .instruction(agent.getInstruction())
                    .leader(delegation && !opencodeRuntime)
                    .mcp(mcpEnabled)
                    .planning(profile.planning())
                    .memory(profile.memory());
            if (agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()) {
                masterBuilder.systemPrompt(agent.getSystemPrompt());
            }
            int maxToolIterations = profile.maxToolIterations();
            if (maxToolIterations > 0) {
                masterBuilder.maxToolIterations(maxToolIterations);
            }

 // 子 Agent
            List<SysAiAgent> subs = delegation ? subAgentsOf(agent.getAgentId()) : List.of();
            for (SysAiAgent sub : subs) {
                if (!Boolean.TRUE.equals(sub.getEnabled())) {
                    continue;
                }
                AgentRuntimeProfile subProfile = AgentRuntimeProfile.fromManaged(sub);
                AgentDefinition.Builder subBuilder = AgentDefinition.builder()
                        .id(sub.getAgentId())
                        .name(sub.getName())
                        .description(sub.getDescription())
                        .role(sub.getRole())
                        .instruction(sub.getInstruction())
                        .leader(false)
                        .mcp(AgentRuntimeProfile.resolve(subProfile.mcp(), false, false))
                        .planning(subProfile.planning())
                        .memory(subProfile.memory());
                if (subProfile.maxToolIterations() > 0) subBuilder.maxToolIterations(subProfile.maxToolIterations());
                if (sub.getSystemPrompt() != null && !sub.getSystemPrompt().isBlank()) {
                    subBuilder.systemPrompt(sub.getSystemPrompt());
                }
                masterBuilder.subAgent(subBuilder.build());
            }

            AgentDefinition definition = masterBuilder.build();
            Agent agentInstance = agentProviderFactory.createAgent();
            if (agentInstance == null) {
                throw new IllegalStateException("Agent 运行时不可用: " + agentProviderFactory.resolveProvider());
            }
            instance = agentInstance;
            agentInstance.definition(definition);
            // 切片四：挂上无状态风险裁决门——低风险放行、高危转人工确认、
            // 非管理员 shell 类工具拒绝；裁决异常 fail-close 转确认。
            agentInstance.riskAdjudicator(agentApprovalGate);
            // 当前记录的 Token 总量上限；各引擎分别消费 AgentBudget，0/未配不限。
            long budgetTokens = profile.maxTotalTokens();
            if (budgetTokens > 0) {
                agentInstance.budget(new AgentBudget(budgetTokens, 0L, 0L, 0L, 0L, 0L));
            }
            try {
                agentInstance.evalHook(agentEvalRecorderProvider.recorder());
            } catch (Exception e) {
                log.warn("[Agent] 评测记录器不可用，本实例不落评测 name={}", agent.getName(), e);
            }
            if (masterClient != null) {
                agentInstance.chatClient(AgentChatClient.borrowedClient(masterClient));
            }

 // 子 Agent 独立模型
            for (SysAiAgent sub : subs) {
                if (!Boolean.TRUE.equals(sub.getEnabled())) {
                    continue;
                }
                ChatClient subClient = buildChatClient(sub);
                if (subClient != null) {
                    resources.add(subClient);
                    agentInstance.chatClient(sub.getAgentId(), AgentChatClient.borrowedClient(subClient));
                }
            }

            AgentMode mode = delegation ? AgentMode.valueOf(strategyStr(agent, STRATEGY_KEY_MODE, "AUTO").toUpperCase(java.util.Locale.ROOT))
                    : AgentMode.SINGLE;
            agentInstance.mode(mode);
            agentInstance.plan(profile.planning());
            if (agentInstance instanceof OpencodeServerAgent nativeAgent) {
                nativeAgent.delegation(delegation);
            }
            chatSkillSupport.configureAgent(agentInstance, profile.defaultSkills());
            var mcp = agentMcpRuntimeService.configure(agentInstance, mcpEnabled, profile.mcpServers(), null);
            if (mcp != null) resources.add(mcp::close);
            Runnable discard = () -> {
                if (released.compareAndSet(false, true)) closeStaged(agentInstance, resources);
            };
            afterCommit(() -> {
                try {
                    publishRuntime(agent, agentInstance, () -> closeResources(resources));
                } catch (RuntimeException failure) {
                    discard.run();
                    throw failure;
                }
            }, discard);
        } catch (Exception e) {
            if (released.compareAndSet(false, true)) closeStaged(instance, resources);
            log.error("[Agent] 构建 Agent 失败: agentId={} name={}", agent.getAgentId(), agent.getName(), e);
            throw new IllegalStateException("Agent 构建失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void rebuildAgentById(String agentId) {
        SysAiAgent agent = requireAgent(agentId);
        /**
         * rebuildAgent。
         * @param agent Agent
         */
        rebuildAgent(agent);
    }

    // ==================== 辅助方法 ====================

    /**
     * 提交成功后才切换实例索引，回滚时释放本次构建的私有资源。
     * @param committed 提交动作
     * @param rolledBack 回滚动作
     */
    private static void afterCommit(Runnable committed, Runnable rolledBack) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            committed.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { committed.run(); }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) rolledBack.run();
            }
        });
    }

    /**
     * 发布已装配实例，名称索引不再注入共享能力，旧实例统一由管理器释放。
     * @param record 当前定义
     * @param instance 新实例
     * @param resources 私有资源
     */
    private synchronized void publishRuntime(SysAiAgent record, Agent instance, AutoCloseable resources) {
        synchronized (agentRegistry) {
            synchronized (chatClientRegistry) {
                Agent previous = agentManager.get(record.getAgentId());
                Agent occupant = agentRegistry.getByName(record.getName());
                if (occupant != null && occupant != previous && occupant != instance) {
                    throw new IllegalArgumentException("Agent 名称已被运行时占用: " + record.getName());
                }
                String oldName = previous != null && previous.getDefinition() != null
                        ? previous.getDefinition().getName() : null;
                String model = registeredModels.get(record.getAgentId());
                ChatClient oldModel = model == null ? null : chatClientRegistry.getByModel(model);
                try {
                    // 名称与模型注册均成功后才切换管理器，旧实例在此之前保持可用。
                    agentRegistry.registerInstance(record.getName(), instance, new String[]{"custom"},
                            new String[]{"chat", "tools", "skills", "routing"}, false);
                    if (model != null) chatClientRegistry.registerAgentModel(model, new AgentChatClient(instance), "chat", "tools", "skills", "routing");
                    if (oldName != null && !oldName.equals(record.getName())) agentRegistry.unregister(oldName, false);
                    agentManager.register(record.getAgentId(), instance, resources);
                } catch (RuntimeException failure) {
                    agentRegistry.unregister(record.getName(), false);
                    if (previous != null && oldName != null) {
                        agentRegistry.registerInstance(oldName, previous, new String[]{"custom"},
                                new String[]{"chat", "tools", "skills", "routing"}, false);
                    }
                    if (model != null && oldModel != null) {
                        chatClientRegistry.registerAgentModel(model, oldModel, "chat", "tools", "skills", "routing");
                    } else if (model != null) {
                        chatClientRegistry.unregisterAgentModel(model);
                    }
                    throw failure;
                }
            }
        }
        log.info("[Agent] 已替换托管实例 agentId={} name={}", record.getAgentId(), record.getName());
    }

    /**
     * 停用或删除的共同出口，避免名称注册表重复关闭同一实例。
     * @param record 当前定义
     */
    private synchronized void removeRuntime(SysAiAgent record) {
        removeRegisteredModel(record);
        Agent previous = agentManager.get(record.getAgentId());
        if (previous != null && previous.getDefinition() != null) {
            agentRegistry.unregister(previous.getDefinition().getName(), false);
        }
        agentRegistry.unregister(record.getName(), false);
        agentManager.destroy(record.getAgentId());
    }

    /**
     * 释放装配失败或事务回滚的新实例。
     * @param instance 暂存实例
     * @param resources 暂存资源
     */
    private static void closeStaged(Agent instance, List<AutoCloseable> resources) {
        if (instance != null) {
            try { instance.close(); } catch (Exception e) { log.warn("[Agent] 暂存实例关闭失败", e); }
        }
        closeResources(resources);
    }

    /**
     * 逐项释放私有资源，不因单项失败中断回收。
     * @param resources 私有资源
     */
    private static void closeResources(List<AutoCloseable> resources) {
        for (AutoCloseable resource : resources.reversed()) {
            try { resource.close(); } catch (Exception e) { log.warn("[Agent] 私有资源关闭失败", e); }
        }
    }

    /**
     * 清理 Agent 对外模型注册，删除、停用和重新注册共用同一生命周期入口。
     * @param agent Agent 定义
     */
    private void removeRegisteredModel(SysAiAgent agent) {
        String registered = registeredModels.remove(agent.getAgentId());
        if (registered != null) chatClientRegistry.unregisterAgentModel(registered);
    }

    /**
     * 构建对话客户端。
     * @param agent Agent
     * @return 构建对话客户端的结果
     */
    private ChatClient buildChatClient(SysAiAgent agent) {
        if (agent.getProviderId() == null || agent.getProviderId().isBlank()
                || agent.getModel() == null || agent.getModel().isBlank()) {
            return null;
        }
        return modelClientResolver.resolveFresh(agent.getProviderId() + ":" + agent.getModel());
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
            } else if (strategy == null) {
                entity.setStrategy(null);
            } else {
                throw new IllegalArgumentException("strategy 必须为对象或 null");
            }
        }
        AgentRuntimeProfile.fromManaged(entity);
        if (entity.getStrategy() != null && entity.getStrategy().containsKey(STRATEGY_KEY_MODE)) {
            AgentMode.valueOf(strategyStr(entity, STRATEGY_KEY_MODE, "AUTO").toUpperCase(java.util.Locale.ROOT));
        }
        if (create) {
            entity.setName(str(body, "name"));
        }
        if (entity.getName() == null || entity.getName().isBlank()) {
            throw new com.chua.starter.ai.support.manage.common.ApiException(
                    com.chua.starter.ai.support.manage.common.ApiErrorCode.INVALID_PARAM, "Agent 名称不能为空");
        }
        entity.setName(entity.getName().trim());
        Agent occupied = agentRegistry.getByName(entity.getName());
        if (entity.getParentId() == null && occupied != null && occupied != agentManager.get(entity.getAgentId())) {
            throw new IllegalArgumentException("Agent 名称已被运行时占用: " + entity.getName());
        }
        long sameName = agentMapper.selectCount(new LambdaQueryWrapper<SysAiAgent>()
                .eq(SysAiAgent::getName, entity.getName()).ne(SysAiAgent::getAgentId, entity.getAgentId()));
        if (sameName > 0) {
            throw new com.chua.starter.ai.support.manage.common.ApiException(
                    com.chua.starter.ai.support.manage.common.ApiErrorCode.CONFLICT, "Agent 名称已存在");
        }
        if (body.containsKey("model") || body.containsKey("provider_id")) {
            boolean hasModel = entity.getModel() != null && !entity.getModel().isBlank();
            boolean hasProvider = entity.getProviderId() != null && !entity.getProviderId().isBlank();
            if (hasModel != hasProvider) {
                throw new com.chua.starter.ai.support.manage.common.ApiException(
                        com.chua.starter.ai.support.manage.common.ApiErrorCode.INVALID_PARAM, "请选择完整的渠道和模型");
            }
            if (hasModel) modelClientResolver.validateBinding(entity.getProviderId(), entity.getModel());
        }
    }

    /**
     * 转为响应。
     * @param agent Agent
     * @param includeSubs includesubs
     * @return 转为响应的结果
     */
    private Map<String, Object> toResponse(SysAiAgent agent, boolean includeSubs) {
        Map<String, Object> map = new LinkedHashMap<>(16);
        map.put("agent_id", agent.getAgentId());
        map.put("parent_id", agent.getParentId());
        map.put("name", agent.getName());
        map.put("description", agent.getDescription());
        map.put("role", agent.getRole());
        map.put("instruction", agent.getInstruction());
        map.put("system_prompt", agent.getSystemPrompt());
        map.put("provider_id", agent.getProviderId());
        map.put("model", agent.getModel());
        map.put("mcp_ids", agent.getMcpIds());
        map.put("skill_ids", agent.getSkillIds());
        Map<String, Object> strategy = new LinkedHashMap<>(agent.getStrategy() != null ? agent.getStrategy() : Map.of());
        // JSON 数字经过 JavaScript 会失真，两个预算字段都以十进制文本输出。
        for (String key : List.of("max_total_tokens", "max_tokens")) {
            if (strategy.get(key) != null) strategy.put(key, strategy.get(key).toString());
        }
        map.put("strategy", strategy);
        map.put("enabled", agent.getEnabled());
        map.put("sort_order", agent.getSortOrder());
        map.put("created_at", agent.getCreatedAt());
        map.put("updated_at", agent.getUpdatedAt());
        if (agent.getParentId() == null) {
            String resolvedModel = registeredModels.get(agent.getAgentId());
            map.put("registered_model_name", resolvedModel != null && chatClientRegistry.isAgentModelRegistered(resolvedModel)
                    ? resolvedModel : null);
        }
        if (includeSubs) {
            map.put("sub_agents", subAgentsOf(agent.getAgentId()).stream()
                    .map(sub -> toResponse(sub, false))
                    .toList());
        }
        return map;
    }

    /**
     * subAgent的。
     * @param parentId 父标识
     * @return subAgent的的结果
     */
    private List<SysAiAgent> subAgentsOf(String parentId) {
        return agentMapper.selectList(new LambdaQueryWrapper<SysAiAgent>()
                .eq(SysAiAgent::getParentId, parentId)
                .orderByAsc(SysAiAgent::getSortOrder)
                .orderByAsc(SysAiAgent::getCreatedAt));
    }

    /**
     * requireAgent。
     * @param agentId Agent标识
     * @return requireAgent的结果
     */
    private SysAiAgent requireAgent(String agentId) {
        SysAiAgent agent = agentMapper.selectById(agentId);
        if (agent == null) {
            /**
             * IllegalArgumentException。
             * @param agentId Agent标识
             * @return IllegalArgumentException的结果
             */
            throw new IllegalArgumentException("Agent 不存在: " + agentId);
        }
        return agent;
    }

    /**
     * strategystr。
     * @param agent Agent
     * @param key Key
     * @param def def
     * @return strategyStr的结果
     */
    private static String strategyStr(SysAiAgent agent, String key, String def) {
        Object v = agent.getStrategy() != null ? agent.getStrategy().get(key) : null;
        if (v == null) return def;
        if (!(v instanceof String text) || text.isBlank()) throw new IllegalArgumentException(key + " 必须为非空文本");
        return text.trim();
    }

    /**
     * str。
     * @param map 映射
     * @param key Key
     * @return str的结果
     */
    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    /**
     * bool。
     * @param map 映射
     * @param key Key
     * @param def def
     * @return bool的结果
     */
    private static boolean bool(Map<String, Object> map, String key, boolean def) {
        Object v = map.get(key);
        if (v == null) return def;
        if (v instanceof Boolean value) return value;
        if (v instanceof String text && ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text))) {
            return Boolean.parseBoolean(text);
        }
        throw new IllegalArgumentException(key + " 必须为布尔值");
    }

    /**
     * intv。
     * @param map 映射
     * @param key Key
     * @param def def
     * @return intv的结果
     */
    private static int intv(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        if (v == null) {
            return def;
        }
        try { return new java.math.BigDecimal(v.toString()).intValueExact(); }
        catch (ArithmeticException | NumberFormatException e) { throw new IllegalArgumentException(key + " 必须为 int 范围整数", e); }
    }

    /**
     * 字符串列表。
     * @param map 映射
     * @param key Key
     * @return 字符串列表的结果
     */
    private static List<String> stringList(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (!(v instanceof List<?> list)) throw new IllegalArgumentException(key + " 必须为字符串数组或 null");
        List<String> out = new ArrayList<>(4); // 元素数量不可预估
        for (Object o : list) {
            if (!(o instanceof String text) || text.isBlank()) throw new IllegalArgumentException(key + " 不得包含空项或非字符串");
            if (!out.contains(text.trim())) out.add(text.trim());
        }
        return out;
    }
}
