package com.chua.starter.spider.dto;

/** @author CH */
public class ExecutionStatusVO {
    /** 状态 */
    private String status;
    /** Completed数量 */
    private Integer completedCount;
    /** 总数数量 */
    private Integer totalCount;
    /** Collected数量 */
    private Integer collectedCount;
    /** 最大值深度reached */
    private Integer maxDepthReached;
    /** Spider代码 */
    private String spiderCode;

    /** 获取Status */
    public String getStatus() { return status; }
    /** 设置Status */
    public void setStatus(String status) { this.status = status; }
    /** 获取Completed计算数量 */
    public Integer getCompletedCount() { return completedCount; }
    /** 设置Completed计算数量 */
    public void setCompletedCount(Integer completedCount) { this.completedCount = completedCount; }
    /** 获取总计计算数量 */
    public Integer getTotalCount() { return totalCount; }
    /** 设置总计计算数量 */
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    /** 获取Collected计算数量 */
    public Integer getCollectedCount() { return collectedCount; }
    /** 设置Collected计算数量 */
    public void setCollectedCount(Integer collectedCount) { this.collectedCount = collectedCount; }
    /** 获取最大值DepthReached */
    public Integer getMaxDepthReached() { return maxDepthReached; }
    /** 设置最大值DepthReached */
    public void setMaxDepthReached(Integer maxDepthReached) { this.maxDepthReached = maxDepthReached; }
    /** 获取SpiderCode */
    public String getSpiderCode() { return spiderCode; }
    /** 设置SpiderCode */
    public void setSpiderCode(String spiderCode) { this.spiderCode = spiderCode; }
}
