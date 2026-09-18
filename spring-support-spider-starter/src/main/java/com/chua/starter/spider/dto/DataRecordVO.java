package com.chua.starter.spider.dto;

import java.time.LocalDateTime;

/** @作者 CH */
public class DataRecordVO {
    /** 蜘蛛标识 */
    private Long spiderId;
    /** spiderexecutionno */
    private String spiderExecutionNo;
    /** 蜘蛛目标URL */
    private String spiderTargetUrl;
    /** 蜘蛛数据名称 */
    private String spiderDataName;
    /** 蜘蛛深度 */
    private Integer spiderDepth;
    /** 蜘蛛数据状态 */
    private String spiderDataStatus;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 蜘蛛数据payload */
    private String spiderDataPayload;

    /**
    * 获取蜘蛛标识
    *
    * @return 获取蜘蛛id的结果
    */
    public Long getSpiderId() { return spiderId; }
    /**
    * 设置蜘蛛标识
    *
    * @param spiderId 蜘蛛标识
    */
    public void setSpiderId(Long spiderId) { this.spiderId = spiderId; }
    /**
    * 获取蜘蛛执行no
    *
    * @return 获取蜘蛛执行no的结果
    */
    public String getSpiderExecutionNo() { return spiderExecutionNo; }
    /**
    * 设置蜘蛛执行no
    *
    * @param spiderExecutionNo 蜘蛛执行no
    */
    public void setSpiderExecutionNo(String spiderExecutionNo) { this.spiderExecutionNo = spiderExecutionNo; }
    /**
    * 获取蜘蛛Targeturl
    *
    * @return 获取蜘蛛Targeturl的结果
    */
    public String getSpiderTargetUrl() { return spiderTargetUrl; }
    /**
    * 设置蜘蛛Targeturl
    *
    * @param spiderTargetUrl 蜘蛛Targeturl
    */
    public void setSpiderTargetUrl(String spiderTargetUrl) { this.spiderTargetUrl = spiderTargetUrl; }
    /**
    * 获取蜘蛛数据名称
    *
    * @return 获取蜘蛛数据名称的结果
    */
    public String getSpiderDataName() { return spiderDataName; }
    /**
    * 设置蜘蛛数据名称
    *
    * @param spiderDataName 蜘蛛数据名称
    */
    public void setSpiderDataName(String spiderDataName) { this.spiderDataName = spiderDataName; }
    /**
    * 获取蜘蛛深度
    *
    * @return 获取蜘蛛深度的结果
    */
    public Integer getSpiderDepth() { return spiderDepth; }
    /**
    * 设置蜘蛛深度
    *
    * @param spiderDepth 蜘蛛深度
    */
    public void setSpiderDepth(Integer spiderDepth) { this.spiderDepth = spiderDepth; }
    /**
    * 获取蜘蛛数据状态
    *
    * @return 获取蜘蛛数据状态的结果
    */
    public String getSpiderDataStatus() { return spiderDataStatus; }
    /**
    * 设置蜘蛛数据状态
    *
    * @param spiderDataStatus 蜘蛛数据状态
    */
    public void setSpiderDataStatus(String spiderDataStatus) { this.spiderDataStatus = spiderDataStatus; }
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
    * 获取蜘蛛数据payload
    *
    * @return 获取蜘蛛数据payload的结果
    */
    public String getSpiderDataPayload() { return spiderDataPayload; }
    /**
    * 设置蜘蛛数据payload
    *
    * @param spiderDataPayload 蜘蛛数据payload
    */
    public void setSpiderDataPayload(String spiderDataPayload) { this.spiderDataPayload = spiderDataPayload; }
}
