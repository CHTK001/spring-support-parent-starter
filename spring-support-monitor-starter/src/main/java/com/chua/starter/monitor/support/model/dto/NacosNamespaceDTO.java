package com.chua.starter.monitor.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Nacos 命名空间新建/编辑请求。
 *
 * <p>新建时 {@code namespaceId} 可留空由 Nacos 自动生成；编辑/删除时按
 * {@code namespaceId} 定位公有空间之外的命名空间。</p>
 *
 * @param datasourceId   数据源 ID，为空时取第一个 nacos 类型数据源
 * @param namespaceId     命名空间 ID（编辑/删除必填）
 * @param namespaceName  命名空间名称（新建/编辑必填）
 * @param namespaceDesc  命名空间描述
 * @author CH
 * @since 4.0.0.43
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NacosNamespaceDTO(
        Long datasourceId,
        String namespaceId,
        String namespaceName,
        String namespaceDesc
) {
}
