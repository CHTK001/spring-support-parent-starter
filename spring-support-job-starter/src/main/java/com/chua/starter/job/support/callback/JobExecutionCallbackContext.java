package com.chua.starter.job.support.callback;

import com.chua.starter.job.support.entity.SysJob;
import com.chua.starter.job.support.entity.SysJobLog;
import com.chua.starter.job.support.thread.JobContext;
import lombok.Builder;
import lombok.Data;

/**
 * 任务执行回调上下文。
 */
@Data
@Builder
public class JobExecutionCallbackContext {

    /**
     * 回调阶段。
     */
    private String phase;

    /**
     * 当前任务。
     */
    private SysJob job;

    /**
     * 当前执行日志。
     */
    private SysJobLog jobLog;

    /**
     * 当前线程的任务上下文。
     */
    private JobContext jobContext;

    /**
     * 当前重试次数，从 1 开始。
     */
    private int attempt;

    /**
     * 总执行次数，包括首次执行和重试。
     */
    private int maxAttempts;

    /**
     * 最近一次异常。
     */
    private Throwable throwable;
}
