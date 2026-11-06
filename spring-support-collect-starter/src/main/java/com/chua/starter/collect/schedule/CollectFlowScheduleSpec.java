package com.chua.starter.collect.schedule;

/**
 * 流程排期登记请求。
 *
 * <p>把"要怎么排期"从流程实体里剥离出来，交给调度端口去落地：有 job-starter 时写成
 * sys_job 的一行，没有时直接忽略。cron 为空表示只登记台账、不参与自动派发。</p>
 *
 * @param flowId   流程ID
 * @param flowName 流程名称（用于台账展示与告警文案）
 * @param cron     定时表达式，空字符串表示不排期
 * @param enabled  是否启用
 * @author CH
 * @since 4.0.0.42
 */
public record CollectFlowScheduleSpec(
        Integer flowId,
        String flowName,
        String cron,
        boolean enabled) {

    /**
     * 规范化：cron 为 null 时按空串处理，避免下游到处判空。
     *
     * @param flowId   流程ID
     * @param flowName 流程名称
     * @param cron     定时表达式，可为 null
     * @param enabled  是否启用
     * @return 规范化后的登记请求
     */
    public static CollectFlowScheduleSpec of(Integer flowId, String flowName, String cron, boolean enabled) {
        return new CollectFlowScheduleSpec(flowId, flowName, cron == null ? "" : cron.trim(), enabled);
    }
}
