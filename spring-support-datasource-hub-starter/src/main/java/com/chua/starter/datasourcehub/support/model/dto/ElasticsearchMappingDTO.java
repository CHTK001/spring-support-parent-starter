package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 给已有 Elasticsearch 索引追加字段的请求
 *
 * @param index        索引名（必填）
 * @param fields       要新增的字段定义
 * @param datasourceId 数据源 ID，空取第一个 elasticsearch 数据源
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ElasticsearchMappingDTO(
        String index,
        List<ElasticsearchFieldDTO> fields,
        Long datasourceId
) {
}
