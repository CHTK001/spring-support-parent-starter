package com.chua.starter.spider.dto;

public class FlowEdgeEntityVO {
    private String flowEdgeFrom;
    private String flowEdgeTo;
    private String flowEdgeText;
    private String flowEdgeCondField;
    private String flowEdgeCondOp;
    private String flowEdgeCondValue;
    private Integer flowEdgePriority;

    public String getFlowEdgeFrom() { return flowEdgeFrom; }
    public void setFlowEdgeFrom(String flowEdgeFrom) { this.flowEdgeFrom = flowEdgeFrom; }
    public String getFlowEdgeTo() { return flowEdgeTo; }
    public void setFlowEdgeTo(String flowEdgeTo) { this.flowEdgeTo = flowEdgeTo; }
    public String getFlowEdgeText() { return flowEdgeText; }
    public void setFlowEdgeText(String flowEdgeText) { this.flowEdgeText = flowEdgeText; }
    public String getFlowEdgeCondField() { return flowEdgeCondField; }
    public void setFlowEdgeCondField(String flowEdgeCondField) { this.flowEdgeCondField = flowEdgeCondField; }
    public String getFlowEdgeCondOp() { return flowEdgeCondOp; }
    public void setFlowEdgeCondOp(String flowEdgeCondOp) { this.flowEdgeCondOp = flowEdgeCondOp; }
    public String getFlowEdgeCondValue() { return flowEdgeCondValue; }
    public void setFlowEdgeCondValue(String flowEdgeCondValue) { this.flowEdgeCondValue = flowEdgeCondValue; }
    public Integer getFlowEdgePriority() { return flowEdgePriority; }
    public void setFlowEdgePriority(Integer flowEdgePriority) { this.flowEdgePriority = flowEdgePriority; }
}