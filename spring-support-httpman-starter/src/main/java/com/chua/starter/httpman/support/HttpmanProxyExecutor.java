package com.chua.starter.httpman.support;

import com.chua.starter.httpman.config.HttpmanProperties;
import com.chua.starter.httpman.model.vo.HttpmanResponse;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.transport.ProxyProvider;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 接口调试控制台 - HTTP 代理执行器
 * <p>基于 Spring WebClient（ReactorClientHttpConnector + reactor-netty 1.3）发出真实 HTTP 请求。
 * 承载以下能力：</p>
 * <ul>
 *   <li>代理配置：显式代理 / 从环境变量 / 不代理（noProxy）。</li>
 *   <li>SSL 校验开关：{@code httpman.sslInsecure=true} 时跳过证书校验（自签/地对错误证书的场景）。</li>
 *   <li>超时：连接超时 + 读取超时（通过 {@code HttpClient.responseTimeout} + 内部 {@code Mono.timeout}）。</li>
 *   <li>鉴权头脱敏：运行记录中的 {@code Authorization} / {@code Cookie} 会做脱敏，让凭据不把落库。</li>
 *   <li>方法白名单：由调用方 {@link com.chua.starter.httpman.service.HttpmanService#execute} 先校验。</li>
 * </ul>
 *
 * <p><b>重要说明</b>：{@code WebClient.Builder} 没有 {@code proxy()} 方法，所以代理不经
 * WebClient.Builder，而是通过 {@code clientConnector(new ReactorClientHttpConnector(resourceFactory,
 * httpclient -> httpClient.proxy(...)))} 直接喂给 reactor-netty。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public class HttpmanProxyExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpmanProxyExecutor.class);

    /** 脱敏的敏感头（运行记录里不存真实凭据） */
    private static final Set<String> SENSITIVE_HEADERS = Set.of("authorization", "cookie", "x-oauth-token");

    /** 脱敏敏感头名（与 behind 一致） */
    static final Set<String> SENSITIVE_KEYS = Set.of("authorization", "cookie", "x-oauth-token");

    /** 版本号和配置 */
    private final HttpmanProperties properties;

    /** 上游 WebClient（连接池由 ReactorResourceFactory 管理） */
    private final WebClient webClient;

    /**
     * 创建执行器。
     *
     * @param properties 全局配置
     */
    public HttpmanProxyExecutor(HttpmanProperties properties) {
        this.properties = properties;
        this.webClient = buildWebClient();
    }

    /** 构建 WebClient。连接池由独立建立的 {@link ReactorResourceFactory} 管理 */
    private WebClient buildWebClient() {
        ReactorResourceFactory resourceFactory = new ReactorResourceFactory();
        resourceFactory.setConnectionProviderSupplier(() -> ConnectionProvider.create(
                "httpman",
                cp -> cp
                        .maxConnections(512)
                        .pendingAcquireTimeout(Duration.ofSeconds(30))
                        .maxLifeTime(Duration.ofSeconds(600))
                        .idleResourceTimeout(Duration.ofSeconds(60))));
        resourceFactory.afterPropertiesSet();

        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(
                resourceFactory,
                this::httpClientMapper);
        return WebClient.builder().clientConnector(connector).build();
    }

    /**
     * reactor-netty 端：按配置拼代理/SSL/超时/重定向。
     *
     * @param httpClient 尚无代理/SSL/超时名称的 HttpClient
     * @return 代理化后的 HttpClient
     */
    private HttpClient httpClientMapper(HttpClient httpClient) {
        // 1) 代理地址（覆盖 java.net.proxy* 系统属性）
        httpClient = mapProxy(httpClient);
        // 2) SSL（跳过证书校验，自签/地对错误证书）
        if (properties.isSslInsecure()) {
            return httpClient.noSSL();
        }
        // 3) 读取超时（连接超时由 reactor-netty 内部兜底）
        return httpClient.responseTimeout(Duration.ofSeconds(properties.getReadTimeout().toSeconds()))
                .followRedirect(true);
    }

    /**
     * 对地址解析，再由 reactor-netty 的 ProxyProvider 接管
     *
     * @param httpClient 无代理的 HttpClient
     * @return 代理化后的 HttpClient
     */
    private HttpClient mapProxy(HttpClient httpClient) {
        String proxy = properties.getProxy();
        if (proxy == null || proxy.isBlank()) {
            return httpClient.noProxy();
        }
        return httpClient.proxy(typeSpec -> typeSpec
                .type(ProxyProvider.Proxy.HTTP)
                .host(new URI(proxy).getHost())
                .port(new URI(proxy).getPort())
                .nonProxyHosts(properties.getProxyBypass()));
    }

    /**
     * 显式地将单次执行拆成 {@code Mono<HttpmanResponse>}，因为执行器不能控制上层。
     *
     * @param method   方法（GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD）
     * @param url      完整地址（含 scheme+host）
     * @param headers  已脱敏的请求头（name -> value）
     * @param params   查询参数（name -> values，可能为空）
     * @param body     正文
     * @param bodyType 正文类型（json/form-data/x-www-form-urlencoded/none）
     * @return 执行结果
     */
    public Mono<HttpmanResponse> execute(String method, String url, Map<String, String> headers,
                                          Map<String, List<String>> params, String body, String bodyType) {
        long start = System.currentTimeMillis();
        try {
            HttpMethod httpMethod = HttpMethod.valueOf(method);
            // 查询参数通过 URI 的 scheme/host/path 组合注入（params 可能悬挂 uri）
            URI uri = buildUri(url, params);
            return webClient.method(httpMethod)
                    .uri(uri)
                    .headers(httpHeaders -> headers.forEach(httpHeaders::add))
                    .bodyValue(body)
                    .contentType(httpHeaders -> applyContentType(httpHeaders, bodyType))
                    .retrieve()
                    .toEntity(byte[].class)
                    .map(responseEntity -> toResponse(method, url, responseEntity, start))
                    .timeout(Duration.ofSeconds(properties.getReadTimeout().toSeconds()))
                    .onErrorResume(e -> Mono.just(HttpmanResponse.error(method, url, System.currentTimeMillis() - start, e)));
        } catch (Throwable e) {
            log.error("HttpmanProxyExecutor execute failed: {}", e.getMessage());
            return Mono.just(HttpmanResponse.error(method, url, System.currentTimeMillis() - start, e));
        }
    }

    /** 组装查询参数：挂在第一个进入的 query 上，其余依次追加（不用重新拼接） */
    private URI buildUri(String url, Map<String, List<String>> params) throws Exception {
        URI uri = new URI(url);
        if (params == null || params.isEmpty()) {
            return uri;
        }
        String query = uri.getQuery();
        StringBuilder sb = new StringBuilder();
        if (query != null) {
            sb.append(query);
        }
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            for (String value : entry.getValue()) {
                sb.append(entry.getKey()).append('=').append(value);
            }
        }
        return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(),
                uri.getPath(), sb.length() > 0 ? sb.toString() : null);
    }

    /**
     * ContentType
     */
    private void applyContentType(HttpHeaders httpHeaders, String bodyType) {
        if (bodyType == null || bodyType.trim().isEmpty() || "none".equalsIgnoreCase(bodyType.trim())) {
            return;
        }
        String type;
        switch (bodyType.trim().toLowerCase()) {
            case "json":
                type = HttpHeaderValues.APPLICATION_JSON.toString();
                break;
            case "form-data":
                type = HttpHeaderValues.APPLICATION_FORM_URLENCODED.toString();
                break;
            case "x-www-form-urlencoded":
                type = HttpHeaderValues.APPLICATION_FORM_URLENCODED.toString();
                break;
            case "text":
                type = HttpHeaderValues.TEXT_PLAIN.toString();
                break;
            default:
                type = bodyType.trim();
                break;
        }
        httpHeaders.setContentType(type);
    }

    /**
     * 从 {@link org.springframework.http.client.ClientHttpResponse} 回写执行结果
     */
    private HttpmanResponse toResponse(String method, String url, org.springframework.http.ResponseEntity<byte[]> responseEntity, long start) {
        HttpmanResponse response = new HttpmanResponse();
        response.setStatus(responseEntity.getStatusCode().value());
        response.setHeaders(responseEntity.getHeaders().toSingleValueMap());
        response.setBody(String.valueOf(responseEntity.getBody() == null
                ? ""
                : new String(responseEntity.getBody(), StandardCharsets.UTF_8)));
        response.setBodyLength(responseEntity.getBody() == null ? 0 : responseEntity.getBody().length);
        response.setCostMs(System.currentTimeMillis() - start);
        return response;
    }

    /**
     * 头部脱敏，返回脱敏后的映射（body / responseRecord 等只读脱敏数据）
     */
    static Map<String, String> maskHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, String> mask = new HashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            if (key == null || !SENSITIVE_HEADERS.contains(key.toLowerCase())) {
                mask.put(key, entry.getValue());
                continue;
            }
            mask.put(key, "******");
        }
        return mask;
    }
}
