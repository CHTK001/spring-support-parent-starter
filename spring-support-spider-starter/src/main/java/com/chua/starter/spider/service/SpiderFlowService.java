package com.chua.starter.spider.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.spider.entity.SpiderFlow;
import com.chua.starter.spider.entity.SpiderFlowEdge;
import com.chua.starter.spider.entity.SpiderFlowHistory;
import com.chua.starter.spider.entity.SpiderFlowNode;
import com.chua.starter.spider.mapper.SpiderFlowEdgeMapper;
import com.chua.starter.spider.mapper.SpiderFlowHistoryMapper;
import com.chua.starter.spider.mapper.SpiderFlowMapper;
import com.chua.starter.spider.mapper.SpiderFlowNodeMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程定义 Service：负责 CRUD、节点/边维护、版本快照。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpiderFlowService {

    /**
     * JSON 解析器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final SpiderFlowMapper flowMapper;
    private final SpiderFlowNodeMapper nodeMapper;
    private final SpiderFlowEdgeMapper edgeMapper;
    private final SpiderFlowHistoryMapper historyMapper;

    /**
     * 分页查询流程列表。
     *
     * @param pageNo   页码
     * @param pageSize 每页大小
     * @param keyword  名称模糊查询
     * @return 流程列表（按更新时间倒序）
     */
    public List<SpiderFlow> pageFlows(int pageNo, int pageSize, String keyword) {
        LambdaQueryWrapper<SpiderFlow> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(SpiderFlow::getFlowName, keyword).or().like(SpiderFlow::getFlowCode, keyword);
        }
        wrapper.orderByDesc(SpiderFlow::getUpdateTime);
        int offset = Math.max(0, (pageNo - 1) * pageSize);
        wrapper.last("LIMIT " + offset + ", " + pageSize);
        return flowMapper.selectList(wrapper);
    }

    /**
     * 按编码查询完整流程（含节点 + 边）。
     *
     * @param flowCode 流程编码
     * @return 流程聚合视图
     */
    public SpiderFlowAggregate getByCode(String flowCode) {
        SpiderFlow flow = findByCode(flowCode);
        if (flow == null) {
            return null;
        }
        List<SpiderFlowNode> nodes = loadNodes(flow.getFlowId());
        List<SpiderFlowEdge> edges = loadEdges(flow.getFlowId());
        return new SpiderFlowAggregate(flow, nodes, edges);
    }

    /**
     * 保存完整流程：覆盖式保存（删除旧节点 + 边，重新写入）+ 版本快照。
     *
     * @param aggregate 聚合视图（来自前端 RelationGraph save）
     * @return 保存后版本号
     */
    @Transactional(rollbackFor = Exception.class)
    public int save(SpiderFlowAggregate aggregate, String changeNote, String changeUser) {
        SpiderFlow flow = aggregate.flow();
        if (StringUtils.isBlank(flow.getFlowCode())) {
            throw new IllegalArgumentException("flowCode 不能为空");
        }

        // 同步填充 flow_graph_json（前端 RelationGraph 完整图 JSON，供画布回填）
        try {
            String graphJson = objectMapper.writeValueAsString(java.util.Map.of(
                    "flowCode", flow.getFlowCode(),
                    "flowName", flow.getFlowName() == null ? "" : flow.getFlowName(),
                    "flowRemark", flow.getFlowRemark() == null ? "" : flow.getFlowRemark(),
                    "flowStatus", flow.getFlowStatus() == null ? 1 : flow.getFlowStatus(),
                    "nodes", aggregate.nodes() == null ? List.of() : aggregate.nodes(),
                    "edges", aggregate.edges() == null ? List.of() : aggregate.edges()
            ));
            flow.setFlowGraphJson(graphJson);
            flow.setFlowNodeCount(aggregate.nodes() == null ? 0 : aggregate.nodes().size());
        } catch (Exception e) {
            throw new IllegalStateException("序列化 graphJson 失败: " + e.getMessage(), e);
        }

        SpiderFlow existed = findByCode(flow.getFlowCode());
        int newVersion = 1;
        if (existed == null) {
            flow.setFlowVersion(1);
            flow.setCreateTime(LocalDateTime.now());
            flow.setUpdateTime(LocalDateTime.now());
            flowMapper.insert(flow);
        } else {
            newVersion = (existed.getFlowVersion() == null ? 0 : existed.getFlowVersion()) + 1;
            flow.setFlowId(existed.getFlowId());
            flow.setFlowVersion(newVersion);
            flow.setUpdateTime(LocalDateTime.now());
            flowMapper.updateById(flow);
            // 写历史快照
            SpiderFlowHistory hist = new SpiderFlowHistory();
            hist.setFlowId(existed.getFlowId());
            hist.setFlowVersion(existed.getFlowVersion());
            hist.setFlowGraphJson(existed.getFlowGraphJson());
            hist.setFlowChangeNote(changeNote);
            hist.setFlowChangeUser(changeUser);
            hist.setCreateTime(LocalDateTime.now());
            historyMapper.insert(hist);
        }

        // 清空旧节点和边（外键 ON DELETE CASCADE 也会清，但显式更稳）
        LambdaQueryWrapper<SpiderFlowNode> nodeWrapper = new LambdaQueryWrapper<>();
        nodeWrapper.eq(SpiderFlowNode::getFlowId, flow.getFlowId());
        nodeMapper.delete(nodeWrapper);
        LambdaQueryWrapper<SpiderFlowEdge> edgeWrapper = new LambdaQueryWrapper<>();
        edgeWrapper.eq(SpiderFlowEdge::getFlowId, flow.getFlowId());
        edgeMapper.delete(edgeWrapper);

        // 写新节点
        if (aggregate.nodes() != null) {
            for (SpiderFlowNode n : aggregate.nodes()) {
                n.setFlowId(flow.getFlowId());
                n.setFlowNodeId(null);
                n.setCreateTime(LocalDateTime.now());
                n.setUpdateTime(LocalDateTime.now());
                nodeMapper.insert(n);
            }
        }
        // 写新边
        if (aggregate.edges() != null) {
            for (SpiderFlowEdge e : aggregate.edges()) {
                e.setFlowId(flow.getFlowId());
                e.setFlowEdgeId(null);
                e.setCreateTime(LocalDateTime.now());
                edgeMapper.insert(e);
            }
        }
        log.info("[Flow] 保存: code={}, version={}, nodes={}, edges={}",
                flow.getFlowCode(), newVersion,
                aggregate.nodes() == null ? 0 : aggregate.nodes().size(),
                aggregate.edges() == null ? 0 : aggregate.edges().size());
        return newVersion;
    }

    /**
     * 切换启用状态。
     *
     * @param flowCode 流程编码
     * @param status   1=启用 / 0=禁用
     */
    public boolean toggle(String flowCode, int status) {
        SpiderFlow flow = findByCode(flowCode);
        if (flow == null) {
            return false;
        }
        flow.setFlowStatus(status);
        flow.setUpdateTime(LocalDateTime.now());
        flowMapper.updateById(flow);
        return true;
    }

    /**
     * 删除流程（级联删除节点/边/历史）。
     *
     * @param flowCode 流程编码
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String flowCode) {
        SpiderFlow flow = findByCode(flowCode);
        if (flow == null) {
            return false;
        }
        flowMapper.deleteById(flow.getFlowId());
        return true;
    }

    /**
     * 历史版本列表（按版本号倒序）。
     *
     * @param flowCode 流程编码
     * @return 历史列表
     */
    public List<SpiderFlowHistory> listHistory(String flowCode) {
        SpiderFlow flow = findByCode(flowCode);
        if (flow == null) {
            return List.of();
        }
        LambdaQueryWrapper<SpiderFlowHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpiderFlowHistory::getFlowId, flow.getFlowId());
        wrapper.orderByDesc(SpiderFlowHistory::getFlowVersion);
        return historyMapper.selectList(wrapper);
    }

    /**
     * 回滚到指定历史版本：把历史的 graphJson 写回主表（version 不变 + 写一条新历史）。
     *
     * @param flowCode 流程编码
     * @param version   历史版本号
     * @return 新版本号
     */
    @Transactional(rollbackFor = Exception.class)
    public int rollback(String flowCode, int version) {
        SpiderFlow flow = findByCode(flowCode);
        if (flow == null) {
            throw new IllegalArgumentException("流程不存在: " + flowCode);
        }
        LambdaQueryWrapper<SpiderFlowHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpiderFlowHistory::getFlowId, flow.getFlowId()).eq(SpiderFlowHistory::getFlowVersion, version);
        SpiderFlowHistory hist = historyMapper.selectOne(wrapper);
        if (hist == null) {
            throw new IllegalArgumentException("历史版本不存在: " + version);
        }

        // 把当前主表版本写历史
        SpiderFlowHistory current = new SpiderFlowHistory();
        current.setFlowId(flow.getFlowId());
        current.setFlowVersion(flow.getFlowVersion());
        current.setFlowGraphJson(flow.getFlowGraphJson());
        current.setFlowChangeNote("回滚前的快照（自动保存）");
        current.setFlowChangeUser("system");
        current.setCreateTime(LocalDateTime.now());
        historyMapper.insert(current);

        // 用历史的 graphJson 重写主表 + 重新解析节点/边
        try {
            SpiderFlowAggregate aggregate = parseGraphJson(hist.getFlowGraphJson());
            aggregate.flow().setFlowId(flow.getFlowId());
            aggregate.flow().setFlowCode(flow.getFlowCode());
            aggregate.flow().setFlowVersion(flow.getFlowVersion() + 1);
            aggregate.flow().setUpdateTime(LocalDateTime.now());
            flowMapper.updateById(aggregate.flow());

            // 重新写入节点 + 边（清空旧的）
            LambdaQueryWrapper<SpiderFlowNode> nodeWrapper = new LambdaQueryWrapper<>();
            nodeWrapper.eq(SpiderFlowNode::getFlowId, flow.getFlowId());
            nodeMapper.delete(nodeWrapper);
            if (aggregate.nodes() != null) {
                for (SpiderFlowNode n : aggregate.nodes()) {
                    n.setFlowId(flow.getFlowId());
                    n.setFlowNodeId(null);
                    n.setCreateTime(LocalDateTime.now());
                    n.setUpdateTime(LocalDateTime.now());
                    nodeMapper.insert(n);
                }
            }
            LambdaQueryWrapper<SpiderFlowEdge> edgeWrapper = new LambdaQueryWrapper<>();
            edgeWrapper.eq(SpiderFlowEdge::getFlowId, flow.getFlowId());
            edgeMapper.delete(edgeWrapper);
            if (aggregate.edges() != null) {
                for (SpiderFlowEdge e : aggregate.edges()) {
                    e.setFlowId(flow.getFlowId());
                    e.setFlowEdgeId(null);
                    e.setCreateTime(LocalDateTime.now());
                    edgeMapper.insert(e);
                }
            }
            return aggregate.flow().getFlowVersion();
        } catch (Exception e) {
            throw new IllegalStateException("解析历史 graphJson 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析 RelationGraph JSON 为聚合视图。
     *
     * @param graphJson RelationGraph 完整 JSON 字符串
     * @return 聚合视图（flow + nodes + edges）
     */
    public SpiderFlowAggregate parseGraphJson(String graphJson) {
        if (StringUtils.isBlank(graphJson)) {
            throw new IllegalArgumentException("graphJson 不能为空");
        }
        try {
            Map<String, Object> root = objectMapper.readValue(graphJson, new TypeReference<Map<String, Object>>() {});
            SpiderFlow flow = new SpiderFlow();
            flow.setFlowCode(asString(root.get("flowCode")));
            flow.setFlowName(asString(root.get("flowName")));
            flow.setFlowRemark(asString(root.get("flowRemark")));
            flow.setFlowStatus(asInt(root.get("flowStatus"), 1));
            flow.setFlowNodeCount(0);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawNodes = (List<Map<String, Object>>) root.getOrDefault("nodes", List.of());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawEdges = (List<Map<String, Object>>) root.getOrDefault("edges", List.of());

            Map<String, Object> graph = (Map<String, Object>) root.getOrDefault("graph", new HashMap<>());
            // graphJson 可包含完整 graph 对象用于回填 RelationGraph 画布
            flow.setFlowGraphJson(objectMapper.writeValueAsString(root));

            // 节点：从 graph.nodes 或顶层 nodes 取
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> graphNodes = (List<Map<String, Object>>) graph.getOrDefault("nodes", List.of());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> graphEdges = (List<Map<String, Object>>) graph.getOrDefault("lines", graph.getOrDefault("edges", List.of()));

            List<SpiderFlowNode> nodes = new java.util.ArrayList<>();
            for (Map<String, Object> n : rawNodes.isEmpty() ? graphNodes : rawNodes) {
                SpiderFlowNode node = new SpiderFlowNode();
                node.setFlowNodeKey(asString(n.getOrDefault("id", n.get("key"))));
                node.setFlowNodeType(asString(n.getOrDefault("type", "script")));
                node.setFlowNodeName(asString(n.getOrDefault("name", n.get("text"))));
                node.setFlowNodeX(asInt(n.get("x"), 0));
                node.setFlowNodeY(asInt(n.get("y"), 0));
                node.setFlowNodeStatus(1);
                // config 字段
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) n.getOrDefault("data", Map.of());
                node.setFlowNodeConfig(objectMapper.writeValueAsString(data));
                nodes.add(node);
            }

            List<SpiderFlowEdge> edges = new java.util.ArrayList<>();
            for (Map<String, Object> e : rawEdges.isEmpty() ? graphEdges : rawEdges) {
                SpiderFlowEdge edge = new SpiderFlowEdge();
                edge.setFlowEdgeFrom(asString(e.getOrDefault("from", e.get("fromNode"))));
                edge.setFlowEdgeTo(asString(e.getOrDefault("to", e.get("toNode"))));
                edge.setFlowEdgeText(asString(e.get("text")));
                edge.setFlowEdgePriority(asInt(e.get("priority"), 0));
                edge.setFlowEdgeCondField(asString(e.get("condField")));
                edge.setFlowEdgeCondOp(asString(e.get("condOp")));
                edge.setFlowEdgeCondValue(asString(e.get("condValue")));
                edges.add(edge);
            }

            flow.setFlowNodeCount(nodes.size());
            return new SpiderFlowAggregate(flow, nodes, edges);
        } catch (Exception e) {
            throw new IllegalArgumentException("解析 graphJson 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按编码查询。
     */
    private SpiderFlow findByCode(String flowCode) {
        LambdaQueryWrapper<SpiderFlow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpiderFlow::getFlowCode, flowCode);
        wrapper.last("LIMIT 1");
        return flowMapper.selectOne(wrapper);
    }

    /**
     * 加载节点列表。
     */
    private List<SpiderFlowNode> loadNodes(Long flowId) {
        LambdaQueryWrapper<SpiderFlowNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpiderFlowNode::getFlowId, flowId);
        return nodeMapper.selectList(wrapper);
    }

    /**
     * 加载边列表。
     */
    private List<SpiderFlowEdge> loadEdges(Long flowId) {
        LambdaQueryWrapper<SpiderFlowEdge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpiderFlowEdge::getFlowId, flowId);
        return edgeMapper.selectList(wrapper);
    }

    /**
     * Object 转 String。
     */
    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    /**
     * Object 转 int（带默认）。
     */
    private int asInt(Object o, int def) {
        if (o == null) {
            return def;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    /**
     * 流程聚合视图：包含流程定义 + 节点 + 边。
     *
     * @param flow   流程定义
     * @param nodes  节点列表
     * @param edges  边列表
     * @author CH
     * @since 4.0.0.42
     */
    public record SpiderFlowAggregate(
            SpiderFlow flow,
            List<SpiderFlowNode> nodes,
            List<SpiderFlowEdge> edges
    ) {
    }
}
