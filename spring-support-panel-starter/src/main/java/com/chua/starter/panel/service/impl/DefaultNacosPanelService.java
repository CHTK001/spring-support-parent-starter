package com.chua.starter.panel.service.impl;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.panel.service.NacosPanelService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认 Nacos 面板服务。
 * 使用 JDK HttpClient 访问 Nacos OpenAPI，不依赖额外 SDK。
 *
 * @author CH
 */
public class DefaultNacosPanelService implements NacosPanelService {

    /**
     * Nacos 连接对象，负责保存请求地址与鉴权信息。
     */
    private record NacosConnection(String baseUrl, String username, String password, String accessToken) {
    }

    /**
     * 连接池，key 为连接标识。
     */
    private final Map<String, NacosConnection> connectionMap = new ConcurrentHashMap<>();
    /**
     * HTTP 客户端。
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    /**
     * JSON 序列化工具。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 请求超时时间（秒）。
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    @Override
    public String open(String host, int port, String username, String password, String protocol) {
        String scheme = StringUtils.isBlank(protocol) ? "http" : protocol.trim().toLowerCase(Locale.ROOT);
        String baseUrl = scheme + "://" + host + ":" + port;
        String trimmedUsername = trimToNull(username);
        String trimmedPassword = trimToNull(password);
        String accessToken = login(baseUrl, trimmedUsername, trimmedPassword);
        String connectionId = UUID.randomUUID().toString();
        NacosConnection connection = new NacosConnection(baseUrl, trimmedUsername, trimmedPassword, accessToken);
        connectionMap.put(connectionId, connection);
        try {
            namespaces(connectionId);
        } catch (Exception exception) {
            connectionMap.remove(connectionId);
            throw new RuntimeException("Nacos 连接失败: " + exception.getMessage(), exception);
        }
        return connectionId;
    }

    @Override
    public void close(String connectionId) {
        connectionMap.remove(connectionId);
    }

    @Override
    public Map<String, Object> namespaces(String connectionId) {
        NacosConnection connection = requireConnection(connectionId);
        String path = "/nacos/v1/console/namespaces";
        return getJson(connection, path, Map.of());
    }

    @Override
    public boolean createNamespace(
            String connectionId,
            String namespaceId,
            String namespaceName,
            String namespaceDesc) {
        NacosConnection connection = requireConnection(connectionId);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("customNamespaceId", defaultString(namespaceId));
        form.put("namespaceName", defaultString(namespaceName));
        form.put("namespaceDesc", defaultString(namespaceDesc));
        String response = postForm(connection, "/nacos/v1/console/namespaces", form);
        return parseBooleanResult(response);
    }

    @Override
    public boolean deleteNamespace(String connectionId, String namespaceId) {
        NacosConnection connection = requireConnection(connectionId);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("namespaceId", defaultString(namespaceId));
        String response = deleteText(connection, "/nacos/v1/console/namespaces", params);
        return parseBooleanResult(response);
    }

    @Override
    public Map<String, Object> listConfigs(
            String connectionId,
            String tenant,
            String dataId,
            String group,
            int pageNo,
            int pageSize) {
        NacosConnection connection = requireConnection(connectionId);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("tenant", defaultString(tenant));
        params.put("dataId", defaultString(dataId));
        params.put("group", defaultString(group));
        params.put("pageNo", String.valueOf(Math.max(pageNo, 1)));
        params.put("pageSize", String.valueOf(Math.max(pageSize, 1)));
        params.put("search", "blur");
        String path = "/nacos/v1/cs/configs";
        return getJson(connection, path, params);
    }

    @Override
    public Map<String, Object> getConfig(String connectionId, String tenant, String dataId, String group) {
        NacosConnection connection = requireConnection(connectionId);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("tenant", defaultString(tenant));
        params.put("dataId", defaultString(dataId));
        params.put("group", defaultString(group));
        String path = "/nacos/v1/cs/configs";
        String content = getText(connection, path, params);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenant", defaultString(tenant));
        result.put("dataId", defaultString(dataId));
        result.put("group", defaultString(group));
        result.put("content", content);
        return result;
    }

    @Override
    public boolean publishConfig(
            String connectionId,
            String tenant,
            String dataId,
            String group,
            String content,
            String type) {
        NacosConnection connection = requireConnection(connectionId);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("tenant", defaultString(tenant));
        form.put("dataId", defaultString(dataId));
        form.put("group", defaultString(group));
        form.put("content", defaultString(content));
        if (StringUtils.isNotBlank(type)) {
            form.put("type", type.trim());
        }
        String response = postForm(connection, "/nacos/v1/cs/configs", form);
        return parseBooleanResult(response);
    }

    @Override
    public boolean deleteConfig(String connectionId, String tenant, String dataId, String group) {
        NacosConnection connection = requireConnection(connectionId);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("tenant", defaultString(tenant));
        params.put("dataId", defaultString(dataId));
        params.put("group", defaultString(group));
        String response = deleteText(connection, "/nacos/v1/cs/configs", params);
        return parseBooleanResult(response);
    }

    @Override
    public boolean cloneConfig(
            String connectionId,
            String sourceTenant,
            String sourceDataId,
            String sourceGroup,
            String targetTenant,
            String targetDataId,
            String targetGroup,
            String type) {
        Map<String, Object> source = getConfig(connectionId, sourceTenant, sourceDataId, sourceGroup);
        String content = String.valueOf(source.getOrDefault("content", ""));
        return publishConfig(
                connectionId,
                targetTenant,
                targetDataId,
                targetGroup,
                content,
                type);
    }

    @Override
    public Map<String, Object> listConfigHistory(
            String connectionId,
            String tenant,
            String dataId,
            String group,
            int pageNo,
            int pageSize) {
        NacosConnection connection = requireConnection(connectionId);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("search", "accurate");
        params.put("tenant", defaultString(tenant));
        params.put("dataId", defaultString(dataId));
        params.put("group", defaultString(group));
        params.put("pageNo", String.valueOf(Math.max(pageNo, 1)));
        params.put("pageSize", String.valueOf(Math.max(pageSize, 1)));
        return getJson(connection, "/nacos/v1/cs/history", params);
    }

    @Override
    public Map<String, Object> getHistoryDetail(
            String connectionId,
            String tenant,
            String dataId,
            String group,
            String historyId) {
        NacosConnection connection = requireConnection(connectionId);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("search", "previous");
        params.put("tenant", defaultString(tenant));
        params.put("dataId", defaultString(dataId));
        params.put("group", defaultString(group));
        params.put("id", defaultString(historyId));
        return getJson(connection, "/nacos/v1/cs/history", params);
    }

    @Override
    public boolean rollbackToHistory(
            String connectionId,
            String tenant,
            String dataId,
            String group,
            String historyId,
            String type) {
        Map<String, Object> history = getHistoryDetail(
                connectionId,
                tenant,
                dataId,
                group,
                historyId);
        String content = String.valueOf(history.getOrDefault("content", ""));
        if (StringUtils.isBlank(content)) {
            throw new IllegalStateException("历史版本内容为空，无法回滚");
        }
        return publishConfig(connectionId, tenant, dataId, group, content, type);
    }

    /**
     * 用户密码登录 Nacos，返回 accessToken。
     * 未提供用户名时跳过登录，返回空 token。
     *
     * @param baseUrl Nacos 基础地址
     * @param username 用户名
     * @param password 密码
     * @return accessToken 或空字符串
     */
    private String login(String baseUrl, String username, String password) {
        if (StringUtils.isBlank(username)) {
            return "";
        }
        try {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("username", defaultString(username));
            form.put("password", defaultString(password));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/nacos/v1/auth/login"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(encodeForm(form)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response, "Nacos 登录失败");
            Map<String, Object> data = objectMapper.readValue(
                    response.body(),
                    new TypeReference<Map<String, Object>>() {
                    });
            Object token = data.get("accessToken");
            if (token == null) {
                throw new IllegalStateException("登录成功但未返回 accessToken");
            }
            return String.valueOf(token);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException("Nacos 登录失败: " + exception.getMessage(), exception);
        }
    }

    /**
     * 发送 GET 请求并返回 JSON 结果。
     *
     * @param connection Nacos 连接
     * @param path 请求路径
     * @param params 查询参数
     * @return JSON 对象
     */
    private Map<String, Object> getJson(
            NacosConnection connection,
            String path,
            Map<String, String> params) {
        String body = getText(connection, path, params);
        try {
            return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception exception) {
            throw new RuntimeException("解析 Nacos 响应失败: " + exception.getMessage(), exception);
        }
    }

    /**
     * 发送 GET 请求并返回文本。
     *
     * @param connection Nacos 连接
     * @param path 请求路径
     * @param params 查询参数
     * @return 文本响应
     */
    private String getText(
            NacosConnection connection,
            String path,
            Map<String, String> params) {
        try {
            URI uri = buildUri(connection, path, params);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .GET();
            applyAuthHeader(connection, builder);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response, "Nacos GET 请求失败");
            return response.body();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException("Nacos GET 请求失败: " + exception.getMessage(), exception);
        }
    }

    /**
     * 发送表单 POST 请求。
     *
     * @param connection Nacos 连接
     * @param path 请求路径
     * @param form 表单参数
     * @return 文本响应
     */
    private String postForm(
            NacosConnection connection,
            String path,
            Map<String, String> form) {
        try {
            URI uri = buildUri(connection, path, Map.of());
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(encodeForm(appendAuthQuery(connection, form))));
            applyAuthHeader(connection, builder);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response, "Nacos POST 请求失败");
            return response.body();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException("Nacos POST 请求失败: " + exception.getMessage(), exception);
        }
    }

    /**
     * 发送 DELETE 请求。
     *
     * @param connection Nacos 连接
     * @param path 请求路径
     * @param params 查询参数
     * @return 文本响应
     */
    private String deleteText(
            NacosConnection connection,
            String path,
            Map<String, String> params) {
        try {
            URI uri = buildUri(connection, path, params);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .DELETE();
            applyAuthHeader(connection, builder);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response, "Nacos DELETE 请求失败");
            return response.body();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException("Nacos DELETE 请求失败: " + exception.getMessage(), exception);
        }
    }

    /**
     * 校验响应状态码。
     *
     * @param response HTTP 响应
     * @param message 失败提示
     */
    private void ensureSuccess(HttpResponse<String> response, String message) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        throw new RuntimeException(message + ": HTTP " + response.statusCode() + " - " + response.body());
    }

    /**
     * 构建请求 URI（自动附加 accessToken）。
     *
     * @param connection Nacos 连接
     * @param path 请求路径
     * @param params 查询参数
     * @return URI
     */
    private URI buildUri(
            NacosConnection connection,
            String path,
            Map<String, String> params) {
        Map<String, String> next = appendAuthQuery(connection, params);
        String query = encodeForm(next);
        String suffix = StringUtils.isBlank(query) ? "" : "?" + query;
        return URI.create(connection.baseUrl() + path + suffix);
    }

    /**
     * 将 accessToken 合并到查询参数中。
     *
     * @param connection Nacos 连接
     * @param params 原始参数
     * @return 合并后的参数
     */
    private Map<String, String> appendAuthQuery(
            NacosConnection connection,
            Map<String, String> params) {
        Map<String, String> next = new LinkedHashMap<>();
        if (params != null) {
            next.putAll(params);
        }
        if (StringUtils.isNotBlank(connection.accessToken())) {
            next.put("accessToken", connection.accessToken());
        }
        return next;
    }

    /**
     * 追加 Basic 鉴权头，用于兼容部分网关代理模式。
     *
     * @param connection Nacos 连接
     * @param builder 请求构建器
     */
    private void applyAuthHeader(NacosConnection connection, HttpRequest.Builder builder) {
        if (StringUtils.isBlank(connection.username())) {
            return;
        }
        String credentials = defaultString(connection.username()) + ":" + defaultString(connection.password());
        String basic = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        builder.header("Authorization", "Basic " + basic);
    }

    /**
     * 序列化 URL 表单参数。
     *
     * @param params 参数
     * @return 编码后的表单字符串
     */
    private String encodeForm(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        params.forEach((key, value) -> {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(urlEncode(key))
                    .append('=')
                    .append(urlEncode(defaultString(value)));
        });
        return builder.toString();
    }

    /**
     * URL 编码工具。
     *
     * @param value 原始值
     * @return 编码值
     */
    private String urlEncode(String value) {
        return URLEncoder.encode(defaultString(value), StandardCharsets.UTF_8);
    }

    /**
     * 获取连接对象，不存在则抛异常。
     *
     * @param connectionId 连接标识
     * @return 连接对象
     */
    private NacosConnection requireConnection(String connectionId) {
        NacosConnection connection = connectionMap.get(connectionId);
        if (connection == null) {
            throw new IllegalStateException("Nacos 连接不存在: " + connectionId);
        }
        return connection;
    }

    /**
     * 空值转空串。
     *
     * @param value 输入值
     * @return 非 null 字符串
     */
    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    /**
     * 统一解析 Nacos 操作返回值。
     * 兼容 true/false 文本，以及 {"code":200} 结构。
     *
     * @param body 响应体
     * @return 是否成功
     */
    private boolean parseBooleanResult(String body) {
        String text = defaultString(body).trim();
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        try {
            Map<String, Object> json = objectMapper.readValue(
                    text,
                    new TypeReference<Map<String, Object>>() {
                    });
            Object code = json.get("code");
            if (code instanceof Number number) {
                return number.intValue() == 200;
            }
            if (code != null) {
                return "200".equals(String.valueOf(code));
            }
            Object success = json.get("success");
            if (success instanceof Boolean bool) {
                return bool;
            }
            if (success != null) {
                return "true".equalsIgnoreCase(String.valueOf(success));
            }
        } catch (Exception ignored) {
            // ignore
        }
        return false;
    }

    /**
     * 去掉空白字符串。
     *
     * @param value 输入值
     * @return 非空字符串或 null
     */
    private String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }
}
