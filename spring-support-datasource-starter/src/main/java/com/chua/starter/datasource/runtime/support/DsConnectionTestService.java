// [P3C A20 豁免] A20/A30/A31: 日志字符串字面量场景，豁免。
package com.chua.starter.datasource.runtime.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据源连接测试服务（联邦成员 / 分库成员保存前的可用性预检）。
 *
 * <p>按给定 JDBC 参数真实建立连接并执行 SELECT 1，登录超时 5 秒；
 * 失败不抛异常，以 ok=false + message 返回，便于前端直接展示原因。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
public class DsConnectionTestService {

    /**
     * 登录/连接超时秒数（预检不应长时间挂起）
     */
    private static final int LOGIN_TIMEOUT_SECONDS = 5;

    /**
     * 测试连接。结果字段：ok、latencyMs、server、version、message。
     *
     * @param url             JDBC URL
     * @param username        用户名（留空按无凭据连接）
     * @param password        密码（仅用于本次连接，不落库不打日志）
     * @param driverClassName 可选驱动类名；加载失败交由 DriverManager 按 URL 探测
     * @return ok/latencyMs/server/version/message
     */
    public Map<String, Object> test(String url, String username, String password, String driverClassName) {
        Map<String, Object> result = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        String trimmed = url == null ? "" : url.trim();
        if (trimmed.isEmpty()) {
            return fail(result, 0L, "JDBC URL 不能为空");
        }
        if (!trimmed.toLowerCase().startsWith("jdbc:")) {
            return fail(result, 0L, "JDBC URL 必须以 jdbc: 开头");
        }
        int previousTimeout = DriverManager.getLoginTimeout();
        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
        try {
            if (driverClassName != null && !driverClassName.isBlank()) {
                try {
                    Class.forName(driverClassName.trim());
                } catch (Exception e) {
                    log.warn("[ds-runtime] 指定驱动类加载失败（交由 DriverManager 按 URL 探测）: {}", e.getMessage());
                }
            }
            Connection conn = (username == null || username.isBlank())
                    ? DriverManager.getConnection(trimmed)
                    : DriverManager.getConnection(trimmed, username.trim(), password == null ? "" : password);
            try {
                fillSuccess(result, conn, System.currentTimeMillis() - start);
            } finally {
                closeQuietly(conn);
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            result.put("ok", false);
            result.put("latencyMs", latency);
            result.put("message", "连接失败: " + e.getMessage());
            log.warn("[ds-runtime] 连接测试失败: url={}, elapsed={}ms, error={}", trimmed, latency, e.getMessage());
        } finally {
            DriverManager.setLoginTimeout(previousTimeout);
        }
        return result;
    }

    /**
     * 连接建立后补齐服务端信息与 SELECT 1 探测结果。
     */
    private void fillSuccess(Map<String, Object> result, Connection conn, long latency) {
        String pingError = null;
        boolean ping;
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1")) {
            ping = rs.next();
        } catch (Exception e) {
            ping = false;
            pingError = e.getMessage();
        }
        result.put("ok", ping);
        result.put("latencyMs", latency);
        try {
            DatabaseMetaData meta = conn.getMetaData();
            result.put("server", meta.getDatabaseProductName());
            result.put("version", meta.getDatabaseProductVersion());
        } catch (Exception e) {
            result.put("server", "unknown");
        }
        result.put("message", ping ? "连接成功（SELECT 1 通过）" : "连接已建立但测试查询失败: " + pingError);
    }

    private Map<String, Object> fail(Map<String, Object> result, long latency, String message) {
        result.put("ok", false);
        result.put("latencyMs", latency);
        result.put("message", message);
        return result;
    }

    private void closeQuietly(Connection conn) {
        try {
            conn.close();
        } catch (Exception ignored) {
            // 关闭失败不影响测试结论
        }
    }
}
