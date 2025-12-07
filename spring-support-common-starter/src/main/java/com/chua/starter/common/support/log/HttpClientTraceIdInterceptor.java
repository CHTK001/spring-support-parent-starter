package com.chua.starter.common.support.log;

import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;
import org.slf4j.MDC;

/**
 * HttpClient TraceId拦截器
 * <p>
 * 在HttpClient请求中添加traceId头。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/01
 */
public class HttpClientTraceIdInterceptor implements HttpRequestInterceptor {

    private static final String TRACE_ID = "traceId";
    private static final String X_TRACE_ID = "X-Trace-Id";

    @Override
    public void process(HttpRequest request, HttpContext context) {
        String traceId = MDC.get(TRACE_ID);
        if (traceId != null && !traceId.isEmpty()) {
            request.addHeader(X_TRACE_ID, traceId);
        }
    }
}
