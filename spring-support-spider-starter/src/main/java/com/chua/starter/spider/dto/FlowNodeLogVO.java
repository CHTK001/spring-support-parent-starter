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
