package com.chua.starter.datasourcehub.support.dbconsole;

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
     *
     * @deprecated 类型与属性校验已移到 {@link DbColumnDef}，本常量不再使用。
     */
    @Deprecated
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
     * 新建表（跨方言）。
     *
     * <p>此前数据库管理器的「新建表」是前端拼一整条 {@code CREATE TABLE `x` (...) COMMENT='..'}
     * 发给通用 SQL 执行端点，两处都只对 MySQL 成立：反引号引号在 H2/PostgreSQL 会被拒，
     * 内联 {@code COMMENT} 同样只有 MySQL 认。这里把这两件事收进方言层。</p>
     *
     * <p>列定义 {@code fields} 是用户自由文本（{@code id BIGINT PRIMARY KEY, name VARCHAR(255)}），
     * 原样透传：它已由用户的 SQL 编辑器心智负责，替他解析反而会引入新的歧义。</p>
     *
     * @param conn    数据库连接
     * @param table   表名，需通过 {@link #isSafeIdentifier} 校验
     * @param fields  列定义原文，可为空（空表在部分内核不合法，由内核报错）
     * @param comment 表注释，可为空
     * @throws SQLException 建表失败
     */
    public static void createTable(Connection conn, String table, String fields, String comment)
            throws SQLException {
        SqlName.checkSimple(table, "表名");
        boolean mysql = isMysqlFamily(conn);
        String quotedTable = mysql ? SqlName.quoteMysql(table, "表名") : SqlName.quote(table, "表名");
        String body = fields == null ? "" : fields.trim();
        StringBuilder ddl = new StringBuilder("CREATE TABLE ").append(quotedTable)
                .append(" (").append(body).append(')');
        if (mysql && comment != null && !comment.isEmpty()) {
            ddl.append(" COMMENT='").append(comment.replace("'", "''")).append('\'');
        }
        execute(conn, ddl.toString());
        if (!mysql) {
            applyTableComment(conn, table, comment);
        }
    }

    /**
     * 应用表注释（跨方言）。
     *
     * <p>MySQL 内联在 CREATE TABLE 里，本方法无操作；H2/PostgreSQL/Oracle 走
     * {@code COMMENT ON TABLE}。注释属装饰性信息，失败不阻断建表 ——
     * 表已经建出来了，为一句注释回滚反而更糟。</p>
     *
     * @param conn    数据库连接
     * @param table   表名
     * @param comment 注释；空值表示不写注释
     */    private static void applyTableComment(Connection conn, String table, String comment) {
        if (comment == null || comment.isEmpty()) {
            return;
        }
        String literal = "'" + comment.replace("'", "''") + "'";
        executeFirstQuietly(conn, List.of(
                "COMMENT ON TABLE " + SqlName.quote(table, "表名") + " IS " + literal,
                "COMMENT ON TABLE " + table + " IS " + literal));
    }

    /**
     * 重命名表（跨方言）。
     *
     * <p>此前前端拼 {@code ALTER TABLE \`a\` RENAME TO \`b\`}，反引号只对 MySQL 成立，
     * H2/PostgreSQL 上直接语法错。标准写法 {@code ALTER TABLE a RENAME TO b} 各内核都认，
     * 但引号仍要按内核给。</p>
     *
     * @param conn    数据库连接
     * @param table   原表名，需通过 {@link #isSafeIdentifier} 校验
     * @param newName 新表名，需通过 {@link #isSafeIdentifier} 校验
     * @throws SQLException 重命名失败
     */
    public static void renameTable(Connection conn, String table, String newName) throws SQLException {
        SqlName.checkSimple(table, "表名");
        SqlName.checkSimple(newName, "新表名");
        boolean mysql = isMysqlFamily(conn);
        String from = mysql ? SqlName.quoteMysql(table, "表名") : SqlName.quote(table, "表名");
        String to = mysql ? SqlName.quoteMysql(newName, "新表名") : SqlName.quote(newName, "新表名");
        executeFirstSupported(conn, List.of(
                "ALTER TABLE " + from + " RENAME TO " + to,
                "ALTER TABLE " + from + " RENAME " + to,
                "RENAME TABLE " + from + " TO " + to),
                "重命名表 " + table);
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
     * <p>走 {@link DbColumnDef}：DDL 片段由该类型自己拼，其中每个片段都已过白名单
     * （见 {@link DbColumnDef} 类注释的「安全豁免」）。非 MySQL 系逐项映射到该内核语法，
     * 拿不准的属性（如 ENUM / ZEROFILL）交给内核报错而**不静默丢弃** ——
     * 静默丢弃会让用户以为改成功了。</p>
     *
     * @param conn  数据库连接
     * @param table 表名
     * @param def   完整列定义
     * @throws SQLException 修改失败
     */
    public static void alterColumn(Connection conn, String table, DbColumnDef def) throws SQLException {
        String safeTable = SqlName.checkSimple(table, "表名");
        String safeColumn = SqlName.checkSimple(def.name(), "列名");
        if (isMysqlFamilyQuiet(conn)) {
            execute(conn, "ALTER TABLE " + SqlName.quoteMysql(safeTable, "表名")
                    + " MODIFY COLUMN " + SqlName.quoteMysql(safeColumn, "列名") + " " + def.toSqlFragment());
            return;
        }
        String quotedTable = SqlName.quote(safeTable, "表名");
        String quotedColumn = SqlName.quote(safeColumn, "列名");
        String typeOnly = typeFragment(def);
        executeFirstQuietly(conn, List.of(
                "ALTER TABLE " + quotedTable + " ALTER COLUMN " + quotedColumn + " " + typeOnly,
                "ALTER TABLE " + quotedTable + " ALTER COLUMN " + quotedColumn + " TYPE " + typeOnly,
                "ALTER TABLE " + quotedTable + " MODIFY COLUMN " + quotedColumn + " " + typeOnly));
        executeFirstQuietly(conn, List.of(def.nullable()
                ? "ALTER TABLE " + quotedTable + " ALTER COLUMN " + quotedColumn + " DROP NOT NULL"
                : "ALTER TABLE " + quotedTable + " ALTER COLUMN " + quotedColumn + " SET NOT NULL"));
        if (!def.defaultValue().isEmpty()) {
            executePreparedFirstQuietly(conn, List.of(
                    "ALTER TABLE " + quotedTable + " ALTER COLUMN " + quotedColumn + " SET DEFAULT ?"),
                    List.of(def.defaultValue()));
        } else {
            executeFirstQuietly(conn, List.of(
                    "ALTER TABLE " + quotedTable + " ALTER COLUMN " + quotedColumn + " DROP DEFAULT"));
        }
        applyColumnComment(conn, safeTable, safeColumn, def.comment());
    }

    /**
     * 只取类型片段（去掉 NULL / DEFAULT / COMMENT 等），供跨内核的类型改写使用。
     *
     * @param def 列定义
     * @return 类型片段
     */
    private static String typeFragment(DbColumnDef def) {
        DbColumnDef typeOnly = new DbColumnDef(
                def.name(), def.type(), def.size(), def.scale(), def.unsigned(), def.zerofill(),
                "", "", def.nullable(), "", "", false, List.of(), false, "");
        return typeOnly.toSqlFragment();
    }

    /**
     * 执行一条 DDL。
     *
     * @param conn 数据库连接
     * @param ddl  DDL 文本
     * @throws SQLException 执行失败
     */
    private static void execute(Connection conn, String ddl) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(ddl);
        }
    }

    /**
     * 新增列（跨方言）。
     *
     * <p>安全豁免同 {@link #alterColumn}：整段 DDL 由 {@link DbColumnDef} 拼装，
     * 每个片段各自过白名单。</p>
     *
     * @param conn  数据库连接
     * @param table 表名
     * @param def   完整列定义
     * @throws SQLException 内核拒绝
     */
    public static void addColumn(Connection conn, String table, DbColumnDef def) throws SQLException {
        String safeTable = SqlName.checkSimple(table, "表名");
        String quotedColumn = SqlName.quoteMysql(SqlName.checkSimple(def.name(), "列名"), "列名");
        if (isMysqlFamilyQuiet(conn)) {
            execute(conn, "ALTER TABLE " + SqlName.quoteMysql(safeTable, "表名")
                    + " ADD COLUMN " + quotedColumn + " " + def.toSqlFragment());
            return;
        }
        executeFirstQuietly(conn, List.of("ALTER TABLE " + SqlName.quote(safeTable, "表名")
                + " ADD COLUMN " + SqlName.quote(def.name(), "列名") + " " + typeFragment(def)));
        if (!def.defaultValue().isEmpty()) {
            executePreparedFirstQuietly(conn, List.of("ALTER TABLE " + SqlName.quote(safeTable, "表名")
                    + " ALTER COLUMN " + SqlName.quote(def.name(), "列名") + " SET DEFAULT ?"),
                    List.of(def.defaultValue()));
        }
        applyColumnComment(conn, safeTable, def.name(), def.comment());
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
