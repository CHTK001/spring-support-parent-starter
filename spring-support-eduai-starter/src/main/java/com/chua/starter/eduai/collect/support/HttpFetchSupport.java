package com.chua.starter.eduai.collect.support;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 采集侧 HTTP 抓取（仅 http/https，响应体上限保护 + 短期缓存）。
 *
 * <p>大文件（如公开成语词库约 10MB）在短时间内被「预览 / 多次采集」反复请求，
 * 这里按 URL 做 TTL 缓存，避免每次都重新下载。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public final class HttpFetchSupport {

    /**
     * 响应体上限（字节）：约 20MB
     */
    private static final int MAX_BODY_BYTES = 20 * 1024 * 1024;

    /**
     * 连接超时
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    /**
     * 请求（含下载）超时
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

    /**
     * 缓存有效期：10 分钟
     */
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    /**
     * URL → 响应体缓存
     */
    private static final Map<String, CachedBody> CACHE = new ConcurrentHashMap<>();

    private HttpFetchSupport() {
    }

    /**
     * GET 抓取文本内容（UTF-8 解码，带 TTL 缓存）。
     *
     * @param url 地址（仅允许 http/https）
     * @return 响应体
     * @throws IOException 网络或协议错误
     */
    public static String get(String url) throws IOException {
        CachedBody cached = CACHE.get(url);
        if (cached != null && !cached.expired()) {
            return new String(cached.body, StandardCharsets.UTF_8);
        }
        byte[] body = send(url);
        CACHE.put(url, new CachedBody(body));
        if (CACHE.size() > 32) {
            CACHE.entrySet().removeIf(e -> e.getValue().expired());
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * GET 抓取二进制内容（归档类数据源，如 zip 数据集）。
     *
     * @param url 地址（仅允许 http/https）
     * @return 响应体字节
     * @throws IOException 网络或协议错误
     */
    public static byte[] getBytes(String url) throws IOException {
        return send(url);
    }

    private static byte[] send(String url) throws IOException {
        URI uri = URI.create(url);
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IOException("仅支持 http/https 地址: " + url);
        }
        HttpClient client = CLIENT;
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "Mozilla/5.0 (compatible; eduai-collector)")
                .GET()
                .build();
        try {
            // 用 Future.get(超时) 做硬性兜底：HttpRequest.timeout 在「连接已建立但长时间无数据」
            // 这类半开连接上不一定触发，曾出现采集线程永久挂住、任务被「执行中」锁死的情况。
            // Future#get 由调用线程计时，到点必定返回，底层请求随后被取消。
            HttpResponse<byte[]> response = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                    .get(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            byte[] body = response.body();
            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode() + ": " + url);
            }
            if (body != null && body.length > MAX_BODY_BYTES) {
                throw new IOException("响应体超过上限(" + MAX_BODY_BYTES + "): " + url);
            }
            return body == null ? new byte[0] : body;
        } catch (TimeoutException e) {
            throw new IOException("抓取超时(" + REQUEST_TIMEOUT.toSeconds() + "s): " + url, e);
        } catch (ExecutionException e) {
            // 还原成 IOException，让上层按「数据源不可用，尝试下一个」处理
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("抓取失败: " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("抓取被中断: " + url, e);
        }
    }

    /**
     * 共享客户端。
     *
     * <p>原先每次请求都新建 {@link HttpClient}，既浪费连接池，也让抓取超时行为不稳定；
     * 这里改为单例复用，超时改由调用方用 {@link Future#get(long, TimeUnit)} 兜底
     * （见 {@link #send(String)}）。</p>
     */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(CONNECT_TIMEOUT)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    /**
     * 缓存条目。
     */
    private static final class CachedBody {
        private final byte[] body;
        private final long expireAt;

        private CachedBody(byte[] body) {
            this.body = body;
            this.expireAt = System.currentTimeMillis() + CACHE_TTL.toMillis();
        }

        private boolean expired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
