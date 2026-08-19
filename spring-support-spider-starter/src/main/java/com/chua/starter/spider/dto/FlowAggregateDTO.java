package com.chua.starter.spider.dto;

import java.util.List;
import java.util.Map;

/** @author CH */
public class FlowAggregateDTO {
    /** Flow */
    private SpiderFlowVO flow;
    /** Nodes */
    private List<FlowNodeEntityVO> nodes;
    /** Edges */
    private List<FlowEdgeEntityVO> edges;
    /** FlowgraphJSON */
    private String flowGraphJson;

    /** 获取Flow */
    public SpiderFlowVO getFlow() { return flow; }
    /** 设置Flow */
    public void setFlow(SpiderFlowVO flow) { this.flow = flow; }
    /** 获取Nodes */
    public List<FlowNodeEntityVO> getNodes() { return nodes; }
    /** 设置Nodes */
    public void setNodes(List<FlowNodeEntityVO> nodes) { this.nodes = nodes; }
    /** 获取Edges */
    public List<FlowEdgeEntityVO> getEdges() { return edges; }
    /** 设置Edges */
    public void setEdges(List<FlowEdgeEntityVO> edges) { this.edges = edges; }
    /** 获取FlowGraphJson */
    public String getFlowGraphJson() { return flowGraphJson; }
    /** 设置FlowGraphJson */
    public void setFlowGraphJson(String flowGraphJson) { this.flowGraphJson = flowGraphJson; }
}
