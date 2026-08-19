package com.chua.starter.spider.dto;

import java.time.LocalDateTime;

/** @author CH */
public class DataRecordVO {
    /** SpiderID */
    private Long spiderId;
    /** SpiderexecutionNO */
    private String spiderExecutionNo;
    /** Spider目标URL */
    private String spiderTargetUrl;
    /** Spider数据名称 */
    private String spiderDataName;
    /** Spider深度 */
    private Integer spiderDepth;
    /** Spider数据状态 */
    private String spiderDataStatus;
    /** Create时间 */
    private LocalDateTime createTime;
    /** Spider数据payload */
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
