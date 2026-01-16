package com.chua.starter.strategy.event;

/**
 * 内容安全策略事件
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
public class ContentSecurityPolicyEvent extends StrategyEvent {

    /**
     * CSP策略
     */
    private final String policy;

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
     * @param policy        CSP策略
     */
    public ContentSecurityPolicyEvent(Object source,
                                    String requestUri,
                                    String requestMethod,
                                    String clientIp,
                                    String userId,
                                    String strategyName,
                                    boolean allowed,
                                    String reason,
                                    String extraData,
                                    String policy) {
        super(source, requestUri, requestMethod, clientIp, userId, strategyName, allowed, reason, extraData);
        this.policy = policy;
    }

    @Override
    public String getEventType() {
        return "CONTENT_SECURITY_POLICY";
    }

    public String getPolicy() {
        return policy;
    }
}

