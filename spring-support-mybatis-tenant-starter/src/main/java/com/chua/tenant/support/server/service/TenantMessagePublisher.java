package com.chua.tenant.support.server.service;

/**
 * 租户消息发布接口
 * <p>
 * 用于发布租户相关的数据变更消�?
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
public interface TenantMessagePublisher {

    /**
     * 发布消息
     *
     * @param topic 消息主题
     * @param data  消息数据
     */
    void publish(String topic, Object data);
}
