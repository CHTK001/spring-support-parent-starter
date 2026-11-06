package com.chua.starter.collect.schedule;

/**
 * 流程调度运行态快照。
 *
 * <p>与具体调度器实现解耦：有 job-starter 时由 job 台账填充，没有时由本地空实现返回
 * 未登记状态。全部为原始类型或不可变字符串，天然深不可变。</p>
 *
 * @param jobKey            调度行键
 * @param nextFireMs        下次触发时间（毫秒时间戳，0 表示无排期）
 * @param lastFireMs        上次触发时间（毫秒时间戳，0 表示未触发过）
 * @param handlerRegistered 执行器是否已注册（决定"排期是否真正可派发"）
 * @author CH
 * @since 4.0.0.42
 */
public record CollectFlowScheduleState(
        String jobKey,
        long nextFireMs,
        long lastFireMs,
        boolean handlerRegistered) {

    /**
     * 未登记时的空状态。
     *
     * @param jobKey 调度行键
     * @return 空状态
     */
    public static CollectFlowScheduleState empty(String jobKey) {
        return new CollectFlowScheduleState(jobKey, 0L, 0L, false);
    }
}
