package com.chua.starter.monitor.support.datasync;

import com.chua.common.support.lang.datasource.dialect.SqlName;
import com.chua.starter.monitor.support.model.SyncColumnVO;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 数据同步的 JDBC 元数据与标识符工具。
 *
 * <p>把「读列定义」「校验并引用表名/列名」这类会拼 SQL 的动作集中在一处，统一走
 * {@link SqlName} 白名单，避免动态标识符被直接拼进语句。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public final class SyncJdbcSupport {

    /**
     * 工具类禁止实例化。
     */
    private SyncJdbcSupport() {
    }

    /**
     * 读取一段 SELECT 的结果集列定义。
     *
     * <p>只取 {@code PreparedStatement} 的元数据，不执行查询，因此对千万级表也是常数开销。
     * 列名取 {@code getColumnLabel}（{@code AS} 别名优先），与后续按列名取值的行为一致。</p>
     *
     * @param conn 连接
     * @param sql  必须是 SELECT 的语句
     * @return 列列表（按结果集顺序）
     * @throws SQLException 语句无法解析或数据库不支持元数据预取
     */
    public static List<SyncColumnVO> columnsOf(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSetMetaData meta = ps.getMetaData();
            if (meta == null) {
                throw new SQLException("无法读取该语句的结果集结构，请确认是单条 SELECT");
            }
            int count = meta.getColumnCount();
            List<SyncColumnVO> columns = new ArrayList<>(count);
            for (int i = 1; i <= count; i++) {
                String label = meta.getColumnLabel(i);
                if (label == null || label.isBlank()) {
                    label = meta.getColumnName(i);
                }
                // 结果集元数据没有「注释」这一项，源侧注释留空（目标侧才有 REMARKS）
                columns.add(new SyncColumnVO(label, safe(meta.getColumnTypeName(i)), ""));
            }
            return columns;
        }
    }

    /**
     * 读取数据库表的列定义。
     *
     * <p>走 {@code DatabaseMetaData.getColumns}：表名允许写成 {@code 库.表}，此时前半段作为
     * catalog 定位，否则用连接的当前 catalog。</p>
     *
     * <p><strong>表不存在时的行为</strong>：MySQL 驱动对不存在的表直接抛
     * {@code SQLException("Table ... doesn't exist")}，让调用方无法区分「表不存在」和
     * 「元数据读取失败」。这里先用 {@code getTables} 预检：<b>表不存在返回空列表</b>，
     * 由调用方决定语义（预览建表场景允许空目标列，执行场景再单独报错），其余异常照抛。</p>
     *
     * @param conn  连接
     * @param table 表名，可带一段 {@code 库.表} 限定
     * @return 列列表（按表定义顺序）；表不存在时返回空列表
     * @throws SQLException 元数据读取失败（非「表不存在」）
     */
    public static List<SyncColumnVO> tableColumns(Connection conn, String table) throws SQLException {
        String catalog = conn.getCatalog();
        String tableName = table;
        int dot = table.indexOf('.');
        if (dot > 0) {
            catalog = table.substring(0, dot);
            tableName = table.substring(dot + 1);
        }
        if (!tableExists(conn, catalog, tableName)) {
            return new ArrayList<>();
        }
        DatabaseMetaData meta = conn.getMetaData();
        List<SyncColumnVO> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(catalog, null, tableName, null)) {
            while (rs.next()) {
                columns.add(new SyncColumnVO(
                        rs.getString("COLUMN_NAME"),
                        safe(rs.getString("TYPE_NAME")),
                        safe(rs.getString("REMARKS"))));
            }
        }
        return columns;
    }

    /**
     * 判断一张表（含 {@code 库.表} 限定）在当前连接里是否存在。
     *
     * <p>走 {@code DatabaseMetaData.getTables}，{@code type=["TABLE"]} 过滤视图；
     * 大小写按驱动默认处理（MySQL 的 information_schema 表名匹配通常大小写不敏感）。</p>
     *
     * @param conn      连接
     * @param catalog   catalog（库名），可为 空 使用连接默认
     * @param tableName 表名（不含库前缀）
     * @return 存在返回 true
     * @throws SQLException 元数据读取失败
     */
    public static boolean tableExists(Connection conn, String catalog, String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(catalog, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    /**
     * 校验并引用目标表名。
     *
     * @param table 表名，可带一段 {@code 库.表} 限定
     * @return 形如 {@code `db`.`tbl`} 的片段
     * @throws IllegalArgumentException 表名含非法字符或超长
     */
    public static String quoteTable(String table) {
        return SqlName.quoteMysql(table, "目标表名");
    }

    /**
     * 校验并引用目标列名。
     *
     * @param column 列名
     * @return 形如 {@code `col`} 的片段
     * @throws IllegalArgumentException 列名含非法字符或超长
     */
    public static String quoteColumn(String column) {
        return SqlName.quoteMysql(column, "目标列名");
    }

    /**
     * 判断语句是否是一条读取用的 SELECT。
     *
     * <p>同步任务只允许读取：写入一律由目标端生成，避免有人把 {@code DELETE} / {@code DROP}
     * 填进源 SQL 把源库改了。这里只认 {@code SELECT} 开头，{@code WITH ... SELECT} 这类 CTE
     * 也被挡下（它也能包裹写语句），需要时把 CTE 封装成视图再同步。</p>
     *
     * @param sql 待判断语句
     * @return 是单条 SELECT 返回 true
     */
    public static boolean isSelect(String sql) {
        if (sql == null) {
            return false;
        }
        String trimmed = sql.stripLeading().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("select");
    }

    /**
     * 抽取列名集合，供映射校验使用。
     *
     * @param columns 列列表
     * @return 列名集合，保持顺序
     */
    public static Set<String> columnNames(List<SyncColumnVO> columns) {
        Set<String> names = new LinkedHashSet<>();
        for (SyncColumnVO column : columns) {
            names.add(column.name());
        }
        return names;
    }

    /**
     * 空值安全的字符串取值。
     *
     * @param value 原值，可为 空
     * @return 原值，为 空 时返回空串
     */
    private static String safe(String value) {
        return value == null ? "" : value;
    }
}