package com.chua.starter.spider.trace;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

/**
* 全链路追踪过滤器。
*
* <p>为每个进入的请求生成 traceId：优先循 {@code X-Trace-Id} 请求头，
* 缺失则生成 UUID；写入 MDC（Key 与 流 引擎 追踪标识 一致），并回写
* 到响应头 {@code X-Trace-Id}，请求结束后清理 MDC，避免线程污染。</p>
*
* @since 4.0.0.42
* @author CH
 */
public class SpiderTraceFilter implements Filter {

    /**
    * 追踪标识 请求头 / 响应头名称
    */
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
    * MDC 中 追踪标识 的 Key（与 流-启动 引擎使用的 Key 保持一致）
    */
    private static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    /** 执行过滤 */
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

 // 优先复用上游传入的 追踪标识，否则生成新的
        String traceId = httpRequest.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

 // 写入 MDC，运行期间所有日志携带该 追踪标识
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        httpResponse.setHeader(TRACE_ID_HEADER, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
 // 结束后清理，避免线程池复用导致 追踪标识 串扰
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}
