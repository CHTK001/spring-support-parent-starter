// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.docker.spi.impl;

import com.chua.starter.docker.spi.RegistryConfig;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

/**
 * 镜像库 HTTP 访问的公共工具（超时、Basic Auth、地址归一化）。
 *
 * <p>只给 {@code spi.impl} 下的提供者使用：所有请求都带连接与读取超时，
 * 避免镜像库不可达时把检索线程挂死。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
final class RegistryHttp {

    /**
     * 连接超时
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * 默认读取超时
     */
    static final Duration READ_TIMEOUT = Duration.ofSeconds(8);

    /**
     * 共享客户端（线程安全）
     */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private RegistryHttp() {
    }

    /**
     * GET 请求结果
     *
     * @param status HTTP 状态码
     * @param body   响应体
     */
    record Result(int status, String body) {

        /**
         * 是否 2xx
         *
         * @return 是否成功
         */
        boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    /**
     * 发起 GET 请求。
     *
     * @param url    完整地址
     * @param cfg    镜像库配置（用于 Basic Auth，可空）
     * @param accept 期望的响应类型
     * @return 状态码与响应体
     * @throws IOException          网络异常
     * @throws InterruptedException 线程中断
     */
    static Result get(String url, RegistryConfig cfg, String accept) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(READ_TIMEOUT)
                .header("Accept", accept == null ? "application/json" : accept)
                .GET();
        if (cfg != null && hasText(cfg.getUsername())) {
            String raw = cfg.getUsername() + ":" + (cfg.getPassword() == null ? "" : cfg.getPassword());
            builder.header("Authorization", "Basic "
                    + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
        }
        HttpResponse<String> response = CLIENT.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Result(response.statusCode(), response.body());
    }

    /**
     * 从库地址里取主机名（去掉协议、路径与 {@code /v2}）。
     *
     * <p>Docker Hub 及其官方别名统一返回空串：官方镜像不需要 host 前缀。</p>
     *
     * @param url 库地址
     * @return 主机名，可能为空
     */
    static String host(String url) {
        if (!hasText(url)) {
            return "";
        }
        String value = url.trim();
        int scheme = value.indexOf("://");
        if (scheme >= 0) {
            value = value.substring(scheme + 3);
        }
        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || "hub.docker.com".equals(value) || "docker.io".equals(value)
                || "index.docker.io".equals(value) || "registry-1.docker.io".equals(value)) {
            return "";
        }
        return value;
    }

    /**
     * URL 编码
     *
     * @param value 原值
     * @return 编码后的值
     */
    static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /**
     * 非空白判断
     *
     * @param value 原值
     * @return 是否非空白
     */
    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}