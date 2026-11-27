package com.chua.starter.datasearch.credit.support;

import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.network.client.HttpClientBuilder;
import com.chua.common.support.network.client.HttpClientFactory;
import com.chua.starter.datasearch.credit.model.CreditQuery;

import java.util.Map;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.CollectionUtils;

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

    /**
     * 默认 User-Agent
     */
    private static final String USER_AGENT = "spring-support-datasearch/1.0";

    /**
     * User-Agent 请求头名
     */
    private static final String USER_AGENT_HEADER = "User-Agent";

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
        try {
            ClientResponse response = execute(build(query, url.trim()), methodOf(query));
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
     * 发起请求并返回完整响应（状态码 + 响应头 + 响应体）。
     *
     * <p>与 {@link #fetch} 的差别：不做「非 2xx 即异常」的裁剪。个别厂商（如 ModelScope）
     * 把额度信息放在响应头里，即使请求超限（429）这些头也在：调用方需要自己读头、自己
     * 判定成败，这里原样交回完整响应。</p>
     *
     * @param query 请求
     * @param url   完整地址
     * @return 完整响应
     * @throws IllegalStateException 地址缺失或网络异常（非 2xx 不算失败）
     */
    public static ClientResponse exchange(CreditQuery query, String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("未配置余额地址");
        }
        if (query.getSignType() != null && !query.getSignType().isBlank()) {
            // 签名类接口目前没有「读响应头」的诉求；仍按签名出口发一次，头从签名请求的响应里取
            CreditSigner.Signed signed = CreditSigner.sign(query, url.trim());
            HttpClientBuilder builder = HttpClientFactory.of(signed.getUrl());
            builder.accept("application/json");
            builder.header(USER_AGENT_HEADER, USER_AGENT);
            for (Map.Entry<String, String> entry : signed.getHeaders().entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
            String body = signed.getBody();
            if (StringUtils.isNotEmpty(body)) {
                builder.body(body);
            }
            String method = signed.getMethod() == null ? "GET" : signed.getMethod().toUpperCase();
            try {
                return execute(builder, method);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("请求失败: " + e.getMessage(), e);
            }
        }
        try {
            return execute(build(query, url.trim()), methodOf(query));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 组装请求：头、令牌、体 / 表单参数，与 {@link #fetch} 的出站口径完全一致。
     *
     * @param query 请求
     * @param url   完整地址（非空）
     * @return 构建器
     */
    private static HttpClientBuilder build(CreditQuery query, String url) {
        HttpClientBuilder builder = HttpClientFactory.of(url);
        builder.accept("application/json");
        builder.header(USER_AGENT_HEADER, USER_AGENT);
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
        if (hasBody) {
            builder.json().body(body);
        } else if (CollectionUtils.isNotEmpty(params)) {
            if ("GET".equals(methodOf(query))) {
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
        applyProxy(builder, query, url);
        return builder;
    }

    /**
     * 请求方法（大写），缺省 GET。
     *
     * @param query 请求
     * @return 方法名
     */
    private static String methodOf(CreditQuery query) {
        return query.getMethod() == null || query.getMethod().isBlank()
                ? "GET" : query.getMethod().trim().toUpperCase();
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
        if (StringUtils.isNotEmpty(body)) {
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
     * 应用出站代理：请求显式配置优先，其次 JVM 系统属性全局代理。
     *
     * <p>Apache HttpClient5 不读取 {@code -Dhttps.proxyHost} 这类 JVM 代理参数，
     * 这里显式补齐：部署侧配置了全局代理时，余额查询与其余出站流量同口径走代理；
     * 全局代理命中 {@code http.nonProxyHosts}（或 https 同名属性）时仍直连。</p>
     *
     * @param builder 请求构建器
     * @param query   请求
     * @param url     完整地址
     */
    private static void applyProxy(HttpClientBuilder builder, CreditQuery query, String url) {
        String proxy = query.getProxy();
        if (proxy != null && !proxy.isBlank()) {
            applyProxy(builder, proxy, url);
            return;
        }
        String system = systemProxy(url);
        if (system != null && !system.isBlank() && !nonProxied(url)) {
            applyProxy(builder, system, url);
        }
    }

    /**
     * 按给定代理地址配置构建器。
     *
     * <p>支持 {@code http://host:port}、{@code https://host:port} 与 {@code host:port}
     * 三种写法；SOCKS 代理不是 HTTP 代理，这里不处理（保持直连）。</p>
     *
     * @param builder 请求构建器
     * @param proxy   代理地址
     * @param url     完整地址（缺省端口时按协议取 80 / 443）
     */
    private static void applyProxy(HttpClientBuilder builder, String proxy, String url) {
        String text = proxy.trim();
        if (text.toLowerCase().startsWith("socks")) {
            return;
        }
        if (text.toLowerCase().startsWith("http://") || text.toLowerCase().startsWith("https://")) {
            text = text.substring(text.indexOf("://") + 3);
        }
        int slash = text.indexOf('/');
        if (slash >= 0) {
            text = text.substring(0, slash);
        }
        String host;
        int port;
        int colon = text.lastIndexOf(':');
        if (colon > 0) {
            host = text.substring(0, colon);
            try {
                port = Integer.parseInt(text.substring(colon + 1));
            } catch (NumberFormatException e) {
                return;
            }
        } else {
            host = text;
            port = url != null && url.toLowerCase().startsWith("https:") ? 443 : 80;
        }
        if (!host.isBlank() && port > 0 && port < 65536) {
            builder.proxy(host, port);
        }
    }

    /**
     * 读 JVM 系统属性里的全局代理；未配置返回 null。
     *
     * @param url 完整地址（决定读 https 还是 http 的属性）
     * @return 代理地址，如 {@code http://192.168.0.1:7897}；未配置返回 null
     */
    private static String systemProxy(String url) {
        boolean secure = url != null && url.toLowerCase().startsWith("https:");
        String host = System.getProperty(secure ? "https.proxyHost" : "http.proxyHost");
        if (host == null || host.isBlank()) {
            return null;
        }
        String port = System.getProperty(secure ? "https.proxyPort" : "http.proxyPort");
        int value = defaultProxyPort(port, secure);
        return (secure ? "https://" : "http://") + host.trim() + ":" + value;
    }

    /**
     * 解析代理端口，非法或缺省时按协议回落 80 / 443。
     *
     * @param port   端口文本
     * @param secure 是否 HTTPS
     * @return 端口号
     */
    private static int defaultProxyPort(String port, boolean secure) {
        if (port != null && !port.isBlank()) {
            try {
                return Integer.parseInt(port.trim());
            } catch (NumberFormatException ignored) {
                // 落到默认端口
            }
        }
        return secure ? 443 : 80;
    }

    /**
     * 目标是否命中 JVM 的 nonProxyHosts 名单（命中则直连）。
     *
     * <p>匹配语义与 JVM 一致：{@code 127.*} 前缀通配、{@code *.local} 后缀通配、
     * 其余精确匹配；https 地址优先读 {@code https.nonProxyHosts}，未配置回落
     * {@code http.nonProxyHosts}。</p>
     *
     * @param url 完整地址
     * @return true 表示不走代理
     */
    private static boolean nonProxied(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String text = url.trim().toLowerCase();
        boolean secure = text.startsWith("https:");
        String patterns = secure && System.getProperty("https.nonProxyHosts") != null
                ? System.getProperty("https.nonProxyHosts") : System.getProperty("http.nonProxyHosts");
        if (patterns == null || patterns.isBlank()) {
            return false;
        }
        String host = hostOf(text);
        if (host == null) {
            return false;
        }
        for (String pattern : patterns.split("\\|")) {
            String item = pattern.trim().toLowerCase();
            if (item.isEmpty()) {
                continue;
            }
            boolean head = item.startsWith("*");
            boolean tail = item.endsWith("*");
            String core = head ? item.substring(1) : item;
            if (tail) {
                core = core.substring(0, core.length() - 1);
            }
            boolean hit;
            if (head && tail) {
                hit = host.contains(core);
            } else if (head) {
                hit = host.endsWith(core);
            } else if (tail) {
                hit = host.startsWith(core);
            } else {
                hit = host.equals(core);
            }
            if (hit) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从地址取主机名（小写）。
     *
     * @param url 小写的完整地址
     * @return 主机名；取不到返回 null
     */
    private static String hostOf(String url) {
        int start = url.indexOf("://");
        start = start < 0 ? 0 : start + 3;
        int end = url.indexOf('/', start);
        String authority = end < 0 ? url.substring(start) : url.substring(start, end);
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            authority = authority.substring(at + 1);
        }
        int colon = authority.lastIndexOf(':');
        if (colon > 0) {
            try {
                Integer.parseInt(authority.substring(colon + 1));
                authority = authority.substring(0, colon);
            } catch (NumberFormatException ignored) {
                // IPv6 裸地址等场景不裁剪
            }
        }
        return authority.isEmpty() ? null : authority;
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
