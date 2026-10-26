package com.chua.starter.monitor.support.datasync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SyncJdbcSupport} 标识符归一化与表存在性判定的单元测试。
 *
 * <p>背景（线上缺陷）：前端把「库.表」按 {@code `库`.`表`} 形态传回，旧实现拿原样字符串
 * 直接调 {@code DatabaseMetaData.getTables}，而该参数是不带引号的裸名 + LIKE 模式，
 * 于是「界面选得到表、后台报表不存在」，前端还会把源侧错误误判成目标侧缺表。
 *
 * <p>这里不连真库：用动态代理打桩 {@link DatabaseMetaData}，只验证
 * 「归一化 → 精确匹配 → 回读真实名」这条链路的口径。
 *
 * @author CH
 */
@DisplayName("同步表名归一化与存在性判定")
class SyncJdbcSupportIdentifierTest {

    /**
     * 三种引用符都要能剥掉，且不影响正常名字。
     */
    @Test
    @DisplayName("剥引用符：反引号/双引号/方括号/空白")
    void stripIdentifierQuotes() {
        assertEquals("jdbc_acc.device_channel", SyncJdbcSupport.stripIdentifierQuotes("`jdbc_acc`.`device_channel`"));
        assertEquals("jdbc_acc.device_channel", SyncJdbcSupport.stripIdentifierQuotes("\"jdbc_acc\".\"device_channel\""));
        assertEquals("jdbc_acc.device_channel", SyncJdbcSupport.stripIdentifierQuotes("[jdbc_acc].[device_channel]"));
        assertEquals("device_channel", SyncJdbcSupport.stripIdentifierQuotes(" device_channel "));
        assertEquals("", SyncJdbcSupport.stripIdentifierQuotes(null));
    }

    /**
     * 带引号的限定名必须能命中真实表，并回读元数据里的真实名字。
     */
    @Test
    @DisplayName("带引号的「库.表」命中真实表")
    void tableExistsWithQuotedName() throws SQLException {
        DatabaseMetaData meta = stubMeta(List.of("device_channel"), List.of("jdbc_acc"));
        assertTrue(SyncJdbcSupport.tableExists(connection(meta, "jdbc_acc"), "jdbc_acc", "`device_channel`"));
        assertTrue(SyncJdbcSupport.tableExists(connection(meta, "jdbc_acc"), "", "`jdbc_acc`.`device_channel`"));
    }

    /**
     * 大小写不一致时按元数据真实值对齐（Linux 上库名大小写敏感）。
     */
    @Test
    @DisplayName("大小写不一致按真实名对齐")
    void tableExistsIgnoreCase() throws SQLException {
        DatabaseMetaData meta = stubMeta(List.of("Device_Channel"), List.of("Jdbc_Acc"));
        assertTrue(SyncJdbcSupport.tableExists(connection(meta, "jdbc_acc"), "jdbc_acc", "device_channel"));
        assertTrue(SyncJdbcSupport.tableExists(connection(meta, "jdbc_acc"), "JDBC_ACC", "device_channel"));
    }

    /**
     * 下划线是元数据 LIKE 的通配符：{@code device_channel} 不能匹配 {@code deviceXchannel}。
     */
    @Test
    @DisplayName("下划线不当通配符：a_b 不匹配 axb")
    void underscoreIsNotWildcard() throws SQLException {
        DatabaseMetaData meta = stubMeta(List.of("deviceXchannel"), List.of("jdbc_acc"));
        assertFalse(SyncJdbcSupport.tableExists(connection(meta, "jdbc_acc"), "jdbc_acc", "device_channel"));
    }

    /**
     * 表确实不存在时返回 null / false，而不是误判成存在。
     */
    @Test
    @DisplayName("表不存在：返回 false 且不误判")
    void tableMissing() throws SQLException {
        DatabaseMetaData meta = stubMeta(List.of("other_table"), List.of("jdbc_acc"));
        assertFalse(SyncJdbcSupport.tableExists(connection(meta, "jdbc_acc"), "jdbc_acc", "device_channel"));
        assertFalse(SyncJdbcSupport.tableExists(connection(meta, "jdbc_acc"), "jdbc_acc", "`device_channel`"));
        assertFalse(SyncJdbcSupport.tableExists(connection(meta, "jdbc_acc"), "`jdbc_acc`.`device_channel`"));
    }

    /**
     * 打桩 {@link DatabaseMetaData}：把「按 LIKE 模式查表」简化成对表名清单的过滤。
     *
     * @param tables  模拟库里的表
     * @param catalogs 模拟库列表
     * @return 打桩后的元数据
     */
    private static DatabaseMetaData stubMeta(List<String> tables, List<String> catalogs) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("getSearchStringEscape".equals(name)) {
                return "\\";
            }
            if ("getTables".equals(name)) {
                String pattern = args.length > 2 ? (String) args[2] : null;
                return resultSet(tables.stream()
                        .filter(t -> pattern == null || likeMatch(t, pattern))
                        .toList(), "TABLE_NAME");
            }
            if ("getCatalogs".equals(name)) {
                return resultSet(catalogs, "TABLE_CAT");
            }
            if ("getColumns".equals(name)) {
                return resultSet(List.of(), "COLUMN_NAME");
            }
            return defaultValue(method.getReturnType());
        };
        return (DatabaseMetaData) Proxy.newProxyInstance(
                SyncJdbcSupportIdentifierTest.class.getClassLoader(),
                new Class<?>[]{DatabaseMetaData.class}, handler);
    }

    /**
     * 打桩连接：只支撑 getCatalog / getMetaData。
     *
     * @param meta    元数据
     * @param catalog 默认库
     * @return 打桩连接
     */
    private static java.sql.Connection connection(DatabaseMetaData meta, String catalog) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("getMetaData".equals(method.getName())) {
                return meta;
            }
            if ("getCatalog".equals(method.getName())) {
                return catalog;
            }
            return defaultValue(method.getReturnType());
        };
        return (java.sql.Connection) Proxy.newProxyInstance(
                SyncJdbcSupportIdentifierTest.class.getClassLoader(),
                new Class<?>[]{java.sql.Connection.class}, handler);
    }

    /**
     * 极简 LIKE 匹配：只认转义符 + {@code _} / {@code %} 两种通配符，够验证转义是否正确。
     *
     * @param value   候选表名
     * @param pattern 模式串（已按驱动转义符转义）
     * @return 命中返回 true
     */
    private static boolean likeMatch(String value, String pattern) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch == '\\' && i + 1 < pattern.length()) {
                regex.append(java.util.regex.Pattern.quote(String.valueOf(pattern.charAt(++i))));
            } else if (ch == '_') {
                regex.append('.');
            } else if (ch == '%') {
                regex.append(".*");
            } else {
                regex.append(java.util.regex.Pattern.quote(String.valueOf(ch)));
            }
        }
        return value.matches(regex.toString());
    }

    /**
     * 打桩 ResultSet：按 list 逐行迭代，取指定列。
     *
     * @param rows   行（单列值）
     * @param column 列名
     * @return 打桩结果集
     */
    private static ResultSet resultSet(List<String> rows, String column) {
        int[] cursor = {-1};
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "next":
                    cursor[0]++;
                    return cursor[0] < rows.size();
                case "getString":
                    if (args != null && args.length == 1 && args[0] instanceof String label && !column.equals(label)) {
                        return null;
                    }
                    return rows.get(cursor[0]);
                case "close":
                    return null;
                default:
                    return defaultValue(method.getReturnType());
            }
        };
        return (ResultSet) Proxy.newProxyInstance(
                SyncJdbcSupportIdentifierTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, handler);
    }

    /**
     * 返回类型的默认值（打桩未覆盖的方法用）。
     *
     * @param type 返回类型
     * @return 默认值
     */
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == void.class) {
            return null;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == char.class) {
            return (char) 0;
        }
        return null;
    }
}
