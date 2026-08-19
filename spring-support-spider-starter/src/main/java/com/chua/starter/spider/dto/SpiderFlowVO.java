package com.chua.starter.spider.dto;

import java.time.LocalDateTime;

/** @author CH */
public class SpiderFlowVO {
    /** Flow代码 */
    private String flowCode;
    /** Flow名称 */
    private String flowName;
    /** Flow版本 */
    private Integer flowVersion;
    /** Flow节点数量 */
    private Integer flowNodeCount;
    /** Flow状态 */
    private Integer flowStatus;
    /** Update时间 */
    private LocalDateTime updateTime;

    /** 获取FlowCode */
    public String getFlowCode() { return flowCode; }
    /** 设置FlowCode */
    public void setFlowCode(String flowCode) { this.flowCode = flowCode; }
    /** 获取FlowName */
    public String getFlowName() { return flowName; }
    /** 设置FlowName */
    public void setFlowName(String flowName) { this.flowName = flowName; }
    /** 获取FlowVersion */
    public Integer getFlowVersion() { return flowVersion; }
    /** 设置FlowVersion */
    public void setFlowVersion(Integer flowVersion) { this.flowVersion = flowVersion; }
    /** 获取FlowNode计算数量 */
    public Integer getFlowNodeCount() { return flowNodeCount; }
    /** 设置FlowNode计算数量 */
    public void setFlowNodeCount(Integer flowNodeCount) { this.flowNodeCount = flowNodeCount; }
    /** 获取FlowStatus */
    public Integer getFlowStatus() { return flowStatus; }
    /** 设置FlowStatus */
    public void setFlowStatus(Integer flowStatus) { this.flowStatus = flowStatus; }
    /** 获取更新Time */
    public LocalDateTime getUpdateTime() { return updateTime; }
    /** 设置更新Time */
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
