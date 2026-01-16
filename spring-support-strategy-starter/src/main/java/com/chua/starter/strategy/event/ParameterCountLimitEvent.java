package com.chua.starter.strategy.event;

/**
 * 参数数量限制事件
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
public class ParameterCountLimitEvent extends StrategyEvent {

    /**
     * 实际参数数量
     */
    private final int parameterCount;

    /**
     * 最大参数数量
     */
    private final int maxParameters;

    /**
     * 构造函数
     *
     * @param source         事件源
     * @param requestUri     请求URI
     * @param requestMethod  请求方法
     * @param clientIp       客户端IP
     * @param userId         用户ID
     * @param strategyName   策略名称
     * @param allowed        是否允许通过
     * @param reason         拒绝原因
     * @param extraData      额外数据
     * @param parameterCount 实际参数数量
     * @param maxParameters  最大参数数量
     */
    public ParameterCountLimitEvent(Object source,
                                  String requestUri,
                                  String requestMethod,
                                  String clientIp,
                                  String userId,
                                  String strategyName,
                                  boolean allowed,
                                  String reason,
                                  String extraData,
                                  int parameterCount,
                                  int maxParameters) {
        super(source, requestUri, requestMethod, clientIp, userId, strategyName, allowed, reason, extraData);
        this.parameterCount = parameterCount;
        this.maxParameters = maxParameters;
    }

    @Override
    public String getEventType() {
        return "PARAMETER_COUNT_LIMIT";
    }

    public int getParameterCount() {
        return parameterCount;
    }

    public int getMaxParameters() {
        return maxParameters;
    }
}

