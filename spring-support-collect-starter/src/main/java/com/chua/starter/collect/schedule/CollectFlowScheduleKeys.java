package com.chua.starter.collect.schedule;

/**
 * 采集流程调度键约定。
 *
 * <p>采集流程在调度台账（sys_job）里的行键与分组是跨模块约定：调度侧按分组
 * {@code collect-flow} 扫描取活，前端按 {@code collect-flow-<流程ID>} 展示。
 * 这类字符串与具体调度器实现无关，所以放在中立包，不依赖 job-starter。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class CollectFlowScheduleKeys {

    /**
     * 流程调度行的键前缀。
     */
    public static final String JOB_KEY_PREFIX = "collect-flow-";

    /**
     * 流程调度行所在的分组。
     */
    public static final String JOB_GROUP = "collect-flow";

    /**
     * 工具类不允许实例化。
     */
    private CollectFlowScheduleKeys() {
    }

    /**
     * 流程ID对应的调度行键。
     *
     * @param flowId 流程ID
     * @return 调度行键
     */
    public static String jobKeyOf(Integer flowId) {
        return JOB_KEY_PREFIX + flowId;
    }
}
