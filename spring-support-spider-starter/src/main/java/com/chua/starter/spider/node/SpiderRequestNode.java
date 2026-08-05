package com.chua.starter.spider.node;

import com.chua.common.support.task.flow.FlowContext;
import com.chua.common.support.task.flow.FlowNode;
import com.chua.common.support.task.flow.FlowNodeField;
import com.chua.common.support.task.flow.FlowNodeOption;
import com.chua.common.support.task.flow.FlowProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 请求节点。
 *
 * <p>发起 HTTP 请求，将响应体、状态码与响应头写入上下文，供下游提取节点使用。
 * 请求 URL、请求头、请求体中支持 {@code {{env.KEY}}} 环境变量插值。</p>
 *
 * <p>节点配置说明：</p>
 * <ul>
 *   <li>{@code url} 请求地址（必填）</li>
 *   <li>{@code method} 请求方法，GET/POST/PUT/DELETE，默认 GET</li>
 *   <li>{@code headers} 请求头键值映射（可选）</li>
 *   <li>{@code body} 请求体，POST/PUT 时生效（可选）</li>
 *   <li>{@code timeoutMs} 超时时间（毫秒），默认 30000</li>
 * </ul>
 *
 * <p>执行后写入上下文的属性：</p>
 * <ul>
 *   <li>{@code spider.request.body} 响应体文本</li>
 *   <li>{@code spider.request.status} 响应状态码</li>
 *   <li>{@code spider.request.headers} 响应头映射</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Component
public class SpiderRequestNode implements FlowNode {

    /**
     * 请求失败占位信息
     */
    private static final Logger log = LoggerFactory.getLogger(SpiderRequestNode.class);

    /**
     * 响应体属性键
     */
    private static final String ATTR_BODY = "spider.request.body";

    /**
     * 响应状态码属性键
     */
    private static final String ATTR_STATUS = "spider.request.status";

    /**
     * 响应头属性键
     */
    private static final String ATTR_HEADERS = "spider.request.headers";

    /**
     * 默认请求方法
     */
    private static final String DEFAULT_METHOD = "GET";

    /**
     * 默认超时时间（毫秒）
     */
    private static final int DEFAULT_TIMEOUT_MS = 30000;

    /**
     * 环境变量解析器
     */
    private final SpiderEnvResolver envResolver;

    /**
     * 通过构造器注入环境变量解析器。
     *
     * @param envResolver 环境变量解析器
     */
    public SpiderRequestNode(SpiderEnvResolver envResolver) {
        this.envResolver = envResolver;
    }

    /**
     * 节点类型标识。
     *
     * @return 节点类型
     */
    @Override
    public String type() {
        return "spider.request";
    }

    /**
     * 节点配置表单元信息。
     *
     * @return 表单元信息列表
     */
    @Override
    public List<FlowNodeField> configSchema() {
        List<FlowNodeField> schema = new ArrayList<>();
        schema.add(FlowNodeField.input("url", "请求地址", true));
        schema.add(FlowNodeField.select("method", "请求方法", List.of(
                FlowNodeOption.of("GET", "GET"),
                FlowNodeOption.of("POST", "POST"),
                FlowNodeOption.of("PUT", "PUT"),
                FlowNodeOption.of("DELETE", "DELETE"))));
        schema.add(FlowNodeField.kv("headers", "请求头"));
        schema.add(FlowNodeField.textarea("body", "请求体", "POST/PUT 时发送的请求体"));
        schema.add(FlowNodeField.number("timeoutMs", "超时(ms)"));
        return schema;
    }

    /**
     * 执行请求节点。
     *
     * <p>根据配置构造请求并发送，将响应体、状态码、响应头写入上下文属性。</p>
     *
     * @param context 当前流程上下文
     */
    @Override
    public void execute(FlowContext context) {
        FlowProps props = context.currentNodeProps();
        String url = envResolver.resolve(props.getString("url"));
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("spider.request 节点缺少 url 参数");
        }
        String method = props.getString("method", DEFAULT_METHOD).toUpperCase();
        Map<String, Object> headers = props.getMap("headers");
        String body = envResolver.resolve(props.getString("body"));
        long timeoutMs = props.getInt("timeoutMs", DEFAULT_TIMEOUT_MS);

        HttpResponse<String> response = send(url, method, headers, body, timeoutMs);
        context.setAttribute(ATTR_BODY, response.body());
        context.setAttribute(ATTR_STATUS, response.statusCode());
        context.setAttribute(ATTR_HEADERS, response.headers().map());
        context.setData(response.body());
        log.info("spider.request 完成: {} {} -> {}", method, url, response.statusCode());
    }

    /**
     * 发送 HTTP 请求。
     *
     * <p>使用 JDK {@link HttpClient}，请求头做环境变量插值。</p>
     *
     * @param url       请求地址
     * @param method    请求方法
     * @param headers   请求头映射
     * @param body      请求体
     * @param timeoutMs 超时时间（毫秒）
     * @return 响应结果
     */
    private HttpResponse<String> send(String url, String method,
                                      Map<String, Object> headers, String body,
                                      long timeoutMs) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .build();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url));
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                builder.header(entry.getKey(), envResolver.resolve(String.valueOf(entry.getValue())));
            }
            switch (method) {
                case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
                case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
                case "DELETE" -> builder.DELETE();
                default -> builder.GET();
            }
            HttpRequest request = builder.build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("spider.request 请求被中断: " + url, e);
        } catch (Exception e) {
            throw new IllegalStateException("spider.request 请求失败: " + url, e);
        }
    }
}