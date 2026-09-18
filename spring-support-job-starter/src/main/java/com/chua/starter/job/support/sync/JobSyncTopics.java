package com.chua.starter.job.support.sync;

/**
* 自研同步通道使用的 topic 常量。
*
* @since 4.0.0.42
* @author CH
 */
public final class JobSyncTopics {

    /**
    * 调度命令 topic (调度器 → 执行器)
    */
    public static final String TASK_COMMAND = "job-cmd";

    /**
    * 执行结果 topic (执行器 → 调度器)
    */
    public static final String TASK_LOG = "job-log";

    /**
    * 注册心跳 topic
    */
    public static final String REGISTER = "job-reg";

    /** 创建 作业同步topics 实例 */
    private JobSyncTopics() {
    }
}
