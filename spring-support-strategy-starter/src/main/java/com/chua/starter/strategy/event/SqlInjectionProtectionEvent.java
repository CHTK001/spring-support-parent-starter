package com.chua.starter.strategy.event;

/**
 * SQL注入防护事件
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
public class SqlInjectionProtectionEvent extends StrategyEvent {

    /**
     * 命中的SQL注入规则
     */
    private final String matchedPattern;

    /**
     * 检测位置（参数/请求头）
     */
    private final String detectionLocation;

    /**
     * 可疑内容
     */
    private final String suspiciousContent;

    /**
     * 构造函数
     *
     * @param source            事件源
     * @param requestUri        请求URI
     * @param requestMethod     请求方法
     * @param clientIp          客户端IP
     * @param userId            用户ID
     * @param strategyName      策略名称
     * @param allowed           是否允许通过
     * @param reason            拒绝原因
     * @param extraData         额外数据
     * @param matchedPattern    命中的SQL注入规则
     * @param detectionLocation 检测位置
     * @param suspiciousContent 可疑内容
     */
    public SqlInjectionProtectionEvent(Object source,
                                     String requestUri,
                                     String requestMethod,
                                     String clientIp,
                                     String userId,
                                     String strategyName,
                                     boolean allowed,
                                     String reason,
                                     String extraData,
                                     String matchedPattern,
                                     String detectionLocation,
                                     String suspiciousContent) {
        super(source, requestUri, requestMethod, clientIp, userId, strategyName, allowed, reason, extraData);
        this.matchedPattern = matchedPattern;
        this.detectionLocation = detectionLocation;
        this.suspiciousContent = suspiciousContent;
    }

    @Override
    public String getEventType() {
        return "SQL_INJECTION_PROTECTION";
    }

    public String getMatchedPattern() {
        return matchedPattern;
    }

    public String getDetectionLocation() {
        return detectionLocation;
    }

    public String getSuspiciousContent() {
        return suspiciousContent;
    }
}

