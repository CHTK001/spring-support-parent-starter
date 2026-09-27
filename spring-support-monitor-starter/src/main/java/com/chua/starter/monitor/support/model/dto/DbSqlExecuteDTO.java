package com.chua.starter.monitor.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * SQL 执行请求
 *
 * @param sql          待执行 SQL
 * @param datasourceId 数据源 ID，为空或 0 表示使用默认主数据源
 * @param database     目标库名，为空表示使用连接的默认库
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DbSqlExecuteDTO(
        String sql,
        Long datasourceId,
        String database
) {
}
