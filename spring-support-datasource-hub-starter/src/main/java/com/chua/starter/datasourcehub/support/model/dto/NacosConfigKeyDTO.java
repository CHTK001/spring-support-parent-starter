package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Nacos 单配置定位条件（读详情、删除都用它）
 *
 * @param datasourceId 数据源 ID，为空时取第一个 nacos 类型数据源
 * @param namespace    命名空间标识，{@code public} 或空表示公有命名空间
 * @param group        配置分组
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NacosConfigKeyDTO(
        Long datasourceId,
        String namespace,
        String group
) {
}
