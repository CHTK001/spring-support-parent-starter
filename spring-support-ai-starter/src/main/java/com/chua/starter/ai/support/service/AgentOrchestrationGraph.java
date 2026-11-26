package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent 编排图契约校验：在任何持久化之前验证结构、归属和无环约束。
 *
 * @param nodes 原节点列表
 * @param edges 原连线列表
 * @author CH
 */
public record AgentOrchestrationGraph(List<?> nodes, List<?> edges) {

    /**
     * 校验完整图请求，缺失字段不能被解释为清空图。
     *
     * @param body 请求体
     * @param allowedAgentIds 本主 Agent 及其子 Agent 标识
     * @return 校验后的图
     */
    public static AgentOrchestrationGraph validate(Map<String, Object> body, Set<String> allowedAgentIds) {
        if (body == null || !(body.get("nodes") instanceof List<?> nodes)
                || !(body.get("edges") instanceof List<?> edges)) {
            throw invalid("编排必须同时包含 nodes 和 edges 数组");
        }
        Map<String, Integer> degrees = new LinkedHashMap<>();
        Map<String, List<String>> next = new HashMap<>();
        Set<String> agents = new HashSet<>();
        for (Object raw : nodes) {
            if (!(raw instanceof Map<?, ?> node) || !(node.get("id") instanceof String id) || id.isBlank()) {
                throw invalid("每个节点必须包含非空字符串 id");
            }
            Object ref = node.get("agent_id") != null ? node.get("agent_id") : node.get("agentId");
            String agentId = ref == null ? id : String.valueOf(ref);
            if (!allowedAgentIds.contains(agentId)) {
                throw invalid("节点引用了不属于当前编排的 Agent: " + agentId);
            }
            if (degrees.putIfAbsent(id, 0) != null || !agents.add(agentId)) {
                throw invalid("编排节点或 Agent 引用重复: " + id);
            }
            next.put(id, new ArrayList<>());
        }
        Set<List<String>> seenEdges = new HashSet<>();
        for (Object raw : edges) {
            if (!(raw instanceof Map<?, ?> edge) || !(edge.get("source") instanceof String source)
                    || !(edge.get("target") instanceof String target)
                    || !degrees.containsKey(source) || !degrees.containsKey(target)) {
                throw invalid("连线 source 和 target 必须引用已有节点 id");
            }
            if (source.equals(target) || !seenEdges.add(List.of(source, target))) {
                throw invalid("编排不允许自连接或重复连线");
            }
            next.get(source).add(target);
            degrees.merge(target, 1, Integer::sum);
        }
        ArrayDeque<String> ready = new ArrayDeque<>();
        degrees.forEach((id, degree) -> {
            if (degree == 0) {
                ready.add(id);
            }
        });
        int visited = 0;
        while (!ready.isEmpty()) {
            String id = ready.removeFirst();
            visited++;
            for (String target : next.get(id)) {
                if (degrees.merge(target, -1, Integer::sum) == 0) {
                    ready.addLast(target);
                }
            }
        }
        if (visited != nodes.size()) {
            throw invalid("编排存在循环依赖，请移除环路后保存");
        }
        return new AgentOrchestrationGraph(nodes, edges);
    }

    /**
     * 构造统一的请求参数错误。
     * @param message 原因
     * @return 业务异常
     */
    private static ApiException invalid(String message) {
        return new ApiException(ApiErrorCode.INVALID_PARAM, message);
    }
}
