package com.chua.starter.spider.dto;

/** @作者 CH */
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
    /** 蜘蛛代码 */
    private String spiderCode;

    /**
     * 获取状态
     *
     * @return 获取状态的结果
     */
    public String getStatus() { return status; }
    /**
     * 设置状态
     *
     * @param status 状态
     */
    public void setStatus(String status) { this.status = status; }
    /**
     * 获取Completed计算数量
     *
     * @return 获取完成数量的结果
     */
    public Integer getCompletedCount() { return completedCount; }
    /**
     * 设置Completed计算数量
     *
     * @param completedCount 完成数量
     */
    public void setCompletedCount(Integer completedCount) { this.completedCount = completedCount; }
    /**
     * 获取总计计算数量
     *
     * @return 获取total数量的结果
     */
    public Integer getTotalCount() { return totalCount; }
    /**
     * 设置总计计算数量
     *
     * @param totalCount total数量
     */
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    /**
     * 获取Collected计算数量
     *
     * @return 获取collected数量的结果
     */
    public Integer getCollectedCount() { return collectedCount; }
    /**
     * 设置Collected计算数量
     *
     * @param collectedCount collected数量
     */
    public void setCollectedCount(Integer collectedCount) { this.collectedCount = collectedCount; }
    /**
     * 获取最大值深度reached
     *
     * @return 获取最大深度reached的结果
     */
    public Integer getMaxDepthReached() { return maxDepthReached; }
    /**
     * 设置最大值深度reached
     *
     * @param maxDepthReached 最大深度reached
     */
    public void setMaxDepthReached(Integer maxDepthReached) { this.maxDepthReached = maxDepthReached; }
    /**
     * 获取蜘蛛编码
     *
     * @return 获取蜘蛛编码的结果
     */
    public String getSpiderCode() { return spiderCode; }
    /**
     * 设置蜘蛛编码
     *
     * @param spiderCode 蜘蛛编码
     */
    public void setSpiderCode(String spiderCode) { this.spiderCode = spiderCode; }
}
