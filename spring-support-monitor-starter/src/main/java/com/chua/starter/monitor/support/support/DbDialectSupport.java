package com.chua.starter.monitor.support.support;

import com.chua.common.support.lang.datasource.dialect.SqlName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 数据库管理器的库级 DDL 支撑。
 *
 * <p>不同内核用不同概念表达"库"：MySQL/MariaDB 用 catalog 且只认 {@code CREATE DATABASE}，
 * H2/PostgreSQL 用 schema 且只认 {@code CREATE SCHEMA}。这里按产品名排候选语句依次尝试，
 * 让上层不必感知内核差异。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class DbDialectSupport {

    /**
     * 列类型定义白名单，只允许类型名、数字、长度参数与逗号。
     */
    private static final Pattern TYPE_DEFINITION = Pattern.compile("[A-Za-z0-9_(), ]{1,128}");

    private DbDialectSupport() {
    }

    /**
     * 校验标识符是否可安全拼进 DDL。
     *
     * @param name 标识符
     * @return true=合法
     */
    public static boolean isSafeIdentifier(String name) {
        return SqlName.isSimple(name);
    }

    /**
     * 新建数据库。
     *
     * @param conn     数据库连接
     * @param name     库名，需通过 {@link #isSafeIdentifier} 校验
     * @param charset  字符集，可为空
     * @param collation 排序规则，可为空
     * @throws SQLException 所有候选语句都失败
     */
    public static void createDatabase(Connection conn, String name, String charset, String collation) throws SQLException {
        SqlName.checkSimple(name, "数据库名");
        boolean mysql = isMysqlFamily(conn);
        List<String> candidates = new ArrayList<>(4);
        String mysqlName = SqlName.quoteMysql(name, "数据库名");
        String plainName = SqlName.quote(name, "数据库名");
        String options = mysqlOptions(charset, collation);
        if (mysql) {
            candidates.add("CREATE DATABASE " + mysqlName + options);
            candidates.add("CREATE SCHEMA " + mysqlName + options);
        } else {
            candidates.add("CREATE SCHEMA " + plainName);
            candidates.add("CREATE DATABASE " + plainName);
        }
        executeFirstSupported(conn, candidates, "创建数据库 " + name);
    }

    /**
     * 删除数据库。
     *
     * @param conn 数据库连接
     * @param name 库名，需通过 {@link #isSafeIdentifier} 校验
     * @throws SQLException 所有候选语句都失败
     */
    public static void dropDatabase(Connection conn, String name) throws SQLException {
        if (!isSafeIdentifier(name)) {
            throw new SQLException("数据库名不合法，拒绝删除");
        }
        // H2 等内核把连接所在的 schema 也当作"库"列出来，删掉它等于清掉运行中的应用自身数据
        if (name.equalsIgnoreCase(conn.getCatalog()) || name.equalsIgnoreCase(conn.getSchema())) {
            throw new SQLException("不能删除当前连接所在的数据库 " + name);
        }
        boolean mysql = isMysqlFamily(conn);
        List<String> candidates = new ArrayList<>(4);
        String mysqlName = SqlName.quoteMysql(name, "数据库名");
        String plainName = SqlName.quote(name, "数据库名");
        if (mysql) {
            candidates.add("DROP DATABASE " + mysqlName);
            candidates.add("DROP SCHEMA " + mysqlName);
        } else {
            candidates.add("DROP SCHEMA " + plainName + " CASCADE");
            candidates.add("DROP DATABASE " + plainName);
        }
        executeFirstSupported(conn, candidates, "删除数据库 " + name);
    }

    /**
     * 拼装 MySQL 系的字符集子句。
     *
     * @param charset   字符集，非法或为空时忽略
     * @param collation 排序规则，非法或为空时忽略
     * @return 结果 形如 {@code  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci}，无有效选项时为空串
     */
    private static String mysqlOptions(String charset, String collation) {
        StringBuilder options = new StringBuilder();
        if (SqlName.isSimple(charset)) {
            options.append(" DEFAULT CHARACTER SET ").append(SqlName.checkSimple(charset, "字符集"));
            if (SqlName.isSimple(collation)) {
                options.append(" COLLATE ").append(SqlName.checkSimple(collation, "排序规则"));
            }
        }
        return options.toString();
    }

    /**
     * 依次执行候选语句，命中即返回。
     *
     * @param conn       数据库连接
     * @param candidates 候选 DDL
     * @param action     失败提示中描述的动作
     * @throws SQLException 全部候选都失败
     */
    private static void executeFirstSupported(Connection conn, List<String> candidates, String action) throws SQLException {
        SQLException first = null;
        for (String candidate : candidates) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(candidate);
                return;
            } catch (SQLException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        throw new SQLException(action + "失败: " + (first == null ? "内核不支持" : first.getMessage()), first);
    }

    /**
     * 修改列定义（跨方言）。
     *
     * <p>安全豁免：表名、列名与类型定义均经白名单校验；默认值和注释属于用户可控值，
     * 全部通过 {@link PreparedStatement} 参数绑定，不拼入 DDL 文本。</p>
     *
     * @param conn         数据库连接
     * @param table        表名
     * @param column       列名
     * @param typeDef      仅类型定义（如 {@code VARCHAR(80)}）
     * @param nullable     是否可空
     * @param defaultValue 默认值，可为空
     * @param comment      注释，可为空
     */
    public static void alterColumn(Connection conn, String table, String column,
                                   String typeDef, boolean nullable, String defaultValue, String comment) {
        String safeTable = SqlName.checkSimple(table, "表名");
        String safeColumn = SqlName.checkSimple(column, "列名");
        String safeType = checkTypeDefinition(typeDef);
        if (isMysqlFamilyQuiet(conn)) {
            StringBuilder ddl = new StringBuilder("ALTER TABLE ")
                    .append(SqlName.quoteMysql(safeTable, "表名"))
                    .append(" MODIFY COLUMN ")
                    .append(SqlName.quoteMysql(safeColumn, "列名"))
                    .append(' ').append(safeType)
                    .append(nullable ? " NULL" : " NOT NULL");
            List<Object> params = new ArrayList<>(2);
            if (defaultValue != null && !defaultValue.isBlank()) {
                ddl.append(" DEFAULT ?");
                params.add(defaultValue);
            }
            if (comment != null && !comment.isBlank()) {
                ddl.append(" COMMENT ?");
                params.add(comment);
            }
            executePreparedFirstQuietly(conn, List.of(ddl.toString()), params);
            return;
        }
        String quotedTable = SqlName.quote(safeTable, "表名");
        String quotedColumn = SqlName.quote(safeColumn, "列名");
        executeFirstQuietly(conn, List.of(
                "ALTER TABLE " + quotedTable + " ALTER COLUMN " + quotedColumn + " " + safeType,
                "ALTER TABLE " + quotedTable + " ALTER COLUMN " + quotedColumn + " TYPE " + safeType,
                "ALTER TABLE " + quotedTable + " MODIFY COLUMN " + quotedColumn + " " + safeType));
        executeFirstQuietly(conn, List.of(nullable
                ? "ALTER TABLE " + quotedTable + " ALTER COLUMN " + quotedColumn + " DROP NOT NULL"
                : "ALTER TABLE " + quotedTable + " ALTER COLUMN " + quotedColumn + " SET NOT NULL"));
        if (defaultValue != null && !defaultValue.isBlank()) {
            executePreparedFirstQuietly(conn, List.of(
                    "ALTER TABLE " + quotedTable + " ALTER COLUMN " + quotedColumn + " SET DEFAULT ?"),
                    List.of(defaultValue));
        } else {
            executeFirstQuietly(conn, List.of(
                    "ALTER TABLE " + quotedTable + " ALTER COLUMN " + quotedColumn + " DROP DEFAULT"));
        }
        applyColumnComment(conn, safeTable, safeColumn, comment);
    }

    /**
     * 新增列（跨方言）。
     *
     * <p>安全豁免：标识符和类型定义经白名单校验，默认值与注释通过
     * {@link PreparedStatement} 参数绑定。</p>
     *
     * @param conn         数据库连接
     * @param table        表名
     * @param column       列名
     * @param typeDef      类型定义
     * @param nullable     是否可空
     * @param defaultValue 默认值，可为空
     * @param comment      注释，可为空
     * @throws SQLException 所有候选 DDL 都失败
     */
    public static void addColumn(Connection conn, String table, String column, String typeDef,
                                 boolean nullable, String defaultValue, String comment) throws SQLException {
        String safeTable = SqlName.checkSimple(table, "表名");
        String safeColumn = SqlName.checkSimple(column, "列名");
        String safeType = checkTypeDefinition(typeDef);
        StringBuilder ddl = new StringBuilder("ALTER TABLE ");
        if (isMysqlFamily(conn)) {
            ddl.append(SqlName.quoteMysql(safeTable, "表名"))
                    .append(" ADD COLUMN ")
                    .append(SqlName.quoteMysql(safeColumn, "列名"));
        } else {
            ddl.append(SqlName.quote(safeTable, "表名"))
                    .append(" ADD COLUMN ")
                    .append(SqlName.quote(safeColumn, "列名"));
        }
        ddl.append(' ').append(safeType).append(nullable ? " NULL" : " NOT NULL");
        List<Object> params = new ArrayList<>(2);
        if (defaultValue != null && !defaultValue.isBlank()) {
            ddl.append(" DEFAULT ?");
            params.add(defaultValue);
        }
        if (comment != null && !comment.isBlank() && isMysqlFamily(conn)) {
            ddl.append(" COMMENT ?");
            params.add(comment);
        }
        executePreparedFirst(conn, List.of(ddl.toString()), params);
        applyColumnComment(conn, safeTable, safeColumn, comment);
    }

    private static String checkTypeDefinition(String typeDef) {
        if (typeDef == null || !TYPE_DEFINITION.matcher(typeDef).matches()) {
            throw new IllegalArgumentException("列类型定义不合法: " + typeDef);
        }
        return typeDef;
    }

    /**
     * 是否 MySQL 系内核（元数据读取失败时按非 MySQL 处理）。
     *
     * @param conn 数据库连接
     * @return true=MySQL 系
     */
    private static boolean isMysqlFamilyQuiet(Connection conn) {
        try {
            return isMysqlFamily(conn);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * 依次尝试候选 DDL，命中即返回。
     *
     * @param conn       数据库连接
     * @param candidates 候选 DDL
     */
    private static void executeFirstQuietly(Connection conn, List<String> candidates) {
        for (String candidate : candidates) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(candidate);
                return;
            } catch (SQLException ignored) {
                // 换下一种写法
            }
        }
    }

    private static void executePreparedFirstQuietly(Connection conn, List<String> candidates, List<Object> params) {
        try {
            executePreparedFirst(conn, candidates, params);
        } catch (SQLException ignored) {
            // 由调用方复核实际结果
        }
    }

    private static void executePreparedFirst(Connection conn, List<String> candidates, List<Object> params)
            throws SQLException {
        SQLException first = null;
        for (String candidate : candidates) {
            try (PreparedStatement statement = conn.prepareStatement(candidate)) {
                for (int index = 0; index < params.size(); index++) {
                    statement.setObject(index + 1, params.get(index));
                }
                statement.executeUpdate();
                return;
            } catch (SQLException ex) {
                if (first == null) {
                    first = ex;
                }
            }
        }
        throw first == null ? new SQLException("没有可执行的 DDL") : first;
    }

    /**
     * 应用/清除列注释（跨方言）。
     *
     * <p>MySQL 系内核的注释写在 {@code ADD/MODIFY COLUMN} 的 {@code COMMENT '...'} 子句里，
     * 本方法对其无操作；H2/PostgreSQL/Oracle 等内核该子句不生效，需补一条
     * {@code COMMENT ON COLUMN}。注释属装饰性信息，失败不阻断列结构变更。</p>
     *
     * @param conn    数据库连接
     * @param table   表名
     * @param column  列名
     * @param comment 注释，空串表示清除
     */
    public static void applyColumnComment(Connection conn, String table, String column, String comment) {
        try {
            if (isMysqlFamily(conn)) {
                return;
            }
        } catch (SQLException e) {
            return;
        }
        String safeTable = SqlName.checkSimple(table, "表名");
        String safeColumn = SqlName.checkSimple(column, "列名");
        String quoted = SqlName.quote(safeTable, "表名") + "." + SqlName.quote(safeColumn, "列名");
        if (comment == null || comment.isEmpty()) {
            executeFirstQuietly(conn, List.of("COMMENT ON COLUMN " + quoted + " IS NULL"));
            return;
        }
        executePreparedFirstQuietly(conn, List.of("COMMENT ON COLUMN " + quoted + " IS ?"), List.of(comment));
    }

    /**
     * 判断是否 MySQL 系内核。
     *
     * @param conn 数据库连接
     * @return true=MySQL/MariaDB/OceanBase 一类
     * @throws SQLException 读取元数据失败
     */
    private static boolean isMysqlFamily(Connection conn) throws SQLException {
        String product = conn.getMetaData().getDatabaseProductName();
        if (product == null) {
            return false;
        }
        String lower = product.toLowerCase(Locale.ROOT);
        return lower.contains("mysql") || lower.contains("mariadb") || lower.contains("oceanbase");
    }
}
