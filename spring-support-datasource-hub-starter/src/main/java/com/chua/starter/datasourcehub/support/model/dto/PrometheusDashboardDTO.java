package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Prometheus 自定义看板保存请求。
 *
 * @param datasourceId 数据源 ID，必填
 * @param name         看板名称，可空
 * @param config       布局配置 JSON 文本（前端定义形状），必填
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrometheusDashboardDTO(
        Long datasourceId,
        String name,
        String config
) {
}
