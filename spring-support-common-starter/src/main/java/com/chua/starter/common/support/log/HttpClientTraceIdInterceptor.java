package com.chua.starter.common.support.log;

import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.MDC;

/**
 * http客户端 追踪标识拦截器（兼容 http客户端 5.x）。
 * @author CH
 * @since 4.0.0
 */
public class HttpClientTraceIdInterceptor implements HttpRequestInterceptor {

    /** 追踪_标识 */
    private static final String TRACE_ID = "traceId";
    /** X_追踪_标识 */
    private static final String X_TRACE_ID = "X-Trace-Id";

    @Override
    /** 处理 */
    public void process(HttpRequest request, org.apache.hc.core5.http.EntityDetails entity, HttpContext context) {
        String traceId = MDC.get(TRACE_ID);
        if (traceId != null && !traceId.isEmpty()) {
            request.setHeader(X_TRACE_ID, traceId);
        }
    }
}

