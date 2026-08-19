package com.chua.starter.spider.dto;

import java.time.LocalDateTime;

/** @author CH */
public class FlowNodeLogVO {
    /** Flow节点密钥 */
    private String flowNodeKey;
    /** Flow节点类型 */
    private String flowNodeType;
    /** Flow节点状态 */
    private String flowNodeStatus;
    /** Flow节点costMS */
    private Long flowNodeCostMs;
    /** Create时间 */
    private LocalDateTime createTime;
    /** Flow节点消息 */
    private String flowNodeMessage;

    /** 获取FlowNodeKey */
    public String getFlowNodeKey() { return flowNodeKey; }
    /** 设置FlowNodeKey */
    public void setFlowNodeKey(String flowNodeKey) { this.flowNodeKey = flowNodeKey; }
    /** 获取FlowNodeType */
    public String getFlowNodeType() { return flowNodeType; }
    /** 设置FlowNodeType */
    public void setFlowNodeType(String flowNodeType) { this.flowNodeType = flowNodeType; }
    /** 获取FlowNodeStatus */
    public String getFlowNodeStatus() { return flowNodeStatus; }
    /** 设置FlowNodeStatus */
    public void setFlowNodeStatus(String flowNodeStatus) { this.flowNodeStatus = flowNodeStatus; }
    /** 获取FlowNodeCostMs */
    public Long getFlowNodeCostMs() { return flowNodeCostMs; }
    /** 设置FlowNodeCostMs */
    public void setFlowNodeCostMs(Long flowNodeCostMs) { this.flowNodeCostMs = flowNodeCostMs; }
    /** 获取创建Time */
    public LocalDateTime getCreateTime() { return createTime; }
    /** 设置创建Time */
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    /** 获取FlowNodeMessage */
    public String getFlowNodeMessage() { return flowNodeMessage; }
    /** 设置FlowNodeMessage */
    public void setFlowNodeMessage(String flowNodeMessage) { this.flowNodeMessage = flowNodeMessage; }
}
