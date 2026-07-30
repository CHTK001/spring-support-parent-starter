package com.chua.starter.common.support.log;

import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.MDC;

/**
 * HttpClient TraceId拦截器（兼容 HttpClient 5.x）。
 */
public class HttpClientTraceIdInterceptor implements HttpRequestInterceptor {

    private static final String TRACE_ID = "traceId";
    private static final String X_TRACE_ID = "X-Trace-Id";

    @Override
    public void process(HttpRequest request, org.apache.hc.core5.http.EntityDetails entity, HttpContext context) {
        String traceId = MDC.get(TRACE_ID);
        if (traceId != null && !traceId.isEmpty()) {
            request.setHeader(X_TRACE_ID, traceId);
        }
    }
}
