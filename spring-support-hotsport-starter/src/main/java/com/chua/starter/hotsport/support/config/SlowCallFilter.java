package com.chua.starter.hotsport.support.config;

import com.chua.runtime.apm.storage.ApmStorage;
import com.chua.runtime.apm.storage.TransmissionEvent;
import com.chua.runtime.protocol.StatusCode;
import com.chua.starter.hotsport.support.config.HotsportProperties;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 慢调用采集/注入过滤器 — 基于 Servlet 层的通用 HTTP 慢调用监听。
 *
 * <p>与 {@code /inject/slow} 的存储级模拟注入不同，该过滤器对所有 Servlet HTTP
 * 请求生效：</p>
 * <ul>
 *   <li><b>采集模式</b>：过滤链前后计时，耗时超过阈值时写入真实慢调用传输记录</li>
 *   <li><b>注入模式</b>：请求参数 {@code __slow} 或请求头 {@code X-Hotsport-Slow-Ms}
 *       指定目标耗时，在过滤链内 sleep 制造真实慢请求</li>
 * </ul>
 *
 * <p>默认排除观测台自身 API、静态资源与健康检查，避免自我采集。</p>
 *
 * @author CH
 * @since 4.1.0
 */
@Slf4j
public class SlowCallFilter implements Filter {

    /**
     * 注入模式请求参数名。
     */
    public static final String SLOW_PARAM = "__slow";

    /**
     * 注入模式请求头名。
     */
    public static final String SLOW_HEADER = "X-Hotsport-Slow-Ms";

    /**
     * 慢调用阈值（ms）。
     */
    private final long thresholdMs;

    /**
     * 最大注入耗时（ms），防误配导致请求长时间挂起。
     */
    private static final long MAX_SLOW_MS = 60000L;

    /**
     * 构造过滤器。
     *
     * @param thresholdMs 慢调用阈值
     */
    public SlowCallFilter(long thresholdMs) {
        this.thresholdMs = Math.max(1, thresholdMs);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        String uri = httpRequest.getRequestURI();
        if (uri == null || isExcluded(uri)) {
            chain.doFilter(request, response);
            return;
        }

        long forcedSlowMs = resolveForcedSlowMs(httpRequest);
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            if (forcedSlowMs > 0 && duration < forcedSlowMs) {
                // 注入模式：若实际链路过快，补齐到目标耗时（上限 60s）
                long remaining = Math.min(forcedSlowMs - duration, MAX_SLOW_MS);
                if (remaining > 0) {
                    try {
                        Thread.sleep(remaining);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                duration = System.currentTimeMillis() - start;
            }
            if (duration >= thresholdMs || forcedSlowMs > 0) {
                recordSlowCall(httpRequest, httpResponse, duration);
            }
        }
    }

    /**
     * 判断请求 URI 是否应被排除。
     *
     * @param uri 请求路径
     * @return 是否排除
     */
    private boolean isExcluded(String uri) {
        if (uri.startsWith("/api/apm/")) {
            return true;
        }
        if (uri.equals("/favicon.ico") || uri.startsWith("/assets/")
                || uri.startsWith("/actuator/") || uri.equals("/health")) {
            return true;
        }
        return false;
    }

    /**
     * 解析注入模式目标耗时。
     *
     * @param request HTTP 请求
     * @return 目标耗时 ms；未开启返回 0
     */
    private long resolveForcedSlowMs(HttpServletRequest request) {
        try {
            String param = request.getParameter(SLOW_PARAM);
            if (param != null && !param.isBlank()) {
                long v = Long.parseLong(param.trim());
                return Math.max(1, Math.min(v, MAX_SLOW_MS));
            }
            String header = request.getHeader(SLOW_HEADER);
            if (header != null && !header.isBlank()) {
                long v = Long.parseLong(header.trim());
                return Math.max(1, Math.min(v, MAX_SLOW_MS));
            }
        } catch (NumberFormatException ignored) {
            // 非法参数忽略，走采集模式
        }
        return 0L;
    }

    /**
     * 写入一条真实 HTTP 慢调用传输记录。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param duration 实际耗时
     */
    private void recordSlowCall(HttpServletRequest request,
                                HttpServletResponse response, long duration) {
        try {
            long now = System.currentTimeMillis();
            String protocol = request.getScheme() != null
                    ? request.getScheme().toUpperCase() : "HTTP";
            String method = request.getMethod() != null ? request.getMethod() : "GET";
            String path = request.getRequestURI() != null ? request.getRequestURI() : "/";
            String operation = method + " " + path;
            String source = request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
            String target = request.getServerName() + ":" + request.getServerPort();

            TransmissionEvent event = TransmissionEvent.builder()
                    .traceId("trace-slow-filter-" + Long.toHexString(now))
                    .spanId("span-" + now)
                    .sourceProtocol(protocol)
                    .sourceSoftware(protocol)
                    .sourceHost(source)
                    .sourcePort(0)
                    .targetProtocol(protocol)
                    .targetSoftware(protocol)
                    .targetHost(request.getServerName())
                    .targetPort(request.getServerPort())
                    .targetPath(path)
                    .protocol(protocol)
                    .software(protocol)
                    .operation(operation)
                    .status(response.getStatus() >= 400 ? StatusCode.ERROR : StatusCode.OK)
                    .statusCode(response.getStatus())
                    .startTime(now - duration)
                    .endTime(now)
                    .duration(duration)
                    .bytesOut(0L)
                    .bytesIn(0L)
                    .errorType(response.getStatus() >= 400 ? "HTTP" + response.getStatus() : null)
                    .errorMessage(response.getStatus() >= 400 ? "HTTP 状态 " + response.getStatus() : null)
                    .build();

            ApmStorage storage = HotsportProperties.storage();
            storage.appendTransmission(event);
            log.info("[SlowCallFilter] 慢调用: {} {}ms {}", operation, duration, source);
        } catch (Exception e) {
            log.debug("[SlowCallFilter] 写入慢调用失败: {}", e.getMessage());
        }
    }

    /**
     * 生成当前过滤配置快照（便于诊断）。
     *
     * @return 配置 Map
     */
    public Map<String, Object> info() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("thresholdMs", thresholdMs);
        m.put("excludes", "[/api/apm/**, /assets/**, /actuator/**, /health]");
        return m;
    }
}
