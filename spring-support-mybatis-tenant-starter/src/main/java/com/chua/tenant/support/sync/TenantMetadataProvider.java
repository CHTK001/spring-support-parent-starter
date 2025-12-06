package com.chua.tenant.support.sync;

import java.util.Map;

/**
 * 租户元数据提供者接�?
 * 通过 SPI 机制实现，用于服务端下发租户元数�?
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/02
 */
public interface TenantMetadataProvider {

    /**
     * 获取提供者名�?
     *
     * @return 提供者名�?
     */
    String getName();

    /**
     * 获取提供者优先级
     * 数值越小优先级越高
     *
     * @return 优先�?
     */
    default int getOrder() {
        return 100;
    }

    /**
     * 获取需要下发的元数�?
     *
     * @param tenantId 租户ID
     * @return 元数据Map，key为元数据类型，value为元数据内容
     */
    Map<String, Object> getMetadata(String tenantId);

    /**
     * 判断是否支持该租�?
     *
     * @param tenantId 租户ID
     * @return 是否支持
     */
    default boolean supports(String tenantId) {
        return true;
    }
}
