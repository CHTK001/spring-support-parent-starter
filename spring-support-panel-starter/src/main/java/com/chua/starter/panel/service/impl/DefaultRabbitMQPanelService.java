package com.chua.starter.panel.service.impl;

import com.chua.starter.panel.service.RabbitMQPanelService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 默认 RabbitMQ 面板服务。
 * 使用 RabbitMQ HTTP Management API 实现管理操作，不依赖额外 SDK。
 *
 * @author CH
 */
public class DefaultRabbitMQPanelService implements RabbitMQPanelService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPE = new TypeReference<>() {};
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private record RabbitConnection(String managementUrl, String username, String password) {}

    private final Map<String, RabbitConnection> connectionMap = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    @Override
    public String open(String host, int port, String username, String password, int managementPort) {
        int actualMgmtPort = managementPort > 0 ? managementPort : port + 10000;
        String managementUrl = "http://" + host + ":" + actualMgmtPort + "/api";
        // verify connection by fetching overview
        try {
            String response = doGet(managementUrl + "/overview", username, password);
            MAPPER.readValue(response, MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ Management API 连接失败 (" + managementUrl + "): " + e.getMessage(), e);
        }
        String connectionId = UUID.randomUUID().toString();
        connectionMap.put(connectionId, new RabbitConnection(managementUrl, username, password));
        return connectionId;
    }

    @Override
    public void close(String connectionId) {
        connectionMap.remove(connectionId);
    }

    @Override
    public Map<String, Object> overview(String connectionId) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            String json = doGet(conn.managementUrl() + "/overview", conn.username(), conn.password());
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 概览查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> listNodes(String connectionId) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            String json = doGet(conn.managementUrl() + "/nodes", conn.username(), conn.password());
            return MAPPER.readValue(json, LIST_MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 节点查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> listQueues(String connectionId) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            String json = doGet(conn.managementUrl() + "/queues?page=1&page_size=500&use_regex=false&pagination=true",
                    conn.username(), conn.password());
            return parseListOrPaginated(json);
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 队列查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> queueDetail(String connectionId, String queueName) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            String vhost = extractVhost(queueName);
            String encodedVhost = encodeVhost(vhost);
            String encodedQueue = encodeName(queueName);
            String json = doGet(conn.managementUrl() + "/queues/" + encodedVhost + "/" + encodedQueue,
                    conn.username(), conn.password());
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 队列详情查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> listExchanges(String connectionId) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            String json = doGet(conn.managementUrl() + "/exchanges?page=1&page_size=500&use_regex=false&pagination=true",
                    conn.username(), conn.password());
            return parseListOrPaginated(json);
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 交换机查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> exchangeDetail(String connectionId, String exchangeName) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            String vhost = extractVhost(exchangeName);
            String encodedVhost = encodeVhost(vhost);
            String encodedExchange = encodeName(exchangeName);
            String json = doGet(conn.managementUrl() + "/exchanges/" + encodedVhost + "/" + encodedExchange,
                    conn.username(), conn.password());
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 交换机详情查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> listBindings(String connectionId) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            String json = doGet(conn.managementUrl() + "/bindings?page=1&page_size=500&use_regex=false&pagination=true",
                    conn.username(), conn.password());
            return parseListOrPaginated(json);
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 绑定查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> listConnections(String connectionId) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            String json = doGet(conn.managementUrl() + "/connections", conn.username(), conn.password());
            return MAPPER.readValue(json, LIST_MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 连接查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> listChannels(String connectionId) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            String json = doGet(conn.managementUrl() + "/channels", conn.username(), conn.password());
            return MAPPER.readValue(json, LIST_MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ Channel 查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean publishMessage(String connectionId, String exchangeName, String routingKey,
                                  String payload, Map<String, Object> properties) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("properties", properties != null ? properties : Map.of());
            body.put("routing_key", routingKey);
            body.put("payload", payload);
            body.put("payload_encoding", "string");
            String vhost = "/";
            String encodedVhost = encodeVhost(vhost);
            String encodedExchange = encodeName(exchangeName.isEmpty() ? "amq.default" : exchangeName);
            String json = doPost(conn.managementUrl() + "/exchanges/" + encodedVhost + "/" + encodedExchange + "/publish",
                    conn.username(), conn.password(), MAPPER.writeValueAsString(body));
            Map<String, Object> result = MAPPER.readValue(json, MAP_TYPE);
            return Boolean.TRUE.equals(result.get("routed"));
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 消息发布失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> getMessages(String connectionId, String queueName, int count, String ackMode) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("count", Math.max(1, Math.min(count, 500)));
            body.put("ackmode", ackMode != null ? ackMode : "ack_requeue_true");
            body.put("truncate", 50000);
            body.put("encoding", "auto");
            String vhost = extractVhost(queueName);
            String encodedVhost = encodeVhost(vhost);
            String encodedQueue = encodeName(queueName);
            String json = doPost(conn.managementUrl() + "/queues/" + encodedVhost + "/" + encodedQueue + "/get",
                    conn.username(), conn.password(), MAPPER.writeValueAsString(body));
            return MAPPER.readValue(json, LIST_MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 消息消费失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean purgeQueue(String connectionId, String queueName) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            String vhost = extractVhost(queueName);
            String encodedVhost = encodeVhost(vhost);
            String encodedQueue = encodeName(queueName);
            doDelete(conn.managementUrl() + "/queues/" + encodedVhost + "/" + encodedQueue + "/contents",
                    conn.username(), conn.password());
            return true;
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 队列清空失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteQueue(String connectionId, String queueName) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            String vhost = extractVhost(queueName);
            String encodedVhost = encodeVhost(vhost);
            String encodedQueue = encodeName(queueName);
            doDelete(conn.managementUrl() + "/queues/" + encodedVhost + "/" + encodedQueue,
                    conn.username(), conn.password());
            return true;
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 队列删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean createQueue(String connectionId, String queueName, boolean durable, boolean autoDelete, Map<String, Object> arguments) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("durable", durable);
            body.put("auto_delete", autoDelete);
            body.put("arguments", arguments != null ? arguments : Map.of());
            String vhost = extractVhost(queueName);
            String encodedVhost = encodeVhost(vhost);
            String encodedQueue = encodeName(queueName);
            doPut(conn.managementUrl() + "/queues/" + encodedVhost + "/" + encodedQueue,
                    conn.username(), conn.password(), MAPPER.writeValueAsString(body));
            return true;
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 队列创建失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean createExchange(String connectionId, String exchangeName, String type, boolean durable, boolean autoDelete, boolean internal, Map<String, Object> arguments) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type", type != null ? type : "direct");
            body.put("durable", durable);
            body.put("auto_delete", autoDelete);
            body.put("internal", internal);
            body.put("arguments", arguments != null ? arguments : Map.of());
            String vhost = extractVhost(exchangeName);
            String encodedVhost = encodeVhost(vhost);
            String encodedExchange = encodeName(exchangeName);
            doPut(conn.managementUrl() + "/exchanges/" + encodedVhost + "/" + encodedExchange,
                    conn.username(), conn.password(), MAPPER.writeValueAsString(body));
            return true;
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 交换机创建失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteExchange(String connectionId, String exchangeName) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            String vhost = extractVhost(exchangeName);
            String encodedVhost = encodeVhost(vhost);
            String encodedExchange = encodeName(exchangeName);
            doDelete(conn.managementUrl() + "/exchanges/" + encodedVhost + "/" + encodedExchange,
                    conn.username(), conn.password());
            return true;
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 交换机删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean createBinding(String connectionId, String exchangeName, String queueName, String routingKey, Map<String, Object> arguments) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("routing_key", routingKey != null ? routingKey : "");
            body.put("arguments", arguments != null ? arguments : Map.of());
            String vhost = extractVhost(exchangeName);
            String encodedVhost = encodeVhost(vhost);
            String encodedExchange = encodeName(exchangeName);
            String encodedQueue = encodeName(queueName);
            doPost(conn.managementUrl() + "/bindings/" + encodedVhost + "/e/" + encodedExchange + "/q/" + encodedQueue,
                    conn.username(), conn.password(), MAPPER.writeValueAsString(body));
            return true;
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 绑定创建失败: " + e.getMessage(), e);
        }
    }

    // ─── 用户管理 ────────────────────────────────────────────────────────

    @Override
    public List<Map<String, Object>> listUsers(String connectionId) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            String json = doGet(conn.managementUrl() + "/users", conn.username(), conn.password());
            return MAPPER.readValue(json, LIST_MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 用户查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> userDetail(String connectionId, String username) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            String json = doGet(conn.managementUrl() + "/users/" + encodeName(username), conn.username(), conn.password());
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 用户详情查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean createUser(String connectionId, String username, String password, String tags) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("password", password);
            body.put("tags", tags != null ? tags : "");
            doPut(conn.managementUrl() + "/users/" + encodeName(username),
                    conn.username(), conn.password(), MAPPER.writeValueAsString(body));
            return true;
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 用户创建失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteUser(String connectionId, String username) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            doDelete(conn.managementUrl() + "/users/" + encodeName(username),
                    conn.username(), conn.password());
            return true;
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 用户删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> getUserPermissions(String connectionId, String username) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            String json = doGet(conn.managementUrl() + "/users/" + encodeName(username) + "/permissions",
                    conn.username(), conn.password());
            return MAPPER.readValue(json, LIST_MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 用户权限查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean setUserPermissions(String connectionId, String username, String vhost,
                                      String configure, String write, String read) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("configure", configure != null ? configure : ".*");
            body.put("write", write != null ? write : ".*");
            body.put("read", read != null ? read : ".*");
            String encodedVhost = encodeVhost(vhost != null ? vhost : "/");
            doPut(conn.managementUrl() + "/permissions/" + encodedVhost + "/" + encodeName(username),
                    conn.username(), conn.password(), MAPPER.writeValueAsString(body));
            return true;
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 用户权限设置失败: " + e.getMessage(), e);
        }
    }

    // ─── 虚拟主机管理 ────────────────────────────────────────────────────

    @Override
    public List<Map<String, Object>> listVhosts(String connectionId) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            String json = doGet(conn.managementUrl() + "/vhosts", conn.username(), conn.password());
            return MAPPER.readValue(json, LIST_MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 虚拟主机查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> vhostDetail(String connectionId, String vhostName) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            String encodedVhost = encodeVhost(vhostName);
            String json = doGet(conn.managementUrl() + "/vhosts/" + encodedVhost, conn.username(), conn.password());
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 虚拟主机详情查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean createVhost(String connectionId, String vhostName) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            String encodedVhost = encodeVhost(vhostName);
            doPut(conn.managementUrl() + "/vhosts/" + encodedVhost,
                    conn.username(), conn.password(), "{}");
            return true;
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 虚拟主机创建失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteVhost(String connectionId, String vhostName) {
        RabbitConnection conn = requireConnection(connectionId);
        try {
            String encodedVhost = encodeVhost(vhostName);
            doDelete(conn.managementUrl() + "/vhosts/" + encodedVhost,
                    conn.username(), conn.password());
            return true;
        } catch (Exception e) {
            throw new RuntimeException("RabbitMQ 虚拟主机删除失败: " + e.getMessage(), e);
        }
    }

    // ─── HTTP helpers ──────────────────────────────────────────────────────

    private String doGet(String url, String username, String password) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuth(username, password))
                .header("Accept", "application/json")
                .header("Connection", "close")
                .GET()
                .timeout(REQUEST_TIMEOUT)
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private String doPost(String url, String username, String password, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuth(username, password))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Connection", "close")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(REQUEST_TIMEOUT)
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private void doPut(String url, String username, String password, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuth(username, password))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Connection", "close")
                .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(REQUEST_TIMEOUT)
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    private void doDelete(String url, String username, String password) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", basicAuth(username, password))
                .header("Accept", "application/json")
                .header("Connection", "close")
                .DELETE()
                .timeout(REQUEST_TIMEOUT)
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    private static String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private RabbitConnection requireConnection(String connectionId) {
        RabbitConnection conn = connectionMap.get(connectionId);
        if (conn == null) {
            throw new IllegalStateException("RabbitMQ 连接不存在: " + connectionId);
        }
        return conn;
    }

    /**
     * RabbitMQ Management API 中 vhost 为 "/" 时需要编码为 "%2f"。
     */
    private static String encodeVhost(String vhost) {
        return "/".equals(vhost) ? "%2f" : vhost;
    }

    private static String encodeName(String name) {
        try {
            return java.net.URLEncoder.encode(name, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return name;
        }
    }

    /**
     * 从队列名中提取 vhost（如果格式为 "vhost::queue" 则分离，否则默认 "/"）。
     */
    private static String extractVhost(String name) {
        if (name != null && name.contains("::")) {
            int idx = name.indexOf("::");
            return name.substring(0, idx);
        }
        return "/";
    }

    /**
     * 解析 RabbitMQ Management API 响应，兼容分页对象 {"items": [...]} 和纯数组 [...] 两种格式。
     */
    private List<Map<String, Object>> parseListOrPaginated(String json) throws Exception {
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) {
            return MAPPER.readValue(json, LIST_MAP_TYPE);
        }
        Map<String, Object> wrapper = MAPPER.readValue(json, MAP_TYPE);
        Object items = wrapper.get("items");
        if (items instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = (List<Map<String, Object>>) (List<?>) list;
            return result;
        }
        return Collections.emptyList();
    }
}
