package com.chua.starter.panel.service.impl;

import com.chua.starter.panel.service.PrometheusPanelService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认 Prometheus 面板服务（基于 JDK HttpClient + Jackson，无额外依赖）。
 *
 * @author CH
 */
public class DefaultPrometheusPanelService implements PrometheusPanelService {

    /** 连接信息 */
    private record PrometheusConnection(String baseUrl, String basicAuth) {}

    private final Map<String, PrometheusConnection> connectionMap = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String open(String host, int port, String username, String password) {
        String baseUrl = "http://" + host + ":" + port;
        String basicAuth = null;
        if (username != null && !username.isBlank()) {
            String credentials = username + ":" + (password == null ? "" : password);
            basicAuth = "Basic " + Base64.getEncoder().encodeToString(
                    credentials.getBytes(StandardCharsets.UTF_8));
        }
        // 测试连接
        String connectionId = UUID.randomUUID().toString();
        PrometheusConnection conn = new PrometheusConnection(baseUrl, basicAuth);
        connectionMap.put(connectionId, conn);
        try {
            get(conn, "/api/v1/labels");
        } catch (Exception e) {
            connectionMap.remove(connectionId);
            throw new RuntimeException("Prometheus 连接失败: " + e.getMessage(), e);
        }
        return connectionId;
    }

    @Override
    public void close(String connectionId) {
        connectionMap.remove(connectionId);
    }

    @Override
    public Map<String, Object> query(String connectionId, String promql, String time) {
        PrometheusConnection conn = getConn(connectionId);
        String path = "/api/v1/query?query=" + encode(promql)
                + (time != null && !time.isBlank() ? "&time=" + encode(time) : "");
        return get(conn, path);
    }

    @Override
    public Map<String, Object> queryRange(String connectionId, String promql,
                                          String start, String end, String step) {
        PrometheusConnection conn = getConn(connectionId);
        String path = "/api/v1/query_range?query=" + encode(promql)
                + "&start=" + encode(start)
                + "&end=" + encode(end)
                + "&step=" + encode(step);
        return get(conn, path);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> listMetrics(String connectionId) {
        PrometheusConnection conn = getConn(connectionId);
        Map<String, Object> resp = get(conn, "/api/v1/label/__name__/values");
        Object data = resp.get("data");
        if (data instanceof List<?> list) {
            return (List<String>) list;
        }
        return Collections.emptyList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> listLabels(String connectionId) {
        PrometheusConnection conn = getConn(connectionId);
        Map<String, Object> resp = get(conn, "/api/v1/labels");
        Object data = resp.get("data");
        if (data instanceof List<?> list) {
            return (List<String>) list;
        }
        return Collections.emptyList();
    }

    @Override
    public Map<String, Object> listTargets(String connectionId) {
        return get(getConn(connectionId), "/api/v1/targets");
    }

    @Override
    public Map<String, Object> alerts(String connectionId) {
        return get(getConn(connectionId), "/api/v1/alerts");
    }

    @Override
    public Map<String, Object> rules(String connectionId) {
        return get(getConn(connectionId), "/api/v1/rules");
    }

    // ─── 内部工具 ──────────────────────────────────────────────────────────────

    private PrometheusConnection getConn(String connectionId) {
        PrometheusConnection conn = connectionMap.get(connectionId);
        if (conn == null) throw new IllegalStateException("Prometheus 连接不存在: " + connectionId);
        return conn;
    }

    private Map<String, Object> get(PrometheusConnection conn, String path) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(conn.baseUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .GET();
            if (conn.basicAuth() != null) {
                builder.header("Authorization", conn.basicAuth());
            }
            HttpResponse<String> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return objectMapper.readValue(response.body(),
                    new TypeReference<Map<String, Object>>() {});
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Prometheus 请求失败: " + e.getMessage(), e);
        }
    }

    private static String encode(String value) {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
