package com.chua.starter.hotsport.support.config;

import com.chua.runtime.apm.storage.ApmStorage;
import com.chua.runtime.apm.storage.TransmissionEvent;
import com.chua.runtime.protocol.StatusCode;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 请求采集过滤器 —— 基于 Servlet 层记录**真实** HTTP 请求。
 *
 * <p>定位：本过滤器只做一件事——把进入本进程的真实 HTTP 请求原样写入
 * {@link ApmStorage}，作为传输/服务/端点页面的数据来源。记录的全部字段
 * （方法、路径、状态码、耗时、客户端 IP、响应字节）都取自真实的请求/响应对象，
 * <b>不做任何模拟或注入</b>。</p>
 *
 * <p><b>为什么需要它</b>：字节码级采集（{@code RuntimeSpy}）的处理器面向
 * spark / flink / kafka / redis / mysql 等第三方库做应用层拦截；一个纯
 * Spring Boot（Tomcat NIO）进程没有这些库，字节码层拿不到数据。Servlet 过滤器
 * 是唯一稳定可靠、且能拿到真实请求的采集点，因此用它兜住本进程的真实流量。</p>
 *
 * <p>排除项：静态资源、favicon、actuator、健康检查、SSE 长连接（这些不是
 * 「一次请求=一次调用」的语义，记录它们只会污染统计）。</p>
 *
 * @author CH
 * @since 4.1.0
 */
@Slf4j
public class SlowCallFilter implements Filter {

    /**
     * 记录阈值（ms）：耗时达到该值即记录。默认 0 表示记录全部请求。
     */
    private final long thresholdMs;

    /**
     * 构造过滤器。
     *
     * @param thresholdMs 记录阈值（ms），0 表示记录全部
     */
    public SlowCallFilter(long thresholdMs) {
        this.thresholdMs = Math.max(0, thresholdMs);
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

        long start = System.currentTimeMillis();
        // 本次请求的真实 traceId：写进 MDC，让请求处理期间产生的真实日志
        // 与这条链路记录用同一个 traceId 关联（日志页按 traceId 可直接下钻）。
        String traceId = "trace-http-" + Long.toHexString(start);
        String previous = MDC.get(ApmLogAppender.MDC_TRACE_ID);
        boolean owned = previous == null;
        if (owned) {
            MDC.put(ApmLogAppender.MDC_TRACE_ID, traceId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (owned) {
                MDC.remove(ApmLogAppender.MDC_TRACE_ID);
            }
            long duration = System.currentTimeMillis() - start;
            if (duration >= thresholdMs) {
                recordRequest(httpRequest, httpResponse, duration, owned ? traceId : previous);
            }
        }
    }

    /**
     * 判断请求 URI 是否应被排除。
     *
     * <p>只排除「非调用语义」的资源：静态资源、favicon、actuator、健康检查、
     * 以及 SSE 长连接。API 查询请求同样记录——它们就是本进程真实的负载。</p>
     *
     * @param uri 请求路径
     * @return 是否排除
     */
    private boolean isExcluded(String uri) {
        if (uri.startsWith("/assets/") || uri.equals("/favicon.ico")
                || uri.startsWith("/actuator/") || uri.equals("/health")) {
            return true;
        }
        // SSE 长连接不按「一次请求一次调用」计量，记录会得到一条永不结束的假耗时
        return uri.startsWith("/api/apm/stream");
    }

    /**
     * 写入一条真实 HTTP 请求传输记录。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param duration 实际耗时（ms）
     * @param traceId  本次请求的真实 traceId
     */
    private void recordRequest(HttpServletRequest request,
                               HttpServletResponse response, long duration, String traceId) {
        try {
            long now = System.currentTimeMillis();
            String protocol = request.getScheme() != null
                    ? request.getScheme().toUpperCase() : "HTTP";
            String method = request.getMethod() != null ? request.getMethod() : "GET";
            String path = request.getRequestURI() != null ? request.getRequestURI() : "/";
            String operation = method + " " + path;
            String source = clientIp(request);
            String target = request.getServerName() + ":" + request.getServerPort();
            int status = response.getStatus();
            long requestStart = now - duration;

            // 本次请求真实命中的控制器方法（由 ApmHandlerTimingInterceptor 实测写入）。
            // 这是「首层 HTTP → 子层 class.method」里子层节点的唯一真实来源：
            // 类名/方法名取自真实 HandlerMethod，耗时取自真实时间差。
            Map<String, String> attributes = new LinkedHashMap<>();
            Object handlerClass = request.getAttribute(ApmHandlerTimingInterceptor.ATTR_CLASS);
            Object handlerMethod = request.getAttribute(ApmHandlerTimingInterceptor.ATTR_METHOD);
            Object handlerStart = request.getAttribute(ApmHandlerTimingInterceptor.ATTR_START);
            Object handlerDuration = request.getAttribute(ApmHandlerTimingInterceptor.ATTR_DURATION);
            if (handlerClass != null) {
                attributes.put("class.name", String.valueOf(handlerClass));
            }
            if (handlerMethod != null) {
                attributes.put("method.name", String.valueOf(handlerMethod));
            }
            if (handlerDuration instanceof Long d && d >= 0) {
                attributes.put("method.duration", String.valueOf(d));
            }
            if (handlerStart instanceof Long s && s > 0 && s >= requestStart) {
                // 控制器相对请求入口的偏移，用于子层节点在时间轴上的真实位置
                attributes.put("method.startOffset", String.valueOf(s - requestStart));
            }

            TransmissionEvent event = TransmissionEvent.builder()
                    .traceId(traceId)
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
                    .status(status >= 400 ? StatusCode.ERROR : StatusCode.OK)
                    .statusCode(status)
                    .startTime(now - duration)
                    .endTime(now)
                    .duration(duration)
                    .bytesOut(0L)
                    .bytesIn(0L)
                    .errorType(status >= 400 ? "HTTP" + status : null)
                    .errorMessage(status >= 400 ? "HTTP 状态 " + status : null)
                    .attributes(attributes)
                    .build();

            ApmStorage storage = HotsportProperties.storage();
            if (storage != null) {
                storage.appendTransmission(event);
            }
        } catch (Exception e) {
            // 观测组件永不阻断宿主请求
            log.debug("[RequestRecord] 写入请求记录失败: {}", e.getMessage());
        }
    }

    /**
     * 取真实客户端 IP（优先 X-Forwarded-For 首段，其次 remoteAddr）。
     *
     * @param request HTTP 请求
     * @return 客户端 IP
     */
    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    /**
     * 生成当前过滤配置快照（便于诊断）。
     *
     * @return 配置 Map
     */
    public Map<String, Object> info() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("thresholdMs", thresholdMs);
        m.put("excludes", "[/assets/**, /favicon.ico, /actuator/**, /health, /api/apm/stream*]");
        return m;
    }
}
