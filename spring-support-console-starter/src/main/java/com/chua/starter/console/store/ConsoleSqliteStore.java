package com.chua.starter.console.store;

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

/**
 * 管理控制台 SQLite 持久层（自包含 sqlite-jdbc，单连接同步访问）。
 *
 * <p>四表：{@code console_feature_override}（@ApiFeature 覆盖）、{@code console_gray_override}
 * （@ApiGray 覆盖）、{@code console_kv}（通用键值运行设置）、{@code console_meta}（注解指纹）。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@Slf4j
public class ConsoleSqliteStore implements AutoCloseable {

    private final Connection connection;

    public ConsoleSqliteStore(ConsoleProperties properties) {
        try {
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + properties.getSqlitePath());
            initSchema();
            log.info("[Console] SQLite 持久层就绪: {}", properties.getSqlitePath());
        } catch (Exception e) {
            throw new IllegalStateException("SQLite 持久层初始化失败: " + properties.getSqlitePath(), e);
        }
    }

    private void initSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS console_feature_override (
                        feature_id VARCHAR(128) PRIMARY KEY,
                        enabled INTEGER NOT NULL,
                        disabled_message VARCHAR(255),
                        disabled_status INTEGER DEFAULT -1,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )""");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS console_gray_override (
                        pattern VARCHAR(128) PRIMARY KEY,
                        percentage INTEGER,
                        roles VARCHAR(500),
                        users VARCHAR(500),
                        headers VARCHAR(500),
                        rule VARCHAR(500),
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )""");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS console_kv (
                        k VARCHAR(255) PRIMARY KEY,
                        v VARCHAR(1000),
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )""");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS console_meta (
                        k VARCHAR(64) PRIMARY KEY,
                        v VARCHAR(64)
                    )""");
        }
    }

    // ==================== @ApiFeature 覆盖 ====================

    public synchronized void upsertFeatureOverride(String featureId, boolean enabled,
                                                   String disabledMessage, int disabledStatus) {
        execute("INSERT INTO console_feature_override(feature_id, enabled, disabled_message, disabled_status) VALUES(?,?,?,?) "
                        + "ON CONFLICT(feature_id) DO UPDATE SET enabled=excluded.enabled, "
                        + "disabled_message=excluded.disabled_message, disabled_status=excluded.disabled_status",
                featureId, enabled ? 1 : 0, disabledMessage, disabledStatus);
    }

    public synchronized FeatureOverrideRow getFeatureOverride(String featureId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT enabled, disabled_message, disabled_status FROM console_feature_override WHERE feature_id=?")) {
            ps.setString(1, featureId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new FeatureOverrideRow(rs.getInt("enabled") != 0,
                            rs.getString("disabled_message"), rs.getInt("disabled_status"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("读取功能覆盖失败: " + featureId, e);
        }
        return null;
    }

    public synchronized void deleteFeatureOverride(String featureId) {
        execute("DELETE FROM console_feature_override WHERE feature_id=?", featureId);
    }

    /** @ApiFeature 覆盖行 */
    public record FeatureOverrideRow(boolean enabled, String disabledMessage, int disabledStatus) {
    }

    // ==================== @ApiGray 覆盖 ====================

    public synchronized void upsertGrayOverride(String pattern, Integer percentage, String[] roles,
                                               String[] users, String[] headers, String rule) {
        execute("INSERT INTO console_gray_override(pattern, percentage, roles, users, headers, rule) VALUES(?,?,?,?,?,?) "
                        + "ON CONFLICT(pattern) DO UPDATE SET percentage=excluded.percentage, roles=excluded.roles, "
                        + "users=excluded.users, headers=excluded.headers, rule=excluded.rule",
                pattern, percentage, join(roles), join(users), join(headers), rule);
    }

    public synchronized GrayOverrideRow getGrayOverride(String pattern) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT percentage, roles, users, headers, rule FROM console_gray_override WHERE pattern=?")) {
            ps.setString(1, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new GrayOverrideRow(rs.getObject("percentage") == null ? null : rs.getInt("percentage"),
                            split(rs.getString("roles")), split(rs.getString("users")),
                            split(rs.getString("headers")), rs.getString("rule"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("读取灰度覆盖失败: " + pattern, e);
        }
        return null;
    }

    public synchronized void deleteGrayOverride(String pattern) {
        execute("DELETE FROM console_gray_override WHERE pattern=?", pattern);
    }

    /** @ApiGray 覆盖行 */
    public record GrayOverrideRow(Integer percentage, String[] roles, String[] users, String[] headers, String rule) {
    }

    // ==================== 通用键值 ====================

    public synchronized void setKv(String key, String value) {
        execute("INSERT INTO console_kv(k, v) VALUES(?,?) ON CONFLICT(k) DO UPDATE SET v=excluded.v", key, value);
    }

    public synchronized String getKv(String key) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT v FROM console_kv WHERE k=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("v");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("读取键值失败: " + key, e);
        }
        return null;
    }

    public synchronized void deleteKv(String key) {
        execute("DELETE FROM console_kv WHERE k=?", key);
    }

    public synchronized Map<String, String> listKv(String prefix) {
        Map<String, String> result = new LinkedHashMap<>();
        String sql = "SELECT k, v FROM console_kv" + (prefix != null && !prefix.isEmpty() ? " WHERE k LIKE ?" : "");
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (prefix != null && !prefix.isEmpty()) {
                ps.setString(1, prefix + "%");
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("k"), rs.getString("v"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("列表键值失败", e);
        }
        return result;
    }

    // ==================== 指纹与重置 ====================

    public synchronized String getFingerprint() {
        try (PreparedStatement ps = connection.prepareStatement("SELECT v FROM console_meta WHERE k='annotation-fingerprint'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString("v") : null;
        } catch (SQLException e) {
            throw new IllegalStateException("读取指纹失败", e);
        }
    }

    public synchronized void setFingerprint(String fingerprint) {
        execute("INSERT INTO console_meta(k, v) VALUES('annotation-fingerprint', ?) "
                + "ON CONFLICT(k) DO UPDATE SET v=excluded.v", fingerprint);
    }

    /**
     * 重置注解派生覆盖（feature/gray 覆盖表清空；键值设置属用户数据，不重置）
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

    private void execute(String sql, Object... args) {
        executeUpdate(sql, args);
    }

    private synchronized int executeUpdate(String sql, Object... args) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("SQLite 执行失败: " + sql, e);
        }
    }

    private static String join(String[] arr) {
        if (arr == null || arr.length == 0) {
            return null;
        }
        return String.join(",", arr);
    }

    private static String[] split(String s) {
        if (s == null || s.isEmpty()) {
            return new String[0];
        }
        return s.split(",");
    }

    /** 全部覆盖行（页面列表用） */
    public synchronized List<Map<String, Object>> listFeatureOverrides() {
        List<Map<String, Object>> rows = new ArrayList<>();
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
            throw new IllegalStateException("列表功能覆盖失败", e);
        }
        return rows;
    }

    /** 全部灰度覆盖行（页面列表用） */
    public synchronized List<Map<String, Object>> listGrayOverrides() {
        List<Map<String, Object>> rows = new ArrayList<>();
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
            throw new IllegalStateException("列表灰度覆盖失败", e);
        }
        return rows;
    }
}
