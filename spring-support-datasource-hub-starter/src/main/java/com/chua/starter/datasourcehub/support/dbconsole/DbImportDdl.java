// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.dbconsole;

import com.chua.common.support.lang.datasource.dialect.SqlName;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 导入时按源数据自动建表（对齐 Navicat 第 5 步「新建表」）。
 *
 * <p>只在目标表不存在且用户勾了「新建表」时用。列类型由源文件里的样本值推断：
 * 全部能解析成整数 → {@code BIGINT}，全部能解析成小数 → {@code DECIMAL(38,10)}，
 * 全是日期时间 → {@code DATETIME}，否则 {@code TEXT}。推断只看前若干行样本，
 * 因为大文件的全部值解析代价高且收益低（类型宽一点不影响写入，窄了才会截断）。</p>
 *
 * <p><b>为什么一律用宽类型</b>：导入向导的价值是「先把数据搬进来」，不是「精确建模」。
 * 推断偏窄会直接导致导入失败（{@code 值 "abc" 无法转为 INT}），而偏宽只是列类型不够精致，
 * 用户随后可以在设计器里改。因此除 {@code DATETIME} 外不做窄化。</p>
 *
 * <p><b>列名清洗</b>：CSV 表头常含空格、中文、括号（{@code 订单 编号(主)}）。数据库标识符
 * 白名单只认 {@code [A-Za-z_][A-Za-z0-9_]*}，因此建表前必须清洗。清洗规则是
 * <b>把非法字符换成下划线、首字符非字母时补前缀</b>，保证「同一份文件每次导入得到的表结构
 * 一致」；清洗后的名字会回传给写入阶段作为目标列名，避免「建表用了一个名、写入用另一个名」。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
public final class DbImportDdl {

    /** 推断类型时最多看的样例行数 */
    private static final int SAMPLE_ROWS = 200;

    /** 清洗后列名的最长长度（与各库 64 的保守上限一致） */
    private static final int MAX_COLUMN_LENGTH = 64;

    private DbImportDdl() {
    }

    /**
     * 一个待建列的「目标列名 + 取值来源」。
     *
     * @param target 目标列名（已清洗，可用于 DDL）
     * @param source 源列名（行 Map 的键，用于取样推断类型）
     */
    record ColumnSpec(String target, String source) {
    }

    /**
     * 按源列建表。
     *
     * @param conn    连接
     * @param table   表名（已校验）
     * @param columns 待建列（目标名 + 源名）
     * @param rows    源行（用于推断类型，可为空）
     * @throws SQLException 建表失败
     */
    static void createTable(Connection conn, String table, List<ColumnSpec> columns,
                            List<Map<String, Object>> rows) throws SQLException {
        if (columns == null || columns.isEmpty()) {
            throw new SQLException("没有可建表的列");
        }
        StringBuilder ddl = new StringBuilder("CREATE TABLE ")
                .append(SqlName.quoteMysql(table, "表名"))
                .append(" (");
        List<Map<String, Object>> safeRows = rows == null ? List.of() : rows;
        List<Map<String, Object>> sample = safeRows.size() <= SAMPLE_ROWS
                ? safeRows : safeRows.subList(0, SAMPLE_ROWS);
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                ddl.append(", ");
            }
            ColumnSpec spec = columns.get(i);
            ddl.append(SqlName.quoteMysql(spec.target(), "列名"))
                    .append(' ').append(inferType(spec.source(), sample));
        }
        ddl.append(')');
        String sql = ddl.toString();
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            throw new SQLException("自动建表失败（" + sql + "）: " + e.getMessage(), e);
        }
    }

    /**
     * 由「源列 → 目标列」映射构造建表规格，并对目标列名做标识符清洗。
     *
     * <p>映射为空时退化成「源列同名建表」：这时目标列名同样要清洗，
     * 否则中文表头会在 DDL 阶段就被 {@link SqlName} 拒掉。</p>
     *
     * @param mapping       源列 → 目标列；可为空
     * @param sourceColumns 源列名（映射为空时使用）
     * @return 建表规格，保持映射的插入顺序
     */
    static List<ColumnSpec> planColumns(Map<String, String> mapping, List<String> sourceColumns) {
        List<ColumnSpec> specs = new ArrayList<>();
        if (mapping != null && !mapping.isEmpty()) {
            List<String> targets = sanitizeAll(new ArrayList<>(mapping.values()));
            int index = 0;
            for (Map.Entry<String, String> entry : mapping.entrySet()) {
                specs.add(new ColumnSpec(targets.get(index++), entry.getKey()));
            }
        } else if (sourceColumns != null) {
            List<String> targets = sanitizeAll(sourceColumns);
            for (int i = 0; i < sourceColumns.size(); i++) {
                specs.add(new ColumnSpec(targets.get(i), sourceColumns.get(i)));
            }
        }
        return specs;
    }

    /**
     * 清洗成合法 SQL 标识符。
     *
     * <p>规则（顺序不可换）：</p>
     * <ol>
     *   <li>去首尾空白；空串给 {@code col}</li>
     *   <li>非 {@code [A-Za-z0-9_]} 一律换 {@code _}（中文、空格、括号、点号都在此处理）</li>
     *   <li>清洗后若<b>没有任何字母数字</b>（表头全是中文/符号），改用 {@code col}：
     *       否则 {@code 订单号} 与 {@code 金额} 会一起变成 {@code ___}，只能靠序号区分，
     *       用户在第 5 步看到一列 {@code ___2} 完全不知道那是哪一列</li>
     *   <li>首字符是数字时补 {@code c_} 前缀</li>
     *   <li>截到 64 字符</li>
     * </ol>
     *
     * @param raw 原始列名
     * @return 合法标识符
     */
    public static String sanitize(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return "col";
        }
        StringBuilder sb = new StringBuilder(text.length() + 2);
        boolean hasAlnum = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean alnum = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
            boolean ok = alnum || c == '_';
            sb.append(ok ? c : '_');
            hasAlnum = hasAlnum || alnum;
        }
        if (!hasAlnum) {
            // 全中文/全符号：不留一串下划线，交给上层按序号命名
            return "col";
        }
        // 首字符不能是数字
        if (sb.charAt(0) >= '0' && sb.charAt(0) <= '9') {
            sb.insert(0, "c_");
        }
        if (sb.length() > MAX_COLUMN_LENGTH) {
            sb.setLength(MAX_COLUMN_LENGTH);
        }
        return sb.toString();
    }

    /**
     * 生成建表目标列名：{@link #sanitize(String)} 后按序号消歧。
     *
     * <p>顺序敏感：{@code 订单号 / 金额 / col} 三列都会得到 {@code col}，必须靠序号拆开。
     * 序号只对「撞名的后来者」加，第一列保持 {@code col}，与大多少数工具的习惯一致。</p>
     *
     * @param raws 原始列名，按文件列序
     * @return 与入参等长的目标列名
     */
    public static List<String> sanitizeAll(List<String> raws) {
        List<String> out = new ArrayList<>();
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (int i = 0; i < raws.size(); i++) {
            String base = sanitize(raws.get(i));
            // 纯占位名补列号：col → col1，第二列 col → col2，便于用户对照源文件
            if ("col".equals(base)) {
                base = "col" + (i + 1);
            }
            String lower = base.toLowerCase(Locale.ROOT);
            Integer count = seen.get(lower);
            if (count == null) {
                seen.put(lower, 1);
                out.add(base);
                continue;
            }
            seen.put(lower, count + 1);
            String suffix = "_" + count;
            String trimmed = base.length() + suffix.length() > MAX_COLUMN_LENGTH
                    ? base.substring(0, MAX_COLUMN_LENGTH - suffix.length()) : base;
            out.add(trimmed + suffix);
        }
        return out;
    }

    /**
     * 推断一列的类型。
     *
     * @param source 源列名（源行 Map 的键）
     * @param sample 样例行
     * @return SQL 类型名
     */
    private static String inferType(String source, List<Map<String, Object>> sample) {
        boolean sawValue = false;
        boolean allInt = true;
        boolean allDecimal = true;
        boolean allDate = true;
        boolean allBool = true;
        for (Map<String, Object> row : sample) {
            Object raw = row.get(source);
            if (raw == null) {
                continue;
            }
            String text = String.valueOf(raw).trim();
            if (text.isEmpty()) {
                continue;
            }
            sawValue = true;
            if (allInt && !isInteger(text)) {
                allInt = false;
            }
            if (allDecimal && !isDecimal(text)) {
                allDecimal = false;
            }
            if (allDate && !isDateTime(text)) {
                allDate = false;
            }
            if (allBool && !isBoolean(text)) {
                allBool = false;
            }
            if (!allInt && !allDecimal && !allDate && !allBool) {
                break;
            }
        }
        if (!sawValue) {
            // 整列全空：给 TEXT，写 NULL 不会失败，写任何值也都放得下
            return "TEXT";
        }
        if (allBool) {
            return "TINYINT(1)";
        }
        if (allInt) {
            return "BIGINT";
        }
        if (allDecimal) {
            // 38,10 覆盖到 DECIMAL 的上限精度，货币/科学计数都不会被截断
            return "DECIMAL(38,10)";
        }
        if (allDate) {
            return "DATETIME";
        }
        return "TEXT";
    }

    /**
     * is Integer
     * @param text 字符串
     * @return 返回值
    */
    private static boolean isInteger(String text) {
        try {
            Long.parseLong(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * is Decimal
     * @param text 字符串
     * @return 返回值
    */
    private static boolean isDecimal(String text) {
        try {
            new java.math.BigDecimal(text.replace(",", ""));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * is Boolean
     * @param text 字符串
     * @return 返回值
    */
    private static boolean isBoolean(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return "0".equals(lower) || "1".equals(lower) || "true".equals(lower) || "false".equals(lower)
                || "yes".equals(lower) || "no".equals(lower);
    }

    /**
     * 是否像日期时间文本。
     *
     * <p>只认三种常见形态（{@code yyyy-MM-dd}、{@code yyyy-MM-dd HH:mm:ss}、
     * {@code yyyy/M/d}），不用宽松解析器：{@code 2026} 这种纯数字会被
     * {@code DateTimeFormatter} 宽松模式当成日期，导致整数列被建成 DATETIME。</p>
     *
     * @param text 单元格文本
     * @return true=像日期时间
     */
    private static boolean isDateTime(String text) {
        if (text.length() < 8) {
            return false;
        }
        String normalized = text.replace('/', '-');
        if (!normalized.matches("\\d{4}-\\d{1,2}-\\d{1,2}([ T]\\d{1,2}:\\d{2}(:\\d{2})?)?(\\.\\d+)?")
                && !normalized.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
            return false;
        }
        try {
            String value = normalized.length() <= 10 ? normalized + " 00:00:00" : normalized.replace('T', ' ');
            if (value.length() == 16) {
                value = value + ":00";
            }
            java.time.LocalDateTime.parse(value.replace(' ', 'T'));
            return true;
        } catch (java.time.format.DateTimeParseException e) {
            return false;
        }
    }
}