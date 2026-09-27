package com.chua.starter.monitor.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Nacos 配置列表查询条件
 *
 * @param datasourceId 数据源 ID，为空时取第一个 nacos 类型数据源
 * @param namespace    命名空间标识，{@code public} 或空表示公有命名空间
 * @param group        分组精确过滤，可空
 * @param dataId       Data ID 关键字，非空时按模糊匹配
 * @param page         页码，从 1 开始
 * @param pageSize     每页条数
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NacosConfigQueryDTO(
        Long datasourceId,
        String namespace,
        String group,
        String dataId,
        Integer page,
        Integer pageSize
) {
}
