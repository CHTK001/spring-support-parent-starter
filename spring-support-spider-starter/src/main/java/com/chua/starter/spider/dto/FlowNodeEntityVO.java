package com.chua.starter.spider.dto;

/** @author CH */
public class FlowNodeEntityVO {
    /** Flow节点密钥 */
    private String flowNodeKey;
    /** Flow节点类型 */
    private String flowNodeType;
    /** Flow节点名称 */
    private String flowNodeName;
    /** Flow节点X坐标 */
    private Integer flowNodeX;
    /** Flow节点Y坐标 */
    private Integer flowNodeY;
    /** Flow节点配置 */
    private String flowNodeConfig;
    /** Flow节点状态 */
    private String flowNodeStatus;

    /** 获取FlowNodeKey */
    public String getFlowNodeKey() { return flowNodeKey; }
    /** 设置FlowNodeKey */
    public void setFlowNodeKey(String flowNodeKey) { this.flowNodeKey = flowNodeKey; }
    /** 获取FlowNodeType */
    public String getFlowNodeType() { return flowNodeType; }
    /** 设置FlowNodeType */
    public void setFlowNodeType(String flowNodeType) { this.flowNodeType = flowNodeType; }
    /** 获取FlowNodeName */
    public String getFlowNodeName() { return flowNodeName; }
    /** 设置FlowNodeName */
    public void setFlowNodeName(String flowNodeName) { this.flowNodeName = flowNodeName; }
    /** 获取FlowNodeX */
    public Integer getFlowNodeX() { return flowNodeX; }
    /** 设置FlowNodeX */
    public void setFlowNodeX(Integer flowNodeX) { this.flowNodeX = flowNodeX; }
    /** 获取FlowNodeY */
    public Integer getFlowNodeY() { return flowNodeY; }
    /** 设置FlowNodeY */
    public void setFlowNodeY(Integer flowNodeY) { this.flowNodeY = flowNodeY; }
    /** 获取FlowNodeConfig */
    public String getFlowNodeConfig() { return flowNodeConfig; }
    /** 设置FlowNodeConfig */
    public void setFlowNodeConfig(String flowNodeConfig) { this.flowNodeConfig = flowNodeConfig; }
    /** 获取FlowNodeStatus */
    public String getFlowNodeStatus() { return flowNodeStatus; }
    /** 设置FlowNodeStatus */
    public void setFlowNodeStatus(String flowNodeStatus) { this.flowNodeStatus = flowNodeStatus; }
}
