package com.chua.starter.filesystem.support.server.embedded;

import com.chua.common.support.network.http.HttpHeader;
import com.chua.common.support.network.http.HttpMethod;
import com.chua.common.support.network.server.request.AbstractServerRequest;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把 {@link HttpServletRequest} 适配成 common-starter 的 {@code ServerRequest}。
 *
 * <p>内置形态下 {@code FileStorageServerFilter} 依旧按 {@code /{桶}/{键}} 解析路径，
 * 因此这里接收的是**已去掉应用内路径前缀**的路径（例如 {@code /fs/local/a.txt} → {@code /local/a.txt}），
 * 这样内置与独立两种形态对过滤器而言完全一致。</p>
 *
 * <p>请求体按需读取并缓存（与 JDK 服务端的 {@code HttpServerRequest} 语义一致），
 * 上传接口依赖 {@link #getBody()} 拿到原始字节。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class ServletServerRequest extends AbstractServerRequest {

    /**
     * 原始 Servlet 请求
     */
    private final HttpServletRequest request;

    /**
     * 去前缀后的请求路径（已解码，以 {@code /} 开头）
     */
    private final String path;

    /**
     * 原始查询串（可能为 null）
     */
    private final String query;

    /**
     * 解析后的查询参数（懒加载）
     */
    private Map<String, String> params;

    /**
     * 创建 Servlet 请求适配器。
     *
     * @param request 原始请求
     * @param path    去前缀后的路径
     * @param query   查询串
     */
    public ServletServerRequest(HttpServletRequest request, String path, String query) {
        this.request = request;
        this.path = path == null || path.isEmpty() ? "/" : path;
        this.query = query;
    }

    @Override
    public String getUri() {
        return query == null || query.isEmpty() ? path : path + "?" + query;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public HttpMethod getMethod() {
        try {
            return HttpMethod.valueOf(request.getMethod().toUpperCase());
        } catch (Exception e) {
            return HttpMethod.GET;
        }
    }

    @Override
    public String getHeader(String name) {
        return request.getHeader(name);
    }

    @Override
    public HttpHeader getHeaders() {
        HttpHeader headers = HttpHeader.create();
        for (String name : java.util.Collections.list(request.getHeaderNames())) {
            java.util.Enumeration<String> values = request.getHeaders(name);
            while (values != null && values.hasMoreElements()) {
                headers.add(name, values.nextElement());
            }
        }
        return headers;
    }

    @Override
    public Map<String, String> getParams() {
        if (params != null) {
            return params;
        }
        Map<String, String> parsed = new LinkedHashMap<>();
        if (query != null && !query.isEmpty()) {
            for (String pair : query.split("&")) {
                if (pair.isEmpty()) {
                    continue;
                }
                int idx = pair.indexOf('=');
                String key = idx < 0 ? pair : pair.substring(0, idx);
                String value = idx < 0 ? "" : pair.substring(idx + 1);
                parsed.put(decode(key), decode(value));
            }
        }
        this.params = parsed;
        return parsed;
    }

    @Override
    public String getRemoteAddress() {
        return request.getRemoteAddr();
    }

    @Override
    public int getRemotePort() {
        return request.getRemotePort();
    }

    @Override
    protected byte[] readBody() {
        try (InputStream in = request.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("读取请求体失败", e);
        }
    }

    /**
     * URL 解码（表单/查询串语义，{@code +} 视为空格）。
     *
     * @param value 原始值
     * @return 解码后的值
     */
    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
}
