package com.chua.starter.spider.dto;

import java.util.List;
import java.util.Map;

/**
 * @作者 CH
*/
public class FlowAggregateDTO {
    /**
     * 流
    */
    private SpiderFlowVO flow;
    /**
     * 节点
    */
    private List<FlowNodeEntityVO> nodes;
    /**
     * Edges
    */
    private List<FlowEdgeEntityVO> edges;
    /**
     * flowgraphjson
    */
    private String flowGraphJson;

    /**
     * 获取流
     *
     * @return 获取流的结果
     */
    public SpiderFlowVO getFlow() { return flow; }
    /**
     * 设置流
     *
     * @param flow 流
     */
    public void setFlow(SpiderFlowVO flow) { this.flow = flow; }
    /**
     * 获取节点
     *
     * @return 获取节点的结果
     */
    public List<FlowNodeEntityVO> getNodes() { return nodes; }
    /**
     * 设置节点
     *
     * @param nodes 节点
     */
    public void setNodes(List<FlowNodeEntityVO> nodes) { this.nodes = nodes; }
    /**
     * 获取Edges
     *
     * @return 获取edges的结果
     */
    public List<FlowEdgeEntityVO> getEdges() { return edges; }
    /**
     * 设置Edges
     *
     * @param edges edges
     */
    public void setEdges(List<FlowEdgeEntityVO> edges) { this.edges = edges; }
    /**
     * 获取流图计算json
     *
     * @return 获取流图计算json的结果
     */
    public String getFlowGraphJson() { return flowGraphJson; }
    /**
     * 设置流图计算json
     *
     * @param flowGraphJson 流图计算json
     */
    public void setFlowGraphJson(String flowGraphJson) { this.flowGraphJson = flowGraphJson; }
}
