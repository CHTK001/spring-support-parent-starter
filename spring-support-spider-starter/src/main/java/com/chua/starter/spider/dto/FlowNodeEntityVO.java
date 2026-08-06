package com.chua.starter.spider.dto;

public class FlowNodeEntityVO {
    private String flowNodeKey;
    private String flowNodeType;
    private String flowNodeName;
    private Integer flowNodeX;
    private Integer flowNodeY;
    private String flowNodeConfig;
    private String flowNodeStatus;

    public String getFlowNodeKey() { return flowNodeKey; }
    public void setFlowNodeKey(String flowNodeKey) { this.flowNodeKey = flowNodeKey; }
    public String getFlowNodeType() { return flowNodeType; }
    public void setFlowNodeType(String flowNodeType) { this.flowNodeType = flowNodeType; }
    public String getFlowNodeName() { return flowNodeName; }
    public void setFlowNodeName(String flowNodeName) { this.flowNodeName = flowNodeName; }
    public Integer getFlowNodeX() { return flowNodeX; }
    public void setFlowNodeX(Integer flowNodeX) { this.flowNodeX = flowNodeX; }
    public Integer getFlowNodeY() { return flowNodeY; }
    public void setFlowNodeY(Integer flowNodeY) { this.flowNodeY = flowNodeY; }
    public String getFlowNodeConfig() { return flowNodeConfig; }
    public void setFlowNodeConfig(String flowNodeConfig) { this.flowNodeConfig = flowNodeConfig; }
    public String getFlowNodeStatus() { return flowNodeStatus; }
    public void setFlowNodeStatus(String flowNodeStatus) { this.flowNodeStatus = flowNodeStatus; }
}