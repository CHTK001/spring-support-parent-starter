package com.chua.report.client.starter.job;


import lombok.Data;

/**
 * 触发器参数类，用于封装与任务触发相关的参数信息。
 * @author CH
 */
@Data
public class TriggerParam {

    /** 任务ID */
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
    /** 日志ID */
    private long logId;
    /** 日志生成时间戳 */
    private long logDateTime;

    // Glue相关参数
    /** Glue类型（例如：脚本、JAR等） */
    private String glueType;
    /** Glue代码源 */
    private String glueSource;
    /** Glue最后更新时间 */
    private long glueUpdatetime;

    // 广播执行相关参数
    /** 广播执行的当前索引 */
    private int broadcastIndex;
    /** 广播执行的总任务数 */
    private int broadcastTotal;

    // 手动添加 getter 方法，确保 Lombok 未生效时也能编译通过
    /** 获取ExecutorHandler */
    public String getExecutorHandler() {
        return executorHandler;
    }

    /** 获取GlueUpdatetime */
    public long getGlueUpdatetime() {
        return glueUpdatetime;
    }

    /** 获取GlueSource */
    public String getGlueSource() {
        return glueSource;
    }

    /** 获取JobId */
    public int getJobId() {
        return jobId;
    }

    /** 获取GlueType */
    public String getGlueType() {
        return glueType;
    }

    /** 获取记录日志Id */
    public long getLogId() {
        return logId;
    }

    /** 获取记录日志DateTime */
    public long getLogDateTime() {
        return logDateTime;
    }

    /** 获取ExecutorParams */
    public String getExecutorParams() {
        return executorParams;
    }

    /** 获取BroadcastIndex */
    public int getBroadcastIndex() {
        return broadcastIndex;
    }

    /** 获取Broadcast总计 */
    public int getBroadcastTotal() {
        return broadcastTotal;
    }

    /** 获取ExecutorTimeout */
    public int getExecutorTimeout() {
        return executorTimeout;
    }

    /** 设置JobId */
    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    /** 设置Profile */
    public void setProfile(String profile) {
        this.profile = profile;
    }

    /** 设置ExecutorHandler */
    public void setExecutorHandler(String executorHandler) {
        this.executorHandler = executorHandler;
    }

    /** 设置ExecutorParams */
    public void setExecutorParams(String executorParams) {
        this.executorParams = executorParams;
    }

    /** 设置ExecutorBlockStrategy */
    public void setExecutorBlockStrategy(String executorBlockStrategy) {
        this.executorBlockStrategy = executorBlockStrategy;
    }

    /** 设置ExecutorTimeout */
    public void setExecutorTimeout(int executorTimeout) {
        this.executorTimeout = executorTimeout;
    }

    /** 设置记录日志Id */
    public void setLogId(long logId) {
        this.logId = logId;
    }

    /** 设置记录日志DateTime */
    public void setLogDateTime(long logDateTime) {
        this.logDateTime = logDateTime;
    }

    /** 设置GlueType */
    public void setGlueType(String glueType) {
        this.glueType = glueType;
    }

    /** 设置GlueSource */
    public void setGlueSource(String glueSource) {
        this.glueSource = glueSource;
    }

    /** 设置GlueUpdatetime */
    public void setGlueUpdatetime(long glueUpdatetime) {
        this.glueUpdatetime = glueUpdatetime;
    }

    /** 设置BroadcastIndex */
    public void setBroadcastIndex(int broadcastIndex) {
        this.broadcastIndex = broadcastIndex;
    }

    /** 设置Broadcast总计 */
    public void setBroadcastTotal(int broadcastTotal) {
        this.broadcastTotal = broadcastTotal;
    }
}

