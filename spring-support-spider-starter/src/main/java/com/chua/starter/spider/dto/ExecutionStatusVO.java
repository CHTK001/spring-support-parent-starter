package com.chua.starter.spider.dto;

public class ExecutionStatusVO {
    private String status;
    private Integer completedCount;
    private Integer totalCount;
    private Integer collectedCount;
    private Integer maxDepthReached;
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