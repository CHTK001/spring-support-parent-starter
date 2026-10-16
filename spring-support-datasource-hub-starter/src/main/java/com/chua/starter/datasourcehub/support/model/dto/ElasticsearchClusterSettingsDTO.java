package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/**
 * 更新 Elasticsearch 集群设置的请求
 *
 * @param persistent   持久设置（重启后仍生效），如 {@code {"cluster.routing.allocation.enable":"all"}}
 * @param transientSettings 临时设置（重启后失效），键名带 {@code transient.} 前缀可省略
 * @param datasourceId 数据源 ID，空取第一个 elasticsearch 数据源
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ElasticsearchClusterSettingsDTO(
        Map<String, Object> persistent,
        Map<String, Object> transientSettings,
        Long datasourceId
) {
}
