package com.chua.starter.job.support;

import lombok.Data;

/**
 * 任务触发参数类
 * <p>
 * 封装任务触发时所需的所有参数信息，作为任务调度和执行的核心传输对象。
 * 在分布式场景下，该对象会被序列化传输到执行器端执行。
 * </p>
 *
 * <h3>参数分类</h3>
 * <ul>
 *     <li><b>基础信息</b> - jobId, profile</li>
 *     <li><b>执行器配置</b> - executorHandler, executorParams, executorBlockStrategy, executorRouter, executorTimeout</li>
 *     <li><b>日志信息</b> - logId, logDateTime</li>
 *     <li><b>Glue配置</b> - glueType, glueSource, glueUpdatetime</li>
 *     <li><b>广播分片</b> - broadcastIndex, broadcastTotal</li>
 * </ul>
 *
 * <h3>使用场景</h3>
 * <pre>{@code
 * TriggerParam param = new TriggerParam();
 * param.setJobId(1);
 * param.setExecutorHandler("demoJobHandler");
 * param.setExecutorParams("{\"key\":\"value\"}");
 * param.setGlueType("BEAN");
 *
 * // 推送到任务线程队列执行
 * jobThread.pushTriggerQueue(param);
 * }</pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 * @see com.chua.starter.job.support.thread.JobThread#pushTriggerQueue(TriggerParam)
 * @see com.chua.starter.job.support.scheduler.LocalJobTrigger
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
