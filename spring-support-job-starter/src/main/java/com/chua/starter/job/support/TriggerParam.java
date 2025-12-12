package com.chua.starter.job.support;

import lombok.Data;

/**
 * 触发器参数类
 * <p>
 * 用于封装与任务触发相关的参数信息。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
@Data
public class TriggerParam {

    /**
     * 任务ID
     */
    private int jobId;

    /**
     * 环境配置
     */
    private String profile;

    /**
     * 执行器处理代码的标识
     */
    private String executorHandler;

    /**
     * 执行器的参数
     */
    private String executorParams;

    /**
     * 执行器的阻塞策略
     */
    private String executorBlockStrategy;

    /**
     * 执行器路由策略
     */
    private String executorRouter;

    /**
     * 执行器超时时间（秒）
     */
    private int executorTimeout;

    /**
     * 日志ID
     */
    private long logId;

    /**
     * 日志生成时间戳
     */
    private long logDateTime;

    /**
     * Glue类型
     */
    private String glueType;

    /**
     * Glue代码源
     */
    private String glueSource;

    /**
     * Glue最后更新时间
     */
    private long glueUpdatetime;

    /**
     * 广播执行的当前索引
     */
    private int broadcastIndex;

    /**
     * 广播执行的总任务数
     */
    private int broadcastTotal;
}
