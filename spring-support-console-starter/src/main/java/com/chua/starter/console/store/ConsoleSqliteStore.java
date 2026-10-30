package com.chua.starter.console.store;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.starter.console.ConsoleProperties;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 管理控制台 sqlite 持久层（自包含 sqlite-JDBC，单连接同步访问）。
 *
 * <p>四表：{@code console_feature_override}（@ApiFeature 覆盖）、{@code console_gray_override}
 * （@apigray 覆盖）、{@code console_kv}（通用键值运行设置）、{@code console_meta}（注解指纹）。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@Slf4j
public class ConsoleSqliteStore implements AutoCloseable {

    private final Connection connection; // connection

    /**
     * 控制台sqlite存储。
     * @param properties 属性
     */
    public ConsoleSqliteStore(ConsoleProperties properties) {
        try {
            ReflectUtils.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + properties.getSqlitePath());
            /**
             * 初始化模式。
             */
            initSchema();
            log.info("[Console] SQLite 持久层就绪: {}", properties.getSqlitePath());
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param properties.getSqlitePath( 属性.获取sqlite路径(
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("SQLite 持久层初始化失败: " + properties.getSqlitePath(), e);
        }
    }

    /**
     * 初始化模式。
     * @return 返回值
     * @throws SQLException 异常
    */
    private void initSchema() throws SQLException {
        /**
         * 尝试。
         * @param connection.createStatement( connection.创建对账单(
         */
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS console_feature_override (
                        /**
                        * VARCHAR。
                        * @param 128 128
                        * @return VARCHAR的结果
                        */
                        feature_id VARCHAR(128) PRIMARY KEY,
                        enabled INTEGER NOT NULL,
                        /**
                        * VARCHAR。
                        * @param 255 255
                        * @return VARCHAR的结果
                        */
                        disabled_message VARCHAR(255),
                        disabled_status INTEGER DEFAULT -1,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )""");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS console_gray_override (
                        /**
                        * VARCHAR。
                        * @param 128 128
                        * @return VARCHAR的结果
                        */
                        pattern VARCHAR(128) PRIMARY KEY,
                        percentage INTEGER,
                        /**
                        * VARCHAR。
                        * @param 500 500
                        * @return VARCHAR的结果
                        */
                        roles VARCHAR(500),
                        /**
                        * VARCHAR。
                        * @param 500 500
                        * @return VARCHAR的结果
                        */
                        users VARCHAR(500),
                        /**
                        * VARCHAR。
                        * @param 500 500
                        * @return VARCHAR的结果
                        */
                        headers VARCHAR(500),
                        /**
                        * VARCHAR。
                        * @param 500 500
                        * @return VARCHAR的结果
                        */
                        rule VARCHAR(500),
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )""");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS console_kv (
                        /**
                        * VARCHAR。
                        * @param 255 255
                        * @return VARCHAR的结果
                        */
                        k VARCHAR(255) PRIMARY KEY,
                        /**
                        * VARCHAR。
                        * @param 1000 1000
                        * @return VARCHAR的结果
                        */
                        v VARCHAR(1000),
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )""");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS console_meta (
                        /**
                        * VARCHAR。
                        * @param 64 64
                        * @return VARCHAR的结果
                        */
                        k VARCHAR(64) PRIMARY KEY,
                        /**
                        * VARCHAR。
                        * @param 64 64
                        * @return VARCHAR的结果
                        */
                        v VARCHAR(64)
                    )""");
        }
    }

 // ==================== @api特征 覆盖 ====================

    public synchronized void upsertFeatureOverride(String featureId, boolean enabled,
                                                   String disabledMessage, int disabledStatus) {
        /**
         * 执行。
         * @param disabled_status 已禁用状态
         */
        execute("INSERT INTO console_feature_override(feature_id, enabled, disabled_message, disabled_status) VALUES(?,?,?,?) "
                        + "ON CONFLICT(feature_id) DO UPDATE SET enabled=excluded.enabled, "
                        + "disabled_message=excluded.disabled_message, disabled_status=excluded.disabled_status",
                featureId, enabled ? 1 : 0, disabledMessage, disabledStatus);
    }

    /**
     * 获取特征override。
     * @param featureId 特征标识
     * @return 获取特征override的结果
     */
    public synchronized FeatureOverrideRow getFeatureOverride(String featureId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT enabled, disabled_message, disabled_status FROM console_feature_override WHERE feature_id=?")) {
            ps.setString(1, featureId);
            /**
             * 尝试。
             * @param ps.executeQuery( ps.执行查询(
             */
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    /**
                     * 特征overriderow。
                     * @param rs.getInt("enabled" R.获取int("已启用"
                     * @return 特征overriderow的结果
                     */
                    return new FeatureOverrideRow(rs.getInt("enabled") != 0,
                            rs.getString("disabled_message"), rs.getInt("disabled_status"));
                }
            }
        } catch (SQLException e) {
            /**
             * illegal状态异常。
             * @param featureId 特征标识
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("读取功能覆盖失败: " + featureId, e);
        }
        return null;
    }

    /**
     * 删除特征override。
     * @param featureId 特征标识
     */
    public synchronized void deleteFeatureOverride(String featureId) {
        /**
         * 执行。
         * @param feature_id=?" 特征标识=?"
         * @param featureId 特征标识
         */
        execute("DELETE FROM console_feature_override WHERE feature_id=?", featureId);
    }

    /**
     * @api特征 覆盖行
     *
     * @param enabled 已启用
     * @param disabledMessage 已禁用消息
     * @param disabledStatus 已禁用状态
     * @return 特征overriderow的结果
     */
    public record FeatureOverrideRow(boolean enabled, String disabledMessage, int disabledStatus) {
    }

 // ==================== @apigray 覆盖 ====================

    public synchronized void upsertGrayOverride(String pattern, Integer percentage, String[] roles,
                                               String[] users, String[] headers, String rule) {
        /**
         * 执行。
         * @param rule rule
         */
        execute("INSERT INTO console_gray_override(pattern, percentage, roles, users, headers, rule) VALUES(?,?,?,?,?,?) "
                        + "ON CONFLICT(pattern) DO UPDATE SET percentage=excluded.percentage, roles=excluded.roles, "
                        + "users=excluded.users, headers=excluded.headers, rule=excluded.rule",
                pattern, percentage, join(roles), join(users), join(headers), rule);
    }

    /**
     * 获取grayoverride。
     * @param pattern 模式
     * @return 获取grayoverride的结果
     */
    public synchronized GrayOverrideRow getGrayOverride(String pattern) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT percentage, roles, users, headers, rule FROM console_gray_override WHERE pattern=?")) {
            ps.setString(1, pattern);
            /**
             * 尝试。
             * @param ps.executeQuery( ps.执行查询(
             */
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    /**
                     * grayoverriderow。
                     * @param rs.getObject("percentage" R.获取对象("percentage"
                     * @return GrayOverrideRow的结果
                     */
                    return new GrayOverrideRow(rs.getObject("percentage") == null ? null : rs.getInt("percentage"),
                            /**
                             * 分割。
                             * @param rs.getString("roles" R.获取字符串("角色"
                             */
                            split(rs.getString("roles")), split(rs.getString("users")),
                            split(rs.getString("headers")), rs.getString("rule"));
                }
            }
        } catch (SQLException e) {
            /**
             * illegal状态异常。
             * @param pattern 模式
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("读取灰度覆盖失败: " + pattern, e);
        }
        return null;
    }

    /**
     * 删除grayoverride。
     * @param pattern 模式
     */
    public synchronized void deleteGrayOverride(String pattern) {
        /**
         * 执行。
         * @param pattern=?" 模式=?"
         * @param pattern 模式
         */
        execute("DELETE FROM console_gray_override WHERE pattern=?", pattern);
    }

    /**
     * @apigray 覆盖行
     *
     * @param percentage percentage
     * @param roles 角色
     * @param users 用户
     * @param headers 头部
     * @param rule rule
     * @return GrayOverrideRow的结果
     */
    public record GrayOverrideRow(Integer percentage, String[] roles, String[] users, String[] headers, String rule) {

        /**
         * 规范构造器：数组做防御性拷贝。
         *
         * <p>value class 前置条件——数组组件必须深不可变；
         * 写入侧 {@code join} 允许传入 {@code null} 数组，但读取侧 {@code split}
         * 恒返回非 {@code null} 数组（空串返回长度 0 数组），故此处按非空校验。</p>
         *
         * @param roles 角色数组
         * @param users 用户数组
         * @param headers 请求头数组
         */
        public GrayOverrideRow {
            roles = Objects.requireNonNull(roles, "roles 不能为 null").clone();
            users = Objects.requireNonNull(users, "users 不能为 null").clone();
            headers = Objects.requireNonNull(headers, "headers 不能为 null").clone();
        }

        /**
         * 访问器覆写：返回内部数组的副本。
         *
         * @return 角色数组副本
         */
        @Override
        public String[] roles() {
            return roles.clone();
        }

        /**
         * 访问器覆写：返回内部数组的副本。
         *
         * @return 用户数组副本
         */
        @Override
        public String[] users() {
            return users.clone();
        }

        /**
         * 访问器覆写：返回内部数组的副本。
         *
         * @return 请求头数组副本
         */
        @Override
        public String[] headers() {
            return headers.clone();
        }
    }

    // ==================== 通用键值 ====================

    /**
     * 设置kv。
     * @param key Key
     * @param value 值
     */
    public synchronized void setKv(String key, String value) {
        /**
         * 执行。
         * @param v v
         */
        execute("INSERT INTO console_kv(k, v) VALUES(?,?) ON CONFLICT(k) DO UPDATE SET v=excluded.v", key, value);
    }

    /**
     * 获取kv。
     * @param key Key
     * @return 获取kv的结果
     */
    public synchronized String getKv(String key) {
        /**
         * 尝试。
         * @param k=?" k=?"
         */
        try (PreparedStatement ps = connection.prepareStatement("SELECT v FROM console_kv WHERE k=?")) {
            ps.setString(1, key);
            /**
             * 尝试。
             * @param ps.executeQuery( ps.执行查询(
             */
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("v");
                }
            }
        } catch (SQLException e) {
            /**
             * illegal状态异常。
             * @param key Key
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("读取键值失败: " + key, e);
        }
        return null;
    }

    /**
     * 删除kv。
     * @param key Key
     */
    public synchronized void deleteKv(String key) {
        /**
         * 执行。
         * @param k=?" k=?"
         * @param key Key
         */
        execute("DELETE FROM console_kv WHERE k=?", key);
    }

    /**
     * 列表kv。
     * @param prefix 前缀
     * @return 列表kv的结果
     */
    public synchronized Map<String, String> listKv(String prefix) {
        Map<String, String> result = new LinkedHashMap<>();
        String sql = "SELECT k, v FROM console_kv" + (prefix != null && !prefix.isEmpty() ? " WHERE k LIKE ?" : "");
        /**
         * 尝试。
         * @param connection.prepareStatement(sql connection.prepare对账单(SQL
         */
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (prefix != null && !prefix.isEmpty()) {
                ps.setString(1, prefix + "%");
            }
            /**
             * 尝试。
             * @param ps.executeQuery( ps.执行查询(
             */
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("k"), rs.getString("v"));
                }
            }
        } catch (SQLException e) {
            /**
             * illegal状态异常。
             * @param "列表键值失败" "列表键值失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("列表键值失败", e);
        }
        return result;
    }

    // ==================== 指纹与重置 ====================

    /**
     * 获取fingerprint。
     * @return 获取fingerprint的结果
     */
    public synchronized String getFingerprint() {
        /**
         * 尝试。
         * @param k='annotation-fingerprint'" k='注解-fingerprint'"
         */
        try (PreparedStatement ps = connection.prepareStatement("SELECT v FROM console_meta WHERE k='annotation-fingerprint'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString("v") : null;
        } catch (SQLException e) {
            /**
             * illegal状态异常。
             * @param "读取指纹失败" "读取指纹失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("读取指纹失败", e);
        }
    }

    /**
     * 设置fingerprint。
     * @param fingerprint fingerprint
     */
    public synchronized void setFingerprint(String fingerprint) {
        /**
         * 执行。
         * @param v v
         */
        execute("INSERT INTO console_meta(k, v) VALUES('annotation-fingerprint', ?) "
                + "ON CONFLICT(k) DO UPDATE SET v=excluded.v", fingerprint);
    }

    /**
     * 重置注解派生覆盖（特征/gray 覆盖表清空；键值设置属用户数据，不重置）
     * @return resetOverrides的结果
     */
    public synchronized int resetOverrides() {
        int features = executeUpdate("DELETE FROM console_feature_override");
        int grays = executeUpdate("DELETE FROM console_gray_override");
        log.warn("[Console] 注解指纹变化，已重置覆盖库: @ApiFeature 覆盖 {} 条, @ApiGray 覆盖 {} 条", features, grays);
        return features + grays;
    }

    @Override
    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            log.warn("[Console] SQLite 关闭异常: {}", e.getMessage());
        }
    }

    // ==================== 工具 ====================

    /**
     * 执行。
     * @param sql SQL
     * @param args 参数
     */
    private void execute(String sql, Object... args) {
        /**
         * 执行更新。
         * @param sql SQL
         * @param args 参数
         */
        executeUpdate(sql, args);
    }

    /**
     * 执行更新。
     * @param sql SQL
     * @param args 参数
     * @return 执行更新的结果
     */
    private synchronized int executeUpdate(String sql, Object... args) {
        /**
         * 尝试。
         * @param connection.prepareStatement(sql connection.prepare对账单(SQL
         */
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            /**
             * illegal状态异常。
             * @param sql SQL
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("SQLite 执行失败: " + sql, e);
        }
    }

    /**
     * 连接。
     * @param arr arr
     * @return 连接的结果
     */
    private static String join(String[] arr) {
        if (arr == null || arr.length == 0) {
            return null;
        }
        return String.join(",", arr);
    }

    /**
     * 分割。
     * @param s s
     * @return 分割的结果
     */
    private static String[] split(String s) {
        if (s == null || s.isEmpty()) {
            return new String[0];
        }
        return s.split(",");
    }

    /**
     * 全部覆盖行（页面列表用）
     *
     * @return 列表特征overrides的结果
     */
    public synchronized List<Map<String, Object>> listFeatureOverrides() {
        List<Map<String, Object>> rows = new ArrayList<>();
        /**
         * 尝试。
         * @param connection.createStatement( connection.创建对账单(
         */
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT feature_id, enabled, disabled_message, disabled_status FROM console_feature_override")) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("featureId", rs.getString("feature_id"));
                row.put("enabled", rs.getInt("enabled") != 0);
                row.put("disabledMessage", rs.getString("disabled_message"));
                row.put("disabledStatus", rs.getInt("disabled_status"));
                rows.add(row);
            }
        } catch (SQLException e) {
            /**
             * illegal状态异常。
             * @param "列表功能覆盖失败" "列表功能覆盖失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("列表功能覆盖失败", e);
        }
        return rows;
    }

    /**
     * 全部灰度覆盖行（页面列表用）
     *
     * @return 列表grayoverrides的结果
     */
    public synchronized List<Map<String, Object>> listGrayOverrides() {
        List<Map<String, Object>> rows = new ArrayList<>();
        /**
         * 尝试。
         * @param connection.createStatement( connection.创建对账单(
         */
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT pattern, percentage, roles, users, headers, rule FROM console_gray_override")) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("pattern", rs.getString("pattern"));
                row.put("percentage", rs.getObject("percentage"));
                row.put("roles", rs.getString("roles"));
                row.put("users", rs.getString("users"));
                row.put("headers", rs.getString("headers"));
                row.put("rule", rs.getString("rule"));
                rows.add(row);
            }
        } catch (SQLException e) {
            /**
             * illegal状态异常。
             * @param "列表灰度覆盖失败" "列表灰度覆盖失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("列表灰度覆盖失败", e);
        }
        return rows;
    }
}
