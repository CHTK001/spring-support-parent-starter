package com.chua.starter.strategy.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * 策略事件基类
 * <p>
 * 所有策略拦截器的事件都继承此类，提供统一的事件数据结构。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Getter
public abstract class StrategyEvent extends ApplicationEvent {

    /**
     * 事件发生时间
     */
    private final LocalDateTime eventTime;

    /**
     * 请求URI
     */
    private final String requestUri;

    /**
     * 请求方法
     */
    private final String requestMethod;

    /**
     * 客户端IP
     */
    private final String clientIp;

    /**
     * 用户ID（如果可获取）
     */
    private final String userId;

    /**
     * 策略名称
     */
    private final String strategyName;

    /**
     * 是否允许通过
     */
    private final boolean allowed;

    /**
     * 拒绝原因（如果被拒绝）
     */
    private final String reason;

    /**
     * 额外数据（JSON格式）
     */
    private final String extraData;

    /**
     * 构造函数
     *
     * @param source       事件源
     * @param requestUri   请求URI
     * @param requestMethod 请求方法
     * @param clientIp     客户端IP
     * @param userId       用户ID
     * @param strategyName 策略名称
     * @param allowed      是否允许通过
     * @param reason       拒绝原因
     * @param extraData    额外数据
     */
    protected StrategyEvent(Object source,
                          String requestUri,
                          String requestMethod,
                          String clientIp,
                          String userId,
                          String strategyName,
                          boolean allowed,
                          String reason,
                          String extraData) {
        super(source);
        this.eventTime = LocalDateTime.now();
        this.requestUri = requestUri;
        this.requestMethod = requestMethod;
        this.clientIp = clientIp;
        this.userId = userId;
        this.strategyName = strategyName;
        this.allowed = allowed;
        this.reason = reason;
        this.extraData = extraData;
    }

    /**
     * 获取事件类型
     *
     * @return 事件类型
     */
    public abstract String getEventType();
}


