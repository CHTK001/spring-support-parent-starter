package com.chua.starter.eduai.collect.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 采集侧 HTTP 抓取（仅 http/https，响应体上限保护）。
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class HttpFetchSupport {

    /**
     * 响应体上限（字节）
     */
    private static final int MAX_BODY_BYTES = 10 * 1024 * 1024;

    /**
     * 请求超时
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private HttpFetchSupport() {
    }

    /**
     * GET 抓取文本内容。
     *
     * @param url 地址（仅允许 http/https）
     * @return 响应体
     * @throws IOException 网络或协议错误
     */
    public static String get(String url) throws IOException {
        URI uri = URI.create(url);
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IOException("仅支持 http/https 地址: " + url);
        }
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(TIMEOUT)
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("User-Agent", "Mozilla/5.0 (compatible; eduai-collector)")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body();
            if (body != null && body.length > MAX_BODY_BYTES) {
                throw new IOException("响应体超过上限: " + url);
            }
            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode() + ": " + url);
            }
            return body == null ? "" : new String(body, StandardCharsets.UTF_8);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("抓取被中断: " + url, e);
        }
    }
}
