package com.chua.starter.monitor.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * SQL 执行请求
 *
 * @param sql          待执行 SQL
 * @param datasourceId 数据源 ID，为空或 0 表示使用默认主数据源
 * @param database     目标库名，为空表示使用连接的默认库
 * @param stopOnError  批量执行时遇错是否停止；为 null 时按 true 处理。
 *                     单条执行端点不读这个字段。
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DbSqlExecuteDTO(
        String sql,
        Long datasourceId,
        String database,
        Boolean stopOnError
) {
    /**
     * 兼容旧调用方：只有三个字段的构造。
     *
     * <p>前端既有调用不传 stopOnError，反序列化会给 null，这里统一按「遇错停止」处理。</p>
     *
     * @param sql          待执行 SQL
     * @param datasourceId 数据源 ID
     * @param database     目标库
     * @return 请求对象
     */
    public static DbSqlExecuteDTO of(String sql, Long datasourceId, String database) {
        return new DbSqlExecuteDTO(sql, datasourceId, database, null);
    }
}
