package com.chua.tenant.support.client.consumer;

import java.util.Map;

/**
 * 租户元数据消费者接口
 * <p>
 * 客户端实现此接口以接收和处理从服务端推送的租户元数据
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
public interface TenantMetadataConsumer {

    /**
     * 获取消费者名称
     *
     * @return 消费者名称
     */
    String getName();

    /**
     * 获取消费者优先级
     * 数值越小优先级越高
     *
     * @return 优先级
     */
    default int getOrder() {
        return 100;
    }

    /**
     * 处理接收到的元数据
     *
     * @param tenantId 租户ID
     * @param metadata 元数据Map
     */
    void consumeMetadata(String tenantId, Map<String, Object> metadata);

    /**
     * 判断是否支持该元数据类型
     *
     * @param metadataType 元数据类型
     * @return 是否支持
     */
    default boolean supports(String metadataType) {
        return true;
    }
}
