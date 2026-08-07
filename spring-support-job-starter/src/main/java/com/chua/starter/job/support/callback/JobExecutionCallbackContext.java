package com.chua.starter.job.support.callback;

import com.chua.starter.job.support.thread.JobContext;
import lombok.Builder;
import lombok.Data;

/**
 * 任务执行回调上下文 (骨架版)。
 * <p>
 * 历史版本含 SysJob / SysJobLog 字段,因对应的实体类与 mapper 已被删除,
 * 暂时只保留 {@link JobContext} 与基本上下文字段,待重构期间补充持久化。
 * </p>
 */
@Data
@Builder
public class JobExecutionCallbackContext {

    private String phase;

    /**
     * 当前线程的任务上下文。
     */
    private JobContext jobContext;

    /**
     * 当前重试次数,从 1 开始。
     */
    private int attempt;

    /**
     * 总执行次数,包括首次执行和重试。
     */
    private int maxAttempts;

    /**
     * 最近一次异常。
     */
    private Throwable throwable;
}
