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

    public SpiderFlowVO getFlow() { return flow; }
    public void setFlow(SpiderFlowVO flow) { this.flow = flow; }
    public List<FlowNodeEntityVO> getNodes() { return nodes; }
    public void setNodes(List<FlowNodeEntityVO> nodes) { this.nodes = nodes; }
    public List<FlowEdgeEntityVO> getEdges() { return edges; }
    public void setEdges(List<FlowEdgeEntityVO> edges) { this.edges = edges; }
    public String getFlowGraphJson() { return flowGraphJson; }
    public void setFlowGraphJson(String flowGraphJson) { this.flowGraphJson = flowGraphJson; }
}
