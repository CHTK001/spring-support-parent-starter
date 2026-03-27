package com.chua.starter.job.support;

/**
 * 注解任务同步到配置表的策略。
 */
public enum AnnotationSyncMode {

    /**
     * 不自动同步。
     */
    NONE,

    /**
     * 仅在配置表中不存在时创建。
     */
    CREATE,

    /**
     * 存在则更新，不存在则创建。
     */
    UPDATE
}
