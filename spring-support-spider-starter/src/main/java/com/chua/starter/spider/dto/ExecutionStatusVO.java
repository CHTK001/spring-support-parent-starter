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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getCompletedCount() { return completedCount; }
    public void setCompletedCount(Integer completedCount) { this.completedCount = completedCount; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public Integer getCollectedCount() { return collectedCount; }
    public void setCollectedCount(Integer collectedCount) { this.collectedCount = collectedCount; }
    public Integer getMaxDepthReached() { return maxDepthReached; }
    public void setMaxDepthReached(Integer maxDepthReached) { this.maxDepthReached = maxDepthReached; }
    public String getSpiderCode() { return spiderCode; }
    public void setSpiderCode(String spiderCode) { this.spiderCode = spiderCode; }
}
