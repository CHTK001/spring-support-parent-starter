package com.chua.starter.datasourcehub.support.support;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * JDBC 跨方言支撑：库切换、库/表/列存在性、库 DDL 与列定义变更、列注释。
 *
 * <p>不同内核概念差异较大：</p>
 * <ul>
 *   <li>库：MySQL 用 catalog 且只认 {@code CREATE DATABASE}；H2/PostgreSQL 用 schema 且只认 {@code CREATE SCHEMA}；</li>
 *   <li>切库：MySQL 的 {@code setCatalog} 不真正切服务端会话库，必须 {@code USE}；H2/PG 用 {@code setSchema}；</li>
 *   <li>改列：MySQL 用 {@code MODIFY COLUMN}；H2/PG 需拆 {@code ALTER COLUMN} + {@code SET/DROP NOT NULL/DEFAULT}；</li>
 *   <li>注释：MySQL 内联 {@code COMMENT}；H2/PG/Oracle 需 {@code COMMENT ON COLUMN}。</li>
 * </ul>
 * 统一封装，供数据源中心与数据库管理器复用。
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class JdbcDialectSupport {

    /**
     * 合法标识符：库/字符集/排序规则等都是拼进 DDL 的裸标识符
     */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    /**
     * 元数据里算作"表"的对象类型：视图也可选，其它（系统表、索引等）一律排除。
     */
    private static final String[] TABLE_TYPES = {"TABLE", "VIEW"};

    private JdbcDialectSupport() {
    }

    /**
     * 校验标识符是否可安全拼进 DDL。
     *
     * @param name 标识符
     * @return true=合法
     */
    public static boolean isSafeIdentifier(String name) {
        return name != null && IDENTIFIER.matcher(name).matches();
    }

    /**
     * 是否 MySQL 系内核（元数据读取失败时按非 MySQL 处理）。
     *
     * @param conn 数据库连接
     * @return true=MySQL/MariaDB/OceanBase 一类
     */
    public static boolean isMysqlFamily(Connection conn) {
        try {
            String product = conn.getMetaData().getDatabaseProductName();
            if (product == null) {
                return false;
            }
            String lower = product.toLowerCase(Locale.ROOT);
            return lower.contains("mysql") || lower.contains("mariadb") || lower.contains("oceanbase");
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * 读取连接当前所在库（权威值）。
     *
     * <p>MySQL 系用 {@code SELECT DATABASE()}：Connector/J 的 {@code getCatalog()} 只反映驱动内部字段，
     * {@code setCatalog()} 不切服务端会话库；其余内核用 {@code getSchema()} 再退回 {@code getCatalog()}。</p>
     *
     * @param conn 数据库连接
     * @return 当前库名，读取失败返回 null
     */
    public static String currentDatabase(Connection conn) {
        if (isMysqlFamily(conn)) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT DATABASE()")) {
                if (rs.next()) {
                    String value = rs.getString(1);
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            } catch (SQLException ignored) {
                // 退回元数据读取
            }
        }
        try {
            String schema = conn.getSchema();
            if (schema != null && !schema.isBlank()) {
                return schema;
            }
        } catch (SQLException ignored) {
            // 驱动不支持 schema，退回 catalog
        }
        try {
            return conn.getCatalog();
        } catch (SQLException ignored) {
            return null;
        }
    }

    /**
     * 切换连接所在库（跨方言）。
     *
     * <p>MySQL 系：{@code setCatalog}（供元数据定位库）+ {@code USE}（真正切会话），两者都要；
     * H2/PostgreSQL：{@code setSchema}，失败退回 {@code setCatalog}。</p>
     *
     * @param conn 数据库连接
     * @param db   目标库名
     * @throws SQLException 切换失败
     */
    public static void switchDatabase(Connection conn, String db) throws SQLException {
        if (isMysqlFamily(conn)) {
            try {
                conn.setCatalog(db);
            } catch (SQLException ignored) {
                // 驱动不支持 setCatalog，元数据退化为按会话默认库
            }
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("USE `" + db.replace("`", "``") + "`");
            }
            return;
        }
        SQLException first = null;
        try {
            conn.setSchema(db);
            return;
        } catch (SQLException e) {
            first = e;
        }
        try {
            conn.setCatalog(db);
        } catch (SQLException e) {
            throw first;
        }
    }

    /**
     * 判断库是否存在。
     *
     * <p>必须显式校验：部分驱动（如 H2）接受任意 catalog 值而不报错，
     * 只比对切换结果会让写错的库名静默落在当前库上执行。</p>
     *
     * @param conn     数据库连接
     * @param database 库名
     * @return 存在返回 true
     * @throws SQLException 读取元数据失败
     */
    public static boolean databaseExists(Connection conn, String database) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getSchemas()) {
            while (rs.next()) {
                if (database.equalsIgnoreCase(rs.getString(1))) {
                    return true;
                }
            }
        } catch (SQLException ignored) {
            // 驱动不支持 schema 视图，继续用 catalog 判断
        }
        try (ResultSet rs = conn.getMetaData().getCatalogs()) {
            while (rs.next()) {
                if (database.equalsIgnoreCase(rs.getString(1))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 新建数据库。
     *
     * @param conn      数据库连接
     * @param name      库名
     * @param charset   字符集，可为空
     * @param collation 排序规则，可为空
     * @throws SQLException 所有候选语句都失败
     */
    public static void createDatabase(Connection conn, String name, String charset, String collation) throws SQLException {
        boolean mysql = isMysqlFamily(conn);
        List<String> candidates = new ArrayList<>(4);
        String mysqlName = "`" + name + "`";
        String plainName = "\"" + name + "\"";
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
     * @param name 库名
     * @throws SQLException 所有候选语句都失败
     */
    public static void dropDatabase(Connection conn, String name) throws SQLException {
        if (!isSafeIdentifier(name)) {
            throw new SQLException("数据库名不合法，拒绝删除");
        }
        if (name.equalsIgnoreCase(conn.getCatalog()) || name.equalsIgnoreCase(conn.getSchema())) {
            throw new SQLException("不能删除当前连接所在的数据库 " + name);
        }
        boolean mysql = isMysqlFamily(conn);
        List<String> candidates = new ArrayList<>(4);
        String mysqlName = "`" + name + "`";
        String plainName = "\"" + name + "\"";
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
     * 修改列定义（跨方言）。
     *
     * <p>MySQL 系用 {@code MODIFY COLUMN <完整定义>}；H2/PostgreSQL 等拆成
     * {@code ALTER COLUMN <类型>} + {@code SET/DROP NOT NULL} + {@code SET/DROP DEFAULT} + 列注释。</p>
     *
     * @param conn         数据库连接
     * @param table        表名
     * @param column       列名
     * @param typeDef      仅类型定义（如 {@code VARCHAR(80)}）
     * @param mysqlDef     MySQL 完整列定义
     * @param nullable     是否可空
     * @param defaultValue 默认值
     * @param comment      注释
     */
    public static void alterColumn(Connection conn, String table, String column,
                                   String typeDef, String mysqlDef,
                                   boolean nullable, String defaultValue, String comment) {
        if (isMysqlFamily(conn)) {
            executeQuietly(conn, "ALTER TABLE `" + table + "` MODIFY COLUMN `" + column + "` " + mysqlDef);
            return;
        }
        executeFirstQuietly(conn, List.of(
                "ALTER TABLE \"" + table + "\" ALTER COLUMN \"" + column + "\" " + typeDef,
                "ALTER TABLE \"" + table + "\" ALTER COLUMN \"" + column + "\" TYPE " + typeDef,
                "ALTER TABLE \"" + table + "\" MODIFY COLUMN \"" + column + "\" " + typeDef));
        executeFirstQuietly(conn, List.of(nullable
                ? "ALTER TABLE \"" + table + "\" ALTER COLUMN \"" + column + "\" DROP NOT NULL"
                : "ALTER TABLE \"" + table + "\" ALTER COLUMN \"" + column + "\" SET NOT NULL"));
        if (defaultValue != null && !defaultValue.isBlank()) {
            executeFirstQuietly(conn, List.of("ALTER TABLE \"" + table + "\" ALTER COLUMN \"" + column
                    + "\" SET DEFAULT '" + defaultValue.replace("'", "''") + "'"));
        } else {
            executeFirstQuietly(conn, List.of("ALTER TABLE \"" + table + "\" ALTER COLUMN \"" + column + "\" DROP DEFAULT"));
        }
        applyColumnComment(conn, table, column, comment);
    }

    /**
     * 应用/清除列注释（跨方言）。
     *
     * <p>MySQL 系内联在列定义中，本方法无操作；H2/PostgreSQL/Oracle 需 {@code COMMENT ON COLUMN}。
     * 注释属装饰性信息，失败不阻断列结构变更。</p>
     *
     * @param conn    数据库连接
     * @param table   表名
     * @param column  列名
     * @param comment 注释
     */
    public static void applyColumnComment(Connection conn, String table, String column, String comment) {
        if (isMysqlFamily(conn)) {
            return;
        }
        String literal = comment == null || comment.isEmpty() ? "NULL" : "'" + comment.replace("'", "''") + "'";
        executeFirstQuietly(conn, List.of(
                "COMMENT ON COLUMN \"" + table + "\".\"" + column + "\" IS " + literal,
                "COMMENT ON COLUMN " + table + "." + column + " IS " + literal));
    }

    /**
     * 应用/清除表注释（跨方言）。
     *
     * <p>与 {@link #applyColumnComment} 对称：MySQL 系把表注释内联在
     * {@code CREATE TABLE} 尾部，本方法无操作；H2/PostgreSQL/Oracle 需 {@code COMMENT ON TABLE}。
     * 注释属装饰性信息，失败不阻断建表。</p>
     *
     * <p>此前表注释只在 MySQL 分支内联，导致 H2/PG/Oracle 上建表请求带了注释
     * 却静默丢失——表下拉因此拿不到中文名，只能回退显示真实表名。</p>
     *
     * @param conn    数据库连接
     * @param table   表名
     * @param comment 注释；空值表示清除
     */
    public static void applyTableComment(Connection conn, String table, String comment) {
        if (isMysqlFamily(conn)) {
            return;
        }
        String literal = comment == null || comment.isEmpty() ? "NULL" : "'" + comment.replace("'", "''") + "'";
        executeFirstQuietly(conn, List.of(
                "COMMENT ON TABLE " + quote(conn, table) + " IS " + literal,
                "COMMENT ON TABLE " + table + " IS " + literal));
    }

    /**
     * 生成表注释 DDL 文本（不执行）。
     *
     * <p>MySQL 系把表注释内联在 CREATE TABLE 里、或本次不带注释时返回 {@code null}
     * （表示没有额外语句）；其余内核返回待执行的 {@code COMMENT ON TABLE}。</p>
     *
     * <p>单独暴露是为了让预览与执行看到同一份语句清单 —— 预览只给 CREATE TABLE
     * 的话，用户会以为表注释没生效。</p>
     *
     * @param conn    数据库连接
     * @param table   表名
     * @param comment 表注释，可为空
     * @return 待执行的注释语句；无额外语句时返回 {@code null}
     */
    public static String tableCommentSql(Connection conn, String table, String comment) {
        if (isMysqlFamily(conn) || comment == null || comment.isEmpty()) {
            return null;
        }
        return "COMMENT ON TABLE " + quote(conn, table) + " IS '" + comment.replace("'", "''") + "'";
    }

    /**
     * 判断表是否存在。
     *
     * <p>按原样与全大写各查一次：MySQL 保留创建时大小写，H2 默认把未加引号的标识符转大写，
     * 只查一种写法会把存在表判成不存在。</p>
     *
     * @param conn  数据库连接
     * @param table 表名
     * @return 存在返回 true
     * @throws SQLException 读取元数据失败
     */
    public static boolean tableExists(Connection conn, String table) throws SQLException {
        if (!isSafeIdentifier(table)) {
            return false;
        }
        for (String candidate : new String[]{table, table.toUpperCase(Locale.ROOT)}) {
            try (ResultSet rs = conn.getMetaData().getTables(conn.getCatalog(), null, candidate, TABLE_TYPES)) {
                if (rs.next()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 读取已存在的列名（小写集合），表不存在返回空集合。
     *
     * @param conn  数据库连接
     * @param table 表名
     * @return 小写列名集合
     * @throws SQLException 读取元数据失败
     */
    public static Set<String> columnNames(Connection conn, String table) throws SQLException {
        Set<String> names = new HashSet<>();
        if (!isSafeIdentifier(table)) {
            return names;
        }
        for (String candidate : new String[]{table, table.toUpperCase(Locale.ROOT)}) {
            try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, candidate, null)) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    if (name != null && !name.isBlank()) {
                        names.add(name.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        return names;
    }

    /**
     * 按方言给标识符加引用符。
     *
     * @param conn       数据库连接
     * @param identifier 标识符（调用方保证已通过 {@link #isSafeIdentifier}）
     * @return 加引用符后的名字
     */
    public static String quote(Connection conn, String identifier) {
        if (isMysqlFamily(conn)) {
            return "`" + identifier + "`";
        }
        return "\"" + identifier + "\"";
    }

    /**
     * 建表（跨方言）。
     *
     * <p>MySQL 系把注释与默认值内联在列定义里；其余内核先建表再逐列补 COMMENT，
     * 表注释同样走 {@code COMMENT ON TABLE}。列名/表名
     * 必须已通过 {@link #isSafeIdentifier}，值不参与拼接（DDL 里只有类型与注释）。</p>
     *
     * @param conn        数据库连接
     * @param table       表名
     * @param columns     列定义
     * @param primaryKey  主键列名，可为空
     * @param comment     表注释，可为空
     * @throws SQLException 建表失败
     */
    public static void createTable(Connection conn, String table, List<ColumnDef> columns,
                                   List<String> primaryKey, String comment) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate(createTableSql(conn, table, columns, primaryKey, comment));
        }
        if (!isMysqlFamily(conn)) {
            for (ColumnDef column : columns) {
                applyColumnComment(conn, table, column.name(), column.comment());
            }
            applyTableComment(conn, table, comment);
        }
    }

    /**
     * 生成建表 DDL 文本（不执行）。
     *
     * <p>预览与执行共用同一份拼装逻辑，避免"预览看到的"和"实际执行的"不一致。</p>
     *
     * @param conn       数据库连接
     * @param table      表名
     * @param columns    列定义
     * @param primaryKey 主键列名，可为空
     * @param comment    表注释，可为空
     * @return CREATE TABLE 语句
     */
    public static String createTableSql(Connection conn, String table, List<ColumnDef> columns,
                                        List<String> primaryKey, String comment) {
        boolean mysql = isMysqlFamily(conn);
        StringBuilder ddl = new StringBuilder("CREATE TABLE ").append(quote(conn, table)).append(" (");
        for (int index = 0; index < columns.size(); index++) {
            ColumnDef column = columns.get(index);
            if (index > 0) {
                ddl.append(", ");
            }
            ddl.append(quote(conn, column.name())).append(' ').append(column.type());
            if (!column.nullable()) {
                ddl.append(" NOT NULL");
            }
            if (column.defaultValue() != null && !column.defaultValue().isBlank()) {
                ddl.append(" DEFAULT '").append(column.defaultValue().replace("'", "''")).append('\'');
            }
            if (mysql && column.comment() != null && !column.comment().isEmpty()) {
                ddl.append(" COMMENT '").append(column.comment().replace("'", "''")).append('\'');
            }
        }
        if (primaryKey != null && !primaryKey.isEmpty()) {
            List<String> quoted = new ArrayList<>(primaryKey.size());
            for (String key : primaryKey) {
                quoted.add(quote(conn, key));
            }
            ddl.append(", PRIMARY KEY (").append(String.join(", ", quoted)).append(')');
        }
        ddl.append(')');
        if (mysql && comment != null && !comment.isEmpty()) {
            ddl.append(" COMMENT='").append(comment.replace("'", "''")).append('\'');
        }
        return ddl.toString();
    }

    /**
     * 追加一列（跨方言），列已存在时忽略。
     *
     * @param conn    数据库连接
     * @param table   表名
     * @param column  列定义
     * @throws SQLException 追加失败
     */
    public static void addColumn(Connection conn, String table, ColumnDef column) throws SQLException {
        if (columnNames(conn, table).contains(column.name().toLowerCase(Locale.ROOT))) {
            return;
        }
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate(addColumnSql(conn, table, column));
        }
        if (!isMysqlFamily(conn)) {
            applyColumnComment(conn, table, column.name(), column.comment());
        }
    }

    /**
     * 生成追加列的 DDL 文本（不执行，也不判断列是否已存在）。
     *
     * @param conn   数据库连接
     * @param table  表名
     * @param column 列定义
     * @return ALTER TABLE ... ADD COLUMN 语句
     */
    public static String addColumnSql(Connection conn, String table, ColumnDef column) {
        StringBuilder ddl = new StringBuilder("ALTER TABLE ").append(quote(conn, table))
                .append(" ADD COLUMN ").append(quote(conn, column.name())).append(' ').append(column.type());
        if (!column.nullable()) {
            ddl.append(" NOT NULL");
        }
        if (column.defaultValue() != null && !column.defaultValue().isBlank()) {
            ddl.append(" DEFAULT '").append(column.defaultValue().replace("'", "''")).append('\'');
        }
        if (isMysqlFamily(conn) && column.comment() != null && !column.comment().isEmpty()) {
            ddl.append(" COMMENT '").append(column.comment().replace("'", "''")).append('\'');
        }
        return ddl.toString();
    }

    /**
     * 建表用的列定义。
     *
     * <p>类型由调用方按方言拼好（如 {@code VARCHAR(255)} / {@code BIGINT}），这里只做拼装，
     * 不参与类型推断。</p>
     *
     * @param name         列名
     * @param type         列类型（含长度）
     * @param nullable     是否可空
     * @param comment      列注释，可为空
     * @param defaultValue 默认值，可为空
     * @author CH
     * @since 4.0.0.42
     */
    public record ColumnDef(String name, String type, boolean nullable, String comment, String defaultValue) {

        /**
         * 可空无默认值的简化构造。
         *
         * @param name    列名
         * @param type    列类型
         * @param comment 列注释
         * @return 列定义
         */
        public static ColumnDef of(String name, String type, String comment) {
            return new ColumnDef(name, type, true, comment, null);
        }
    }

    /**
     * 拼装 MySQL 系的字符集子句。
     *
     * @param charset   字符集
     * @param collation 排序规则
     * @return 结果，无有效选项时为空串
     */
    private static String mysqlOptions(String charset, String collation) {
        StringBuilder options = new StringBuilder();
        if (isSafeIdentifier(charset)) {
            options.append(" DEFAULT CHARACTER SET ").append(charset);
            if (isSafeIdentifier(collation)) {
                options.append(" COLLATE ").append(collation);
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
     * 执行单条 DDL，失败忽略（调用方以元数据复核结果）。
     *
     * @param conn 数据库连接
     * @param ddl  DDL
     */
    private static void executeQuietly(Connection conn, String ddl) {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(ddl);
        } catch (SQLException ignored) {
            // 由调用方复核实际结果
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
}