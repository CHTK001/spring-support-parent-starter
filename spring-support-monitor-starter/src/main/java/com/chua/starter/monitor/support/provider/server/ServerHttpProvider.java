package com.chua.starter.monitor.support.provider.server;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.starter.monitor.support.enums.ProviderType;
import com.chua.starter.monitor.support.enums.ServiceType;
import com.chua.starter.monitor.support.spi.CollectContext;
import com.chua.starter.monitor.support.spi.CollectResult;
import com.chua.starter.monitor.support.spi.MetricPoint;
import com.chua.starter.monitor.support.spi.MetricProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 服务器 HTTP 采集 Provider
 * <p>
 * 请求目标服务器暴露的 HTTP 接口(actuator/exporter 等),
 * 支持 JSON 响应按指标名取值, 原始文本返回可给终端/解析规则二次解析。
 * </p>
 *
 * @since 2026/8/4
 * @author CH
 */
@Slf4j
@Component
public class ServerHttpProvider implements MetricProvider {

    /**
     * 默认超时(毫秒)
     */
    private static final int DEFAULT_TIMEOUT_MS = 5_000;

    @Override
    /** ProviderType */
    public ProviderType providerType() {
        return ProviderType.HTTP;
    }

    @Override
    /** SupportServiceTypes */
    public Set<ServiceType> supportServiceTypes() {
        return Set.of(ServiceType.SERVER);
    }

    @Override
    /** Collect */
    public Mono<CollectResult> collect(CollectContext context) {
        return Mono.fromCallable(() -> doCollect(context))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 同步执行 HTTP 请求
     *
     * @param context 采集上下文
     * @return 采集结果
     */

    private CollectResult doCollect(CollectContext context) {
        CollectResult result = new CollectResult();
        result.setTask(context.getTask());
        result.setMonitor(context.getMonitor());

        JsonObject config = Json.getJsonObject(context.getTask().getCollectConfig());
        String url = str(config.getObject("url"));
        if (url == null || url.isEmpty()) {
            return result.fail("collect_config 缺少 url");
        }

        String method = str(config.getObject("method"));
        String body = str(config.getObject("body"));
        Integer timeout = intOrNull(config.getObject("timeout"));

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeout == null ? DEFAULT_TIMEOUT_MS : timeout))
                    .build();

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeout == null ? DEFAULT_TIMEOUT_MS : timeout));

            // 注入请求头
            JsonObject headers = config.getJsonObject("headers");
            if (headers != null) {
                headers.forEach((k, v) -> {
                    if (v != null) {
                        reqBuilder.header(k, String.valueOf(v));
                    }
                });
            }

            if ("POST".equalsIgnoreCase(method)) {
                reqBuilder.POST(body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
            } else {
                reqBuilder.GET();
            }

            HttpResponse<String> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            result.setRawOutput(responseBody);

            if (response.statusCode() >= 300) {
                return result.fail("HTTP 状态码异常: " + response.statusCode());
            }

            // JSON 响应自动取值
            if (isJson(responseBody)) {
                parseJsonFields(responseBody, result);
            }
            result.setSuccess(true);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("[ServerHttpProvider] HTTP 请求失败 url={}", url, e);
            result.fail(e.getMessage());
        }
        return result;
    }

    /**
     * 解析 JSON 响应中的数值字段为指标点
     *
     * @param json   JSON 字符串
     * @param result 采集结果
     */
    private void parseJsonFields(String json, CollectResult result) {
        JsonObject obj = Json.getJsonObject(json);
        for (Map.Entry<String, Object> entry : obj.entrySet()) {
            if (entry.getValue() instanceof Number num) {
                result.addPoint(MetricPoint.builder()
                        .metricName(entry.getKey())
                        .value(num.doubleValue())
                        .unit("")
                        .tags(new HashMap<>())
                        .targetTable("ms_monitor_metric")
                        .build());
            }
        }
    }

    /**
     * 判断响应是否为 JSON
     *
     * @param body 响应体
     * @return true 表示为 JSON
     */
    private boolean isJson(String body) {
        if (body == null) {
            return false;
        }
        String trimmed = body.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    /**
     * 对象转字符串
     *
     * @param o 对象
     * @return 字符串
     */
    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /**
     * 对象转 Integer
     *
     * @param o 对象
     * @return Integer, 转换失败返回 null
     */
    private static Integer intOrNull(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}