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
