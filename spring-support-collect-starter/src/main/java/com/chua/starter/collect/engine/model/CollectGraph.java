package com.chua.starter.collect.engine.model;

import com.chua.starter.collect.dto.CollectDslDTO;
import com.chua.starter.collect.dto.CollectEdgeDTO;
import com.chua.starter.collect.dto.CollectNodeDTO;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 可执行流程图。
 *
 * <p>由 DSL 构建并做静态校验。环路在构建期直接拒绝：循环语义由节点的循环次数参数表达，
 * 避免汇聚计数在有环图上死锁。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Getter
public class CollectGraph {

    /**
     * 开始节点类型。
     */
    public static final String SHAPE_START = "start";

    /**
     * 结束节点类型。
     */
    public static final String SHAPE_END = "end";

    /**
     * 节点表，key 为节点ID。
     */
    private final Map<String, CollectNode> nodes = new LinkedHashMap<>();

    /**
     * 原始 DSL。
     */
    private final CollectDslDTO dsl;

    /**
     * 构造流程图。
     *
     * @param dsl DSL 对象
     */
    private CollectGraph(CollectDslDTO dsl) {
        this.dsl = dsl;
    }

    /**
     * 由 DSL 构建流程图。
     *
     * @param dsl          DSL 对象
     * @param knownShapes  已注册节点类型集合，为空则跳过类型校验
     * @return 流程图
     * @throws CollectGraphException 校验失败
     */
    public static CollectGraph build(CollectDslDTO dsl, Set<String> knownShapes) {
        if (dsl == null || dsl.nodes() == null || dsl.nodes().isEmpty()) {
            throw new CollectGraphException("流程图没有任何节点");
        }
        CollectGraph graph = new CollectGraph(dsl);
        for (CollectNodeDTO dto : dsl.nodes()) {
            if (dto.id() == null || dto.id().isBlank()) {
                throw new CollectGraphException("存在缺少 ID 的节点");
            }
            if (dto.shape() == null || dto.shape().isBlank()) {
                throw new CollectGraphException("节点 " + dto.id() + " 缺少类型");
            }
            if (knownShapes != null && !knownShapes.isEmpty() && !knownShapes.contains(dto.shape())) {
                throw new CollectGraphException("节点 " + dto.id() + " 的类型未注册: " + dto.shape());
            }
            if (graph.nodes.containsKey(dto.id())) {
                throw new CollectGraphException("节点 ID 重复: " + dto.id());
            }
            graph.nodes.put(dto.id(), new CollectNode(dto));
        }
        List<CollectEdgeDTO> edges = dsl.edges() == null ? List.of() : dsl.edges();
        for (CollectEdgeDTO dto : edges) {
            CollectNode from = graph.nodes.get(dto.from());
            CollectNode to = graph.nodes.get(dto.to());
            if (from == null || to == null) {
                throw new CollectGraphException("连线 " + dto.id() + " 的端点不存在");
            }
            if (from == to) {
                throw new CollectGraphException("节点 " + from.id() + " 不允许自连");
            }
            from.addOutEdge(new CollectEdge(dto, to));
            to.increaseInDegree();
        }
        graph.detectCycle();
        if (graph.entryNodes().isEmpty()) {
            throw new CollectGraphException("流程缺少开始节点，且不存在入度为 0 的节点");
        }
        graph.checkStartEnd();
        graph.checkOutputVariables();
        return graph;
    }

    /**
     * 校验输出名唯一：每个产生数据的节点按输出名在上下文中独立保存，
     * 同名输出会让下游引用失去节点归属，保存期直接拒绝。
     *
     * <p>只检查显式填写的 outputVariable；留空时由各执行器默认名兜底，
     * 不在这里猜测后端默认值。</p>
     *
     * @throws CollectGraphException 输出名重复
     */
    private void checkOutputVariables() {
        Map<String, List<CollectNode>> byOutput = new LinkedHashMap<>();
        for (CollectNode node : nodes.values()) {
            String output = node.param("outputVariable");
            if (output == null || output.isBlank()) {
                continue;
            }
            byOutput.computeIfAbsent(output.trim(), key -> new ArrayList<>()).add(node);
        }
        List<String> duplicates = new ArrayList<>();
        for (Map.Entry<String, List<CollectNode>> entry : byOutput.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            duplicates.add(entry.getKey() + " -> " + entry.getValue().stream()
                    .map(node -> node.id() + "（" + node.name() + "）")
                    .collect(Collectors.joining("、")));
        }
        if (!duplicates.isEmpty()) {
            throw new CollectGraphException("输出名不允许重复: " + String.join("；", duplicates));
        }
    }

    /**
     * 校验首尾结构：只能有一个开始节点，每条分支的末端必须是结束节点，且不能有游离节点。
     *
     * <p>结束节点本身不能再往后连，否则下游分支脱离了收束点，运行到那里就悄悄断了；
     * 反向同理——除开始节点外入度为 0 的节点没有任何路径可达，留在画布上只会永远不执行。</p>
     *
     * @throws CollectGraphException 结构不合规
     */
    private void checkStartEnd() {
        List<CollectNode> starts = new ArrayList<>();
        List<CollectNode> ends = new ArrayList<>();
        for (CollectNode node : nodes.values()) {
            if (SHAPE_START.equals(node.shape())) {
                starts.add(node);
            }
            if (SHAPE_END.equals(node.shape())) {
                ends.add(node);
            }
        }
        if (starts.isEmpty()) {
            throw new CollectGraphException("流程缺少开始节点");
        }
        if (starts.size() > 1) {
            throw new CollectGraphException("流程只能有一个开始节点，当前有 " + starts.size() + " 个: "
                    + starts.stream().map(CollectNode::id).toList());
        }
        if (ends.isEmpty()) {
            throw new CollectGraphException("流程必须以「结束」节点收尾，请补充结束节点");
        }
        for (CollectNode node : nodes.values()) {
            boolean leaf = node.getOutEdges().isEmpty();
            if (leaf && !SHAPE_END.equals(node.shape())) {
                throw new CollectGraphException("节点 " + node.id() + "（" + node.name() + "）没有出边，"
                        + "每条分支都必须收到「结束」节点");
            }
            if (!leaf && SHAPE_END.equals(node.shape())) {
                throw new CollectGraphException("结束节点 " + node.id() + " 不能再连出边");
            }
        }
        List<String> orphans = new ArrayList<>();
        for (CollectNode node : nodes.values()) {
            if (!SHAPE_START.equals(node.shape()) && node.getInDegree() == 0) {
                orphans.add(node.id() + "（" + node.name() + "）");
            }
        }
        if (!orphans.isEmpty()) {
            throw new CollectGraphException("节点 " + String.join("、", orphans) + " 没有入边，永远不会被执行，请连线或删除");
        }
    }

    /**
     * 全部节点。
     *
     * @return 节点集合
     */
    public Collection<CollectNode> allNodes() {
        return nodes.values();
    }

    /**
     * 入口节点：优先取 start 类型节点，没有则取入度为 0 的节点。
     *
     * @return 入口节点列表
     */
    public List<CollectNode> entryNodes() {
        List<CollectNode> starts = new ArrayList<>();
        for (CollectNode node : nodes.values()) {
            if (SHAPE_START.equals(node.shape())) {
                starts.add(node);
            }
        }
        if (!starts.isEmpty()) {
            return starts;
        }
        List<CollectNode> orphans = new ArrayList<>();
        for (CollectNode node : nodes.values()) {
            if (node.getInDegree() == 0) {
                orphans.add(node);
            }
        }
        return orphans;
    }

    /**
     * 环路检测。
     *
     * @throws CollectGraphException 存在环路
     */
    private void detectCycle() {
        Map<String, Integer> state = new HashMap<>();
        for (CollectNode node : nodes.values()) {
            visit(node, state, new HashSet<>());
        }
    }

    /**
     * 深度优先遍历标记访问状态。
     *
     * @param node      当前节点
     * @param state     状态表，1 访问中 2 已完成
     * @param visiting  当前路径上的节点集合
     */
    private void visit(CollectNode node, Map<String, Integer> state, Set<String> visiting) {
        Integer mark = state.get(node.id());
        if (mark != null && mark == 2) {
            return;
        }
        if (visiting.contains(node.id())) {
            throw new CollectGraphException("流程存在环路: " + node.id() + "，循环请使用节点的循环次数参数表达");
        }
        visiting.add(node.id());
        for (CollectEdge edge : node.getOutEdges()) {
            visit(edge.getTarget(), state, visiting);
        }
        visiting.remove(node.id());
        state.put(node.id(), 2);
    }

    /**
     * 流程图校验异常。
     *
     * @author CH
     * @since 4.0.0.42
     */
    public static class CollectGraphException extends RuntimeException {

        /**
         * 构造异常。
         *
         * @param message 校验失败说明
         */
        public CollectGraphException(String message) {
            super(message);
        }
    }
}
