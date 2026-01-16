package com.chua.starter.strategy.event;

import lombok.Getter;

/**
 * 限流事件
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Getter
public class RateLimitEvent extends StrategyEvent {

    /**
     * 限流配置ID
     */
    private final Long configId;

    /**
     * 限流维度（IP/USER/API/GLOBAL）
     */
    private final String dimension;

    /**
     * 限流键
     */
    private final String rateLimitKey;

    /**
     * 限流阈值（每周期允许的请求数）
     */
    private final Integer limitForPeriod;

    /**
     * 限流周期（秒）
     */
    private final Integer refreshPeriodSeconds;

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
     * @param configId            限流配置ID
     * @param dimension           限流维度
     * @param rateLimitKey        限流键
     * @param limitForPeriod      限流阈值
     * @param refreshPeriodSeconds 限流周期
     */
    public RateLimitEvent(Object source,
                         String requestUri,
                         String requestMethod,
                         String clientIp,
                         String userId,
                         String strategyName,
                         boolean allowed,
                         String reason,
                         String extraData,
                         Long configId,
                         String dimension,
                         String rateLimitKey,
                         Integer limitForPeriod,
                         Integer refreshPeriodSeconds) {
        super(source, requestUri, requestMethod, clientIp, userId, strategyName, allowed, reason, extraData);
        this.configId = configId;
        this.dimension = dimension;
        this.rateLimitKey = rateLimitKey;
        this.limitForPeriod = limitForPeriod;
        this.refreshPeriodSeconds = refreshPeriodSeconds;
    }

    @Override
    public String getEventType() {
        return "RATE_LIMIT";
    }
}


