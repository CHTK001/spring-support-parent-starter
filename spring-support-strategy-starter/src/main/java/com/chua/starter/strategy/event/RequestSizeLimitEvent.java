package com.chua.starter.strategy.event;

/**
 * 请求大小限制事件
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
public class RequestSizeLimitEvent extends StrategyEvent {

    /**
     * 实际请求大小（字节）
     */
    private final long actualSize;

    /**
     * 最大允许大小（字节）
     */
    private final long maxSize;

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
     * @param actualSize    实际请求大小
     * @param maxSize       最大允许大小
     */
    public RequestSizeLimitEvent(Object source,
                               String requestUri,
                               String requestMethod,
                               String clientIp,
                               String userId,
                               String strategyName,
                               boolean allowed,
                               String reason,
                               String extraData,
                               long actualSize,
                               long maxSize) {
        super(source, requestUri, requestMethod, clientIp, userId, strategyName, allowed, reason, extraData);
        this.actualSize = actualSize;
        this.maxSize = maxSize;
    }

    @Override
    public String getEventType() {
        return "REQUEST_SIZE_LIMIT";
    }

    public long getActualSize() {
        return actualSize;
    }

    public long getMaxSize() {
        return maxSize;
    }
}

