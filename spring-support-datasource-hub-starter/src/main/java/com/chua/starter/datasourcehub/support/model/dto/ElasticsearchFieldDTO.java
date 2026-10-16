package com.chua.starter.datasourcehub.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Elasticsearch 字段定义（建索引/加字段的入参）
 *
 * @param name     字段名（必填，支持 {@code a.b} 形式的对象子字段）
 * @param type    字段类型，缺省 {@code keyword}
 * @param analyzer 分词器，仅 text 有意义
 * @param format   日期格式，仅 date 有意义
 * @param index    是否建索引，缺省建
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ElasticsearchFieldDTO(
        String name,
        String type,
        String analyzer,
        String format,
        Boolean index
) {
}
