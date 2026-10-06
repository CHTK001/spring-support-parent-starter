package com.chua.starter.datasourcehub.support.model;

/**
 * Nacos 服务摘要
 *
 * @param name                   服务名（含分组前缀时为 {@code 分组@@服务名}，此处只放展示名）
 * @param clusterCount           集群数
 * @param healthyInstanceCount   健康实例数
 * @param unhealthyInstanceCount 不健康实例数
 * @param triggerFlag            是否开启主动健康检查
 * @author CH
 * @since 4.0.0.42
 */
public record NacosServiceVO(
        String name,
        Integer clusterCount,
        Integer healthyInstanceCount,
        Integer unhealthyInstanceCount,
        Boolean triggerFlag
) {
}
