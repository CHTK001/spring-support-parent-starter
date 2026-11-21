package com.chua.starter.cdp.proxy;

import com.chua.common.support.network.server.filter.ServerFilter;
import com.chua.common.support.network.server.filter.ServerFilterChain;
import com.chua.common.support.network.server.request.ServerRequest;
import com.chua.common.support.network.server.response.ServerResponse;
import com.chua.starter.cdp.config.CdpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * 实时页面反向代理过滤器
 *
 * <p>挂在独立的 chua {@link Server} 上，把 {@code /{sessionId}/{上游地址}} 形式的请求
 * 转发给真实上游：</p>
 * <ul>
 *   <li>无头浏览器与用户浏览器（iframe）访问同一会话，共享服务端 Cookie 罐；</li>
 *   <li>上游 {@code Set-Cookie} 记入会话罐并从客户端响应中剥离；</li>
 *   <li>响应剥离 {@code X-Frame-Options} / {@code CSP frame-ancestors}，允许 iframe 嵌入；</li>
 *   <li>HTML 响应注入 {@code <base>} 与钩子脚本，让页面后续请求继续走代理；</li>
 *   <li>重定向 {@code Location} 改写为代理路径；WebSocket 升级请求暂不支持（400）。</li>
 * </ul>
 *
 * <p>上游调用使用 JDK {@link HttpClient}（不跟随重定向，逐跳记录 Cookie）；
 * 本服务器为每请求虚拟线程模型，阻塞式转发不会拖垮 IO 事件循环。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@RequiredArgsConstructor
public class CdpDebugProxyFilter implements ServerFilter {

    /**
     * 请求头复制黑名单：逐跳头 / 由代理重算的头 / 会话罐接管的头
     */
    private static final Set<String> REQUEST_HEADER_SKIP = Set.of(
            "host", "connection", "keep-alive", "transfer-encoding", "upgrade",
            "proxy-connection", "proxy-authorization", "te", "trailer",
            "accept-encoding", "content-length", "cookie");

    /**
     * 响应头剥离名单：会话罐接管 / 逐跳头 / 阻止 iframe 嵌入的头
     */
    private static final Set<String> RESPONSE_HEADER_SKIP = Set.of(
            "set-cookie", "connection", "keep-alive", "transfer-encoding",
            "content-length", "content-encoding", "upgrade",
            "x-frame-options", "content-security-policy",
            "cross-origin-embedder-policy", "cross-origin-opener-policy",
            "cross-origin-resource-policy");

    /**
     * CDP 属性
     */
    private final CdpProperties properties;

    /**
     * 会话管理器
     */
    private final ProxySessionManager sessions;

    /**
     * 脚本注入器
     */
    private final CdpProxyScriptInjector injector;

    /**
     * 上游 HTTP 客户端（不自动跟随重定向，保证逐跳 Cookie 记账）
     */
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public void doFilter(ServerRequest request, ServerResponse response, ServerFilterChain chain) throws Exception {
        String uri = request.getUri();
        if (uri == null || uri.isEmpty()) {
            endText(response, HttpURLConnection.HTTP_BAD_REQUEST, "缺少请求路径");
            return;
        }
        String path = uri.startsWith("/") ? uri.substring(1) : uri;
        int slash = path.indexOf('/');
        String sessionId = slash < 0 ? path : path.substring(0, slash);
        String rest = slash < 0 ? "" : path.substring(slash + 1);
        ProxySession session = sessions.get(sessionId);
        if (session == null) {
            endText(response, HttpURLConnection.HTTP_NOT_FOUND, "会话不存在或已超时: " + sessionId);
            return;
        }
        String connection = request.getHeader("Connection");
        String upgrade = request.getHeader("Upgrade");
        if (upgrade != null && connection != null && connection.toLowerCase(Locale.ROOT).contains("upgrade")) {
            endText(response, HttpURLConnection.HTTP_BAD_REQUEST, "代理暂不支持 WebSocket 升级请求");
            return;
        }
        URI upstream;
        try {
            upstream = resolveUpstream(session, rest);
            sessions.validateUpstream(upstream);
        } catch (IllegalArgumentException e) {
            endText(response, HttpURLConnection.HTTP_BAD_REQUEST, e.getMessage());
            return;
        }
        try {
            forward(request, response, session, upstream);
        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("[cdp-proxy] 上游超时: {} -> {}", session.getId(), upstream);
            endText(response, HttpURLConnection.HTTP_GATEWAY_TIMEOUT, "上游响应超时");
        } catch (Exception e) {
            log.warn("[cdp-proxy] 上游请求失败: {} -> {} : {}", session.getId(), upstream, e.getMessage());
            endText(response, HttpURLConnection.HTTP_BAD_GATEWAY, "上游请求失败: " + e.getMessage());
        }
    }

    /**
     * 转发请求并回写响应
     */
    private void forward(ServerRequest request, ServerResponse response, ProxySession session, URI upstream)
            throws Exception {
        String method = request.getMethod() == null ? "GET" : request.getMethod().name();
        HttpRequest.Builder builder = HttpRequest.newBuilder(upstream)
                .timeout(Duration.ofSeconds(30))
                // 只声明 gzip：绝大多数上游支持，且本代理可解压后注入；不透传客户端的 br/zstd 声明，
                // 避免拿到无法解压注入的 HTML（非 HTML 响应的压缩体原样透传，由客户端解压）
                .header("Accept-Encoding", "gzip");
        for (Map.Entry<String, String> entry : request.getHeaders().toMap().entrySet()) {
            String name = entry.getKey();
            if (name == null || REQUEST_HEADER_SKIP.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            builder.header(name, entry.getValue());
        }
        String cookie = session.cookieHeader(upstream.getHost());
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        byte[] body = request.getBody();
        boolean hasBody = body != null && body.length > 0;
        builder.method(method, hasBody ? HttpRequest.BodyPublishers.ofByteArray(body) : HttpRequest.BodyPublishers.noBody());
        HttpResponse<byte[]> upstreamResponse = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        int status = upstreamResponse.statusCode();
        for (String setCookie : upstreamResponse.headers().allValues("set-cookie")) {
            session.storeCookie(setCookie, upstream.getHost());
        }
        response.setStatus(status);
        String host = request.getHeader("Host");
        String contentType = null;
        for (Map.Entry<String, List<String>> entry : upstreamResponse.headers().map().entrySet()) {
            String name = entry.getKey();
            String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
            if (RESPONSE_HEADER_SKIP.contains(lower)) {
                continue;
            }
            if ("content-type".equals(lower)) {
                contentType = entry.getValue() == null || entry.getValue().isEmpty() ? null : entry.getValue().get(0);
            }
            if ("location".equals(lower)) {
                continue;
            }
            if (entry.getValue() != null) {
                for (String value : entry.getValue()) {
                    response.setHeader(name, value);
                }
            }
        }
        if (status >= 300 && status < 400) {
            String location = upstreamResponse.headers().firstValue("location").orElse(null);
            if (location != null) {
                URI target = upstream.resolve(location);
                response.setHeader("Location", toProxyPath(session, target));
            }
        }
        byte[] responseBody = upstreamResponse.body();
        boolean head = "HEAD".equalsIgnoreCase(method);
        // 上游压缩协商：HTML 注入前解压；其余压缩响应按压缩形态透传（客户端自行解压），大幅减少重页面传输量
        String contentEncoding = upstreamResponse.headers().firstValue("content-encoding").orElse(null);
        boolean compressed = contentEncoding != null && !contentEncoding.isBlank()
                && !"identity".equalsIgnoreCase(contentEncoding);
        boolean injectable = !head && isHtml(contentType)
                && properties.getLiveProxy().isInjectScript()
                && responseBody != null && responseBody.length > 0;
        if (injectable && compressed) {
            byte[] plain = "gzip".equalsIgnoreCase(contentEncoding) ? gunzip(responseBody) : null;
            if (plain != null) {
                responseBody = inject(session, upstream, host, contentType, plain);
                compressed = false;
            }
            // 解压失败：保持压缩形态透传（页面可用但无钩子），绝不把压缩体当明文发出
        } else if (injectable) {
            responseBody = inject(session, upstream, host, contentType, responseBody);
        }
        if (compressed && contentEncoding != null) {
            response.setHeader("Content-Encoding", contentEncoding);
        }
        if (responseBody != null && responseBody.length > 0 && !head) {
            response.setBody(responseBody);
        }
        response.setHeader("Content-Length", String.valueOf(responseBody == null ? 0 : responseBody.length));
        response.end();
    }

    /**
     * 注入 {@code <base>} 与钩子脚本
     *
     * <p>{@code baseHref} 取客户端实际访问的代理地址（请求 Host 头 + 会话前缀 + 上游路径），
     * 保证跨机访问时相对地址仍解析到代理。</p>
     */
    private byte[] inject(ProxySession session, URI upstream, String host, String contentType, byte[] body) {
        try {
            String charsetName = injector.charsetOf(contentType);
            Charset charset;
            try {
                charset = Charset.forName(charsetName);
            } catch (Exception e) {
                charset = Charset.defaultCharset();
            }
            String html = new String(body, charset);
            String base = host == null || host.isBlank() ? selfBase() : "http://" + host;
            String baseHref = base + toProxyPath(session, upstream, false);
            String injected = injector.inject(html, baseHref, session.getId(), originOf(upstream));
            return injected.getBytes(charset);
        } catch (Exception e) {
            log.debug("[cdp-proxy] 脚本注入失败: {}", e.getMessage());
            return body;
        }
    }

    /**
     * gzip 解压；失败返回 {@code null}（调用方按压缩形态透传）
     */
    private byte[] gunzip(byte[] data) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return in.readAllBytes();
        } catch (IOException e) {
            log.debug("[cdp-proxy] gzip 解压失败，按压缩形态透传: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析上游地址：空 → 会话当前地址；绝对（含浏览器规范化后的单斜杠形态）→ 原样；
     * 其余 → 相对会话当前地址解析
     */
    private URI resolveUpstream(ProxySession session, String rest) {
        String candidate = rest == null ? "" : rest.trim();
        if (candidate.isEmpty()) {
            return URI.create(session.getCurrentUrl());
        }
        String normalized = candidate;
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https:/") && !lower.startsWith("https://")) {
            normalized = "https://" + normalized.substring("https:/".length());
        } else if (lower.startsWith("http:/") && !lower.startsWith("http://")) {
            normalized = "http://" + normalized.substring("http:/".length());
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            int query = normalized.indexOf('?');
            String base = query < 0 ? normalized : normalized.substring(0, query);
            return URI.create(base + (query < 0 ? "" : normalized.substring(query)));
        }
        URI current = URI.create(session.getCurrentUrl());
        return current.resolve(candidate);
    }

    /**
     * 代理路径形式：{@code /{sessionId}/{scheme:/host/path}}
     *
     * @param withQuery true 时附带查询串
     */
    private String toProxyPath(ProxySession session, URI target, boolean withQuery) {
        String scheme = target.getScheme() == null ? "http" : target.getScheme();
        int port = target.getPort();
        String hostPort = port < 0 ? target.getHost() : target.getHost() + ":" + port;
        String path = target.getRawPath() == null || target.getRawPath().isEmpty() ? "/" : target.getRawPath();
        String query = withQuery && target.getRawQuery() != null ? "?" + target.getRawQuery() : "";
        return "/" + session.getId() + "/" + scheme + ":/" + hostPort + path + query;
    }

    /**
     * 代理路径形式（含查询串）
     */
    private String toProxyPath(ProxySession session, URI target) {
        return toProxyPath(session, target, true);
    }

    /**
     * 上游源：scheme://host[:port]
     */
    private String originOf(URI upstream) {
        int port = upstream.getPort();
        return upstream.getScheme() + "://" + upstream.getHost() + (port < 0 ? "" : ":" + port);
    }

    /**
     * 上游是否为 HTML
     */
    private boolean isHtml(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).contains("text/html");
    }

    /**
     * 无头浏览器视角的代理基地址
     */
    private String selfBase() {
        return "http://127.0.0.1:" + properties.getLiveProxy().getPort();
    }

    /**
     * 输出纯文本响应并结束
     */
    private void endText(ServerResponse response, int status, String message) {
        response.setStatus(status);
        response.setHeader("Content-Type", "text/plain; charset=utf-8");
        response.setHeader("Content-Length", String.valueOf(message.length()));
        response.setBody(message);
        response.end();
    }

    /**
     * @return 会话列表快照（供管理端点复用）
     */
    public List<ProxySession> snapshot() {
        return new ArrayList<>(sessions.list());
    }
}
