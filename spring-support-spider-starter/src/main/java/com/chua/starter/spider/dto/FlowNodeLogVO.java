package com.chua.starter.spider.dto;

import java.time.LocalDateTime;

/** @作者 CH */
public class FlowNodeLogVO {
    /** 流节点密钥 */
    private String flowNodeKey;
    /** 流节点类型 */
    private String flowNodeType;
    /** 流节点状态 */
    private String flowNodeStatus;
    /** 流节点costms */
    private Long flowNodeCostMs;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 流节点消息 */
    private String flowNodeMessage;

    /**
    * 获取流节点钥匙
    *
    * @return 获取流节点钥匙的结果
     */
    public String getFlowNodeKey() { return flowNodeKey; }
    /**
    * 设置流节点钥匙
    *
    * @param flowNodeKey 流节点钥匙
     */
    public void setFlowNodeKey(String flowNodeKey) { this.flowNodeKey = flowNodeKey; }
    /**
    * 获取流节点类型
    *
    * @return 获取流节点类型的结果
     */
    public String getFlowNodeType() { return flowNodeType; }
    /**
    * 设置流节点类型
    *
    * @param flowNodeType 流节点类型
     */
    public void setFlowNodeType(String flowNodeType) { this.flowNodeType = flowNodeType; }
    /**
    * 获取流节点状态
    *
    * @return 获取流节点状态的结果
     */
    public String getFlowNodeStatus() { return flowNodeStatus; }
    /**
    * 设置流节点状态
    *
    * @param flowNodeStatus 流节点状态
     */
    public void setFlowNodeStatus(String flowNodeStatus) { this.flowNodeStatus = flowNodeStatus; }
    /**
    * 获取流节点costms
    *
    * @return 获取流节点costms的结果
     */
    public Long getFlowNodeCostMs() { return flowNodeCostMs; }
    /**
    * 设置流节点costms
    *
    * @param flowNodeCostMs 流节点costms
     */
    public void setFlowNodeCostMs(Long flowNodeCostMs) { this.flowNodeCostMs = flowNodeCostMs; }
    /**
    * 获取创建时间
    *
    * @return 获取创建时间的结果
     */
    public LocalDateTime getCreateTime() { return createTime; }
    /**
    * 设置创建时间
    *
    * @param createTime 创建时间
     */
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    /**
    * 获取流节点消息
    *
    * @return 获取流节点消息的结果
     */
    public String getFlowNodeMessage() { return flowNodeMessage; }
    /**
    * 设置流节点消息
    *
    * @param flowNodeMessage 流节点消息
     */
    public void setFlowNodeMessage(String flowNodeMessage) { this.flowNodeMessage = flowNodeMessage; }
}
