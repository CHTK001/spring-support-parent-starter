package com.chua.common.starter.support.network.client;

import com.chua.common.support.network.client.ClientRequest;
import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.network.client.spi.HttpClientExecutor;
import com.chua.common.support.network.http.HttpHeader;
import com.chua.common.support.network.http.HttpVersion;
import com.chua.common.support.spi.annotations.ConditionalOnClass;
import com.chua.common.support.spi.annotations.Spi;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 基于 Spring WebClient 的 HTTP 客户端执行器。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi("webclient")
@ConditionalOnClass("org.springframework.web.reactive.function.client.WebClient")
public class WebClientHttpClientExecutor implements HttpClientExecutor {

    private final WebClient webClient;

    public WebClientHttpClientExecutor() {
        this.webClient = WebClient.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    @Override
    public String getName() {
        return "webclient";
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("org.springframework.web.reactive.function.client.WebClient");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public int getOrder() {
        return 50;
    }

    @Override
    public ClientResponse execute(ClientRequest request) throws Exception {
        WebClient.RequestBodySpec spec = buildSpec(request);
        Object body = request.getBody();
        if (body != null) {
            return toClientResponse(spec.bodyValue(body).exchangeToMono(this::mapResponse).block());
        }
        return toClientResponse(spec.exchangeToMono(this::mapResponse).block());
    }

    @Override
    public CompletableFuture<ClientResponse> executeAsync(ClientRequest request) {
        WebClient.RequestBodySpec spec = buildSpec(request);
        Object body = request.getBody();
        Mono<ClientResponse> mono = body != null
                ? spec.bodyValue(body).exchangeToMono(this::mapResponse)
                : spec.exchangeToMono(this::mapResponse);
        return mono.toFuture();
    }

    @Override
    public List<HttpVersion> supportedVersions() {
        return List.of(HttpVersion.HTTP_1_1, HttpVersion.HTTP_2);
    }

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

    private static ClientResponse toClientResponse(ClientResponse response) {
        return response;
    }

    @Override
    public void close() {
    }
}