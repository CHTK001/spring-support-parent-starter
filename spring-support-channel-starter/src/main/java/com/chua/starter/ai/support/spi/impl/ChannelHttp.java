// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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

    /** HTTP 429 Too Many Requests。 */
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    /** 限流退避默认时长（服务端未给 Retry-After 时）。 */
    private static final long DEFAULT_RETRY_AFTER_MS = 2_000L;

    /** 限流退避重试次数（连同首次请求，最多打上游 1 + N 次）。 */
    private static final int RATE_LIMIT_RETRIES = 2;

    /**
     * 限流错误信息前缀：{@link #isRateLimited(Throwable)} 据此识别。
     *
     * <p>用前缀而不是自定义异常类型，是因为 {@link ApiException} 的契约被整个
     * 渠道模块复用（前端按 {@code ApiErrorCode} 分支），加子类会牵动面过大。</p>
     */
    static final String RATE_LIMITED_MARKER = "渠道上游限流";

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

    /**
     * execute
     * @param label 字符串
     * @param request 参数
     * @return 返回值
    */
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
        if (response.statusCode() == HTTP_TOO_MANY_REQUESTS) {
            // ⚠️ 429 是「你请求太快了」，不是业务失败：渠道登录轮询通常 2s 一次，
            // 上游限流时直接抛错会让整个绑定流程中断（实测 ZCode：
            // GET /oauth/cli/poll/{flow} -> 429，弹窗直接显示「失败」）。
            // 按 Retry-After 退避后重试；仍被限流则抛 ROLE_LIMITED 语义的错误，
            // 由调用方（pollLogin）决定继续等而不是失败。
            for (int attempt = 1; attempt <= RATE_LIMIT_RETRIES; attempt++) {
                sleepQuietly(retryAfterMillis(response));
                try {
                    response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (java.io.IOException e) {
                    throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "渠道 HTTP 请求失败: " + label + " (" + e.getMessage() + ")", e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "渠道 HTTP 请求被中断: " + label, e);
                }
                if (response.statusCode() != HTTP_TOO_MANY_REQUESTS) {
                    break;
                }
            }
            if (response.statusCode() == HTTP_TOO_MANY_REQUESTS) {
                throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                        RATE_LIMITED_MARKER + ": " + label + " -> 429 " + truncate(response.body()));
            }
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR,
                    "渠道上游返回非 2xx: " + label + " -> " + response.statusCode() + " " + truncate(response.body()));
        }
        return response.body();
    }

    /**
     * 判断异常是否为「上游限流」。
     *
     * <p>登录轮询遇到限流不应判失败：用户还在授权页操作，等下一轮即可。</p>
     *
     * @param e 异常
     * @return 是否为限流
     */
    static boolean isRateLimited(Throwable e) {
        return e instanceof ApiException && e.getMessage() != null
                && e.getMessage().startsWith(RATE_LIMITED_MARKER);
    }

    /**
     * retry After Millis
     * @param response 参数
     * @return 返回值
    */
    private static long retryAfterMillis(HttpResponse<String> response) {
        String raw = response.headers().firstValue("Retry-After").orElse(null);
        if (raw != null) {
            try {
                long sec = Long.parseLong(raw.trim());
                return Math.min(Math.max(sec, 1L), 10L) * 1000L;
            } catch (NumberFormatException ignored) {
                // Retry-After 也可能是 HTTP-date，按默认值处理。
            }
        }
        return DEFAULT_RETRY_AFTER_MS;
    }

    /**
     * sleep Quietly
     * @param millis 长整数
     * @return 返回值
    */
    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "渠道 HTTP 请求被中断: 限流退避", ie);
        }
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