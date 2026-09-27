package com.chua.starter.datasourcehub.support.support;

import java.sql.Connection;
import java.util.Locale;

/**
 * 来源字段类型到 SQL 列类型的推断（跨方言）。
 *
 * <p>采集侧只知道自己手里是字符串、整数还是时间，具体落成什么列类型由数据源模块决定：
 * MySQL 系用 {@code TINYINT(1)}/{@code DATETIME}/{@code TEXT}，其余内核用
 * {@code BOOLEAN}/{@code TIMESTAMP}/{@code CLOB}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class ColumnTypeSupport {

    /**
     * 默认字符串列宽。
     */
    private static final String DEFAULT_TEXT = "VARCHAR(255)";

    /**
     * 工具类不允许实例化。
     */
    private ColumnTypeSupport() {
    }

    /**
     * 推断列类型。
     *
     * @param conn       数据库连接（判定方言）
     * @param sourceType 来源类型，可为空（按字符串处理）
     * @return SQL 列类型
     */
    public static String infer(Connection conn, String sourceType) {
        boolean mysql = JdbcDialectSupport.isMysqlFamily(conn);
        String type = sourceType == null ? "" : sourceType.trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "INT", "INTEGER", "LONG", "BIGINT", "SHORT" -> "BIGINT";
            case "DECIMAL", "NUMBER", "DOUBLE", "FLOAT", "BIGDECIMAL" -> "DECIMAL(20,4)";
            case "BOOLEAN", "BOOL" -> mysql ? "TINYINT(1)" : "BOOLEAN";
            case "DATE" -> "DATE";
            case "DATETIME", "TIMESTAMP" -> mysql ? "DATETIME" : "TIMESTAMP";
            case "TEXT", "JSON", "OBJECT", "ARRAY" -> mysql ? "TEXT" : "CLOB";
            case "BINARY", "BYTES" -> mysql ? "BLOB" : "BLOB";
            default -> DEFAULT_TEXT;
        };
    }

    /**
     * 来源类型是否与目标列类型明显不兼容（只做粗判，用于给界面提示）。
     *
     * @param sourceType 来源类型
     * @param targetType 目标列类型（JDBC 元数据的 TYPE_NAME）
     * @return 需要转换时返回 true
     */
    public static boolean needCast(String sourceType, String targetType) {
        String source = sourceType == null ? "" : sourceType.trim().toUpperCase(Locale.ROOT);
        String target = targetType == null ? "" : targetType.trim().toUpperCase(Locale.ROOT);
        if (source.isEmpty() || target.isEmpty()) {
            return false;
        }
        if (isNumeric(source) && isNumeric(target)) {
            return false;
        }
        if (isText(source) && isText(target)) {
            return false;
        }
        return source.contains(target) || target.contains(source) ? false : !source.equals(target);
    }

    /**
     * 是否数值类。
     *
     * @param type 类型名
     * @return true=数值
     */
    private static boolean isNumeric(String type) {
        return type.contains("INT") || type.contains("DECIMAL") || type.contains("NUMBER")
                || type.contains("DOUBLE") || type.contains("FLOAT") || type.contains("NUMERIC");
    }

    /**
     * 是否文本类。
     *
     * @param type 类型名
     * @return true=文本
     */
    private static boolean isText(String type) {
        return type.contains("CHAR") || type.contains("TEXT") || type.contains("CLOB")
                || type.contains("STRING");
    }
}
