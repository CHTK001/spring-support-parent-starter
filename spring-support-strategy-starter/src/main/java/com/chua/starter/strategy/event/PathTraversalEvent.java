package com.chua.starter.strategy.event;

/**
 * 路径穿透防护事件
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
public class PathTraversalEvent extends StrategyEvent {

    /**
     * 命中的路径穿透规则
     */
    private final String matchedPattern;

    /**
     * 检测位置（参数/路径）
     */
    private final String detectionLocation;

    /**
     * 可疑路径
     */
    private final String suspiciousPath;

    /**
     * 构造函数
     *
     * @param source           事件源
     * @param requestUri       请求URI
     * @param requestMethod    请求方法
     * @param clientIp         客户端IP
     * @param userId           用户ID
     * @param strategyName     策略名称
     * @param allowed          是否允许通过
     * @param reason           拒绝原因
     * @param extraData        额外数据
     * @param matchedPattern   命中的路径穿透规则
     * @param detectionLocation 检测位置
     * @param suspiciousPath   可疑路径
     */
    public PathTraversalEvent(Object source,
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
                             String suspiciousPath) {
        super(source, requestUri, requestMethod, clientIp, userId, strategyName, allowed, reason, extraData);
        this.matchedPattern = matchedPattern;
        this.detectionLocation = detectionLocation;
        this.suspiciousPath = suspiciousPath;
    }

    @Override
    public String getEventType() {
        return "PATH_TRAVERSAL";
    }

    public String getMatchedPattern() {
        return matchedPattern;
    }

    public String getDetectionLocation() {
        return detectionLocation;
    }

    public String getSuspiciousPath() {
        return suspiciousPath;
    }
}


