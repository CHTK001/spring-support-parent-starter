package com.chua.starter.monitor.support.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 客户端 HTTP 工具（包内共享）。
 *
 * <p>固定 {@link HttpClient.Builder#NO_PROXY}：本机系统代理会劫持内网地址
 * （历史坑：探测/上报被系统代理吞掉表现为超时或 000），agent 直连 monitor
 * 服务端，不走系统代理。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
final class MonitorHttp {

    /**
     * 上报鉴权头，与服务端 AgentEndpointController.AGENT_KEY_HEADER 一致
     */
    static final String AGENT_KEY_HEADER = "X-Agent-Key";

    /**
     * ReturnResult 成功码，与 utils 层 ReturnCode.SUCCESS/OK 一致
     */
    static final String SUCCESS_CODE = "00000";

    /**
     * 信封解析器（线程安全，静态复用）
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MonitorHttp() {
    }

    /**
     * 构建共享 HTTP 客户端。
     *
     * @param properties 客户端配置
     * @return HttpClient
     */
    static HttpClient create(MonitorClientProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500, properties.getConnectTimeoutMs())))
                .proxy(HttpClient.Builder.NO_PROXY)
                .build();
    }

    /**
     * 拼接服务端地址与路径（容忍 serverUrl 末尾多余斜杠）。
     *
     * @param properties 客户端配置
     * @param path       以 / 开头的路径
     * @return 完整 URI
     */
    static URI uri(MonitorClientProperties properties, String path) {
        String base = properties.getServerUrl() == null ? "" : properties.getServerUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + path);
    }

    /**
     * 构建 GET 请求（自动携带 X-Agent-Key）。
     *
     * @param properties 客户端配置
     * @param path       路径
     * @return 请求
     */
    static HttpRequest get(MonitorClientProperties properties, String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(properties, path))
                .timeout(Duration.ofMillis(Math.max(500, properties.getReadTimeoutMs())))
                .GET();
        return withAgentKey(builder, properties).build();
    }

    /**
     * 构建 POST JSON 请求（自动携带 X-Agent-Key）。
     *
     * @param properties 客户端配置
     * @param path       路径
     * @param json       请求体
     * @return 请求
     */
    static HttpRequest postJson(MonitorClientProperties properties, String path, String json) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(properties, path))
                .timeout(Duration.ofMillis(Math.max(500, properties.getReadTimeoutMs())))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        return withAgentKey(builder, properties).build();
    }

    /**
     * 解析 ReturnResult 信封是否成功（code=00000）。
     *
     * <p>服务端业务失败（如 points 非法）也返回 HTTP 200，必须看信封 code。</p>
     *
     * @param body 响应体，可为 null
     * @return 是否成功
     */
    static boolean businessOk(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        try {
            JsonNode node = MAPPER.readTree(body);
            return SUCCESS_CODE.equals(node.path("code").asText(null));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 补充鉴权头。
     *
     * @param builder    请求构建器
     * @param properties 客户端配置
     * @return 构建器
     */
    private static HttpRequest.Builder withAgentKey(HttpRequest.Builder builder, MonitorClientProperties properties) {
        String agentKey = properties.getAgentKey();
        if (agentKey != null && !agentKey.isBlank()) {
            builder.header(AGENT_KEY_HEADER, agentKey.trim());
        }
        return builder;
    }
}
