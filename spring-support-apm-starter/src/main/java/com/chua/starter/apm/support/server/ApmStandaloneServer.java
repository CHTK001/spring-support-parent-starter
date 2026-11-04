package com.chua.starter.apm.support.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.network.http.HttpMethod;
import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.request.ServerRequest;
import com.chua.common.support.network.server.response.ServerResponse;
import com.chua.starter.apm.support.properties.ApmProperties;
import com.chua.starter.apm.support.web.ApmDispatcher;
import com.chua.starter.apm.support.web.ApmRequest;
import com.chua.starter.apm.support.web.ApmResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 独立模式的 APM 服务。
 * <p>
 * 用 utils 层的 {@link ServerBuilder}（{@code Server} SPI，默认 {@code jdk} 实现）单起一个
 * HTTP 服务，和嵌入模式共用 {@link ApmDispatcher}，因此两种模式的接口、参数、返回值
 * 完全一致 —— 换模式不影响前端。
 * </p>
 * <p>典型用途：一个独立的「可观测网关」进程，或宿主不是 Spring Web 的场景。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class ApmStandaloneServer implements AutoCloseable {

    /**
     * JSON 内容类型
     */
    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";

    /**
     * 通配后缀
     */
    private static final String ANY_PATH = "/**";

    private final ApmProperties properties;
    private final ApmDispatcher dispatcher;
    private final ObjectMapper mapper;

    private volatile Server server;

    /**
     * 构造独立服务
     *
     * @param properties 配置
     * @param dispatcher 统一路由器
     * @param mapper     JSON 序列化器
     */
    public ApmStandaloneServer(ApmProperties properties, ApmDispatcher dispatcher, ObjectMapper mapper) {
        this.properties = properties;
        this.dispatcher = dispatcher;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    /**
     * 启动服务（幂等）
     */
    public synchronized void start() {
        if (server != null) {
            return;
        }
        String base = basePath();
        Server instance = ServerBuilder.create()
                .type(properties.getServerType())
                .host(properties.getHost())
                .port(properties.getPort())
                .mapping(base + ANY_PATH, HttpMethod.GET, this::handle)
                .mapping(base + ANY_PATH, HttpMethod.POST, this::handle)
                .build();
        instance.start();
        server = instance;
        log.info("[APM] 独立服务已启动: http://{}:{}{}", properties.getHost(), properties.getPort(), base);
    }

    /**
     * 停止服务（幂等）
     */
    public synchronized void stop() {
        Server instance = server;
        if (instance == null) {
            return;
        }
        try {
            instance.stop();
        } catch (Exception e) {
            log.debug("[APM] 停止独立服务失败: {}", e.getMessage());
        } finally {
            server = null;
        }
    }

    /**
     * 是否已启动
     *
     * @return 是否已启动
     */
    public boolean isRunning() {
        return server != null;
    }

    /**
     * 服务访问地址（供服务信息面板展示）
     *
     * @return 地址
     */
    public String url() {
        return "http://" + properties.getHost() + ":" + properties.getPort() + basePath();
    }

    /**
     * 统一前缀规范化
     *
     * @return 前缀
     */
    private String basePath() {
        String base = properties.getPath() == null ? "" : properties.getPath().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.startsWith("/")) {
            base = "/" + base;
        }
        return base;
    }

    /**
     * 统一处理
     *
     * @param request  请求
     * @param response 响应
     * @throws Exception 处理失败
     */
    private void handle(ServerRequest request, ServerResponse response) throws Exception {
        ApmResult result;
        try {
            ApmRequest apmRequest = new ApmRequest(
                    request.getMethod() == null ? "GET" : request.getMethod().name(),
                    request.getPath(),
                    params(request));
            result = dispatcher.dispatch(apmRequest);
        } catch (Exception e) {
            log.debug("[APM] 独立服务处理请求失败: {}", e.getMessage());
            result = ApmResult.fail("APM 请求处理失败: " + e.getMessage());
        }
        Object body = result.ok()
                ? ReturnResult.ok(result.data())
                : ReturnResult.error(result.message());
        String json = mapper.writeValueAsString(body);
        response.setStatus(200);
        response.setContentType(JSON_CONTENT_TYPE);
        response.setBody(json.getBytes(StandardCharsets.UTF_8));
        response.end();
    }

    /**
     * 取查询参数
     *
     * @param request 请求
     * @return 参数
     */
    private Map<String, String> params(ServerRequest request) {
        Map<String, String> params = request.getParams();
        return params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
    }

    @Override
    public void close() {
        stop();
    }
}
