package com.chua.starter.spider.support.validator;

import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import com.chua.starter.spider.support.domain.SpiderFlowEdge;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;

import java.util.*;

/**
 * 编排定义校验器。
 *
 * <p>校验规则：
 * <ol>
 *   <li>必须存在唯一的 START 节点和唯一的 END 节点</li>
 *   <li>主链路必须从 START 可达 END（BFS 可达性）</li>
 *   <li>悬空节点检测：非 START/END 节点若无任何连线则报错</li>
 *   <li>非法回环检测：不经过 ERROR_HANDLER 的环路报错</li>
 * </ol>
 * </p>
 *
 * @author CH
 */
public class SpiderFlowValidator {

    /**
     * 校验编排定义。
     *
     * @param flow 编排定义
     * @return 校验结果，{@link SpiderFlowValidationResult#isValid()} 为 true 表示通过
     */
    public SpiderFlowValidationResult validate(SpiderFlowDefinition flow) {
        SpiderFlowValidationResult result = new SpiderFlowValidationResult();

        if (flow == null) {
            result.addError("$", "编排定义不能为 null");
            return result;
        }

        List<SpiderFlowNode> nodes = flow.getNodes() != null ? flow.getNodes() : Collections.emptyList();
        List<SpiderFlowEdge> edges = flow.getEdges() != null ? flow.getEdges() : Collections.emptyList();

        // 1. 找 START / END
        SpiderFlowNode startNode = findUniqueNode(nodes, SpiderNodeType.START, result);
        SpiderFlowNode endNode = findUniqueNode(nodes, SpiderNodeType.END, result);
        if (!result.isValid()) {
            return result;
        }

        // 构建邻接表（有向图）
        Map<String, List<String>> adjOut = buildAdjacency(nodes, edges);
        Map<String, List<String>> adjIn = buildInAdjacency(nodes, edges);

        // 2. 悬空节点检测（非 START/END 节点无任何连线）
        for (SpiderFlowNode node : nodes) {
            if (node.getNodeType() == SpiderNodeType.START || node.getNodeType() == SpiderNodeType.END) {
                continue;
            }
            boolean hasIn = adjIn.getOrDefault(node.getNodeId(), Collections.emptyList()).size() > 0;
            boolean hasOut = adjOut.getOrDefault(node.getNodeId(), Collections.emptyList()).size() > 0;
            if (!hasIn && !hasOut) {
                result.addError(node.getNodeId(), "悬空节点：该节点没有任何连线");
            }
        }

        // 3. 主链路可达性：从 START BFS 能否到达 END
        Set<String> reachableFromStart = bfsReachable(startNode.getNodeId(), adjOut);
        if (!reachableFromStart.contains(endNode.getNodeId())) {
            result.addError(endNode.getNodeId(), "主链路不可达：从 START 节点无法到达 END 节点");
        }

        // 4. 非法回环检测（不经过 ERROR_HANDLER 的环路）
        Set<String> errorHandlerIds = new HashSet<>();
        for (SpiderFlowNode node : nodes) {
            if (node.getNodeType() == SpiderNodeType.ERROR_HANDLER) {
                errorHandlerIds.add(node.getNodeId());
            }
        }
        detectIllegalCycles(nodes, adjOut, errorHandlerIds, result);

        return result;
    }

    private SpiderFlowNode findUniqueNode(List<SpiderFlowNode> nodes, SpiderNodeType type,
                                          SpiderFlowValidationResult result) {
        List<SpiderFlowNode> found = nodes.stream()
                .filter(n -> n.getNodeType() == type)
                .toList();
        if (found.isEmpty()) {
            result.addError("$", "缺少 " + type + " 节点");
            return null;
        }
        if (found.size() > 1) {
            result.addError("$", type + " 节点不唯一，共 " + found.size() + " 个");
            return null;
        }
        return found.get(0);
    }

    private Map<String, List<String>> buildAdjacency(List<SpiderFlowNode> nodes, List<SpiderFlowEdge> edges) {
        Map<String, List<String>> adj = new HashMap<>();
        for (SpiderFlowNode node : nodes) {
            adj.put(node.getNodeId(), new ArrayList<>());
        }
        for (SpiderFlowEdge edge : edges) {
            adj.computeIfAbsent(edge.getSourceNodeId(), k -> new ArrayList<>())
               .add(edge.getTargetNodeId());
        }
        return adj;
    }

    private Map<String, List<String>> buildInAdjacency(List<SpiderFlowNode> nodes, List<SpiderFlowEdge> edges) {
        Map<String, List<String>> adj = new HashMap<>();
        for (SpiderFlowNode node : nodes) {
            adj.put(node.getNodeId(), new ArrayList<>());
        }
        for (SpiderFlowEdge edge : edges) {
            adj.computeIfAbsent(edge.getTargetNodeId(), k -> new ArrayList<>())
               .add(edge.getSourceNodeId());
        }
        return adj;
    }

    private Set<String> bfsReachable(String startId, Map<String, List<String>> adj) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(startId);
        visited.add(startId);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (String next : adj.getOrDefault(cur, Collections.emptyList())) {
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return visited;
    }

    /**
     * 使用 DFS 检测不经过 ERROR_HANDLER 的环路。
     * 若环路中所有节点均不是 ERROR_HANDLER，则视为非法回环。
     */
    private void detectIllegalCycles(List<SpiderFlowNode> nodes,
                                     Map<String, List<String>> adj,
                                     Set<String> errorHandlerIds,
                                     SpiderFlowValidationResult result) {
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();

        for (SpiderFlowNode node : nodes) {
            if (!visited.contains(node.getNodeId())) {
                List<String> cyclePath = new ArrayList<>();
                dfsCycleDetect(node.getNodeId(), adj, visited, inStack, cyclePath, errorHandlerIds, result);
            }
        }
    }

    private boolean dfsCycleDetect(String nodeId,
                                   Map<String, List<String>> adj,
                                   Set<String> visited,
                                   Set<String> inStack,
                                   List<String> path,
                                   Set<String> errorHandlerIds,
                                   SpiderFlowValidationResult result) {
        visited.add(nodeId);
        inStack.add(nodeId);
        path.add(nodeId);

        for (String neighbor : adj.getOrDefault(nodeId, Collections.emptyList())) {
            if (!visited.contains(neighbor)) {
                if (dfsCycleDetect(neighbor, adj, visited, inStack, path, errorHandlerIds, result)) {
                    return true;
                }
            } else if (inStack.contains(neighbor)) {
                // 找到环路，检查环路中是否有 ERROR_HANDLER
                int cycleStart = path.indexOf(neighbor);
                List<String> cycle = path.subList(cycleStart, path.size());
                boolean hasErrorHandler = cycle.stream().anyMatch(errorHandlerIds::contains);
                if (!hasErrorHandler) {
                    result.addError(nodeId, "非法回环：检测到不经过 ERROR_HANDLER 的环路，涉及节点: " + cycle);
                }
                return false;
            }
        }

        inStack.remove(nodeId);
        path.remove(path.size() - 1);
        return false;
    }
}
