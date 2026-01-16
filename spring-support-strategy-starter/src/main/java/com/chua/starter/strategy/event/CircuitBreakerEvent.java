package com.chua.starter.strategy.event;

/**
 * 熔断事件
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
public class CircuitBreakerEvent extends StrategyEvent {

    /**
     * 熔断配置ID
     */
    private final Long configId;

    /**
     * 熔断器状态（CLOSED/OPEN/HALF_OPEN）
     */
    private final String state;

    /**
     * 失败率阈值
     */
    private final Float failureRateThreshold;

    /**
     * 构造函数
     *
     * @param source              事件源
     * @param requestUri          请求URI
     * @param requestMethod       请求方法
     * @param clientIp            客户端IP
     * @param userId              用户ID
     * @param strategyName        策略名称
     * @param allowed             是否允许通过
     * @param reason              拒绝原因
     * @param extraData           额外数据
     * @param configId            熔断配置ID
     * @param state               熔断器状态
     * @param failureRateThreshold 失败率阈值
     */
    public CircuitBreakerEvent(Object source,
                               String requestUri,
                               String requestMethod,
                               String clientIp,
                               String userId,
                               String strategyName,
                               boolean allowed,
                               String reason,
                               String extraData,
                               Long configId,
                               String state,
                               Float failureRateThreshold) {
        super(source, requestUri, requestMethod, clientIp, userId, strategyName, allowed, reason, extraData);
        this.configId = configId;
        this.state = state;
        this.failureRateThreshold = failureRateThreshold;
    }

    @Override
    public String getEventType() {
        return "CIRCUIT_BREAKER";
    }

    public Long getConfigId() {
        return configId;
    }

    public String getState() {
        return state;
    }

    public Float getFailureRateThreshold() {
        return failureRateThreshold;
    }
}


