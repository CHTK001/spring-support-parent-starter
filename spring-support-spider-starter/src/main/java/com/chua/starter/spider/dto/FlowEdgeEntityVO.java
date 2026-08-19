package com.chua.starter.spider.dto;

/** @author CH */
public class FlowEdgeEntityVO {
    /** Flowedgefrom */
    private String flowEdgeFrom;
    /** FlowedgeTO */
    private String flowEdgeTo;
    /** Flowedge文本 */
    private String flowEdgeText;
    /** Flowedgecondfield */
    private String flowEdgeCondField;
    /** FlowedgecondOP */
    private String flowEdgeCondOp;
    /** Flowedgecond值 */
    private String flowEdgeCondValue;
    /** Flowedge优先级 */
    private Integer flowEdgePriority;

    /** 获取FlowEdgeFrom */
    public String getFlowEdgeFrom() { return flowEdgeFrom; }
    /** 设置FlowEdgeFrom */
    public void setFlowEdgeFrom(String flowEdgeFrom) { this.flowEdgeFrom = flowEdgeFrom; }
    /** 获取FlowEdgeTo */
    public String getFlowEdgeTo() { return flowEdgeTo; }
    /** 设置FlowEdgeTo */
    public void setFlowEdgeTo(String flowEdgeTo) { this.flowEdgeTo = flowEdgeTo; }
    /** 获取FlowEdgeText */
    public String getFlowEdgeText() { return flowEdgeText; }
    /** 设置FlowEdgeText */
    public void setFlowEdgeText(String flowEdgeText) { this.flowEdgeText = flowEdgeText; }
    /** 获取FlowEdgeCondField */
    public String getFlowEdgeCondField() { return flowEdgeCondField; }
    /** 设置FlowEdgeCondField */
    public void setFlowEdgeCondField(String flowEdgeCondField) { this.flowEdgeCondField = flowEdgeCondField; }
    /** 获取FlowEdgeCondOp */
    public String getFlowEdgeCondOp() { return flowEdgeCondOp; }
    /** 设置FlowEdgeCondOp */
    public void setFlowEdgeCondOp(String flowEdgeCondOp) { this.flowEdgeCondOp = flowEdgeCondOp; }
    /** 获取FlowEdgeCondValue */
    public String getFlowEdgeCondValue() { return flowEdgeCondValue; }
    /** 设置FlowEdgeCondValue */
    public void setFlowEdgeCondValue(String flowEdgeCondValue) { this.flowEdgeCondValue = flowEdgeCondValue; }
    /** 获取FlowEdgePriority */
    public Integer getFlowEdgePriority() { return flowEdgePriority; }
    /** 设置FlowEdgePriority */
    public void setFlowEdgePriority(Integer flowEdgePriority) { this.flowEdgePriority = flowEdgePriority; }
}
