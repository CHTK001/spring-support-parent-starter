package com.chua.starter.spider.dto;

import java.time.LocalDateTime;

public class SpiderFlowVO {
    private String flowCode;
    private String flowName;
    private Integer flowVersion;
    private Integer flowNodeCount;
    private Integer flowStatus;
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