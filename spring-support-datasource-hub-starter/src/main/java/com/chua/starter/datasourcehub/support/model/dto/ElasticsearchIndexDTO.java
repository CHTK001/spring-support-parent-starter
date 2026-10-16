package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 新建 Elasticsearch 索引请求（相当于「设计表」）
 *
 * @param name         索引名（必填）
 * @param shards       主分片数，空取服务端默认
 * @param replicas     副本数，空取服务端默认
 * @param fields       字段定义列表，空表示建一个无显式 mapping 的索引
 * @param datasourceId 数据源 ID，空取第一个 elasticsearch 数据源
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ElasticsearchIndexDTO(
        String name,
        Integer shards,
        Integer replicas,
        List<ElasticsearchFieldDTO> fields,
        Long datasourceId
) {
}
