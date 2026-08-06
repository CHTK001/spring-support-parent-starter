package com.chua.starter.spider.dto;

import java.time.LocalDateTime;

public class DataRecordVO {
    private Long spiderId;
    private String spiderExecutionNo;
    private String spiderTargetUrl;
    private String spiderDataName;
    private Integer spiderDepth;
    private String spiderDataStatus;
    private LocalDateTime createTime;
    private String spiderDataPayload;

    public Long getSpiderId() { return spiderId; }
    public void setSpiderId(Long spiderId) { this.spiderId = spiderId; }
    public String getSpiderExecutionNo() { return spiderExecutionNo; }
    public void setSpiderExecutionNo(String spiderExecutionNo) { this.spiderExecutionNo = spiderExecutionNo; }
    public String getSpiderTargetUrl() { return spiderTargetUrl; }
    public void setSpiderTargetUrl(String spiderTargetUrl) { this.spiderTargetUrl = spiderTargetUrl; }
    public String getSpiderDataName() { return spiderDataName; }
    public void setSpiderDataName(String spiderDataName) { this.spiderDataName = spiderDataName; }
    public Integer getSpiderDepth() { return spiderDepth; }
    public void setSpiderDepth(Integer spiderDepth) { this.spiderDepth = spiderDepth; }
    public String getSpiderDataStatus() { return spiderDataStatus; }
    public void setSpiderDataStatus(String spiderDataStatus) { this.spiderDataStatus = spiderDataStatus; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public String getSpiderDataPayload() { return spiderDataPayload; }
    public void setSpiderDataPayload(String spiderDataPayload) { this.spiderDataPayload = spiderDataPayload; }
}