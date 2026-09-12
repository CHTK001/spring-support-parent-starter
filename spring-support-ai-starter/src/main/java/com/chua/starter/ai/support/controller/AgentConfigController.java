package com.chua.starter.ai.support.controller;

import com.chua.common.support.ai.agent.AgentDefinition;
import com.chua.common.support.ai.chat.AgentModelDefinition;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.registry.AgentRegistry;
import com.chua.starter.ai.support.registry.ChatClientRegistry;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
   * 智能体 配置管理控制器 —— 智能体 编排、智能体-as-模型 转换。
 * <p>
 * 编排配置持久化通过 {@link SysAiGlobalSettingService} 以 "orchestration" 分组存储，
   * 每个 智能体 的配置序列化为 JSON 保存在对应字段。
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@RestController
@RequestMapping("/v2/agent-config")
@RequiredArgsConstructor
@Tag(name = "Agent 配置管理", description = "Agent 编排与 Agent-as-Model 接口")
public class AgentConfigController {

    /** 智能体注册表 */
    private final AgentRegistry agentRegistry;
    /** 对话客户端注册表 */
    private final ChatClientRegistry chatClientRegistry;
    /** SYSAI全局设置服务 */
    private final SysAiGlobalSettingService sysAiGlobalSettingService;

 // ==================== 智能体-as-模型 ====================

    @Permission("admin")
    @Operation(summary = "注册 Agent 为模型", description = "将指定 Agent 包装为 AgentModelDefinition 并注册到 ChatClientRegistry，使其作为模型对外暴露")
    @PostMapping("/register-as-model")
    /**
     * 注册智能体as模型
     *
     * @param body 主体
     * @return 注册智能体as模型的结果
     */
    public ReturnResult<Map<String, Object>> registerAgentAsModel(@RequestBody Map<String, Object> body) {
        String agentName = (String) body.get("agentName");
        String modelName = (String) body.get("modelName");
        Integer groupId = body.get("groupId") instanceof Number
                ? ((Number) body.get("groupId")).intValue() : null;

        if (agentName == null || agentName.isBlank()) {
            return ReturnResult.error("agentName 不能为空");
        }
        if (modelName == null || modelName.isBlank()) {
            modelName = "agent-" + agentName;
        }

        try {
            var agent = agentRegistry.getByName(agentName);
            if (agent == null) {
                return ReturnResult.error("Agent " + agentName + " 不存在");
            }

            ChatClient agentModel = new AgentModelDefinition(agent);
            chatClientRegistry.register("agent", modelName, agentModel, "chat", "tools", "skills", "routing");

            // 持久化注册记录
            Map<String, Object> registryBody = new LinkedHashMap<>();
            registryBody.put("group", "agent-models");
            registryBody.put("fields", List.of(
                    Map.of("field", modelName, "value", agentName,
                            "valueType", "STRING", "description", "Agent registered as model")
            ));
            sysAiGlobalSettingService.replace(registryBody);

            if (groupId != null) {
                log.info("[AgentConfig] Agent {} 已注册为模型 {} 并绑定到分组 {}", agentName, modelName, groupId);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("agentName", agentName);
            result.put("modelName", modelName);
            result.put("groupId", groupId);
            result.put("status", "registered");
            return ReturnResult.ok(result, "Agent 已成功注册为模型");
        } catch (Exception e) {
            log.error("[AgentConfig] 注册 Agent 为模型失败: agentName={}", agentName, e);
            return ReturnResult.error("注册失败: " + e.getMessage());
        }
    }

    @Permission("admin")
    @Operation(summary = "注销 Agent 模型", description = "从 ChatClientRegistry 移除 Agent 模型注册")
    @DeleteMapping("/unregister-model/{modelName}")
    /**
     * 注销智能体模型
     *
     * @param modelName 模型名称
     * @return 注销智能体模型的结果
     */
    public ReturnResult<Map<String, Object>> unregisterAgentModel(@PathVariable String modelName) {
        log.info("[AgentConfig] 注销 Agent 模型: modelName={}", modelName);
        // 从持久化配置中移除
        Map<String, Object> registryBody = new LinkedHashMap<>();
        registryBody.put("group", "agent-models");
        registryBody.put("fields", List.of(
                Map.of("field", modelName, "value", "", "valueType", "STRING",
                        "description", "unregistered", "status", 0)
        ));
        sysAiGlobalSettingService.replace(registryBody);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelName", modelName);
        result.put("status", "unregistered");
        return ReturnResult.ok(result, "Agent 模型已注销（需重启生效）");
    }

 // ==================== 智能体 编排 ====================

    @Operation(summary = "获取 Agent 编排结构", description = "返回用于 RelationGraph 可视化的节点和边数据")
    @GetMapping("/orchestration/{agentName}")
    /**
     * 获取Orchestration
     *
     * @param agentName 智能体名称
     * @return 获取orchestration的结果
     */
    public ReturnResult<Map<String, Object>> getOrchestration(@PathVariable String agentName) {
        var agent = agentRegistry.getByName(agentName);
        if (agent == null) {
            return ReturnResult.error("Agent " + agentName + " 不存在");
        }

        AgentDefinition definition = agent.getDefinition();
        List<AgentDefinition> subAgents = agent.getSubAgents();

        // 构建节点
        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, Object> masterNode = new LinkedHashMap<>();
        masterNode.put("id", agentName);
        masterNode.put("label", definition.getName());
        masterNode.put("type", "relation-rect");
        masterNode.put("kind", "master");
        masterNode.put("position", Map.of("x", 400, "y", 100));
        nodes.add(masterNode);

        // 从持久化配置加载编排边数据
        Map<String, String> persistedEdges = loadOrchestrationEdges(agentName);

        int idx = 0;
        for (AgentDefinition sub : subAgents) {
            Map<String, Object> subNode = new LinkedHashMap<>();
            subNode.put("id", sub.getId());
            subNode.put("label", sub.getName());
            subNode.put("type", "relation-rect");
            subNode.put("kind", "sub-agent");
            subNode.put("position", Map.of("x", 200 + idx * 200, "y", 350));
            nodes.add(subNode);
            idx++;
        }

 // 构建边（主智能体 → 子智能体），合并持久化配置
        List<Map<String, Object>> edges = new ArrayList<>();
        for (AgentDefinition sub : subAgents) {
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("id", agentName + "-" + sub.getId());
            edge.put("source", agentName);
            edge.put("target", sub.getId());
            edge.put("label", persistedEdges.getOrDefault(sub.getId(), "routes to"));
            edges.add(edge);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("agentName", agentName);
        result.put("mode", definition.isLeader() ? "ROUTER" : "SINGLE");
        return ReturnResult.ok(result);
    }

    @Permission("admin")
    @Operation(summary = "保存 Agent 编排", description = "持久化 Agent 的编排配置（子Agent关联、模式等）")
    @PostMapping("/orchestration/save")
    /**
     * 保存Orchestration
     *
     * @param body 主体
     * @return 保存orchestration的结果
     */
    public ReturnResult<Map<String, Object>> saveOrchestration(@RequestBody Map<String, Object> body) {
        String agentName = (String) body.get("agentName");
        if (agentName == null || agentName.isBlank()) {
            return ReturnResult.error("agentName 不能为空");
        }

        // 提取编排数据
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) body.get("edges");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) body.get("nodes");

        // 持久化到全局配置（orchestration 分组）
        List<Map<String, Object>> fields = new ArrayList<>();
        if (edges != null) {
            for (Map<String, Object> edge : edges) {
                String source = (String) edge.get("source");
                String target = (String) edge.get("target");
                String label = (String) edge.getOrDefault("label", "routes to");
                if (source != null && target != null) {
                    fields.add(Map.of(
                            "field", agentName + ":" + target,
                            "value", source + "|" + label,
                            "valueType", "STRING",
                            "description", "orchestration edge"
                    ));
                }
            }
        }
        if (nodes != null) {
            for (Map<String, Object> node : nodes) {
                String nodeId = (String) node.get("id");
                String kind = (String) node.getOrDefault("kind", "sub-agent");
                if (nodeId != null) {
                    fields.add(Map.of(
                            "field", agentName + ":" + nodeId + ":kind",
                            "value", kind,
                            "valueType", "STRING",
                            "description", "orchestration node kind"
                    ));
                }
            }
        }

        Map<String, Object> replaceBody = new LinkedHashMap<>();
        replaceBody.put("group", "orchestration");
        replaceBody.put("fields", fields);
        sysAiGlobalSettingService.replace(replaceBody);

        log.info("[AgentConfig] 编排配置已持久化: agentName={}, edges={}, nodes={}",
                agentName, edges != null ? edges.size() : 0, nodes != null ? nodes.size() : 0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agentName", agentName);
        result.put("edgesCount", edges != null ? edges.size() : 0);
        result.put("nodesCount", nodes != null ? nodes.size() : 0);
        result.put("status", "persisted");
        return ReturnResult.ok(result, "编排配置已保存到数据库");
    }

 // ==================== 智能体 模型列表 ====================

    @Operation(summary = "Agent 模型列表", description = "返回所有已注册为模型的 Agent 列表")
    @GetMapping("/agent-models")
    /**
     * 列表智能体模型
     *
     * @return 列表智能体模型的结果
     */
    public ReturnResult<List<Map<String, Object>>> listAgentModels() {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> models = chatClientRegistry.getModels();
        for (String model : models) {
            ChatClient client = chatClientRegistry.getByModel(model);
            if (client instanceof AgentModelDefinition agentModel) {
                AgentDefinition def = agentModel.getAgent().getDefinition();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("modelName", model);
                item.put("agentName", def.getName());
                item.put("description", def.getDescription());
                item.put("mode", def.isLeader() ? "ROUTER" : "SINGLE");
                result.add(item);
            }
        }
        return ReturnResult.ok(result);
    }

    // ==================== 辅助方法 ====================

    /**
      * 从全局配置加载指定 智能体 的编排边数据。
     *
     * @param agentName 智能体 名称
     * @return target → 标签 映射
     */
    private Map<String, String> loadOrchestrationEdges(String agentName) {
        Map<String, String> edges = new LinkedHashMap<>();
        try {
            var settings = sysAiGlobalSettingService.list("orchestration");
            String prefix = agentName + ":";
            for (Map<String, Object> setting : settings) {
                String field = (String) setting.get("field");
                if (field == null || !field.startsWith(prefix)) {
                    continue;
                }
                String suffix = field.substring(prefix.length());
 // 跳过 节点 种类 配置
                if (suffix.endsWith(":kind")) {
                    continue;
                }
                String value = (String) setting.get("value");
                if (value != null && value.contains("|")) {
 // 格式: 源|标签
                    edges.put(suffix, value.substring(value.indexOf("|") + 1));
                } else if (value != null) {
                    edges.put(suffix, value);
                }
            }
        } catch (Exception e) {
            log.warn("[AgentConfig] 加载编排配置失败: agentName={}", agentName, e);
        }
        return edges;
    }
}

