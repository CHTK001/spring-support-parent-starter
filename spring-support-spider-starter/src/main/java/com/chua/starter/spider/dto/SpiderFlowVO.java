package com.chua.starter.spider.dto;

import java.time.LocalDateTime;

/** @作者 CH */
public class SpiderFlowVO {
    /** 流代码 */
    private String flowCode;
    /** 流名称 */
    private String flowName;
    /** 流版本 */
    private Integer flowVersion;
    /** 流节点数量 */
    private Integer flowNodeCount;
    /** 流状态 */
    private Integer flowStatus;
    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
    * 获取流编码
    *
    * @return 获取流编码的结果
     */
    public String getFlowCode() { return flowCode; }
    /**
    * 设置流编码
    *
    * @param flowCode 流编码
     */
    public void setFlowCode(String flowCode) { this.flowCode = flowCode; }
    /**
    * 获取流名称
    *
    * @return 获取流名称的结果
     */
    public String getFlowName() { return flowName; }
    /**
    * 设置流名称
    *
    * @param flowName 流名称
     */
    public void setFlowName(String flowName) { this.flowName = flowName; }
    /**
    * 获取流版本
    *
    * @return 获取流版本的结果
     */
    public Integer getFlowVersion() { return flowVersion; }
    /**
    * 设置流版本
    *
    * @param flowVersion 流版本
     */
    public void setFlowVersion(Integer flowVersion) { this.flowVersion = flowVersion; }
    /**
    * 获取流节点计算数量
    *
    * @return 获取流节点数量的结果
     */
    public Integer getFlowNodeCount() { return flowNodeCount; }
    /**
    * 设置流节点计算数量
    *
    * @param flowNodeCount 流节点数量
     */
    public void setFlowNodeCount(Integer flowNodeCount) { this.flowNodeCount = flowNodeCount; }
    /**
    * 获取流状态
    *
    * @return 获取流状态的结果
     */
    public Integer getFlowStatus() { return flowStatus; }
    /**
    * 设置流状态
    *
    * @param flowStatus 流状态
     */
    public void setFlowStatus(Integer flowStatus) { this.flowStatus = flowStatus; }
    /**
    * 获取更新时间
    *
    * @return 获取更新时间的结果
     */
    public LocalDateTime getUpdateTime() { return updateTime; }
    /**
    * 设置更新时间
    *
    * @param updateTime 更新时间
     */
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
