package com.chua.starter.ai.support.manage.store.datasource;

import com.chua.common.support.lang.datasource.dialect.SqlName;
import com.chua.common.support.spi.annotations.Spi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 本地 MySQL 数据库需求数据源提供方。
 *
 * <p>从另一台 MySQL 数据库（或本机）的 {@code sys_ai_requirement} 表中读取需求条目，
 * 写入本地库。适用于多实例同步、备份恢复等场景。</p>
 *
 * <h3>配置字段（JSON 存入 sys_ai_data_source.config）：</h3>
 * <pre>
 * {
 *   "host":     "127.0.0.1",
 *   "port":     3306,
 *   "database": "ai_test",
 *   "username": "root",
 *   "password": "root@",
 *   "table":    "sys_ai_requirement"
 * }
 * </pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@Spi("local")
public class LocalDbRequirementDataSource implements IRequirementDataSource {

    private static final String TYPE = "local"; // 类型
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10); // connection超时
    private static final int MAX_POOL_SIZE = 2; // 最大游泳池大小

    /**
     * 数量查询语句，表名由 {@link SqlName} 校验后填充。
     */
    private static final String COUNT_SQL =
            """
            /**
    * 数量。
    * @param * *
    * @return 数量的结果
    */
            SELECT COUNT(*) FROM `%s`
            """;

    /**
     * 获取 查询：读取远程表全部需求列（不含 extra JSON，避免序列化问题）
    */
    private static final String FETCH_SQL =
            """
            SELECT sys_ai_requirement_id, sys_ai_requirement_title,
                   sys_ai_requirement_content, sys_ai_requirement_tag,
                   sys_ai_requirement_status, sys_ai_requirement_order_key,
                   sys_ai_requirement_completion_note,
                   sys_ai_requirement_owner_conversation_id, sys_ai_requirement_owner_terminal_id,
                   sys_ai_requirement_origin, sys_ai_requirement_started_at,
                   sys_ai_requirement_completed_at, sys_ai_requirement_attempt_count,
                   create_time, update_time
            FROM `%s`
            ORDER BY sys_ai_requirement_id
            """;

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * 从远程 MySQL 读取所有需求，返回条数（不实际写入本地）。
     * 实际写入由调用方（sysairequirement存储.同步数据源）负责。
     * @param config 配置
     * @param lastSyncAt 最后一个同步at
     * @return 同步的结果
     */
    @Override
    @SuppressWarnings("unchecked")
    public int sync(Map<String, Object> config, long lastSyncAt) {
        HikariDataSource ds = buildDataSource(config);
        if (ds == null) {
            log.warn("[LocalDbDS] 无法构建数据源，配置缺失");
            return 0;
        }
        try {
            String table = validateTableName(getString(config, "table", "sys_ai_requirement"));
            if (table == null) {
                log.warn("[LocalDbDS] 非法的表名，已拒绝");
                return 0;
            }
            String sql = COUNT_SQL.formatted(table);

            int count = 0;
            /**
             * 尝试。
             * @param ds.getConnection( ds.获取connection(
             */
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    count = rs.getInt(1);
                }
            } catch (Exception e) {
                log.error("[LocalDbDS] 读取远程需求失败: {}", e.getMessage(), e);
                return 0;
            }
            return count;
        } finally {
            ds.close();
        }
    }

    /**
     * 读取远程需求完整列表（供 存储 层直接写入）。
     *
     * @return 远程需求记录列表
     * @param config 配置
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchRemoteRequirements(Map<String, Object> config) {
        HikariDataSource ds = buildDataSource(config);
        if (ds == null) {
            return Collections.emptyList();
        }
        try {
            String table = validateTableName(getString(config, "table", "sys_ai_requirement"));
            if (table == null) {
                log.warn("[LocalDbDS] 非法的表名，已拒绝");
                return Collections.emptyList();
            }
            String sql = FETCH_SQL.formatted(table);

            List<Map<String, Object>> result = new ArrayList<>();
            /**
             * 尝试。
             * @param ds.getConnection( ds.获取connection(
             */
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("requirement_id", rs.getString("sys_ai_requirement_id"));
                    row.put("title", rs.getString("sys_ai_requirement_title"));
                    row.put("content", rs.getString("sys_ai_requirement_content"));
                    row.put("tag", rs.getString("sys_ai_requirement_tag"));
                    row.put("status", rs.getString("sys_ai_requirement_status"));
                    row.put("order_key", rs.getString("sys_ai_requirement_order_key"));
                    row.put("completion_note", rs.getString("sys_ai_requirement_completion_note"));
                    row.put("owner_conversation_id", rs.getString("sys_ai_requirement_owner_conversation_id"));
                    row.put("owner_terminal_id", rs.getString("sys_ai_requirement_owner_terminal_id"));
                    row.put("origin", rs.getString("sys_ai_requirement_origin"));
                    row.put("started_at", rs.getLong("sys_ai_requirement_started_at"));
                    row.put("completed_at", rs.getLong("sys_ai_requirement_completed_at"));
                    row.put("attempt_count", rs.getInt("sys_ai_requirement_attempt_count"));
                    row.put("created_at", rs.getLong("create_time"));
                    row.put("updated_at", rs.getLong("update_time"));
                    result.add(row);
                }
            } catch (Exception e) {
                log.error("[LocalDbDS] 读取远程需求失败: {}", e.getMessage(), e);
            }
            return result;
        } finally {
            ds.close();
        }
    }

    @Override
    public boolean testConnection(Map<String, Object> config) {
        HikariDataSource ds = buildDataSource(config);
        if (ds == null) {
            return false;
        }
        /**
         * 尝试。
         * @param ds.getConnection( ds.获取connection(
         */
        try (Connection conn = ds.getConnection()) {
            return conn.isValid(3);
        } catch (Exception e) {
            log.warn("[LocalDbDS] 连接测试失败: {}", e.getMessage());
            return false;
        } finally {
            ds.close();
        }
    }

    /**
     * 构建数据源。
     * @param config 配置
     * @return 构建数据源的结果
     */
    private HikariDataSource buildDataSource(Map<String, Object> config) {
        String host = getString(config, "host", "127.0.0.1");
        int port = getInt(config, "port", 3306);
        String database = getString(config, "database");
        String username = getString(config, "username");
        String password = getString(config, "password");

        if (database == null || database.isEmpty() || username == null) {
            log.warn("[LocalDbDS] 缺少必要配置: database={}, username={}", database, username);
            return null;
        }

        try {
            HikariConfig hc = new HikariConfig();
            hc.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                    host, port, database));
            hc.setUsername(username);
            hc.setPassword(password != null ? password : "");
            hc.setMaximumPoolSize(MAX_POOL_SIZE);
            hc.setMinimumIdle(1);
            hc.setConnectionTimeout(CONNECTION_TIMEOUT.toMillis());
            hc.setPoolName("local-ds-sync-" + database);
            hc.setAutoCommit(true);
            /**
             * hikari数据源。
             * @param hc hc
             * @return hikari数据源的结果
             */
            return new HikariDataSource(hc);
        } catch (Exception e) {
            log.error("[LocalDbDS] 数据源构建失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 校验表名合法性，仅允许字母、数字、下划线，防 SQL 注入。
     *
     * @param raw 原始表名
     * @return 校验通过的表名，非法时返回 空
     */
    private String validateTableName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (!SqlName.isSimple(raw)) {
            return null;
        }
        return raw;
    }

    /**
     * 获取字符串。
     * @param map 映射
     * @param key Key
     * @return 获取字符串的结果
     */
    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    /**
     * 获取字符串。
     * @param map 映射
     * @param key Key
     * @param defaultValue 默认值
     * @return 获取字符串的结果
     */
    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v != null ? v.toString() : defaultValue;
    }

    /**
     * 获取int。
     * @param map 映射
     * @param key Key
     * @param defaultValue 默认值
     * @return 获取int的结果
     */
    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object v = map.get(key);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        return defaultValue;
    }
}
