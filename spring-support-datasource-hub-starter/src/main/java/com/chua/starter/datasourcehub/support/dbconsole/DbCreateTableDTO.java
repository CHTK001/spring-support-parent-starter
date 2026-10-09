package com.chua.starter.datasourcehub.support.dbconsole;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * 新建表请求。
 *
 * <p>与 {@code DbDatabaseDTO} 同形：{@code datasourceId} 为空或 0 表示使用默认主数据源。</p>
 *
 * <p><b>列定义有两种形态，二选一</b>（{@code columns} 优先）：</p>
 * <ul>
 *   <li>{@code columns}：结构化的「设计表」列，键与「新增列」端点完全一致
 *       （{@code columnName / type / size / scale / nullable / autoIncrement / defaultValue / comment …}），
 *       由 {@code DbConsoleController#columnDef} 解析、{@link DbColumnDef} 在构造期校验。
 *       建表对话框走这条 —— 界面上是列网格，不是 SQL 文本框；</li>
 *   <li>{@code fields}：列定义原文（{@code id BIGINT PRIMARY KEY, name VARCHAR(255)}），
 *       保留给「粘一段列定义就建」的用法，后端原样透传给内核。</li>
 * </ul>
 *
 * <p>两条路径都<b>不在前端拼 CREATE TABLE</b>：引号、{@code COMMENT}、自增写法全按内核选
 * （见 {@link DbDialectSupport#createTable}）。</p>
 *
 * @param table        表名（需通过标识符白名单）
 * @param columns      结构化列定义；为空时回退到 {@code fields}
 * @param primaryKey   主键列名，须取自 {@code columns}；可为空
 * @param fields       列定义原文；{@code columns} 为空时必填
 * @param comment      表注释，可为空
 * @param datasourceId 数据源 ID，为空或 0 表示使用默认主数据源
 * @param database     目标库名，可为空（用连接默认库）
 * @author CH
 * @since 4.0.0.46
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DbCreateTableDTO(
        String table,
        List<Map<String, Object>> columns,
        List<String> primaryKey,
        String fields,
        String comment,
        Long datasourceId,
        String database
) {
}
