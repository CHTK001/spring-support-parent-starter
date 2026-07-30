package com.chua.report.client.starter.job;


import lombok.Data;

/**
 * 触发器参数类，用于封装与任务触发相关的参数信息。
 */
@Data
public class TriggerParam {

    private int jobId; // 任务ID

    private String profile;

    // 执行器相关参数
    private String executorHandler; // 执行器处理代码的标识
    private String executorParams; // 执行器的参数
    private String executorBlockStrategy; // 执行器的阻塞策略
    private String executorRouter; // 执行器的阻塞策略
    private int executorTimeout; // 执行器超时时间

    // 日志相关参数
    private long logId; // 日志ID
    private long logDateTime; // 日志生成时间戳

    // Glue相关参数
    private String glueType; // Glue类型（例如：脚本、JAR等）
    private String glueSource; // Glue代码源
    private long glueUpdatetime; // Glue最后更新时间

    // 广播执行相关参数
    private int broadcastIndex; // 广播执行的当前索引
    private int broadcastTotal; // 广播执行的总任务数

    // 手动添加 getter 方法，确保 Lombok 未生效时也能编译通过
    public String getExecutorHandler() {
        return executorHandler;
    }

    public long getGlueUpdatetime() {
        return glueUpdatetime;
    }

    public String getGlueSource() {
        return glueSource;
    }

    public int getJobId() {
        return jobId;
    }

    public String getGlueType() {
        return glueType;
    }

    public long getLogId() {
        return logId;
    }

    public long getLogDateTime() {
        return logDateTime;
    }

    public String getExecutorParams() {
        return executorParams;
    }

    public int getBroadcastIndex() {
        return broadcastIndex;
    }

    public int getBroadcastTotal() {
        return broadcastTotal;
    }

    public int getExecutorTimeout() {
        return executorTimeout;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public void setExecutorHandler(String executorHandler) {
        this.executorHandler = executorHandler;
    }

    public void setExecutorParams(String executorParams) {
        this.executorParams = executorParams;
    }

    public void setExecutorBlockStrategy(String executorBlockStrategy) {
        this.executorBlockStrategy = executorBlockStrategy;
    }

    public void setExecutorTimeout(int executorTimeout) {
        this.executorTimeout = executorTimeout;
    }

    public void setLogId(long logId) {
        this.logId = logId;
    }

    public void setLogDateTime(long logDateTime) {
        this.logDateTime = logDateTime;
    }

    public void setGlueType(String glueType) {
        this.glueType = glueType;
    }

    public void setGlueSource(String glueSource) {
        this.glueSource = glueSource;
    }

    public void setGlueUpdatetime(long glueUpdatetime) {
        this.glueUpdatetime = glueUpdatetime;
    }

    public void setBroadcastIndex(int broadcastIndex) {
        this.broadcastIndex = broadcastIndex;
    }

    public void setBroadcastTotal(int broadcastTotal) {
        this.broadcastTotal = broadcastTotal;
    }
}
