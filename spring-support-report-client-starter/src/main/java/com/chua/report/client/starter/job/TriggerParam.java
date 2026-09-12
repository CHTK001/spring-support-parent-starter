package com.chua.report.client.starter.job;


import lombok.Data;

/**
 * 触发器参数类，用于封装与任务触发相关的参数信息。
 * @author CH
 * @since 4.0.0
 */
@Data
public class TriggerParam {

    /** 任务标识 */
    private int jobId;

    /** 配置文件 */
    private String profile;

    // 执行器相关参数
    /** 执行器处理代码的标识 */
    private String executorHandler;
    /** 执行器的参数 */
    private String executorParams;
    /** 执行器的阻塞策略 */
    private String executorBlockStrategy;
    /** 执行器的阻塞策略 */
    private String executorRouter;
    /** 执行器超时时间 */
    private int executorTimeout;

    // 日志相关参数
    /** 日志标识 */
    private long logId;
    /** 日志生成时间戳 */
    private long logDateTime;

 // 胶水相关参数
    /** 胶水类型（例如：脚本、JAR等） */
    private String glueType;
    /** 胶水代码源 */
    private String glueSource;
    /** 胶水最后更新时间 */
    private long glueUpdatetime;

    // 广播执行相关参数
    /** 广播执行的当前索引 */
    private int broadcastIndex;
    /** 广播执行的总任务数 */
    private int broadcastTotal;

    // 手动添加 getter 方法，确保 Lombok 未生效时也能编译通过
    /**
     * 获取执行器处理器
     *
     * @return 获取执行器处理器的结果
     */
    public String getExecutorHandler() {
        return executorHandler;
    }

    /**
     * 获取胶水updatetime
     *
     * @return 获取胶水updatetime的结果
     */
    public long getGlueUpdatetime() {
        return glueUpdatetime;
    }

    /**
     * 获取胶水源
     *
     * @return 获取胶水源的结果
     */
    public String getGlueSource() {
        return glueSource;
    }

    /**
      * 获取作业标识
     *
     * @return 获取作业id的结果
     */
    public int getJobId() {
        return jobId;
    }

    /**
     * 获取胶水类型
     *
     * @return 获取胶水类型的结果
     */
    public String getGlueType() {
        return glueType;
    }

    /**
      * 获取记录日志标识
     *
     * @return 获取日志id的结果
     */
    public long getLogId() {
        return logId;
    }

    /**
     * 获取记录日志日期时间
     *
     * @return 获取日志日期时间的结果
     */
    public long getLogDateTime() {
        return logDateTime;
    }

    /**
     * 获取执行器参数
     *
     * @return 获取执行器参数的结果
     */
    public String getExecutorParams() {
        return executorParams;
    }

    /**
     * 获取broadcast索引
     *
     * @return 获取broadcast索引的结果
     */
    public int getBroadcastIndex() {
        return broadcastIndex;
    }

    /**
     * 获取Broadcast总计
     *
     * @return 获取broadcasttotal的结果
     */
    public int getBroadcastTotal() {
        return broadcastTotal;
    }

    /**
     * 获取执行器超时
     *
     * @return 获取执行器超时的结果
     */
    public int getExecutorTimeout() {
        return executorTimeout;
    }

    /**
      * 设置作业标识
     *
     * @param jobId 作业标识
     */
    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    /**
     * 设置配置文件
     *
     * @param profile 配置文件
     */
    public void setProfile(String profile) {
        this.profile = profile;
    }

    /**
     * 设置执行器处理器
     *
     * @param executorHandler 执行器处理器
     */
    public void setExecutorHandler(String executorHandler) {
        this.executorHandler = executorHandler;
    }

    /**
     * 设置执行器参数
     *
     * @param executorParams 执行器参数
     */
    public void setExecutorParams(String executorParams) {
        this.executorParams = executorParams;
    }

    /**
     * 设置执行器blockstrategy
     *
     * @param executorBlockStrategy 执行器blockstrategy
     */
    public void setExecutorBlockStrategy(String executorBlockStrategy) {
        this.executorBlockStrategy = executorBlockStrategy;
    }

    /**
     * 设置执行器超时
     *
     * @param executorTimeout 执行器超时
     */
    public void setExecutorTimeout(int executorTimeout) {
        this.executorTimeout = executorTimeout;
    }

    /**
      * 设置记录日志标识
     *
     * @param logId 日志标识
     */
    public void setLogId(long logId) {
        this.logId = logId;
    }

    /**
     * 设置记录日志日期时间
     *
     * @param logDateTime 日志日期时间
     */
    public void setLogDateTime(long logDateTime) {
        this.logDateTime = logDateTime;
    }

    /**
     * 设置胶水类型
     *
     * @param glueType 胶水类型
     */
    public void setGlueType(String glueType) {
        this.glueType = glueType;
    }

    /**
     * 设置胶水源
     *
     * @param glueSource 胶水源
     */
    public void setGlueSource(String glueSource) {
        this.glueSource = glueSource;
    }

    /**
     * 设置胶水updatetime
     *
     * @param glueUpdatetime 胶水updatetime
     */
    public void setGlueUpdatetime(long glueUpdatetime) {
        this.glueUpdatetime = glueUpdatetime;
    }

    /**
     * 设置broadcast索引
     *
     * @param broadcastIndex broadcast索引
     */
    public void setBroadcastIndex(int broadcastIndex) {
        this.broadcastIndex = broadcastIndex;
    }

    /**
     * 设置Broadcast总计
     *
     * @param broadcastTotal broadcasttotal
     */
    public void setBroadcastTotal(int broadcastTotal) {
        this.broadcastTotal = broadcastTotal;
    }
}

