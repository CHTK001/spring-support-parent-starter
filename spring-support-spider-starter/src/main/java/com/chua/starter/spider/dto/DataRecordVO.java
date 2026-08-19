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

    /** 获取SpiderId */
    public Long getSpiderId() { return spiderId; }
    /** 设置SpiderId */
    public void setSpiderId(Long spiderId) { this.spiderId = spiderId; }
    /** 获取SpiderExecutionNo */
    public String getSpiderExecutionNo() { return spiderExecutionNo; }
    /** 设置SpiderExecutionNo */
    public void setSpiderExecutionNo(String spiderExecutionNo) { this.spiderExecutionNo = spiderExecutionNo; }
    /** 获取SpiderTargetUrl */
    public String getSpiderTargetUrl() { return spiderTargetUrl; }
    /** 设置SpiderTargetUrl */
    public void setSpiderTargetUrl(String spiderTargetUrl) { this.spiderTargetUrl = spiderTargetUrl; }
    /** 获取SpiderDataName */
    public String getSpiderDataName() { return spiderDataName; }
    /** 设置SpiderDataName */
    public void setSpiderDataName(String spiderDataName) { this.spiderDataName = spiderDataName; }
    /** 获取SpiderDepth */
    public Integer getSpiderDepth() { return spiderDepth; }
    /** 设置SpiderDepth */
    public void setSpiderDepth(Integer spiderDepth) { this.spiderDepth = spiderDepth; }
    /** 获取SpiderDataStatus */
    public String getSpiderDataStatus() { return spiderDataStatus; }
    /** 设置SpiderDataStatus */
    public void setSpiderDataStatus(String spiderDataStatus) { this.spiderDataStatus = spiderDataStatus; }
    /** 获取创建Time */
    public LocalDateTime getCreateTime() { return createTime; }
    /** 设置创建Time */
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    /** 获取SpiderDataPayload */
    public String getSpiderDataPayload() { return spiderDataPayload; }
    /** 设置SpiderDataPayload */
    public void setSpiderDataPayload(String spiderDataPayload) { this.spiderDataPayload = spiderDataPayload; }
}
