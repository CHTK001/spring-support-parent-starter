package com.chua.starter.monitor.support.service;

import com.chua.starter.monitor.support.config.AgentDeployProperties;
import com.chua.starter.monitor.support.model.AgentHealthVO;
import com.chua.starter.server.support.entity.MsServerHost;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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

    /**
     * JSON 解析器（忽略未知字段，避免探针新增字段被判为不可达）
    */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 成功状态码
    */
    private static final int HTTP_OK = 200;

    /**
     * 模块配置
    */
    private final AgentDeployProperties properties;

    /**
     * Agent 不可达 / 返回异常。
     */
    public static class AgentUnreachableException extends RuntimeException {

        /**
         * 串行版本UID
        */
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
     * 探测 /health，失败时返回 null（用于「能不能连通」这类非致命判断）。
     *
     * @param host 主机
     * @return 健康信息（status/version/buildTime/port），不可达时为 null
     */
    public AgentHealthVO health(MsServerHost host) {
        try {
            return MAPPER.convertValue(get(host, "/health", null), AgentHealthVO.class);
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
    public AgentHealthVO awaitHealthy(MsServerHost host, int seconds, String expectVersion) {
        long deadline = System.currentTimeMillis() + Math.max(1, seconds) * 1000L;
        AgentHealthVO last = null;
        while (System.currentTimeMillis() < deadline) {
            AgentHealthVO info = health(host);
            if (info != null) {
                last = info;
                if (expectVersion == null || expectVersion.isBlank() || expectVersion.equals(info.version())) {
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
}
