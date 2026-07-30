package com.chua.starter.panel.service.impl;

import com.chua.starter.panel.service.InfluxV3PanelService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认 InfluxDB 3.x 面板服务（基于 JDK HttpClient + Jackson，无额外依赖）。
 * InfluxDB 3.x 使用 SQL 查询，HTTP API v3。
 *
 * @author CH
 */
public class DefaultInfluxV3PanelService implements InfluxV3PanelService {

    /** 连接信息 */
    private record InfluxV3Connection(String baseUrl, String token, String defaultDatabase) {}

    private final Map<String, InfluxV3Connection> connectionMap = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String open(String host, int port, String token, String defaultDatabase) {
        String baseUrl = "http://" + host + ":" + port;
        String connectionId = UUID.randomUUID().toString();
        InfluxV3Connection conn = new InfluxV3Connection(baseUrl, token, defaultDatabase);
        connectionMap.put(connectionId, conn);
        // 测试连接
        try {
            getHealth(conn);
        } catch (Exception e) {
            connectionMap.remove(connectionId);
            throw new RuntimeException("InfluxDB 3.x 连接失败: " + e.getMessage(), e);
        }
        return connectionId;
    }

    @Override
    public void close(String connectionId) {
        connectionMap.remove(connectionId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> listDatabases(String connectionId) {
        InfluxV3Connection conn = getConn(connectionId);
        try {
            // 使用 SQL SHOW DATABASES
            List<Map<String, Object>> rows = executeSql(conn, null, "SHOW DATABASES");
            List<String> databases = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                // 结果列名可能是 "iox::database" 或 "database_name"
                Object val = row.values().stream().findFirst().orElse(null);
                if (val != null) {
                    databases.add(String.valueOf(val));
                }
            }
            return databases;
        } catch (Exception e) {
            throw new RuntimeException("列出数据库失败: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> listTables(String connectionId, String database) {
        InfluxV3Connection conn = getConn(connectionId);
        try {
            List<Map<String, Object>> rows = executeSql(conn, database, "SHOW TABLES");
            List<String> tables = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                // 结果列名可能是 "table_name" 或第一列
                Object val = row.getOrDefault("table_name",
                        row.values().stream().findFirst().orElse(null));
                if (val != null) {
                    tables.add(String.valueOf(val));
                }
            }
            return tables;
        } catch (Exception e) {
            throw new RuntimeException("列出表失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> query(String connectionId, String database, String sql) {
        InfluxV3Connection conn = getConn(connectionId);
        try {
            return executeSql(conn, database, sql);
        } catch (Exception e) {
            throw new RuntimeException("SQL 查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void write(String connectionId, String database, String lineProtocol) {
        InfluxV3Connection conn = getConn(connectionId);
        try {
            String url = conn.baseUrl() + "/api/v3/write?database=" + encode(database);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + conn.token())
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(lineProtocol))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("写入失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> health(String connectionId) {
        InfluxV3Connection conn = getConn(connectionId);
        return getHealth(conn);
    }

    // ─── 内部工具 ──────────────────────────────────────────────────────────────

    private InfluxV3Connection getConn(String connectionId) {
        InfluxV3Connection conn = connectionMap.get(connectionId);
        if (conn == null) throw new IllegalStateException("InfluxDB 3.x 连接不存在: " + connectionId);
        return conn;
    }

    /**
     * 执行 SQL 查询，返回行列表。
     * POST /api/v3/query_sql body: {"database":"xxx","query":"SELECT ..."}
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> executeSql(InfluxV3Connection conn, String database, String sql) {
        try {
            String db = (database != null && !database.isBlank()) ? database : conn.defaultDatabase();
            Map<String, Object> bodyMap = new LinkedHashMap<>();
            if (db != null && !db.isBlank()) {
                bodyMap.put("database", db);
            }
            bodyMap.put("query", sql);
            String bodyJson = objectMapper.writeValueAsString(bodyMap);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(conn.baseUrl() + "/api/v3/query_sql"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + conn.token())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }

            String body = response.body();
            if (body == null || body.isBlank()) {
                return Collections.emptyList();
            }

            // InfluxDB 3.x 响应可能是 JSON 数组（行列表）或包含 results 字段的对象
            body = body.trim();
            if (body.startsWith("[")) {
                return objectMapper.readValue(body, new TypeReference<List<Map<String, Object>>>() {});
            }

            // 尝试解析为对象，提取 results 字段
            Map<String, Object> parsed = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            Object results = parsed.get("results");
            if (results instanceof List<?> list) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        rows.add((Map<String, Object>) m);
                    }
                }
                return rows;
            }
            return Collections.emptyList();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("SQL 执行失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> getHealth(InfluxV3Connection conn) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(conn.baseUrl() + "/health"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + conn.token())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }
            String body = response.body();
            if (body == null || body.isBlank()) {
                return Map.of("status", "pass");
            }
            return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("健康检查失败: " + e.getMessage(), e);
        }
    }

    private static String encode(String value) {
        if (value == null) return "";
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
