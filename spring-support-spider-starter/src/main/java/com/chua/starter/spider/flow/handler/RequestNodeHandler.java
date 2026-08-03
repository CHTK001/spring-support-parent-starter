package com.chua.starter.spider.flow.handler;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.spider.entity.SpiderFlowNode;
import com.chua.starter.spider.flow.NodeHandler;
import com.chua.starter.spider.flow.SpiderFlowContext;
import com.chua.starter.spider.flow.SpiderFlowUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP 请求节点处理器：支持 GET/POST/PUT/DELETE，结果写入 ctx.var(saveAs)。
 *
 * <p>config JSON 结构：</p>
 * <pre>
 * {
 *   "method":   "GET|POST|PUT|DELETE",   // 默认 GET
 *   "url":      "https://example.com",   // 支持 ${varName} 占位符
 *   "headers":  { "User-Agent": "..." },  // 可选
 *   "body":     "raw body",              // 可选（POST/PUT）
 *   "timeoutMs": 30000,                  // 默认 30000
 *   "saveAs":   "resp"                   // 保存到 ctx.var 的 key，默认 resp
 * }
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class RequestNodeHandler implements NodeHandler {

    /**
     * 默认 HTTP 方法
     */
    private static final String DEFAULT_METHOD = "GET";

    /**
     * 默认超时（毫秒）
     */
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    /**
     * 默认结果变量名
     */
    private static final String DEFAULT_SAVE_AS = "resp";

    /**
     * HTTP 方法：GET
     */
    private static final String METHOD_GET = "GET";

    /**
     * HTTP 方法：POST
     */
    private static final String METHOD_POST = "POST";

    /**
     * HTTP 方法：PUT
     */
    private static final String METHOD_PUT = "PUT";

    /**
     * HTTP 方法：DELETE
     */
    private static final String METHOD_DELETE = "DELETE";

    /**
     * 占位符正则前缀
     */
    private static final String PLACEHOLDER_PREFIX = "${";

    /**
     * 占位符正则后缀
     */
    private static final String PLACEHOLDER_SUFFIX = "}";

    /**
     * 节点类型编码
     */
    private static final String TYPE = "request";

    /**
     * HTTP 客户端（每次新建，避免线程问题）
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * JSON 解析器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String supportedType() {
        return TYPE;
    }

    @Override
    public String execute(SpiderFlowNode node, SpiderFlowContext context) throws Exception {
        Map<String, Object> config = parseConfig(node.getFlowNodeConfig());

        String method = SpiderFlowUtils.defaultIfEmpty(asString(config.get("method")), DEFAULT_METHOD).toUpperCase();
        String url = render(asString(config.get("url")), context);
        long timeoutMs = asLong(config.get("timeoutMs"), DEFAULT_TIMEOUT_MS);
        String saveAs = SpiderFlowUtils.defaultIfEmpty(asString(config.get("saveAs")), DEFAULT_SAVE_AS);
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) config.getOrDefault("headers", Map.of());
        String body = render(asString(config.get("body")), context);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", "Mozilla/5.0 spider-flow/1.0");

        if (headers != null) {
            for (Map.Entry<String, String> h : headers.entrySet()) {
                builder.header(h.getKey(), h.getValue());
            }
        }

        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofString(
                body == null ? "" : body, StandardCharsets.UTF_8);
        switch (method) {
            case METHOD_GET -> builder.GET();
            case METHOD_POST -> builder.POST(publisher);
            case METHOD_PUT -> builder.PUT(publisher);
            case METHOD_DELETE -> builder.DELETE();
            default -> builder.GET();
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String responseBody = response.body();
        int status = response.statusCode();

        Map<String, Object> result = Map.of(
                "status", status,
                "contentType", contentType,
                "body", responseBody
        );
        context.putVar(saveAs, result);

        log.info("[Flow {}] HTTP {} {} -> {}", context.getExecutionNo(), method, url, status);
        return "HTTP " + status;
    }

    /**
     * 解析 config JSON。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String json) {
        if (StringUtils.isBlank(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("[Request] config JSON 解析失败，使用空配置: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 简单变量替换 ${var}。
     */
    private String render(String template, SpiderFlowContext context) {
        if (template == null || !template.contains(PLACEHOLDER_PREFIX)) {
            return template;
        }
        StringBuilder sb = new StringBuilder(template);
        int start;
        while ((start = sb.indexOf(PLACEHOLDER_PREFIX)) >= 0) {
            int end = sb.indexOf(PLACEHOLDER_SUFFIX, start);
            if (end < 0) {
                break;
            }
            String key = sb.substring(start + PLACEHOLDER_PREFIX.length(), end);
            Object value = context.getVar(key);
            sb.replace(start, end + 1, value == null ? "" : value.toString());
        }
        return sb.toString();
    }

    /**
     * Object 转 String。
     */
    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    /**
     * Object 转 long（带默认）。
     */
    private long asLong(Object o, long def) {
        if (o == null) {
            return def;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException ex) {
            return def;
        }
    }
}


