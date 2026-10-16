package com.chua.starter.datasourcehub.support.model;

/**
 * Elasticsearch 字段（mapping 属性）行
 *
 * @param name     字段名
 * @param type    字段类型（text/keyword/long/date/object/nested/...）
 * @param analyzer 分词器，仅 text 类型有值
 * @param format   日期格式，仅 date 类型可能有值
 * @param index   是否建索引（{@code false} 表示只存储不检索）
 * @author CH
 * @since 4.0.0.42
 */
public record ElasticsearchFieldVO(
        String name,
        String type,
        String analyzer,
        String format,
        Boolean index
) {
}
