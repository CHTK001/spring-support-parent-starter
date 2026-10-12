package com.chua.starter.datasourcehub.support.middleware;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 中间件 OpenAPI 的通用 HTTP 通道。
 *
 * <p>只负责「发请求、拿文本、把非 2xx 还原成异常」，不掺任何业务语义；
 * 各中间件的响应结构差异由各自的实现类解析。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class MiddlewareHttp {

    /**
     * 请求超时秒数：中间件多为内网服务，超过该时长视为不可用
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 失败响应体回填长度，避免把整页 HTML 塞进异常信息
     */
    private static final int ERROR_BODY_LIMIT = 200;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private MiddlewareHttp() {
    }

    /**
     * GET 请求。
     *
     * @param url   不含查询串的地址
     * @param query 查询参数，可为空
     * @return 响应体文本
     * @throws IOException 连接失败或响应非 2xx
     */
    public static String get(String url, Map<String, String> query) throws IOException {
        return get(url, query, Map.of());
    }

    /**
     * GET 请求，可带自定义请求头。
     *
     * @param url     不含查询串的地址
     * @param query   查询参数，可为空
     * @param headers 请求头，可为空
     * @return 响应体文本
     * @throws IOException 连接失败或响应非 2xx
     */
    public static String get(String url, Map<String, String> query, Map<String, String> headers) throws IOException {
        HttpRequest request = builder(appendQuery(url, query), headers)
                .GET()
                .build();
        return send(request);
    }

    /**
     * JSON 方式 POST（InfluxDB 查询接口只吃 application/json）。
     *
     * @param url     地址
     * @param query   查询参数，可为空
     * @param json    JSON 请求体
     * @param headers 请求头，可为空
     * @return 响应体文本
     * @throws IOException 连接失败或响应非 2xx
     */
    public static String postJson(String url, Map<String, String> query, String json,
                                  Map<String, String> headers) throws IOException {
        return send(jsonRequest(url, query, json, headers));
    }

    /**
     * JSON 方式 POST 并保留状态码与响应体，非 2xx 不抛错。
     *
     * <p>Neo4j 的 Cypher 报错放在 {@code errors} 数组里随 400 返回，正文比状态码更有信息量，
     * 只能走这个方法自行判定。</p>
     *
     * @param url     地址
     * @param query   查询参数，可为空
     * @param json    JSON 请求体
     * @param headers 请求头，可为空
     * @return 响应，含状态码与原始正文
     * @throws IOException 连接失败或请求超时
     */
    public static Response postJsonResponse(String url, Map<String, String> query, String json,
                                            Map<String, String> headers) throws IOException {
        return exchange(jsonRequest(url, query, json, headers));
    }

    /**
     * 构造 JSON POST 请求。
     *
     * @param url     地址
     * @param query   查询参数
     * @param json    JSON 请求体
     * @param headers 请求头
     * @return 请求
     */
    private static HttpRequest jsonRequest(String url, Map<String, String> query, String json,
                                           Map<String, String> headers) {
        return builder(appendQuery(url, query), headers)
                .header("Content-Type", "application/json;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json == null ? "" : json, StandardCharsets.UTF_8))
                .build();
    }

    /**
     * 构造带超时与自定义头的请求构建器。
     *
     * @param url     完整地址（含查询串）
     * @param headers 请求头，null 值跳过
     * @return 构建器
     */
    private static HttpRequest.Builder builder(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                builder.header(entry.getKey(), entry.getValue());
            }
        }
        return builder;
    }

    /**
     * DELETE 请求（部分 OpenAPI 用 DELETE + 查询串表达删除条件）。
     *
     * @param url   不含查询串的地址
     * @param query 查询参数，可为空
     * @return 响应体文本
     * @throws IOException 连接失败或响应非 2xx
     */
    public static String delete(String url, Map<String, String> query) throws IOException {
        return delete(url, query, Map.of());
    }

    /**
     * DELETE 请求，可带自定义请求头。
     *
     * <p>InfluxDB 的删除桶接口要求 {@code Authorization: Token xxx}，而两个参数的
     * {@link #delete(String, Map)} 不带任何头，用它必然 401 —— 实测删除桶接口因此完全不可用。</p>
     *
     * @param url     不含查询串的地址
     * @param query   查询参数，可为空
     * @param headers 请求头，可为空
     * @return 响应体文本
     * @throws IOException 连接失败或响应非 2xx
     */
    public static String delete(String url, Map<String, String> query,
                               Map<String, String> headers) throws IOException {
        HttpRequest request = builder(appendQuery(url, query), headers)
                .DELETE()
                .build();
        return send(request);
    }

    /**
     * GET 请求并保留响应头。
     *
     * <p>Nacos 读取单个配置时正文即配置内容，格式与最后修改时间放在 {@code Config-Type}、
     * {@code Last-Modified} 头里，只能走这个方法。</p>
     *
     * @param url   不含查询串的地址
     * @param query 查询参数，可为空
     * @return 含响应头的响应
     * @throws IOException 连接失败或响应非 2xx
     */
    public static Response getResponse(String url, Map<String, String> query) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(appendQuery(url, query)))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        return execute(request);
    }

    /**
     * GET 请求并保留状态码与响应体，非 2xx 不抛错。
     *
     * <p>Neo4j 5 默认要求 HTTP 也带凭据，未授权时根资源返回 401 且正文里才有可读原因，
     * 需要状态码校验请走两参的 {@link #getResponse(String, Map)}。</p>
     *
     * @param url     不含查询串的地址
     * @param query   查询参数，可为空
     * @param headers 请求头，可为空
     * @return 响应，含状态码与原始正文
     * @throws IOException 连接失败或响应超时
     */
    public static Response getResponse(String url, Map<String, String> query,
                                       Map<String, String> headers) throws IOException {
        return exchange(builder(appendQuery(url, query), headers).GET().build());
    }

    /**
     * 表单方式提交（Nacos 等 OpenAPI 以 x-www-form-urlencoded 接收写操作）。
     *
     * @param url  地址
     * @param form 表单字段，可为空
     * @return 响应体文本
     * @throws IOException 连接失败或响应非 2xx
     */
    public static String postForm(String url, Map<String, String> form) throws IOException {
        return postForm(url, Map.of(), form);
    }

    /**
     * 表单方式提交，并带上查询参数。
     *
     * <p>鉴权令牌放查询串而非表单：官方 SDK 即如此，且只从查询串取参的服务端也能收到。</p>
     *
     * @param url   地址
     * @param query 查询参数，可为空
     * @param form  表单字段，可为空
     * @return 响应体文本
     * @throws IOException 连接失败或响应非 2xx
     */
    public static String postForm(String url, Map<String, String> query, Map<String, String> form) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(appendQuery(url, query)))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(formBody(form)))
                .build();
        return send(request);
    }

    /**
     * 以表单方式 PUT（Nacos 实例上下线/改权重等更新接口用 PUT + 表单）。
     *
     * @param url   地址
     * @param query 查询参数，可为空
     * @param form  表单字段，可为空
     * @return 响应体文本
     * @throws IOException 连接失败或响应非 2xx
     */
    public static String putForm(String url, Map<String, String> query, Map<String, String> form) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(appendQuery(url, query)))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .method("PUT", HttpRequest.BodyPublishers.ofString(formBody(form)))
                .build();
        return send(request);
    }

    /**
     * PATCH JSON 并返回响应体（InfluxDB 更新 Bucket 保留期走 PATCH）。
     *
     * @param url     地址
     * @param query   查询参数，可为空
     * @param json    JSON 请求体
     * @param headers 请求头，可为空
     * @return 响应体文本
     * @throws IOException 连接失败或响应非 2xx
     */
    public static String patchJson(String url, Map<String, String> query, String json,
                                   Map<String, String> headers) throws IOException {
        HttpRequest request = builder(appendQuery(url, query), headers)
                .header("Content-Type", "application/json;charset=UTF-8")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json == null ? "" : json, StandardCharsets.UTF_8))
                .build();
        return send(request);
    }

    /**
     * JSON 方式 PUT（InfluxDB 3 更新数据库配置走 PUT + JSON）。
     *
     * @param url     地址
     * @param query   查询参数，可为空
     * @param json    JSON 请求体
     * @param headers 请求头，可为空
     * @return 响应体文本
     * @throws IOException 连接失败或响应非 2xx
     */
    public static String putJson(String url, Map<String, String> query, String json,
                                   Map<String, String> headers) throws IOException {
        HttpRequest request = builder(appendQuery(url, query), headers)
                .header("Content-Type", "application/json;charset=UTF-8")
                .method("PUT", HttpRequest.BodyPublishers.ofString(json == null ? "" : json, StandardCharsets.UTF_8))
                .build();
        return send(request);
    }

    /**
     * 以纯文本方式 POST（InfluxDB line protocol 写入接口只吃 text/plain）。
     *
     * @param url         地址
     * @param query       查询参数，可为空
     * @param body        请求体文本
     * @param contentType 正文类型，如 {@code text/plain; charset=utf-8}
     * @param headers     请求头，可为空
     * @return 响应体文本
     * @throws IOException 连接失败或响应非 2xx
     */
    public static String postText(String url, Map<String, String> query, String body, String contentType,
                                  Map<String, String> headers) throws IOException {
        HttpRequest request = builder(appendQuery(url, query), headers)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8))
                .build();
        return send(request);
    }

    /**
     * 发起请求并校验状态码。
     *
     * @param request 请求
     * @return 响应体文本
     * @throws IOException 连接失败或响应非 2xx
     */
    private static String send(HttpRequest request) throws IOException {
        return execute(request).body();
    }

    /**
     * 发起请求并保留状态码与响应头。
     *
     * @param request 请求
     * @return 响应
     * @throws IOException 连接失败或响应非 2xx
     */
    private static Response execute(HttpRequest request) throws IOException {
        Response response = exchange(request);
        int status = response.status();
        if (status < 200 || status >= 300) {
            throw new IOException(status + " " + abbreviate(response.body()));
        }
        return response;
    }

    /**
     * 发起请求并原样保留状态码、响应头与正文。
     *
     * @param request 请求
     * @return 响应
     * @throws IOException 连接失败或请求被中断
     */
    private static Response exchange(HttpRequest request) throws IOException {
        HttpResponse<String> response;
        try {
            response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求被中断: " + request.uri(), e);
        }
        return new Response(response.statusCode(), response.body() == null ? "" : response.body(),
                response.headers().map());
    }

    /**
     * 中间件 HTTP 响应。
     *
     * @param status  状态码
     * @param body    响应体文本
     * @param headers 响应头，键的大小写由 HTTP 客户端决定
     * @author CH
     * @since 4.0.0.42
     */
    public record Response(int status, String body, Map<String, List<String>> headers) {

        /**
         * 规范构造器：响应头做防御性拷贝。
         *
         * <p>value class 前置条件——集合组件必须深不可变。{@link #header(String)} 显式容忍
         * headers 为 null，故此处保留 null 语义，仅在非空时拷成不可变映射；
         * 用 {@link LinkedHashMap} 承接而非 {@link Map#copyOf}，以保留 HTTP 客户端给出的头顺序
         * 并容忍 null 值。</p>
         *
         * @param status  状态码
         * @param body    响应体文本
         * @param headers 响应头，键的大小写由 HTTP 客户端决定，可为 null
         */
        public Response {
            headers = headers == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        }

        /**
         * 按名称读取响应头首个值。
         *
         * @param name 头名称，大小写不敏感
         * @return 头值，不存在时为空串
         */
        public String header(String name) {
            if (headers == null) {
                return "";
            }
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                    List<String> values = entry.getValue();
                    return values == null || values.isEmpty() ? "" : values.get(0);
                }
            }
            return "";
        }
    }

    /**
     * 拼接查询串。
     *
     * @param url   地址
     * @param query 查询参数
     * @return 带查询串的地址
     */
    private static String appendQuery(String url, Map<String, String> query) {
        String body = formBody(query);
        if (body.isEmpty()) {
            return url;
        }
        return url + (url.indexOf('?') >= 0 ? "&" : "?") + body;
    }

    /**
     * 把键值对编码成 {@code a=1&b=2}，null 值按空串处理。
     *
     * @param params 参数
     * @return 编码结果，无参数时为空串
     */
    private static String formBody(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String value = entry.getValue() == null ? "" : entry.getValue();
            joiner.add(encode(entry.getKey()) + "=" + encode(value));
        }
        return joiner.toString();
    }

    /**
     * URL 编码。
     *
     * @param raw 原值
     * @return 编码结果
     */
    private static String encode(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }

    /**
     * 截断过长的响应体。
     *
     * @param body 响应体
     * @return 可读片段
     */
    private static String abbreviate(String body) {
        String text = body.replaceAll("\\s+", " ").trim();
        return text.length() <= ERROR_BODY_LIMIT ? text : text.substring(0, ERROR_BODY_LIMIT) + "...";
    }
}
