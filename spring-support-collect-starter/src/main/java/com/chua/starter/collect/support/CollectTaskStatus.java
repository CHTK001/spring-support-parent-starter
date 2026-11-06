package com.chua.starter.collect.support;

/**
 * 任务与节点执行状态常量。
 *
 * <p>与 {@code collect_task.collect_task_status}、{@code collect_task_log.collect_task_log_status}
 * 的取值一致，前端按同一套字面量渲染状态标签。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class CollectTaskStatus {

    /**
     * 执行中。
     */
    public static final String RUNNING = "RUNNING";

    /**
     * 执行成功。
     */
    public static final String SUCCESS = "SUCCESS";

    /**
     * 执行失败。
     */
    public static final String FAILURE = "FAILURE";

    /**
     * 被手动停止。
     */
    public static final String STOPPED = "STOPPED";

    /**
     * 未执行（连线未放行或循环集合为空）。
     */
    public static final String SKIPPED = "SKIPPED";

    private CollectTaskStatus() {
    }
}
