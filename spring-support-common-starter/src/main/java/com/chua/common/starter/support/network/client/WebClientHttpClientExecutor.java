package com.chua.common.starter.support.network.client;

import com.chua.common.support.network.client.ClientRequest;
import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.network.client.spi.HttpClientExecutor;
import com.chua.common.support.network.http.HttpHeader;
import com.chua.common.support.network.http.HttpVersion;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.spi.annotations.ConditionalOnClass;
import com.chua.common.support.spi.annotations.Spi;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 基于 Spring web客户端 的 HTTP 客户端执行器。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi("webclient")
@ConditionalOnClass("org.springframework.web.reactive.function.client.WebClient")
public class WebClientHttpClientExecutor implements HttpClientExecutor {

    /**
     * Web客户端
    */
    private final WebClient webClient;

    /**
     * 创建 web客户端http客户端执行器 实例
    */
    public WebClientHttpClientExecutor() {
        this.webClient = WebClient.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    @Override
    /**
     * 获取名称
     * @return 返回值
    */
    public String getName() {
        return "webclient";
    }

    @Override
    /**
     * 是否可用
     * @return 返回值
    */
    public boolean isAvailable() {
        return ReflectUtils.forName("org.springframework.web.reactive.function.client.WebClient") != null;
    }

    @Override
    /**
     * 获取订单
     * @return 返回值
    */
    public int getOrder() {
        return 50;
    }

    @Override
    /**
     * 执行
     * @param request 参数
     * @return 返回值
     * @throws Exception 异常
    */
    public ClientResponse execute(ClientRequest request) throws Exception {
        WebClient.RequestBodySpec spec = buildSpec(request);
        Object body = request.getBody();
        if (body != null) {
            return toClientResponse(spec.bodyValue(body).exchangeToMono(this::mapResponse).block());
        }
        return toClientResponse(spec.exchangeToMono(this::mapResponse).block());
    }

    @Override
    /**
     * 执行异步
     * @param request 参数
     * @return 返回值
    */
    public Mono<ClientResponse> executeAsync(ClientRequest request) {
        WebClient.RequestBodySpec spec = buildSpec(request);
        Object body = request.getBody();
        return body != null
                ? spec.bodyValue(body).exchangeToMono(this::mapResponse)
                : spec.exchangeToMono(this::mapResponse);
    }

    @Override
    /**
     * 支持版本
     * @return 返回值
    */
    public List<HttpVersion> supportedVersions() {
        return List.of(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2);
    }

    /**
     * 构建Spec
     *
     * @param request 请求
     * @return 构建spec的结果
     */
    private WebClient.RequestBodySpec buildSpec(ClientRequest request) {
        WebClient.RequestBodySpec spec = webClient
                .method(toHttpMethod(request.getMethod()))
                .uri(request.getUrl());

        if (request.getHeaders() != null) {
            spec.headers(headers -> {
                for (Map.Entry<String, String> entry : request.getHeaders().toMap().entrySet()) {
                    headers.set(entry.getKey(), entry.getValue());
                }
            });
        }
        return spec;
    }

    /**
     * 映射响应
     *
     * @param resp resp
     * @return 映射响应的结果
     */
    private Mono<ClientResponse> mapResponse(org.springframework.web.reactive.function.client.ClientResponse resp) {
        ClientResponse cr = new ClientResponse();
        cr.setStatusCode(resp.statusCode().value());
        HttpHeader header = HttpHeader.create();
        resp.headers().asHttpHeaders().forEach((k, v) -> {
            if (v != null && !v.isEmpty()) {
                header.add(k, String.join(", ", v));
            }
        });
        cr.setHeaders(header);
        return resp.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(b -> {
                    cr.setBody(b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    return cr;
                });
    }

    /**
     * 转为http方法
     *
     * @param method 方法
     * @return 转为http方法的结果
     */
    private static HttpMethod toHttpMethod(com.chua.common.support.network.http.HttpMethod method) {
        return switch (method) {
            case GET -> HttpMethod.GET;
            case POST -> HttpMethod.POST;
            case PUT -> HttpMethod.PUT;
            case DELETE -> HttpMethod.DELETE;
            case PATCH -> HttpMethod.PATCH;
            case HEAD -> HttpMethod.HEAD;
            case OPTIONS -> HttpMethod.OPTIONS;
        };
    }

    /**
     * 转为客户端响应
     *
     * @param response 响应
     * @return 转为客户端响应的结果
     */
    private static ClientResponse toClientResponse(ClientResponse response) {
        return response;
    }

    @Override
    /**
     * 关闭
    */
    public void close() {
    }
}
