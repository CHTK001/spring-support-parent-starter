package com.chua.starter.datasourcehub.support.dbconsole;

import com.chua.common.support.lang.datasource.dialect.SqlName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据库对象（存储过程 / 函数 / 触发器 / 事件）与外键的库级支撑。
 *
 * <p>清单走 {@code information_schema}（跨库都有），定义走 {@code SHOW CREATE *}：
 * 优先 {@code SHOW CREATE}，不可用时回落到 {@code information_schema} 里的
 * {@code ROUTINE_DEFINITION / ACTION_STATEMENT / EVENT_DEFINITION}。
 * 这与 {@link DbDialectSupport} 的「按产品名排候选语句依次尝试」是同一套降级思路。</p>
 *
 * <p><b>安全豁免</b>：对象名、库名、表名、列名、索引名全部经 {@link SqlName} 白名单校验
 * 并用 {@link SqlName#quoteMysql} 引用。<b>唯独对象定义（CREATE PROCEDURE/FUNCTION/TRIGGER/EVENT
 * 的整段 SQL）是逐字执行的</b>——这是该功能的固有语义，与已有的 {@code POST /db/execute}
 * 同一权限级别（能调 {@code /db/execute} 的人本来就能执行任意 DDL），
 * 不构成新的越权面。因此本类不做任何字符串拼接式 DDL 构造。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public final class DbObjectDialect {

    /**
     * 对象类别。
     *
     * <p>列表取值与 {@code information_schema} 里的列名对应，避免散落的字符串比较。</p>
     */
    public enum Category {
        /** 存储过程 */
        PROCEDURE("PROCEDURE"),
        /** 函数 */
        FUNCTION("FUNCTION"),
        /** 触发器 */
        TRIGGER("TRIGGER"),
        /** 事件（定时任务） */
        EVENT("EVENT");

        private final String label;

        /**
         * Category
         * @param label 字符串
         * @return 返回值
        */
        Category(String label) {
            this.label = label;
        }

        /**
         * 展示用中文名。
         *
         * @return 中文名
         */
        public String label() {
            return label;
        }

        /**
         * 解析类别名。
         *
         * @param value 类别名，大小写不敏感
         * @return 类别
         * @throws IllegalArgumentException 类别不认识
         */
        public static Category of(String value) {
            String key = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            for (Category category : values()) {
                if (category.name().equals(key)) {
                    return category;
                }
            }
            throw new IllegalArgumentException("不支持的对象类别: " + value);
        }
    }

    private DbObjectDialect() {
    }

    // ==================== 列表 ====================

    /**
     * 列出例程（存储过程 / 函数）。
     *
     * @param conn   数据库连接
     * @param schema 所属库
     * @param type   类别
     * @return 例程清单，键为「名称 / 语言 / 返回值 / 定义者 / 创建时间 / 修改时间」
     * @throws SQLException 读取元数据失败
     */
    public static List<Map<String, Object>> listRoutines(Connection conn, String schema, Category type)
            throws SQLException {
        String sql = "SELECT ROUTINE_NAME, ROUTINE_TYPE, DTD_IDENTIFIER, ROUTINE_DEFINITION,"
                + " SQL_DATA_ACCESS, IS_DETERMINISTIC, SECURITY_TYPE, DEFINER, CREATED, LAST_ALTERED,"
                + " ROUTINE_COMMENT FROM information_schema.ROUTINES"
                + " WHERE ROUTINE_SCHEMA = ? AND ROUTINE_TYPE = ? ORDER BY ROUTINE_NAME";
        List<Map<String, Object>> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", rs.getString("ROUTINE_NAME"));
                    row.put("category", type.name());
                    row.put("returns", rs.getString("DTD_IDENTIFIER"));
                    row.put("dataAccess", rs.getString("SQL_DATA_ACCESS"));
                    row.put("deterministic", "YES".equalsIgnoreCase(rs.getString("IS_DETERMINISTIC")));
                    row.put("security", rs.getString("SECURITY_TYPE"));
                    row.put("definer", rs.getString("DEFINER"));
                    row.put("created", rs.getString("CREATED"));
                    row.put("modified", rs.getString("LAST_ALTERED"));
                    row.put("comment", rs.getString("ROUTINE_COMMENT"));
                    list.add(row);
                }
            }
        }
        return list;
    }

    /**
     * 列出触发器。
     *
     * @param conn   数据库连接
     * @param schema 所属库
     * @return 触发器清单
     * @throws SQLException 读取元数据失败
     */
    public static List<Map<String, Object>> listTriggers(Connection conn, String schema) throws SQLException {
        String sql = "SELECT TRIGGER_NAME, EVENT_MANIPULATION, EVENT_OBJECT_TABLE, ACTION_TIMING,"
                + " ACTION_STATEMENT, ACTION_ORIENTATION, ACTION_CONDITION, DEFINER, CREATED,"
                + " CHARACTER_SET_CLIENT, DATABASE_COLLATION FROM information_schema.TRIGGERS"
                + " WHERE TRIGGER_SCHEMA = ? ORDER BY EVENT_OBJECT_TABLE, ACTION_TIMING, TRIGGER_NAME";
        List<Map<String, Object>> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", rs.getString("TRIGGER_NAME"));
                    row.put("category", Category.TRIGGER.name());
                    row.put("table", rs.getString("EVENT_OBJECT_TABLE"));
                    row.put("timing", rs.getString("ACTION_TIMING"));
                    row.put("event", rs.getString("EVENT_MANIPULATION"));
                    row.put("definer", rs.getString("DEFINER"));
                    row.put("created", rs.getString("CREATED"));
                    row.put("charset", rs.getString("CHARACTER_SET_CLIENT"));
                    row.put("collation", rs.getString("DATABASE_COLLATION"));
                    list.add(row);
                }
            }
        }
        return list;
    }

    /**
     * 列出事件。
     *
     * @param conn   数据库连接
     * @param schema 所属库
     * @return 事件清单
     * @throws SQLException 读取元数据失败
     */
    public static List<Map<String, Object>> listEvents(Connection conn, String schema) throws SQLException {
        String sql = "SELECT EVENT_NAME, STATUS, EVENT_DEFINITION, EXECUTE_AT, INTERVAL_VALUE,"
                + " INTERVAL_FIELD, STARTS, ENDS, ON_COMPLETION, DEFINER, CREATED, LAST_ALTERED"
                + " FROM information_schema.EVENTS WHERE EVENT_SCHEMA = ? ORDER BY EVENT_NAME";
        List<Map<String, Object>> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", rs.getString("EVENT_NAME"));
                    row.put("category", Category.EVENT.name());
                    row.put("status", rs.getString("STATUS"));
                    row.put("starts", rs.getString("STARTS"));
                    row.put("ends", rs.getString("ENDS"));
                    row.put("onCompletion", rs.getString("ON_COMPLETION"));
                    row.put("definer", rs.getString("DEFINER"));
                    row.put("created", rs.getString("CREATED"));
                    row.put("modified", rs.getString("LAST_ALTERED"));
                    list.add(row);
                }
            }
        }
        return list;
    }

    /**
     * 按类别列出对象。
     *
     * @param conn   数据库连接
     * @param schema 所属库
     * @param type   类别
     * @return 对象清单
     * @throws SQLException 读取失败
     */
    public static List<Map<String, Object>> list(Connection conn, String schema, Category type)
            throws SQLException {
        return switch (type) {
            case PROCEDURE, FUNCTION -> listRoutines(conn, schema, type);
            case TRIGGER -> listTriggers(conn, schema);
            case EVENT -> listEvents(conn, schema);
        };
    }

    // ==================== 定义 ====================

    /**
     * 读取对象定义。
     *
     * <p>优先 {@code SHOW CREATE *}（能拿到含 DEFINER / 字符集 / 排序规则的完整语句），
     * 失败时回落到 {@code information_schema} 的定义列。</p>
     *
     * @param conn   数据库连接
     * @param schema 所属库
     * @param name   对象名
     * @param type   类别
     * @return 定义文本
     * @throws SQLException 对象不存在或读取失败
     */
    public static String definition(Connection conn, String schema, String name, Category type)
            throws SQLException {
        String safeSchema = SqlName.checkSimple(schema, "库名");
        String safeName = SqlName.checkSimple(name, "对象名");
        String quoted = SqlName.quoteMysql(safeName, "对象名");
        String show = switch (type) {
            case PROCEDURE -> "SHOW CREATE PROCEDURE " + SqlName.quoteMysql(safeSchema, "库名") + "." + quoted;
            case FUNCTION -> "SHOW CREATE FUNCTION " + SqlName.quoteMysql(safeSchema, "库名") + "." + quoted;
            case TRIGGER -> "SHOW CREATE TRIGGER " + SqlName.quoteMysql(safeSchema, "库名") + "." + quoted;
            case EVENT -> "SHOW CREATE EVENT " + SqlName.quoteMysql(safeSchema, "库名") + "." + quoted;
        };
        String viaShow = tryShowCreate(conn, show);
        if (viaShow != null) {
            return viaShow;
        }
        String viaMeta = definitionFromMeta(conn, schema, name, type);
        if (viaMeta != null && !viaMeta.isBlank()) {
            return viaMeta;
        }
        throw new SQLException("读取不到定义: " + name);
    }

    /**
     * 尝试用 {@code SHOW CREATE} 取定义。
     *
     * <p><b>判定必须用「以 CREATE 开头」，不能用「包含 CREATE」。</b>
     * {@code SHOW CREATE PROCEDURE} 的列依次是「名称 / sql_mode / 建库语句 / 字符集 / 排序规则」，
     * 按「包含 CREATE」会命中相邻列 —— 实测取回来的是
     * {@code STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,...} 这段 sql_mode。</p>
     *
     * @param conn 数据库连接
     * @param sql  SHOW 语句
     * @return 定义文本；失败返回 null
     */
    private static String tryShowCreate(Connection conn, String sql) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                int count = rs.getMetaData().getColumnCount();
                // 先按列名精确取（PROCEDURE / FUNCTION / TRIGGER / EVENT / Create …）
                for (int i = 1; i <= count; i++) {
                    String label = rs.getMetaData().getColumnLabel(i);
                    if (label != null && label.toUpperCase(Locale.ROOT).startsWith("CREATE")) {
                        Object value = rs.getObject(i);
                        if (value instanceof String text) {
                            return text;
                        }
                    }
                }
                // 列名不可用时退回「去空白后以 CREATE 开头」
                for (int i = 1; i <= count; i++) {
                    Object value = rs.getObject(i);
                    if (value instanceof String text
                            && text.strip().toUpperCase(Locale.ROOT).startsWith("CREATE")) {
                        return text;
                    }
                }
            }
        } catch (SQLException ignored) {
            // 非 MySQL 系内核或对象不存在，走元数据回落
        }
        return null;
    }

    private static String definitionFromMeta(Connection conn, String schema, String name, Category type)
            throws SQLException {
        String sql = switch (type) {
            case PROCEDURE, FUNCTION -> "SELECT ROUTINE_DEFINITION FROM information_schema.ROUTINES"
                    + " WHERE ROUTINE_SCHEMA = ? AND ROUTINE_NAME = ?";
            case TRIGGER -> "SELECT ACTION_STATEMENT FROM information_schema.TRIGGERS"
                    + " WHERE TRIGGER_SCHEMA = ? AND TRIGGER_NAME = ?";
            case EVENT -> "SELECT EVENT_DEFINITION FROM information_schema.EVENTS"
                    + " WHERE EVENT_SCHEMA = ? AND EVENT_NAME = ?";
        };
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    // ==================== 写入 ====================

    /**
     * 执行一段对象定义（CREATE / CREATE OR REPLACE）。
     *
     * <p>整段逐字执行 —— 见类注释的「安全豁免」。</p>
     *
     * @param conn       数据库连接
     * @param definition 完整 DDL
     * @throws SQLException 内核拒绝（权限不足、语法错等原样上抛）
     */
    public static void executeDefinition(Connection conn, String definition) throws SQLException {
        String text = definition == null ? "" : definition.trim();
        if (text.isEmpty()) {
            throw new SQLException("对象定义不能为空");
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(text);
        }
    }

    /**
     * 删除对象。
     *
     * @param conn   数据库连接
     * @param schema 所属库
     * @param name   对象名
     * @param type   类别
     * @param ifExists 对象不存在时是否静默
     * @throws SQLException 删除失败
     */
    public static void drop(Connection conn, String schema, String name, Category type, boolean ifExists)
            throws SQLException {
        String safeSchema = SqlName.checkSimple(schema, "库名");
        String safeName = SqlName.checkSimple(name, "对象名");
        String qualified = SqlName.quoteMysql(safeSchema, "库名") + "." + SqlName.quoteMysql(safeName, "对象名");
        String sql = switch (type) {
            case PROCEDURE -> "DROP PROCEDURE " + (ifExists ? "IF EXISTS " : "") + qualified;
            case FUNCTION -> "DROP FUNCTION " + (ifExists ? "IF EXISTS " : "") + qualified;
            case TRIGGER -> "DROP TRIGGER " + (ifExists ? "IF EXISTS " : "") + qualified;
            case EVENT -> "DROP EVENT " + (ifExists ? "IF EXISTS " : "") + qualified;
        };
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    /**
     * 对象是否已存在。
     *
     * @param conn   数据库连接
     * @param schema 所属库
     * @param name   对象名
     * @param type   类别
     * @return true=存在
     * @throws SQLException 读取失败
     */
    public static boolean exists(Connection conn, String schema, String name, Category type)
            throws SQLException {
        String sql = switch (type) {
            case PROCEDURE, FUNCTION -> "SELECT COUNT(*) FROM information_schema.ROUTINES"
                    + " WHERE ROUTINE_SCHEMA = ? AND ROUTINE_NAME = ?";
            case TRIGGER -> "SELECT COUNT(*) FROM information_schema.TRIGGERS"
                    + " WHERE TRIGGER_SCHEMA = ? AND TRIGGER_NAME = ?";
            case EVENT -> "SELECT COUNT(*) FROM information_schema.EVENTS"
                    + " WHERE EVENT_SCHEMA = ? AND EVENT_NAME = ?";
        };
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    // ==================== 外键 ====================

    /**
     * 外键定义（新增用）。
     *
     * @param name       约束名
     * @param columns    本表列
     * @param refTable   引用表
     * @param refColumns 引用列
     * @param onUpdate   更新规则
     * @param onDelete   删除规则
     */
    public record ForeignKeyDefinition(
            String name,
            List<String> columns,
            String refTable,
            List<String> refColumns,
            String onUpdate,
            String onDelete
    ) {
        /**
         * 规范构造器：集合做防御性拷贝。
         *
         * @param name       约束名
         * @param columns    本表列
         * @param refTable   引用表
         * @param refColumns 引用列
         * @param onUpdate   更新规则
         * @param onDelete   删除规则
         */
        public ForeignKeyDefinition {
            columns = columns == null ? List.of() : List.copyOf(columns);
            refColumns = refColumns == null ? List.of() : List.copyOf(refColumns);
        }
    }

    /**
     * 新增外键（ALTER TABLE ADD CONSTRAINT）。
     *
     * <p>安全豁免：约束名、表名、列名全部经 {@link SqlName} 白名单校验后引用，
     * 且列必须是本表与被引用表**实际存在**的列（由调用方用 JDBC 元数据校验）。</p>
     *
     * @param conn   数据库连接
     * @param table  本表
     * @param fk     外键定义
     * @throws SQLException 内核拒绝
     */
    public static void addForeignKey(Connection conn, String table, ForeignKeyDefinition fk) throws SQLException {
        String safeTable = SqlName.checkSimple(table, "表名");
        if (fk.columns().isEmpty() || fk.refColumns().isEmpty()) {
            throw new SQLException("外键至少需要一个列");
        }
        if (fk.columns().size() != fk.refColumns().size()) {
            throw new SQLException("本表列数与引用列数不一致");
        }
        String local = joinQuoted(fk.columns(), "本表列");
        String foreign = joinQuoted(fk.refColumns(), "引用列");
        String sql = "ALTER TABLE " + SqlName.quoteMysql(safeTable, "表名")
                + " ADD CONSTRAINT " + SqlName.quoteMysql(SqlName.checkSimple(fk.name(), "约束名"), "约束名")
                + " FOREIGN KEY (" + local + ")"
                + " REFERENCES " + SqlName.quoteMysql(SqlName.checkSimple(fk.refTable(), "引用表"), "引用表")
                + " (" + foreign + ")"
                + rule("ON UPDATE", fk.onUpdate())
                + rule("ON DELETE", fk.onDelete());
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    /**
     * 删除外键（ALTER TABLE DROP FOREIGN KEY）。
     *
     * @param conn     数据库连接
     * @param table    本表
     * @param fkName   约束名
     * @throws SQLException 内核拒绝
     */
    public static void dropForeignKey(Connection conn, String table, String fkName) throws SQLException {
        String safeTable = SqlName.checkSimple(table, "表名");
        String sql = "ALTER TABLE " + SqlName.quoteMysql(safeTable, "表名")
                + " DROP FOREIGN KEY " + SqlName.quoteMysql(SqlName.checkSimple(fkName, "约束名"), "约束名");
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    /**
     * join Quoted
     * @param label 字符串
     * @param names 参数
     * @return 返回值
    */
    private static String joinQuoted(List<String> names, String label) {
        List<String> quoted = new ArrayList<>(names.size());
        for (String name : names) {
            quoted.add(SqlName.quoteMysql(SqlName.checkSimple(name, label), label));
        }
        return String.join(", ", quoted);
    }

    /**
     * 拼引用动作子句；只接受 CASCADE / SET NULL / SET DEFAULT / RESTRICT / NO ACTION，
     * 避免拼入任意文本。
     *
     * @param keyword ON UPDATE / ON DELETE
     * @param action  动作名，空则不出子句
     * @return 子句文本
     * @throws SQLException 动作不认识
     */
    private static String rule(String keyword, String action) throws SQLException {
        if (action == null || action.isBlank()) {
            return "";
        }
        String key = action.trim().toUpperCase(Locale.ROOT);
        if ("SET NULL".equals(key)) {
            return " " + keyword + " SET NULL";
        }
        if ("SET DEFAULT".equals(key)) {
            return " " + keyword + " SET DEFAULT";
        }
        return switch (key) {
            case "CASCADE", "RESTRICT", "NO ACTION" -> " " + keyword + " " + key;
            default -> throw new SQLException("不支持的引用动作: " + action);
        };
    }
}