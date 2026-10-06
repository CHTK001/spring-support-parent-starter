package com.chua.starter.datasourcehub.support.dbconsole;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/**
 * SQL 执行请求
 *
 * @param sql          待执行 SQL
 * @param datasourceId 数据源 ID，为空或 0 表示使用默认主数据源
 * @param database     目标库名，为空表示使用连接的默认库
 * @param stopOnError  批量执行时遇错是否停止；为 null 时按 true 处理。
 *                     单条执行端点不读这个字段。
 * @param params       {@code ${name}} 占位符的取值表；为空表示脚本里没有占位符。
 *                     取值会被转成**字面量**再内联（见
 *                     {@code SqlParameterBinder#toLiteral}），不是原样拼接。
 * @param txId         显式事务 ID；不为空时在**该事务的连接**上执行
 *                     （连接由 {@code DbTxRegistry} 跨请求持有）。
 *                     此时 datasourceId / database 被忽略 ——
 *                     事务的库在开启时就定了，中途换库会让
 *                     「已经改过的行」和「要提交的行」落在不同库里。
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DbSqlExecuteDTO(
        String sql,
        Long datasourceId,
        String database,
        Boolean stopOnError,
        Map<String, Object> params,
        String txId
) {
    /**
     * 兼容旧调用方：只有三个字段的构造。
     *
     * <p>前端既有调用不传 stopOnError / params，反序列化会给 null，
     * 这里统一按「遇错停止、无参数」处理。</p>
     *
     * @param sql          待执行 SQL
     * @param datasourceId 数据源 ID
     * @param database     目标库
     * @return 请求对象
     */
    public static DbSqlExecuteDTO of(String sql, Long datasourceId, String database) {
        return new DbSqlExecuteDTO(sql, datasourceId, database, null, null, null);
    }

    /**
     * 带参数的构造。
     *
     * @param sql          待执行 SQL
     * @param datasourceId 数据源 ID
     * @param database     目标库
     * @param params       占位符取值
     * @return 请求对象
     */
    public static DbSqlExecuteDTO withParams(String sql, Long datasourceId, String database,
                                             Map<String, Object> params) {
        return new DbSqlExecuteDTO(sql, datasourceId, database, null, params, null);
    }

    /**
     * 是否在既有事务里执行。
     *
     * @return true=在事务里跑
     */
    public boolean inTransaction() {
        return txId != null && !txId.isBlank();
    }

    /**
     * 遇错是否停止。
     *
     * @return true=停止（默认）
     */
    public boolean stopOnErrorOrDefault() {
        return stopOnError == null || stopOnError;
    }
}