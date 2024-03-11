package com.chua.starter.monitor.job;


import lombok.Data;

/**
 * 触发器参数类，用于封装与任务触发相关的参数信息。
 */
@Data
public class TriggerParam {

    private int jobId; // 任务ID

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

}
