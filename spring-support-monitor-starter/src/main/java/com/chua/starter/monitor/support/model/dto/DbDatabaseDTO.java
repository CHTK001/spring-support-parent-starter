package com.chua.starter.monitor.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 数据库创建请求
 *
 * @param name         数据库名
 * @param datasourceId 数据源 ID，为空或 0 表示使用默认主数据源
 * @param charset      字符集，可为空（非 MySQL 内核会忽略）
 * @param collation    排序规则，可为空（非 MySQL 内核会忽略）
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DbDatabaseDTO(
        String name,
        Long datasourceId,
        String charset,
        String collation
) {
}
