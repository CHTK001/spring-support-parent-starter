package com.chua.starter.job.support.sync;

/**
 * 自研同步通道使用的 topic 常量。
 *
 * @since 4.0.0.42
 */
public final class JobSyncTopics {

    /**
     * 调度命令 topic (Scheduler → Executor)
     */
    public static final String TASK_COMMAND = "job-cmd";

    /**
     * 执行结果 topic (Executor → Scheduler)
     */
    public static final String TASK_LOG = "job-log";

    /**
     * 注册心跳 topic
     */
    public static final String REGISTER = "job-reg";

    private JobSyncTopics() {
    }
}
