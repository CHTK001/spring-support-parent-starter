package com.chua.starter.strategy.event;

/**
 * 防抖事件
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
public class DebounceEvent extends StrategyEvent {

    /**
     * 防抖配置ID
     */
    private final Long configId;

    /**
     * 防抖模式（ip/user/session/global）
     */
    private final String mode;

    /**
     * 防抖键
     */
    private final String debounceKey;

    /**
     * 防抖时长（毫秒）
     */
    private final Long duration;

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
     * @param configId      防抖配置ID
     * @param mode          防抖模式
     * @param debounceKey   防抖键
     * @param duration      防抖时长
     */
    public DebounceEvent(Object source,
                        String requestUri,
                        String requestMethod,
                        String clientIp,
                        String userId,
                        String strategyName,
                        boolean allowed,
                        String reason,
                        String extraData,
                        Long configId,
                        String mode,
                        String debounceKey,
                        Long duration) {
        super(source, requestUri, requestMethod, clientIp, userId, strategyName, allowed, reason, extraData);
        this.configId = configId;
        this.mode = mode;
        this.debounceKey = debounceKey;
        this.duration = duration;
    }

    @Override
    public String getEventType() {
        return "DEBOUNCE";
    }

    public Long getConfigId() {
        return configId;
    }

    public String getMode() {
        return mode;
    }

    public String getDebounceKey() {
        return debounceKey;
    }

    public Long getDuration() {
        return duration;
    }
}


