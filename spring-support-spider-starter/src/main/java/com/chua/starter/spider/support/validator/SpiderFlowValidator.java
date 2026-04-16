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
 *   <li>连线类型兼容性校验：源节点输出类型必须与目标节点接受的输入类型兼容</li>
 *   <li>CONDITION 节点必须恰好有 2 条出边（true/false 双端口）</li>
 * </ol>
 * </p>
 *
 * @author CH
 */
public class SpiderFlowValidator {

    /**
     * 节点数据类型标识，用于连线兼容性校验。
     */
    private enum DataType {
        URL_CONTEXT,
        RAW_DOCUMENT,
        RAW_RECORD,
        PROCESSED_RECORD,
        PIPELINE_RESULT,
        ERROR_CONTEXT,
        BRANCH,
        ANY
    }

    /**
     * 返回节点的输出数据类型集合。
     * HUMAN_INPUT 和 DELAY 为透传节点，其输出类型取决于上游，此处返回 ANY 表示透传。
     */
    private Set<DataType> getOutputTypes(SpiderNodeType type) {
        switch (type) {
            case START:          return EnumSet.of(DataType.URL_CONTEXT);
            case DOWNLOADER:     return EnumSet.of(DataType.RAW_DOCUMENT);
            case URL_EXTRACTOR:  return EnumSet.of(DataType.URL_CONTEXT);
            case DATA_EXTRACTOR: return EnumSet.of(DataType.RAW_RECORD);
            case DETAIL_FETCH:   return EnumSet.of(DataType.RAW_RECORD);
            case PROCESSOR:      return EnumSet.of(DataType.PROCESSED_RECORD);
            case FILTER:         return EnumSet.of(DataType.RAW_RECORD, DataType.PROCESSED_RECORD);
            case HUMAN_INPUT:    return EnumSet.of(DataType.ANY);
            case PIPELINE:       return EnumSet.of(DataType.PIPELINE_RESULT);
            case END:            return EnumSet.noneOf(DataType.class);
            case CONDITION:      return EnumSet.of(DataType.BRANCH);
            case ERROR_HANDLER:  return EnumSet.of(DataType.URL_CONTEXT, DataType.RAW_RECORD, DataType.PIPELINE_RESULT);
            case DELAY:          return EnumSet.of(DataType.ANY);
            default:             return EnumSet.of(DataType.ANY);
        }
    }

    /**
     * 返回节点接受的输入数据类型集合。
     * HUMAN_INPUT 和 DELAY 为透传节点，接受任意类型。
     */
    private Set<DataType> getAcceptedInputTypes(SpiderNodeType type) {
        switch (type) {
            case START:          return EnumSet.noneOf(DataType.class);
            case DOWNLOADER:     return EnumSet.of(DataType.URL_CONTEXT);
            case URL_EXTRACTOR:  return EnumSet.of(DataType.RAW_DOCUMENT);
            case DATA_EXTRACTOR: return EnumSet.of(DataType.RAW_DOCUMENT);
            case DETAIL_FETCH:   return EnumSet.of(DataType.RAW_RECORD);
            case PROCESSOR:      return EnumSet.of(DataType.RAW_RECORD, DataType.PROCESSED_RECORD);
            case FILTER:         return EnumSet.of(DataType.RAW_RECORD, DataType.PROCESSED_RECORD);
            case HUMAN_INPUT:    return EnumSet.of(DataType.ANY);
            case PIPELINE:       return EnumSet.of(DataType.PROCESSED_RECORD, DataType.RAW_RECORD);
            case END:            return EnumSet.of(DataType.PIPELINE_RESULT);
            case CONDITION:      return EnumSet.of(DataType.RAW_RECORD, DataType.PROCESSED_RECORD, DataType.RAW_DOCUMENT);
            case ERROR_HANDLER:  return EnumSet.of(DataType.ERROR_CONTEXT);
            case DELAY:          return EnumSet.of(DataType.ANY);
            default:             return EnumSet.of(DataType.ANY);
        }
    }

    /**
     * 判断源节点的输出类型与目标节点的接受输入类型是否兼容。
     * 若任一方包含 ANY，则视为透传，直接兼容。
     */
    private boolean isTypeCompatible(Set<DataType> outputTypes, Set<DataType> acceptedInputTypes) {
        if (outputTypes.contains(DataType.ANY) || acceptedInputTypes.contains(DataType.ANY)) {
            return true;
        }
        for (DataType out : outputTypes) {
            if (acceptedInputTypes.contains(out)) {
                return true;
            }
        }
        return false;
    }

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

        // 构建邻接表（有向图）和节点 ID → 节点对象映射
        Map<String, List<String>> adjOut = buildAdjacency(nodes, edges);
        Map<String, List<String>> adjIn = buildInAdjacency(nodes, edges);
        Map<String, SpiderFlowNode> nodeMap = buildNodeMap(nodes);

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

        // 5. 连线类型兼容性校验
        validateConnectionTypes(edges, nodeMap, result);

        // 6. CONDITION 节点出边数量校验（必须恰好 2 条：true/false 双端口）
        validateConditionOutEdges(nodes, adjOut, result);

        return result;
    }

    /**
     * 校验每条边的源节点输出类型与目标节点接受输入类型是否兼容。
     */
    private void validateConnectionTypes(List<SpiderFlowEdge> edges,
                                         Map<String, SpiderFlowNode> nodeMap,
                                         SpiderFlowValidationResult result) {
        for (SpiderFlowEdge edge : edges) {
            SpiderFlowNode source = nodeMap.get(edge.getSourceNodeId());
            SpiderFlowNode target = nodeMap.get(edge.getTargetNodeId());
            if (source == null || target == null) {
                continue;
            }
            Set<DataType> outputTypes = getOutputTypes(source.getNodeType());
            Set<DataType> acceptedInputTypes = getAcceptedInputTypes(target.getNodeType());

            // END 节点无输出，START 节点无输入，跳过这两种边界情况的反向校验
            if (outputTypes.isEmpty()) {
                result.addError(edge.getEdgeId() != null ? edge.getEdgeId() : source.getNodeId(),
                        "连线非法：" + source.getNodeType() + " 节点没有输出，不能作为连线源");
                continue;
            }
            if (acceptedInputTypes.isEmpty()) {
                result.addError(edge.getEdgeId() != null ? edge.getEdgeId() : target.getNodeId(),
                        "连线非法：" + target.getNodeType() + " 节点不接受任何输入，不能作为连线目标");
                continue;
            }

            if (!isTypeCompatible(outputTypes, acceptedInputTypes)) {
                result.addError(edge.getEdgeId() != null ? edge.getEdgeId() : source.getNodeId(),
                        "连线类型不兼容：" + source.getNodeType() + " 输出类型 " + outputTypes
                                + " 与 " + target.getNodeType() + " 接受的输入类型 " + acceptedInputTypes + " 不匹配");
            }
        }
    }

    /**
     * 校验 CONDITION 节点必须恰好有 2 条出边（true/false 双端口）。
     */
    private void validateConditionOutEdges(List<SpiderFlowNode> nodes,
                                           Map<String, List<String>> adjOut,
                                           SpiderFlowValidationResult result) {
        for (SpiderFlowNode node : nodes) {
            if (node.getNodeType() != SpiderNodeType.CONDITION) {
                continue;
            }
            int outCount = adjOut.getOrDefault(node.getNodeId(), Collections.emptyList()).size();
            if (outCount != 2) {
                result.addError(node.getNodeId(),
                        "CONDITION 节点必须恰好有 2 条出边（true/false 双端口），当前出边数量：" + outCount);
            }
        }
    }

    private Map<String, SpiderFlowNode> buildNodeMap(List<SpiderFlowNode> nodes) {
        Map<String, SpiderFlowNode> map = new HashMap<>();
        for (SpiderFlowNode node : nodes) {
            map.put(node.getNodeId(), node);
        }
        return map;
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
