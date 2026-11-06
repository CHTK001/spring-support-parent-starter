package com.chua.starter.collect.support;

/**
 * 执行通知的触发时机。
 *
 * <p>只区分成功与失败两类事实：任务被手动停止（STOPPED）与跳过（SKIPPED）既不是成功也不是失败，
 * 只有 {@link #ALWAYS} 会推送，避免停一次流程就收到一条告警。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class CollectNotifyTiming {

    /**
     * 仅成功时通知。
     */
    public static final String SUCCESS = "SUCCESS";

    /**
     * 仅失败时通知。
     */
    public static final String FAILURE = "FAILURE";

    /**
     * 每次终态都通知。
     */
    public static final String ALWAYS = "ALWAYS";

    private CollectNotifyTiming() {
    }

    /**
     * 该时机是否命中本次任务终态。
     *
     * @param timing 配置的时机
     * @param status 任务终态，取值见 {@link CollectTaskStatus}
     * @return 需要发送返回 true
     */
    public static boolean matches(String timing, String status) {
        if (ALWAYS.equalsIgnoreCase(timing)) {
            return true;
        }
        if (SUCCESS.equalsIgnoreCase(timing)) {
            return CollectTaskStatus.SUCCESS.equals(status);
        }
        if (FAILURE.equalsIgnoreCase(timing)) {
            return CollectTaskStatus.FAILURE.equals(status);
        }
        return false;
    }
}
