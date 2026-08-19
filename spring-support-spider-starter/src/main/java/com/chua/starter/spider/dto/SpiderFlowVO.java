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

    public String getFlowCode() { return flowCode; }
    public void setFlowCode(String flowCode) { this.flowCode = flowCode; }
    public String getFlowName() { return flowName; }
    public void setFlowName(String flowName) { this.flowName = flowName; }
    public Integer getFlowVersion() { return flowVersion; }
    public void setFlowVersion(Integer flowVersion) { this.flowVersion = flowVersion; }
    public Integer getFlowNodeCount() { return flowNodeCount; }
    public void setFlowNodeCount(Integer flowNodeCount) { this.flowNodeCount = flowNodeCount; }
    public Integer getFlowStatus() { return flowStatus; }
    public void setFlowStatus(Integer flowStatus) { this.flowStatus = flowStatus; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
