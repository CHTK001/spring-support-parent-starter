package com.chua.starter.job.support.remote;

import lombok.Data;

/**
 * 远程执行器接收任务后的回执。
 * <p>
 * 在中心推送模式下，执行器除了告诉调度中心“请求已接收”，还会尽量回传
 * 当前这次执行的最终结果，便于中心侧在不共享业务库的情况下仍能记录任务日志。
 * </p>
 */
@Data
public class RemoteJobTriggerResponse {

    /**
     * 是否已成功进入本地执行队列。
     */
    private boolean accepted;

    /**
     * 任务 ID。
     */
    private Integer jobId;

    /**
     * 任务编号。
     */
    private String jobNo;

    /**
     * 执行器处理器名称。
     */
    private String executorHandler;

    /**
     * 触发流水号。
     */
    private Long logId;

    /**
     * 触发流水编号。
     */
    private String logNo;

    /**
     * 回执说明。
     */
    private String message;

    /**
     * 是否已经由执行器侧日志体系接管。
     * <p>
     * 当执行器本地开启了表轮询并复用了 {@code LocalJobTrigger} 时，日志会由执行器
     * 自己写入统一任务表，此时中心侧不应重复写入。
     * </p>
     */
    private boolean logHandledByExecutor;

    /**
     * 执行状态，沿用任务日志表的状态值。
     * <p>
     * 典型值:
     * </p>
     * <ul>
     *     <li>{@code PADDING} - 已接收，执行结果由执行器侧异步落库</li>
     *     <li>{@code SUCCESS} - 已执行成功</li>
     *     <li>{@code FAILURE} - 已执行失败</li>
     * </ul>
     */
    private String executeCode;

    /**
     * 执行结果说明。
     */
    private String executeMessage;

    /**
     * 执行耗时，单位毫秒。
     */
    private Long costMs;
}
