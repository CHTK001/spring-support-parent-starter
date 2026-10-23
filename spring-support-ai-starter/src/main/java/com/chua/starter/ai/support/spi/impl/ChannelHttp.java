package com.chua.starter.ai.support.spi.impl;

import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 账号渠道 SPI 实现共享的 HTTP / JSON 辅助。
 *
 * <p>各渠道实现不是 Spring Bean，故统一使用静态
 * {@link java.net.http.HttpClient}；JSON 解析走 common 模块的
 * {@code com.chua.common.support.json.Json}（非 Spring Bean，可直接静态调用）。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
final class ChannelHttp {

    private ChannelHttp() {
    }

    /** 共享的静态 HttpClient，各渠道实现复用。 */
    static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** 单次请求默认超时（秒）。 */
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    /** 登录轮询单次请求超时（秒）；轮询间隔短，超时也应短。 */
    static final Duration POLL_TIMEOUT = Duration.ofSeconds(30);

    /**
     * 执行一次 POST JSON 请求，返回响应体字符串。
     *
     * @param url 目标地址
     * @param body 请求体 JSON（可为 null，表示无 body）
     * @param headers 附加请求头（可为 null）
     * @return 响应体；HTTP 非 2xx 时抛 {@link ApiException}
     */
    static String postJson(String url, String body, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .POST(body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (headers != null) {
            headers.forEach((k, v) -> {
                if (k != null && v != null) {
                    builder.header(k, v);
                }
            });
        }
        return execute(builder.build(), "POST " + url);
    }

    /**
     * 执行一次 GET 请求，返回响应体字符串。
     *
     * @param url 目标地址
     * @param headers 附加请求头（可为 null）
     * @return 响应体；HTTP 非 2xx 时抛 {@link ApiException}
     */
    static String getJson(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .GET();
        if (headers != null) {
            headers.forEach((k, v) -> {
                if (k != null && v != null) {
                    builder.header(k, v);
                }
            });
        }
        return execute(builder.build(), "GET " + url);
    }

    /**
     * 执行一次 POST 表单请求（OAuth 授权码/刷新码等用 {@code application/x-www-form-urlencoded}）。
     *
     * @param url 目标地址
     * @param form 表单体，形如 {@code client_id=...&code=...}
     * @param headers 附加请求头（可为 null）
     * @return 响应体；HTTP 非 2xx 时抛 {@link ApiException}
     */
    static String postForm(String url, String form, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form));
        if (headers != null) {
            // ⚠️ 不能重复设置 Content-Type：同名头设置两次时 JDK HttpClient 会发出重复头，
            // 上游（实测 WorkOS）会丢弃请求体，回 400 "client_id is required"。
            // 调用方传入的 Content-Type 优先（覆盖上面的默认值，而非追加）。
            headers.forEach((k, v) -> {
                if (k == null || v == null) {
                    return;
                }
                if ("Content-Type".equalsIgnoreCase(k)) {
                    builder.setHeader(k, v);
                } else {
                    builder.header(k, v);
                }
            });
        }
        return execute(builder.build(), "POST " + url);
    }

    /** 执行请求，非 2xx 抛 {@link ApiException}（携带上游状态码与响应体摘要）。 */
    private static String execute(HttpRequest request, String label) {
        HttpResponse<String> response;
        try {
            response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "渠道 HTTP 请求失败: " + label + " (" + e.getMessage() + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "渠道 HTTP 请求被中断: " + label, e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "渠道上游返回非 2xx: " + label + " -> " + response.statusCode() + " " + truncate(response.body()));
        }
        return response.body();
    }

    /** 摘要化响应体（最多 300 字符），避免异常信息带出超大 body。 */
    static String truncate(String body) {
        if (body == null) {
            return "";
        }
        String normalized = body.replaceAll("\\s+", " ");
        return normalized.length() > 300 ? normalized.substring(0, 300) + "..." : normalized;
    }
}
