package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Nacos 配置发布请求（新建与更新同一语义，Nacos 按 dataId+group+tenant 覆盖）
 *
 * @param datasourceId 数据源 ID，为空时取第一个 nacos 类型数据源
 * @param dataId       配置 Data ID
 * @param group       配置分组
 * @param namespace    命名空间标识，{@code public} 或空表示公有命名空间
 * @param format       配置格式（yaml/properties/json/xml/text）
 * @param content      配置正文
 * @param betaIps     Beta 发布目标 IP（逗号分隔），为空表示全量发布
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NacosConfigSaveDTO(
        Long datasourceId,
        String dataId,
        String group,
        String namespace,
        String format,
        String content,
        String betaIps
) {
}
