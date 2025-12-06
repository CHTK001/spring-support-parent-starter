package com.chua.tenant.support.server.provider;

import java.util.Map;

/**
 * 租户元数据提供者接口
 * <p>
 * 服务端实现此接口以提供需要下发给客户端的租户元数据
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
public interface TenantMetadataProvider {

    /**
     * 获取提供者名称
     *
     * @return 提供者名称
     */
    String getName();

    /**
     * 获取提供者优先级
     * 数值越小优先级越高
     *
     * @return 优先级
     */
    default int getOrder() {
        return 100;
    }

    /**
     * 获取需要下发的元数据
     *
     * @param tenantId 租户ID
     * @return 元数据Map，key为元数据类型，value为元数据内容
     */
    Map<String, Object> getMetadata(String tenantId);

    /**
     * 判断是否支持该租户
     *
     * @param tenantId 租户ID
     * @return 是否支持
     */
    default boolean supports(String tenantId) {
        return true;
    }
}
