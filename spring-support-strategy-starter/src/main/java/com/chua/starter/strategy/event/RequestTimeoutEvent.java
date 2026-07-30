package com.chua.starter.strategy.event;

/**
 * 请求超时事件
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
public class RequestTimeoutEvent extends StrategyEvent {

    /**
     * 请求处理时长（毫秒）
     */
    private final long duration;

    /**
     * 超时时间（毫秒）
     */
    private final long timeout;

    /**
     * 构造函数
     *
     * @param source        事件源
     * @param requestUri    请求URI
     * @param requestMethod 请求方法
     * @param clientIp      客户端IP
     * @param userId        用户ID
     * @param strategyName  策略名称
     * @param allowed       是否允许通过
     * @param reason        拒绝原因
     * @param extraData     额外数据
     * @param duration      请求处理时长
     * @param timeout       超时时间
     */
    public RequestTimeoutEvent(Object source,
                             String requestUri,
                             String requestMethod,
                             String clientIp,
                             String userId,
                             String strategyName,
                             boolean allowed,
                             String reason,
                             String extraData,
                             long duration,
                             long timeout) {
        super(source, requestUri, requestMethod, clientIp, userId, strategyName, allowed, reason, extraData);
        this.duration = duration;
        this.timeout = timeout;
    }

    @Override
    public String getEventType() {
        return "REQUEST_TIMEOUT";
    }

    public long getDuration() {
        return duration;
    }

    public long getTimeout() {
        return timeout;
    }
}

