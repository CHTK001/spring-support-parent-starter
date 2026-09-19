package com.chua.starter.monitor.support.service;

import com.chua.starter.monitor.support.config.AgentDeployProperties;
import com.chua.starter.server.support.entity.MsServerHost;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent HTTP 客户端
 * <p>
 * 主机画像的所有远端数据、以及部署生命周期里的存活校验，都走这一个出口：
 * 端口只认 {@code ms_server_host_agent_port}（缺省回落配置），不再各处写死 19876。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentHttpClient {

    /** JSON 解析器 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 成功状态码 */
    private static final int HTTP_OK = 200;

    /** 模块配置 */
    private final AgentDeployProperties properties;

    /**
     * Agent 不可达 / 返回异常。
     */
    public static class AgentUnreachableException extends RuntimeException {

        /** 串行版本UID */
        private static final long serialVersionUID = 1L;

        /**
         * 构造异常。
         *
         * @param message 失败原因
         */
        public AgentUnreachableException(String message) {
            super(message);
        }
    }

    /**
     * 取该主机的 agent 端口：主机级配置优先，其次模块默认，最后内置默认值。
     *
     * @param host 主机
     * @return 端口
     */
    public int portOf(MsServerHost host) {
        Integer configured = host == null ? null : host.getServerHostAgentPort();
        if (configured != null && configured > 0 && configured < 65536) {
            return configured;
        }
        int fallback = properties.getPort();
        return fallback > 0 && fallback < 65536 ? fallback : AgentDeployProperties.DEFAULT_PORT;
    }

    /**
     * 拼装 agent 基础地址。
     *
     * @param host 主机
     * @return http://address:port
     */
    public String baseUrl(MsServerHost host) {
        String address = host == null ? null : host.getServerHostAddress();
        if (address == null || address.isBlank()) {
            throw new AgentUnreachableException("主机地址为空，无法访问 Agent");
        }
        return "http://" + address.trim() + ":" + portOf(host);
    }

    /**
     * GET 一个 agent 端点并解析为 JSON。
     *
     * @param host  主机
     * @param path  端点路径（以 / 开头）
     * @param query 查询参数，可为 null
     * @return 解析后的 JSON 节点
     */
    public JsonNode get(MsServerHost host, String path, Map<String, String> query) {
        String url = buildUrl(host, path, query);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(timeoutMillis());
            conn.setReadTimeout(timeoutMillis());
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            InputStream stream = code >= HTTP_OK && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String body = stream == null ? "" : readAll(stream);
            if (code != HTTP_OK) {
                throw new AgentUnreachableException("Agent 返回 HTTP " + code + "：" + brief(body));
            }
            return MAPPER.readTree(body);
        } catch (AgentUnreachableException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentUnreachableException("Agent 不可达(" + host.getServerHostAddress() + ":" + portOf(host) + ")："
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * GET 一个返回对象的端点。
     *
     * @param host 主机
     * @param path 端点路径
     * @return 字段映射
     */
    public Map<String, Object> getMap(MsServerHost host, String path) {
        return toMap(get(host, path, null));
    }

    /**
     * GET 一个返回列表的端点；{@code {list:[],total:n}} 包装会自动拆封。
     *
     * @param host  主机
     * @param path  端点路径
     * @param query 查询参数，可为 null
     * @return 条目列表
     */
    public List<Map<String, Object>> getList(MsServerHost host, String path, Map<String, String> query) {
        JsonNode node = get(host, path, query);
        List<Map<String, Object>> list = new ArrayList<>();
        JsonNode array = node.isArray() ? node : node.path("list");
        if (array.isArray()) {
            for (JsonNode item : array) {
                list.add(toMap(item));
            }
        }
        return list;
    }

    /**
     * 探测 /health，失败时返回 null（用于「能不能连通」这类非致命判断）。
     *
     * @param host 主机
     * @return 健康信息（status/version/buildTime/port），不可达时为 null
     */
    public Map<String, Object> health(MsServerHost host) {
        try {
            return getMap(host, "/health");
        } catch (Exception e) {
            log.debug("[agent] /health 探测失败 hostId={}: {}", host.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * 等待 agent 就绪，并校验版本是否为期望值。
     *
     * @param host           主机
     * @param seconds        最长等待秒数
     * @param expectVersion  期望版本，空则只要求可达
     * @return 就绪后的健康信息；超时仍不可达时返回 null
     */
    public Map<String, Object> awaitHealthy(MsServerHost host, int seconds, String expectVersion) {
        long deadline = System.currentTimeMillis() + Math.max(1, seconds) * 1000L;
        Map<String, Object> last = null;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> info = health(host);
            if (info != null) {
                last = info;
                String version = text(info.get("version"));
                if (expectVersion == null || expectVersion.isBlank() || expectVersion.equals(version)) {
                    return info;
                }
            }
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return last;
            }
        }
        return last;
    }

    /**
     * 拼装完整 URL，并在配置了 token 时自动附加。
     *
     * @param host  主机
     * @param path  端点路径
     * @param query 查询参数
     * @return 完整 URL
     */
    private String buildUrl(MsServerHost host, String path, Map<String, String> query) {
        StringBuilder sb = new StringBuilder(baseUrl(host)).append(path);
        StringBuilder qs = new StringBuilder();
        if (query != null) {
            for (Map.Entry<String, String> entry : query.entrySet()) {
                append(qs, entry.getKey(), entry.getValue());
            }
        }
        String token = properties.getToken();
        if (token != null && !token.isBlank() && "/exec".equals(path)) {
            append(qs, "token", token);
        }
        if (qs.length() > 0) {
            sb.append('?').append(qs);
        }
        return sb.toString();
    }

    /**
     * 追加一个已编码的查询参数。
     *
     * @param qs    查询串缓冲
     * @param key   键
     * @param value 值
     */
    private void append(StringBuilder qs, String key, String value) {
        if (value == null) {
            return;
        }
        if (qs.length() > 0) {
            qs.append('&');
        }
        qs.append(key).append('=').append(java.net.URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    /**
     * JSON 节点转 Map。
     *
     * @param node 节点
     * @return 字段映射
     */
    private Map<String, Object> toMap(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return map;
        }
        node.fields().forEachRemaining(entry -> map.put(entry.getKey(), toValue(entry.getValue())));
        return map;
    }

    /**
     * JSON 标量转 Java 值。
     *
     * @param node 节点
     * @return 值
     */
    private Object toValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            return node.asLong();
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isArray() || node.isObject()) {
            return node.toString();
        }
        return node.asText();
    }

    /**
     * 读取全部响应体。
     *
     * @param in 输入流
     * @return 文本
     * @throws java.io.IOException 读取失败
     */
    private String readAll(InputStream in) throws java.io.IOException {
        byte[] buf = in.readAllBytes();
        return new String(buf, StandardCharsets.UTF_8);
    }

    /**
     * 请求超时毫秒。
     *
     * @return 毫秒
     */
    private int timeoutMillis() {
        int seconds = properties.getRequestTimeoutSeconds();
        return (seconds <= 0 ? 8 : seconds) * 1000;
    }

    /**
     * 截断长文本，避免异常信息过长。
     *
     * @param text 文本
     * @return 截断结果
     */
    private String brief(String text) {
        if (text == null) {
            return "";
        }
        String one = text.replaceAll("\\s+", " ").trim();
        return one.length() > 200 ? one.substring(0, 200) + "..." : one;
    }

    /**
     * 取文本值。
     *
     * @param value 原值
     * @return 文本，null 转空串
     */
    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
