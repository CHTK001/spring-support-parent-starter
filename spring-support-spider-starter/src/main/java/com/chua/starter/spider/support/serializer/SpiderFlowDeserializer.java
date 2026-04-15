package com.chua.starter.spider.support.serializer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import com.chua.starter.spider.support.domain.SpiderFlowEdge;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 JSON 字符串反序列化为 {@link SpiderFlowDefinition}。
 * <p>结构不合法时抛出含字段路径的 {@link SpiderFlowDeserializeException}。</p>
 *
 * @author CH
 */
public class SpiderFlowDeserializer {

    /**
     * 将 JSON 字符串反序列化为编排定义。
     *
     * @param json JSON 字符串
     * @return 编排定义
     * @throws SpiderFlowDeserializeException JSON 结构不合法时，异常消息包含具体字段路径
     */
    public SpiderFlowDefinition deserialize(String json) {
        if (json == null || json.isBlank()) {
            throw new SpiderFlowDeserializeException("$", "JSON 字符串不能为空");
        }

        JSONObject root;
        try {
            root = JSON.parseObject(json);
        } catch (Exception e) {
            throw new SpiderFlowDeserializeException("$", "JSON 格式非法: " + e.getMessage());
        }

        if (root == null) {
            throw new SpiderFlowDeserializeException("$", "JSON 解析结果为 null");
        }

        SpiderFlowDefinition flow = new SpiderFlowDefinition();
        flow.setId(root.getLong("id"));
        flow.setTaskId(root.getLong("taskId"));
        flow.setVersion(root.getInteger("version"));

        // 解析 nodes
        JSONArray nodesArray = root.getJSONArray("nodes");
        if (nodesArray == null) {
            throw new SpiderFlowDeserializeException("$.nodes", "nodes 字段缺失或类型错误，期望 JSON 数组");
        }
        List<SpiderFlowNode> nodes = new ArrayList<>(nodesArray.size());
        for (int i = 0; i < nodesArray.size(); i++) {
            JSONObject nodeObj = nodesArray.getJSONObject(i);
            if (nodeObj == null) {
                throw new SpiderFlowDeserializeException("$.nodes[" + i + "]", "节点元素不能为 null");
            }
            nodes.add(parseNode(nodeObj, i));
        }
        flow.setNodes(nodes);

        // 解析 edges
        JSONArray edgesArray = root.getJSONArray("edges");
        if (edgesArray == null) {
            throw new SpiderFlowDeserializeException("$.edges", "edges 字段缺失或类型错误，期望 JSON 数组");
        }
        List<SpiderFlowEdge> edges = new ArrayList<>(edgesArray.size());
        for (int i = 0; i < edgesArray.size(); i++) {
            JSONObject edgeObj = edgesArray.getJSONObject(i);
            if (edgeObj == null) {
                throw new SpiderFlowDeserializeException("$.edges[" + i + "]", "边元素不能为 null");
            }
            edges.add(parseEdge(edgeObj, i));
        }
        flow.setEdges(edges);

        return flow;
    }

    private SpiderFlowNode parseNode(JSONObject obj, int index) {
        String path = "$.nodes[" + index + "]";

        String nodeId = obj.getString("nodeId");
        if (nodeId == null || nodeId.isBlank()) {
            throw new SpiderFlowDeserializeException(path + ".nodeId", "nodeId 不能为空");
        }

        String nodeTypeStr = obj.getString("nodeType");
        if (nodeTypeStr == null || nodeTypeStr.isBlank()) {
            throw new SpiderFlowDeserializeException(path + ".nodeType", "nodeType 不能为空");
        }
        SpiderNodeType nodeType;
        try {
            nodeType = SpiderNodeType.valueOf(nodeTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new SpiderFlowDeserializeException(path + ".nodeType",
                    "nodeType 值非法: '" + nodeTypeStr + "'，合法值: " + java.util.Arrays.toString(SpiderNodeType.values()));
        }

        SpiderFlowNode node = new SpiderFlowNode();
        node.setNodeId(nodeId);
        node.setNodeType(nodeType);
        node.setLabel(obj.getString("label"));
        node.setPositionX(obj.getDouble("positionX"));
        node.setPositionY(obj.getDouble("positionY"));

        JSONObject configObj = obj.getJSONObject("config");
        if (configObj != null) {
            node.setConfig(configObj.toJavaObject(java.util.Map.class));
        }

        return node;
    }

    private SpiderFlowEdge parseEdge(JSONObject obj, int index) {
        String path = "$.edges[" + index + "]";

        String edgeId = obj.getString("edgeId");
        if (edgeId == null || edgeId.isBlank()) {
            throw new SpiderFlowDeserializeException(path + ".edgeId", "edgeId 不能为空");
        }
        String sourceNodeId = obj.getString("sourceNodeId");
        if (sourceNodeId == null || sourceNodeId.isBlank()) {
            throw new SpiderFlowDeserializeException(path + ".sourceNodeId", "sourceNodeId 不能为空");
        }
        String targetNodeId = obj.getString("targetNodeId");
        if (targetNodeId == null || targetNodeId.isBlank()) {
            throw new SpiderFlowDeserializeException(path + ".targetNodeId", "targetNodeId 不能为空");
        }

        SpiderFlowEdge edge = new SpiderFlowEdge();
        edge.setEdgeId(edgeId);
        edge.setSourceNodeId(sourceNodeId);
        edge.setTargetNodeId(targetNodeId);
        return edge;
    }
}
