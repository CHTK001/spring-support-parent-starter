package com.chua.starter.strategy.event;

import java.util.Set;

/**
 * HTTP方法限制事件
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
public class HttpMethodRestrictionEvent extends StrategyEvent {

    /**
     * 请求方法
     */
    private final String method;

    /**
     * 允许的方法集合
     */
    private final Set<String> allowedMethods;

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
     * @param method        请求方法
     * @param allowedMethods 允许的方法集合
     */
    public HttpMethodRestrictionEvent(Object source,
                                    String requestUri,
                                    String requestMethod,
                                    String clientIp,
                                    String userId,
                                    String strategyName,
                                    boolean allowed,
                                    String reason,
                                    String extraData,
                                    String method,
                                    Set<String> allowedMethods) {
        super(source, requestUri, requestMethod, clientIp, userId, strategyName, allowed, reason, extraData);
        this.method = method;
        this.allowedMethods = allowedMethods;
    }

    @Override
    public String getEventType() {
        return "HTTP_METHOD_RESTRICTION";
    }

    public String getMethod() {
        return method;
    }

    public Set<String> getAllowedMethods() {
        return allowedMethods;
    }
}

