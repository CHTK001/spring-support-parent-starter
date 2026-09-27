package com.chua.starter.monitor.support.model;

/**
 * Nacos 命名空间
 *
 * @param id          命名空间标识，public 命名空间为 {@code public}
 * @param namespace   请求时传给 Nacos 的 tenant 值，public 命名空间为空串
 * @param name        命名空间展示名
 * @param configCount 该命名空间下的配置数量
 * @author CH
 * @since 4.0.0.42
 */
public record NacosNamespaceVO(
        String id,
        String namespace,
        String name,
        Integer configCount
) {
}
