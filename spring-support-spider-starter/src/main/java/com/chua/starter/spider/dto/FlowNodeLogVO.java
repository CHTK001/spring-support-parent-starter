package com.chua.starter.spider.dto;

import java.time.LocalDateTime;

public class FlowNodeLogVO {
    private String flowNodeKey;
    private String flowNodeType;
    private String flowNodeStatus;
    private Long flowNodeCostMs;
    private LocalDateTime createTime;
    private String flowNodeMessage;

    public String getFlowNodeKey() { return flowNodeKey; }
    public void setFlowNodeKey(String flowNodeKey) { this.flowNodeKey = flowNodeKey; }
    public String getFlowNodeType() { return flowNodeType; }
    public void setFlowNodeType(String flowNodeType) { this.flowNodeType = flowNodeType; }
    public String getFlowNodeStatus() { return flowNodeStatus; }
    public void setFlowNodeStatus(String flowNodeStatus) { this.flowNodeStatus = flowNodeStatus; }
    public Long getFlowNodeCostMs() { return flowNodeCostMs; }
    public void setFlowNodeCostMs(Long flowNodeCostMs) { this.flowNodeCostMs = flowNodeCostMs; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public String getFlowNodeMessage() { return flowNodeMessage; }
    public void setFlowNodeMessage(String flowNodeMessage) { this.flowNodeMessage = flowNodeMessage; }
}