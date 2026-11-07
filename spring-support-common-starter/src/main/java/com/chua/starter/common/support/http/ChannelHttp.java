// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.common.support.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 渠道类客户端共享的 HTTP / JSON 辅助。
 *
 * <p>渠道实现（免登录渠道、需登录的账号渠道）散落在 AI 模块与账号模块，
 * 两者都要发同样的上游请求，故把这段逻辑下沉到 common：上层模块只依赖本类，
 * common 不反向依赖任何业务模块。</p>
 *
 * <p>各渠道实现不是 Spring Bean，统一使用静态 {@link HttpClient}；JSON 解析交给调用方
 * （走 common 模块的 {@code com.chua.common.support.json.Json}，可直接静态调用）。</p>
 *
 * <p>失败一律抛 {@link ChannelHttpException}，由调用方在自己的边界上翻译成模块内的
 * 契约异常。识别「上游限流」请用 {@link #isRateLimited(Throwable)} 或
 * {@link ChannelHttpException#getHttpStatus()}，不要匹配中文措辞。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class ChannelHttp {

    /**
     * 机器可读错误码：上游服务异常（非 2xx、连接失败等）。
     */
    public static final String CODE_UPSTREAM_ERROR = "UPSTREAM_ERROR";

    /**
     * 机器可读错误码：调用被中断。
     */
    public static final String CODE_INTERNAL_ERROR = "INTERNAL_ERROR";

    /** 共享的静态 HttpClient，各渠道实现复用。 */
    public static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 不跟随重定向的静态 HttpClient。
     *
     * <p>只有「拿登录 Cookie」这一种场景需要它：magpie 的页面鉴权用
     * {@code 303 + Set-Cookie} 把 URL 里的 {@code ?k=} 换成 Cookie，
     * 一旦跟随重定向，{@code Set-Cookie} 就随中间响应一起丢了。</p>
     */
    public static final HttpClient NO_REDIRECT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /** 单次请求默认超时（秒）。 */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    /** 登录轮询单次请求超时（秒）；轮询间隔短，超时也应短。 */
    public static final Duration POLL_TIMEOUT = Duration.ofSeconds(30);

    /** HTTP 429 Too Many Requests。 */
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    /** 限流退避默认时长（服务端未给 Retry-After 时）。 */
    private static final long DEFAULT_RETRY_AFTER_MS = 2_000L;

    /** 限流退避重试次数（连同首次请求，最多打上游 1 + N 次）。 */
    private static final int RATE_LIMIT_RETRIES = 2;

    /**
     * 限流错误信息前缀：调用方按消息前缀识别限流时使用。
     *
     * <p>之所以既给状态码又给前缀：上层模块的契约异常（{@code ApiException}）只带
     * 自己的错误码词表，没有 HTTP 状态码字段，翻成契约异常后仍要靠前缀判断，
     * 才能让「登录轮询遇到 429 继续等」这一行为保持不变。</p>
     */
    public static final String RATE_LIMITED_MARKER = "渠道上游限流";

    private ChannelHttp() {
    }

    /**
     * 执行一次 POST JSON 请求，返回响应体字符串。
     *
     * @param url 目标地址
     * @param body 请求体 JSON（可为 null，表示无 body）
     * @param headers 附加请求头（可为 null）
     * @return 响应体；HTTP 非 2xx 时抛 {@link ChannelHttpException}
     */
    public static String postJson(String url, String body, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .POST(body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        applyHeaders(builder, headers);
        return execute(builder.build(), "POST " + url);
    }

    /**
     * 执行一次 GET 请求，返回响应体字符串。
     *
     * @param url 目标地址
     * @param headers 附加请求头（可为 null）
     * @return 响应体；HTTP 非 2xx 时抛 {@link ChannelHttpException}
     */
    public static String getJson(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .GET();
        applyHeaders(builder, headers);
        return execute(builder.build(), "GET " + url);
    }

    /**
     * 执行一次 GET 请求并原样返回响应（不跟随重定向、非 2xx 不抛异常）。
     *
     * <p>供「用页面 Key 换 Cookie」这类需要读 {@code Set-Cookie} 的握手使用；
     * 业务请求仍走 {@link #getJson(String, Map)}，非 2xx 一律抛错。</p>
     *
     * @param url     目标地址
     * @param headers 附加请求头（可为 null）
     * @return 原始响应（状态码、响应头、响应体）
     */
    public static HttpResponse<String> getRaw(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .GET();
        applyHeaders(builder, headers);
        try {
            return NO_REDIRECT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            throw new ChannelHttpException(CODE_UPSTREAM_ERROR, 0,
                    "渠道 HTTP 请求失败: GET " + url + " (" + e.getMessage() + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChannelHttpException(CODE_INTERNAL_ERROR, 0, "渠道 HTTP 请求被中断: GET " + url, e);
        }
    }

    /**
     * 执行一次 POST 表单请求（OAuth 授权码/刷新码等用 {@code application/x-www-form-urlencoded}）。
     *
     * @param url 目标地址
     * @param form 表单体，形如 {@code client_id=...&code=...}
     * @param headers 附加请求头（可为 null）
     * @return 响应体；HTTP 非 2xx 时抛 {@link ChannelHttpException}
     */
    public static String postForm(String url, String form, Map<String, String> headers) {
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
     * 判断异常是否为「上游限流」。
     *
     * <p>登录轮询遇到限流不应判失败：用户还在授权页操作，等下一轮即可。</p>
     *
     * @param e 异常
     * @return 是否为限流
     */
    public static boolean isRateLimited(Throwable e) {
        return e instanceof ChannelHttpException
                && ((ChannelHttpException) e).getHttpStatus() == HTTP_TOO_MANY_REQUESTS;
    }

    /**
     * 摘要化响应体（最多 300 字符），避免异常信息带出超大 body。
     *
     * @param body 原始响应体，可为 null
     * @return 压平空白并截断后的摘要
     */
    public static String truncate(String body) {
        if (body == null) {
            return "";
        }
        String normalized = body.replaceAll("\\s+", " ");
        return normalized.length() > 300 ? normalized.substring(0, 300) + "..." : normalized;
    }

    /**
     * 逐个头写入请求；空名或空值直接跳过，避免 JDK 抛 IllegalArgumentException。
     *
     * @param builder 请求构造器
     * @param headers 附加请求头，可为 null
     */
    private static void applyHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers == null) {
            return;
        }
        headers.forEach((k, v) -> {
            if (k != null && v != null) {
                builder.header(k, v);
            }
        });
    }

    /**
     * 发送请求并把结果规整成「2xx 返回响应体、其余抛异常」。
     *
     * @param request 已构造好的请求
     * @param label 日志/异常信息里的请求标识，形如 {@code POST https://...}
     * @return 2xx 的响应体
     */
    private static String execute(HttpRequest request, String label) {
        HttpResponse<String> response;
        try {
            response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            throw new ChannelHttpException(CODE_UPSTREAM_ERROR, 0,
                    "渠道 HTTP 请求失败: " + label + " (" + e.getMessage() + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChannelHttpException(CODE_INTERNAL_ERROR, 0, "渠道 HTTP 请求被中断: " + label, e);
        }
        if (response.statusCode() == HTTP_TOO_MANY_REQUESTS) {
            // ⚠️ 429 是「你请求太快了」，不是业务失败：渠道登录轮询通常 2s 一次，
            // 上游限流时直接抛错会让整个绑定流程中断（实测 ZCode：
            // GET /oauth/cli/poll/{flow} -> 429，弹窗直接显示「失败」）。
            // 按 Retry-After 退避后重试；仍被限流则抛带限流前缀的错误，
            // 由调用方（pollLogin）决定继续等而不是失败。
            for (int attempt = 1; attempt <= RATE_LIMIT_RETRIES; attempt++) {
                sleepQuietly(retryAfterMillis(response));
                try {
                    response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (java.io.IOException e) {
                    throw new ChannelHttpException(CODE_UPSTREAM_ERROR, 0,
                            "渠道 HTTP 请求失败: " + label + " (" + e.getMessage() + ")", e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ChannelHttpException(CODE_INTERNAL_ERROR, 0,
                            "渠道 HTTP 请求被中断: " + label, e);
                }
                if (response.statusCode() != HTTP_TOO_MANY_REQUESTS) {
                    break;
                }
            }
            if (response.statusCode() == HTTP_TOO_MANY_REQUESTS) {
                throw new ChannelHttpException(CODE_UPSTREAM_ERROR, HTTP_TOO_MANY_REQUESTS,
                        RATE_LIMITED_MARKER + ": " + label + " -> 429 " + truncate(response.body()), null);
            }
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ChannelHttpException(CODE_UPSTREAM_ERROR, response.statusCode(),
                    "渠道上游返回非 2xx: " + label + " -> " + response.statusCode()
                            + " " + truncate(response.body()), null);
        }
        return response.body();
    }

    /**
     * 读取 429 响应的 Retry-After 并换算成毫秒。
     *
     * @param response 429 响应
     * @return 退避毫秒数，限定在 1s ~ 10s
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
     * 退避等待，被中断时恢复中断标记并抛 {@link ChannelHttpException}。
     *
     * @param millis 等待毫秒数
     */
    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ChannelHttpException(CODE_INTERNAL_ERROR, 0, "渠道 HTTP 请求被中断: 限流退避", ie);
        }
    }
}
