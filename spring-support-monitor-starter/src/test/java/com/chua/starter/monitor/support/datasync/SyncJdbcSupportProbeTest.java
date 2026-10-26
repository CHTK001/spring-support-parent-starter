package com.chua.starter.monitor.support.datasync;

import com.chua.starter.monitor.support.model.SyncColumnVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 源 SQL 取列的两种路径：解析与只取 1 条的探针。
 *
 * <p>背景：同步配置界面要在一张千万级表上显示「这段 SQL 返回哪几列」。早先的做法是
 * 拿 {@code PreparedStatement#getMetaData()}，但部分驱动返回 null；改成直接执行又会整表拉数据。
 * 现在的口径是：<b>字段名来自 SQL 文本解析</b>，需要回库确认时按方言只取 1 条。</p>
 *
 * <p>这里不连真库：用动态代理打桩连接与结果集，断言「方言分页被加上」与「setMaxRows 被调用」，
 * 这正是防「校验把整表拉进内存」的那两点。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@DisplayName("源 SQL 取列：解析优先 + 方言分页探针")
class SyncJdbcSupportProbeTest {

    /**
     * MySQL 连接下探针 SQL 应带 {@code LIMIT 1 OFFSET 0}，且必须 setMaxRows(1)。
     *
     * @throws Exception 打桩失败
     */
    @Test
    @DisplayName("探针按方言收敛到 1 条")
    void probeUsesDialectPagination() throws Exception {
        AtomicReference<String> executed = new AtomicReference<>();
        AtomicInteger maxRows = new AtomicInteger(-1);
        List<SyncColumnVO> columns = SyncJdbcSupport.probeColumns(
                connection("jdbc:mysql://127.0.0.1:3306/demo", executed, maxRows, "id", "name"),
                "select id, name from t_order");

        assertEquals(List.of("id", "name"), columns.stream().map(SyncColumnVO::name).toList());
        assertEquals(1, maxRows.get(), "必须限制返回 1 行");
        assertTrue(executed.get().toLowerCase().contains("limit 1"),
                "MySQL 方言应追加 LIMIT 1，实际: " + executed.get());
    }

    /**
     * 语句自带 LIMIT 时不再叠加（{@code LIMIT 10 LIMIT 1} 是语法错误）。
     *
     * @throws Exception 打桩失败
     */
    @Test
    @DisplayName("语句已有行数限制时不重复分页")
    void probeKeepsExistingLimit() throws Exception {
        AtomicReference<String> executed = new AtomicReference<>();
        List<SyncColumnVO> columns = SyncJdbcSupport.probeColumns(
                connection("jdbc:mysql://127.0.0.1:3306/demo", executed, new AtomicInteger(), "id"),
                "select id from t_order limit 10");

        assertEquals("select id from t_order limit 10", executed.get().trim());
        assertEquals(1, columns.size());
    }

    /**
     * 解析出的字段名优先，类型取驱动给的；两者一致时不丢类型。
     *
     * @throws Exception 打桩失败
     */
    @Test
    @DisplayName("字段名来自解析，类型来自驱动")
    void columnsOfPrefersParsedNames() throws Exception {
        AtomicReference<String> executed = new AtomicReference<>();
        List<SyncColumnVO> columns = SyncJdbcSupport.columnsOf(
                connection("jdbc:mysql://127.0.0.1:3306/demo", executed, new AtomicInteger(),
                        new String[]{"USER_ID", "USER_NAME"}, new String[]{"BIGINT", "VARCHAR"}),
                "select id as user_id, name as user_name from t_user");

        // 驱动把别名按自己的规则折叠成大写，与解析结果只有大小写差异时以 SQL 文本为准
        assertEquals(List.of("user_id", "user_name"), columns.stream().map(SyncColumnVO::name).toList());
        assertEquals("BIGINT", columns.get(0).type());
        assertEquals("VARCHAR", columns.get(1).type());
    }

    /**
     * 驱动给的列名与 SQL 文本解析结果不一致（数量或名字不同）时整体用驱动的。
     *
     * <p>这条兜底很重要：按名取值的下游只认驱动真实返回的标签，宁可用驱动名也不要用一个解析猜出的名字。</p>
     *
     * @throws Exception 打桩失败
     */
    @Test
    @DisplayName("解析结果与驱动对不上时用驱动列名")
    void columnsOfFallsBackWhenNamesDiffer() throws Exception {
        List<SyncColumnVO> columns = SyncJdbcSupport.columnsOf(
                connection("jdbc:mysql://127.0.0.1:3306/demo", new AtomicReference<>(), new AtomicInteger(),
                        new String[]{"ID", "OTHER"}, new String[]{"BIGINT", "VARCHAR"}),
                "select id, name from t_user");

        assertEquals(List.of("ID", "OTHER"), columns.stream().map(SyncColumnVO::name).toList());
    }

    /**
     * {@code SELECT *} 无法解析，字段名用驱动返回的。
     *
     * @throws Exception 打桩失败
     */
    @Test
    @DisplayName("SELECT * 回落到驱动元数据")
    void starFallsBackToDriver() throws Exception {
        List<SyncColumnVO> columns = SyncJdbcSupport.columnsOf(
                connection("jdbc:mysql://127.0.0.1:3306/demo", new AtomicReference<>(), new AtomicInteger(),
                        "id", "name"),
                "select * from t_order");

        assertEquals(List.of("id", "name"), columns.stream().map(SyncColumnVO::name).toList());
    }

    /**
     * 打桩一个连接：记录实际执行的 SQL 与 setMaxRows 的入参。
     *
     * @param url        JDBC URL（决定方言）
     * @param executed   出参：实际执行的 SQL
     * @param maxRows    出参：setMaxRows 的入参
     * @param labels     结果集列名
     * @return 连接代理
     * @throws SQLException 打桩失败
     */
    private static Connection connection(String url, AtomicReference<String> executed,
                                         AtomicInteger maxRows, String... labels) throws SQLException {
        String[] types = new String[labels.length];
        for (int i = 0; i < types.length; i++) {
            types[i] = "VARCHAR";
        }
        return connection(url, executed, maxRows, labels, types);
    }

    /**
     * 打桩一个连接（列名与类型分开指定）。
     *
     * @param url      JDBC URL（决定方言）
     * @param executed 出参：实际执行的 SQL
     * @param maxRows  出参：setMaxRows 的入参
     * @param labels   结果集列名
     * @param types    结果集列类型
     * @return 连接代理
     * @throws SQLException 打桩失败
     */
    private static Connection connection(String url, AtomicReference<String> executed, AtomicInteger maxRows,
                                         String[] labels, String[] types) throws SQLException {
        ResultSetMetaData meta = (ResultSetMetaData) proxy(ResultSetMetaData.class, (name, ret, args) -> {
            switch (name) {
                case "getColumnCount":
                    return labels.length;
                case "getColumnLabel":
                case "getColumnName":
                    return labels[(Integer) args[0] - 1];
                case "getColumnTypeName":
                    return types[(Integer) args[0] - 1];
                default:
                    return defaultValue(ret);
            }
        });
        ResultSet rs = (ResultSet) proxy(ResultSet.class, (name, ret, args) ->
                "getMetaData".equals(name) ? meta : defaultValue(ret));
        PreparedStatement ps = (PreparedStatement) proxy(PreparedStatement.class, (name, ret, args) -> {
            switch (name) {
                case "setMaxRows":
                    maxRows.set((Integer) args[0]);
                    return null;
                case "executeQuery":
                    return rs;
                case "close":
                    return null;
                default:
                    return defaultValue(ret);
            }
        });
        DatabaseMetaData dbMeta = (DatabaseMetaData) proxy(DatabaseMetaData.class, (name, ret, args) ->
                "getURL".equals(name) ? url : defaultValue(ret));
        return (Connection) proxy(Connection.class, (name, ret, args) -> {
            switch (name) {
                case "prepareStatement":
                    executed.set((String) args[0]);
                    return ps;
                case "getMetaData":
                    return dbMeta;
                case "close":
                    return null;
                default:
                    return defaultValue(ret);
            }
        });
    }

    /**
     * 创建只按「方法名 + 返回类型」分派的动态代理。
     *
     * @param type    接口
     * @param handler 处理函数
     * @return 代理实例
     */
    private static Object proxy(Class<?> type, Handler handler) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, method, args) ->
                handler.invoke(method.getName(), method.getReturnType(), args));
    }

    /**
     * 极简分派接口（避免每个代理重复写 InvocationHandler 样板）。
     */
    @FunctionalInterface
    private interface Handler {
        /**
         * 处理一次调用。
         *
         * @param name 方法名
         * @param ret  声明的返回类型
         * @param args 参数，可为 null
         * @return 返回值
         * @throws Throwable 透传
         */
        Object invoke(String name, Class<?> ret, Object[] args) throws Throwable;
    }

    /**
     * 未打桩方法的默认返回值。
     *
     * @param type 返回类型
     * @return 基本类型的零值，其余为 null
     */
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == float.class) {
            return 0F;
        }
        return '\0';
    }
}
