package com.chua.starter.datasourcehub.support.dbconsole;

import com.chua.common.support.lang.datasource.dialect.SqlName;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 列定义的完整模型（对齐 Navicat「列属性」页的全部维度）。
 *
 * <p>此前 {@code MonitorController#buildColumnDef} 只产出「类型 + 长度 + NULL + 默认值」，
 * 落不到 MySQL 的 {@code UNSIGNED / ZEROFILL / CHARACTER SET / COLLATE /
 * AUTO_INCREMENT / ON UPDATE CURRENT_TIMESTAMP / ENUM('a','b')} 上，用户改不了这些属性，
 * 而它们恰恰是「改表结构」里最高频的部分。</p>
 *
 * <p><b>安全豁免</b>：DDL 是拼出来的（{@code MODIFY COLUMN} 不支持对 DEFAULT 之外的
 * 值做参数绑定），因此每个片段都必须**独立**过白名单：
 * <ul>
 *   <li>类型名取自 {@link #SUPPORTED_TYPES}，不接受自定义；</li>
 *   <li>长度/精度为整数且落在类型允许的范围内；</li>
 *   <li>CHARACTER SET / COLLATE 走 {@link SqlName#checkSimple}；</li>
 *   <li>ENUM/SET 的取值逐个转义并拒绝超长与控制字符；</li>
 *   <li>ON UPDATE 只允许 CURRENT_TIMESTAMP[(n)] / NOW() 这三种内核写法。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.44
 */
public record DbColumnDef(
        String name,
        String type,
        Integer size,
        Integer scale,
        boolean unsigned,
        boolean zerofill,
        String charset,
        String collation,
        boolean nullable,
        String defaultValue,
        String onUpdate,
        boolean autoIncrement,
        List<String> enumValues,
        boolean generated,
        String comment
) {
    /**
     * 支持的列类型。
     *
     * <p>按类别分组便于前端渲染下拉，也便于 {@link #requiresSize} 判定。
     * 名称一律大写；不在表内的一律拒绝，绝不把前端字符串直接拼进 DDL。</p>
     */
    public static final Set<String> SUPPORTED_TYPES = Set.of(
            // 整数
            "TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "BIGINT", "BIT", "BOOL", "BOOLEAN",
            // 定点 / 浮点
            "DECIMAL", "NUMERIC", "FLOAT", "DOUBLE", "REAL", "DOUBLE PRECISION",
            // 字符
            "CHAR", "VARCHAR", "TINYTEXT", "TEXT", "MEDIUMTEXT", "LONGTEXT",
            // 二进制
            "BINARY", "VARBINARY", "TINYBLOB", "BLOB", "MEDIUMBLOB", "LONGBLOB",
            // 日期时间
            "DATE", "TIME", "DATETIME", "TIMESTAMP", "YEAR",
            // 枚举集合
            "ENUM", "SET",
            // JSON
            "JSON");

    /** 允许 UNSIGNED / ZEROFILL 的类型（字符与日期类不允许）。 */
    private static final Set<String> UNSIGNED_ALLOWED = Set.of(
            "TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "BIGINT",
            "DECIMAL", "NUMERIC", "FLOAT", "DOUBLE", "REAL", "DOUBLE PRECISION", "BIT", "YEAR");

    /** 需要长度的类型。 */
    private static final Set<String> SIZE_REQUIRED = Set.of(
            "CHAR", "VARCHAR", "BINARY", "VARBINARY", "DECIMAL", "NUMERIC");

    /** 允许带小数位（精度）的类型。 */
    private static final Set<String> SCALE_ALLOWED = Set.of("DECIMAL", "NUMERIC", "FLOAT", "DOUBLE", "REAL");

    /** ENUM / SET 的取值个数上限。 */
    private static final int MAX_ENUM_VALUES = 255;

    /** 单个 ENUM / SET 取值的长度上限（与内核一致）。 */
    private static final int MAX_ENUM_VALUE_LENGTH = 255;

    /**
     * 规整构造器：白名单校验全部前置，非法直接拒绝。
     *
     * <p>刻意在**构造期**失败而不是在拼 DDL 时才发现：调用方（controller）只需
     * 一次 try/catch 就能拿到可读错误，不会出现「校验一半、拼到一半才炸」。</p>
     *
     * @param name          列名
     * @param type          类型
     * @param size          长度
     * @param scale         小数位
     * @param unsigned      是否无符号
     * @param zerofill      是否补零
     * @param charset       字符集
     * @param collation     排序规则
     * @param nullable      是否可空
     * @param defaultValue  默认值
     * @param onUpdate      ON UPDATE 表达式
     * @param autoIncrement 是否自增
     * @param enumValues    枚举/集合取值
     * @param generated     是否生成列
     * @param comment       注释
     * @throws IllegalArgumentException 任一属性非法
     */
    public DbColumnDef {
        name = SqlName.checkSimple(trim(name), "列名");
        type = normalizeType(type);
        size = normalizeSize(type, size, scale);
        scale = normalizeScale(type, scale);
        if (unsigned && !UNSIGNED_ALLOWED.contains(type)) {
            throw new IllegalArgumentException("类型 " + type + " 不支持 UNSIGNED");
        }
        zerofill = zerofill && unsigned;
        charset = normalizeIdent(charset, "字符集");
        collation = normalizeIdent(collation, "排序规则");
        defaultValue = defaultValue == null ? "" : defaultValue.trim();
        onUpdate = normalizeOnUpdate(onUpdate);
        if (autoIncrement && !UNSIGNED_ALLOWED.contains(type)) {
            throw new IllegalArgumentException("AUTO_INCREMENT 只能用于整数类型，当前是 " + type);
        }
        if (autoIncrement && nullable) {
            throw new IllegalArgumentException("AUTO_INCREMENT 列不能可空");
        }
        enumValues = normalizeEnum(type, enumValues);
        comment = comment == null ? "" : comment;
        if (comment.length() > 1024) {
            throw new IllegalArgumentException("列注释过长（上限 1024 字符）");
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 规整类型名。
     *
     * @param raw 类型
     * @return 大写标准名
     */
    private static String normalizeType(String raw) {
        String key = trim(raw).toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (!SUPPORTED_TYPES.contains(key)) {
            throw new IllegalArgumentException("不支持的列类型: " + raw + "（可选："
                    + new java.util.TreeSet<>(SUPPORTED_TYPES).stream().reduce((a, b) -> a + ", " + b).orElse("") + "）");
        }
        return key;
    }

    private static Integer normalizeSize(String type, Integer size, Integer scale) {
        if (SIZE_REQUIRED.contains(type) && size == null) {
            throw new IllegalArgumentException(type + " 必须指定长度");
        }
        if (size == null) {
            return null;
        }
        if (size < 1 || size > 65535) {
            throw new IllegalArgumentException("长度必须在 1 到 65535 之间");
        }
        if (type.startsWith("CHAR") && size > 255 && !"LONGTEXT".equals(type)) {
            throw new IllegalArgumentException(type + " 的长度不能超过 255");
        }
        return size;
    }

    private static Integer normalizeScale(String type, Integer scale) {
        if (scale == null) {
            return null;
        }
        if (!SCALE_ALLOWED.contains(type)) {
            throw new IllegalArgumentException("类型 " + type + " 不支持小数位");
        }
        if (scale < 0 || scale > 30) {
            throw new IllegalArgumentException("小数位必须在 0 到 30 之间");
        }
        return scale;
    }

    private static String normalizeIdent(String value, String label) {
        String key = trim(value);
        if (key.isEmpty()) {
            return "";
        }
        return SqlName.checkSimple(key, label);
    }

    /**
     * 规整 ON UPDATE 表达式：只放行内核真正支持的写法。
     *
     * <p>这里**不能**用「含 CURRENT_TIMESTAMP 就放行」这类包含式判断，
     * 否则 {@code CURRENT_TIMESTAMP(); DROP TABLE x} 会被当成合法表达式拼进 DDL。</p>
     *
     * @param raw 表达式
     * @return 规整后的表达式，空表示不设置
     */
    private static String normalizeOnUpdate(String raw) {
        String key = trim(raw).toUpperCase(Locale.ROOT);
        if (key.isEmpty()) {
            return "";
        }
        // 去掉所有空白后再匹配：用户可能写 "CURRENT_TIMESTAMP (3)"，
        // 但**只去空白、不放宽其他字符**，否则 "CURRENT_TIMESTAMP() ; DROP" 会被误判为合法
        String compact = key.replaceAll("\\s+", "");
        if (compact.equals("NOW()")) {
            return "NOW()";
        }
        if (compact.equals("CURRENT_TIMESTAMP") || compact.equals("NOW")) {
            return "CURRENT_TIMESTAMP";
        }
        var matcher = java.util.regex.Pattern.compile("^(?:CURRENT_TIMESTAMP|NOW)\\(([0-9]{1,2})\\)$").matcher(compact);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("ON UPDATE 只支持 CURRENT_TIMESTAMP / NOW()（可带 0~6 位小数秒）");
        }
        int value = Integer.parseInt(matcher.group(1));
        if (value > 6) {
            throw new IllegalArgumentException("小数秒最多 6 位");
        }
        return "CURRENT_TIMESTAMP(" + value + ")";
    }

    private static List<String> normalizeEnum(String type, List<String> values) {
        boolean isEnum = "ENUM".equals(type) || "SET".equals(type);
        if (!isEnum) {
            if (values != null && !values.isEmpty()) {
                throw new IllegalArgumentException("只有 ENUM / SET 才能配置取值列表");
            }
            return List.of();
        }
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(type + " 必须至少配置一个取值");
        }
        // 去重但保持顺序（内核按顺序编号，顺序变了语义就变了）
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            String item = value == null ? "" : value;
            if (item.length() > MAX_ENUM_VALUE_LENGTH) {
                throw new IllegalArgumentException("ENUM/SET 单个取值最长 " + MAX_ENUM_VALUE_LENGTH + " 字符");
            }
            // 拒绝控制字符与反斜杠：转义后语义会变得不可预期
            for (int i = 0; i < item.length(); i++) {
                char c = item.charAt(i);
                if (c < 0x20 || c == '\\' || c == '\'' || c == '"') {
                    throw new IllegalArgumentException("ENUM/SET 取值不能含控制字符、引号或反斜杠: " + item);
                }
            }
            unique.add(item);
        }
        if (unique.isEmpty()) {
            throw new IllegalArgumentException(type + " 必须至少配置一个非空取值");
        }
        if (unique.size() > MAX_ENUM_VALUES) {
            throw new IllegalArgumentException(type + " 取值个数不能超过 " + MAX_ENUM_VALUES);
        }
        return List.copyOf(unique);
    }

    /**
     * 该类型是否需要长度输入。
     *
     * @return true=需要
     */
    public boolean requiresSize() {
        return SIZE_REQUIRED.contains(type) || "FLOAT".equals(type) || "DOUBLE".equals(type)
                || "DOUBLE PRECISION".equals(type) || "TIME".equals(type)
                || "DATETIME".equals(type) || "TIMESTAMP".equals(type);
    }

    /**
     * 该类型是否支持 UNSIGNED。
     *
     * @return true=支持
     */
    public boolean allowsUnsigned() {
        return UNSIGNED_ALLOWED.contains(type);
    }

    /**
     * 该类型是否是枚举/集合。
     *
     * @return true=ENUM 或 SET
     */
    public boolean isEnum() {
        return "ENUM".equals(type) || "SET".equals(type);
    }

    /**
     * 拼出类型定义片段（如 {@code VARCHAR(64) NOT NULL DEFAULT 'a' COMMENT '名称'}）。
     *
     * <p>注意：{@code DEFAULT} 的值既拼在这里（用于 DDL 文本）也通过
     * {@link #bindDefaults} 绑定（用于不依赖 DDL 文本的场景）；两者都用同一个
     * {@link #quoteSqlLiteral} 转义规则，保证不会出现「拼的和绑的不一致」。</p>
     *
     * @return DDL 片段
     */
    public String toSqlFragment() {
        StringBuilder ddl = new StringBuilder(type);
        if (size != null) {
            ddl.append('(').append(size);
            if (scale != null) {
                ddl.append(", ").append(scale);
            }
            ddl.append(')');
        } else if (scale != null && SCALE_ALLOWED.contains(type)) {
            ddl.append('(').append(scale).append(')');
        }
        if (isEnum()) {
            List<String> quoted = new ArrayList<>(enumValues.size());
            for (String value : enumValues) {
                quoted.add('\'' + value.replace("'", "''") + '\'');
            }
            ddl.append('(').append(String.join(", ", quoted)).append(')');
        }
        if (unsigned) {
            ddl.append(" UNSIGNED");
        }
        if (zerofill) {
            ddl.append(" ZEROFILL");
        }
        if (!charset.isEmpty()) {
            ddl.append(" CHARACTER SET ").append(charset);
        }
        if (!collation.isEmpty()) {
            ddl.append(" COLLATE ").append(collation);
        }
        ddl.append(nullable ? " NULL" : " NOT NULL");
        if (!defaultValue.isEmpty()) {
            ddl.append(" DEFAULT ").append(quoteSqlLiteral(defaultValue));
        }
        if (autoIncrement) {
            ddl.append(" AUTO_INCREMENT");
        }
        if (generated) {
            ddl.append(" GENERATED ALWAYS AS (");
            ddl.append(quoteSqlLiteral(defaultValue));
            ddl.append(") STORED");
        }
        if (!onUpdate.isEmpty()) {
            ddl.append(" ON UPDATE ").append(onUpdate);
        }
        if (!comment.isEmpty()) {
            ddl.append(" COMMENT ").append(quoteSqlLiteral(comment));
        }
        return ddl.toString();
    }

    /**
     * SQL 字面量转义。
     *
     * @param value 原始值
     * @return 带单引号的字面量
     */
    public static String quoteSqlLiteral(String value) {
        return '\'' + value.replace("\\", "\\\\").replace("'", "\\'") + '\'';
    }

    /**
     * 读取列的完整属性（供「修改列」时回填表单）。
     *
     * @param columnType   内核返回的类型名（含长度，如 {@code varchar(64)}）
     * @param size         内核返回的长度
     * @param nullable     是否可空
     * @param defaultValue 默认值原文
     * @param isNullableAutoInc 内核报告的自增标记
     * @return 可直接塞进表单的属性视图
     */
    public static Map<String, Object> parseAttributes(
            String columnType, int size, boolean nullable, String defaultValue, boolean isNullableAutoInc) {
        Map<String, Object> attrs = new java.util.LinkedHashMap<>();
        String raw = columnType == null ? "" : columnType;
        String upper = raw.toUpperCase(Locale.ROOT);
        boolean unsigned = upper.contains("UNSIGNED");
        boolean zerofill = upper.contains("ZEROFILL");
        if (unsigned) {
            upper = upper.replace(" UNSIGNED", "").replace("UNSIGNED", "").trim();
        }
        if (zerofill) {
            upper = upper.replace(" ZEROFILL", "").replace("ZEROFILL", "").trim();
        }
        String base = upper;
        List<String> enumValues = List.of();
        int open = upper.indexOf('(');
        if (open > 0 && upper.endsWith(")")) {
            base = upper.substring(0, open).trim();
            String inside = upper.substring(open + 1, upper.length() - 1);
            if ("ENUM".equals(base) || "SET".equals(base)) {
                List<String> values = new ArrayList<>();
                for (String part : inside.split(",")) {
                    values.add(part.trim().replaceAll("^'|'$", ""));
                }
                enumValues = values;
            }
        } else {
            base = upper.split("\\s+")[0];
        }
        attrs.put("baseType", base);
        attrs.put("unsigned", unsigned);
        attrs.put("zerofill", zerofill);
        attrs.put("size", size > 0 ? size : null);
        attrs.put("enumValues", enumValues);
        attrs.put("nullable", nullable);
        attrs.put("autoIncrement", isNullableAutoInc);
        attrs.put("defaultValue", normalizeDefault(defaultValue));
        return attrs;
    }

    /**
     * 归一化列默认值文本。
     *
     * <p>两个必须处理的坑：</p>
     * <ul>
     *   <li>内核给 {@code DEFAULT 'x'} 裹的引号要去掉，否则回填后变成 {@code 'x'}（双引号）；</li>
     *   <li><b>JDBC 的 {@code COLUMN_DEF} 对「可空且无显式默认值」的列返回字面文本
     *       {@code NULL}，而不是空值</b>。把它当默认值原样拼进 {@code MODIFY COLUMN}
     *       就成了 {@code DEFAULT 'NULL'}，内核报 {@code Invalid default value for 'xxx'}
     *       —— 表现为「只想改个注释却失败」，根因离报错信息很远。
     *       所以字面量 {@code NULL} 必须一律视为「无默认值」。</li>
     * </ul>
     *
     * @param raw 原始文本，可为空
     * @return 去壳后的值；无默认值时返回空串
     */
    public static String normalizeDefault(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (value.equalsIgnoreCase("NULL")) {
            return "";
        }
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1).replace("''", "'").replace("\\'", "'");
        }
        if (value.equalsIgnoreCase("CURRENT_TIMESTAMP")) {
            return "CURRENT_TIMESTAMP";
        }
        if (value.toUpperCase(Locale.ROOT).startsWith("CURRENT_TIMESTAMP(")
                && value.endsWith(")")) {
            return value;
        }
        return value;
    }

    /**
     * 把默认值按内核类型绑定（供需要 PreparedStatement 的路径使用）。
     *
     * @param ps        语句
     * @param index     参数序号
     * @param value     默认值文本
     * @return 绑定的值
     * @throws SQLException 绑定失败
     */
    public static Object bindDefault(PreparedStatement ps, int index, String value) throws SQLException {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return null;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        if (upper.equals("CURRENT_TIMESTAMP") || upper.startsWith("CURRENT_TIMESTAMP(") || upper.equals("NOW()")) {
            return new java.sql.Timestamp(System.currentTimeMillis());
        }
        try {
            if (text.matches("-?\\d+")) {
                return Long.valueOf(text);
            }
            if (text.matches("-?\\d+\\.\\d+")) {
                return new BigDecimal(text);
            }
        } catch (NumberFormatException ignored) {
            // 退回字符串
        }
        return text;
    }
}