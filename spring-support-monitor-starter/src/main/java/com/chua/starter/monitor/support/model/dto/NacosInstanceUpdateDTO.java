package com.chua.starter.monitor.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Nacos 实例更新请求（上下线与改权重共用同一端点）。
 *
 * @param datasourceId 数据源 ID，可空
 * @param namespace    命名空间标识
 * @param serviceName  服务名
 * @param groupName    分组
 * @param ip           实例 IP
 * @param port         实例端口
 * @param weight       新权重（0-10000），不改权重时为 null
 * @param enabled      是否启用，不改上下线时为 null
 * @author CH
 * @since 4.0.0.43
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NacosInstanceUpdateDTO(
        Long datasourceId,
        String namespace,
        String serviceName,
        String groupName,
        String ip,
        Integer port,
        Double weight,
        Boolean enabled
) {
}
