package com.chua.starter.job.support.remote;

import lombok.Data;

/**
 * 调度中心下发到远程执行器的任务触发请求。
 */
@Data
public class RemoteJobTriggerRequest {

    /**
     * 中心侧任务 ID，用于保持同一任务在执行器侧串行执行。
     */
    private Integer jobId;

    /**
     * 中心侧任务编号。
     */
    private String jobNo;

    /**
     * 任务名称，仅用于排错和日志。
     */
    private String jobName;

    /**
     * 本地已注册的 JobHandler 名称。
     */
    private String executorHandler;

    /**
     * 执行参数，覆盖任务默认参数。
     */
    private String executorParams;

    /**
     * 执行超时时间，单位秒。
     */
    private Integer executorTimeout;

    /**
     * 外部生成的触发流水，便于平台与执行器做幂等串联。
     */
    private Long logId;

    /**
     * 外部生成的触发编号。
     */
    private String logNo;

    /**
     * 触发时间戳，毫秒。
     */
    private Long logDateTime;

    /**
     * 广播分片序号。
     */
    private Integer broadcastIndex;

    /**
     * 广播分片总数。
     */
    private Integer broadcastTotal;

    /**
     * 触发来源，例如 API / MANUAL。
     */
    private String triggerType;

    /**
     * 任务分发模式。
     */
    private String dispatchMode;

    /**
     * 远程执行器地址覆盖值。
     */
    private String remoteExecutorAddress;
}
