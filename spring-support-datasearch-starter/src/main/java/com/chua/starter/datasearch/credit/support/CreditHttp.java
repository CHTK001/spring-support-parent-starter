package com.chua.starter.datasearch.credit.support;

import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.network.client.HttpClientBuilder;
import com.chua.common.support.network.client.HttpClientFactory;
import com.chua.starter.datasearch.credit.model.CreditQuery;

import java.util.Map;

/**
 * 余额查询的 HTTP 出口。
 *
 * <p>各实现只在「地址怎么拼、令牌怎么带」上有差别，真正发请求这一段共用一个出口，
 * 避免每家复制一遍超时 / 鉴权 / 非 2xx 处理。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
public final class CreditHttp {

    /**
     * 默认鉴权请求头
     */
    public static final String AUTH_HEADER = "Authorization";

    /**
     * 令牌前缀
     */
    private static final String BEARER = "Bearer ";

    /**
     * 已自带鉴权方案的前缀
     */
    private static final String[] SCHEME_PREFIX = {"Bearer ", "Basic ", "Key ", "Token "};

    private static final String METHOD_POST = "POST";

    private static final String METHOD_PUT = "PUT";

    private static final String METHOD_DELETE = "DELETE";

    private static final String METHOD_PATCH = "PATCH";

    private static final int SNIPPET_LIMIT = 200;

    private CreditHttp() {
    }

    /**
     * 发起请求并返回响应正文。
     *
     * @param query 请求
     * @param url   完整地址
     * @return 响应正文
     * @throws IllegalStateException 地址缺失、网络异常或非 2xx
     */
    public static String fetch(CreditQuery query, String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("未配置余额地址");
        }
        // AK/SK 签名方案走独立出口：签名器已按各家规范产出「完整 URL + 最终请求头」，
        // 这里原样发出，不再补 Bearer、不再二次编码，避免签名字节与实际请求错位。
        if (query.getSignType() != null && !query.getSignType().isBlank()) {
            return fetchSigned(CreditSigner.sign(query, url.trim()));
        }
        HttpClientBuilder builder = HttpClientFactory.of(url.trim());
        builder.accept("application/json");
        builder.header("User-Agent", "spring-support-datasearch/1.0");
        if (query.getHeaders() != null) {
            for (Map.Entry<String, String> entry : query.getHeaders().entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }
        }
        String token = query.getToken();
        if (token != null && !token.isBlank()) {
            String header = query.getTokenHeader() == null || query.getTokenHeader().isBlank()
                    ? AUTH_HEADER : query.getTokenHeader().trim();
            builder.header(header, authorize(header, token.trim()));
        }
        String body = query.getBody();
        boolean hasBody = body != null && !body.isBlank();
        Map<String, String> params = query.getParams();
        String method = query.getMethod() == null || query.getMethod().isBlank()
                ? "GET" : query.getMethod().trim().toUpperCase();
        if (hasBody) {
            builder.json().body(body);
        } else if (params != null && !params.isEmpty()) {
            if ("GET".equals(method)) {
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    builder.query(entry.getKey(), entry.getValue());
                }
            } else {
                builder.form();
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    builder.formData(entry.getKey(), entry.getValue());
                }
            }
        }
        try {
            ClientResponse response = execute(builder, method);
            String text = response.getBodyString();
            if (!response.isSuccess()) {
                throw new IllegalStateException("HTTP " + response.getStatusCode() + " " + snippet(text));
            }
            return text;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发出一次已签名的请求。
     *
     * <p>签名器给出的地址与请求头就是最终形态：地址已含签名参数、头已含签名值，
     * 这里只做「建请求 → 按方法执行 → 判 2xx」，绝不追加 {@code Bearer} 或改 URL，
     * 否则签名会与请求不一致。</p>
     *
     * @param signed 签名后的请求
     * @return 响应正文
     * @throws IllegalStateException 网络异常或非 2xx
     */
    private static String fetchSigned(CreditSigner.Signed signed) {
        HttpClientBuilder builder = HttpClientFactory.of(signed.getUrl());
        builder.accept("application/json");
        builder.header("User-Agent", "spring-support-datasearch/1.0");
        for (Map.Entry<String, String> entry : signed.getHeaders().entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        String body = signed.getBody();
        if (body != null && !body.isEmpty()) {
            // Content-Type 已由签名器写进 headers，这里只设 body，不再 json() 重复设头
            builder.body(body);
        }
        String method = signed.getMethod() == null ? "GET" : signed.getMethod().toUpperCase();
        try {
            ClientResponse response = execute(builder, method);
            String text = response.getBodyString();
            if (!response.isSuccess()) {
                throw new IllegalStateException("HTTP " + response.getStatusCode() + " " + snippet(text));
            }
            return text;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 拼鉴权头值：已带方案的原样返回，否则补 Bearer。
     *
     * @param header 请求头名
     * @param token  令牌
     * @return 头值
     */
    public static String authorize(String header, String token) {
        if (!AUTH_HEADER.equalsIgnoreCase(header)) {
            return token;
        }
        for (String prefix : SCHEME_PREFIX) {
            if (token.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return token;
            }
        }
        return BEARER + token;
    }

    /**
     * 拼接地址：base 去掉末尾斜杠后接 path，path 为空时原样返回。
     *
     * @param base 基础地址
     * @param path 路径
     * @return 完整地址
     */
    public static String join(String base, String path) {
        if (base == null || base.isBlank()) {
            return path;
        }
        String text = base.trim();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        if (path == null || path.isBlank()) {
            return text;
        }
        return text + (path.startsWith("/") ? path : "/" + path);
    }

    /**
     * 按方法执行请求。
     *
     * @param builder 构建器
     * @param method  方法
     * @return 响应
     */
    private static ClientResponse execute(HttpClientBuilder builder, String method) {
        if (METHOD_POST.equals(method)) {
            return builder.post();
        }
        if (METHOD_PUT.equals(method)) {
            return builder.put();
        }
        if (METHOD_DELETE.equals(method)) {
            return builder.delete();
        }
        if (METHOD_PATCH.equals(method)) {
            return builder.patch();
        }
        return builder.get();
    }

    /**
     * 截取响应片段用于错误提示。
     *
     * @param text 响应
     * @return 片段
     */
    private static String snippet(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() <= SNIPPET_LIMIT ? oneLine : oneLine.substring(0, SNIPPET_LIMIT);
    }
}
