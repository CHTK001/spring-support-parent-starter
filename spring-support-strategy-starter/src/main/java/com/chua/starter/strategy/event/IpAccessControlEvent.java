package com.chua.starter.strategy.event;

/**
 * IP访问控制事件
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
public class IpAccessControlEvent extends StrategyEvent {

    /**
     * 匹配的规则名称
     */
    private final String ruleName;

    /**
     * 规则类型（WHITELIST/BLACKLIST）
     */
    private final String ruleType;

    /**
     * IP模式
     */
    private final String ipPattern;

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
     * @param ruleName      规则名称
     * @param ruleType      规则类型
     * @param ipPattern     IP模式
     */
    public IpAccessControlEvent(Object source,
                               String requestUri,
                               String requestMethod,
                               String clientIp,
                               String userId,
                               String strategyName,
                               boolean allowed,
                               String reason,
                               String extraData,
                               String ruleName,
                               String ruleType,
                               String ipPattern) {
        super(source, requestUri, requestMethod, clientIp, userId, strategyName, allowed, reason, extraData);
        this.ruleName = ruleName;
        this.ruleType = ruleType;
        this.ipPattern = ipPattern;
    }

    @Override
    public String getEventType() {
        return "IP_ACCESS_CONTROL";
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getRuleType() {
        return ruleType;
    }

    public String getIpPattern() {
        return ipPattern;
    }
}


