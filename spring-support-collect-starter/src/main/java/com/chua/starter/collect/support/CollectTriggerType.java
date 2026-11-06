package com.chua.starter.collect.support;

/**
 * 任务触发方式常量。
 *
 * <p>与 {@code collect_task.collect_task_trigger_type} 的取值一致。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class CollectTriggerType {

    /**
     * 定时调度触发。
     */
    public static final String CRON = "CRON";

    /**
     * 控制台手动运行。
     */
    public static final String MANUAL = "MANUAL";

    /**
     * 单节点/草稿调试运行，结果不落正式记录表。
     */
    public static final String TEST = "TEST";

    private CollectTriggerType() {
    }
}
