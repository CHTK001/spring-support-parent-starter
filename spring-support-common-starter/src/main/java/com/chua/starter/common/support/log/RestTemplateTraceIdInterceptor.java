package com.chua.starter.common.support.log;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;

import java.io.IOException;

/**
 * RestTemplate TraceId拦截器
 * <p>
 * 在RestTemplate请求中添加traceId头。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/01
 */
public class RestTemplateTraceIdInterceptor implements ClientHttpRequestInterceptor {

    private static final String TRACE_ID = "traceId";
    private static final String X_TRACE_ID = "X-Trace-Id";

    @Override
    @NonNull
    public ClientHttpResponse intercept(@NonNull HttpRequest request, @NonNull byte[] body,
                                         @NonNull ClientHttpRequestExecution execution) throws IOException {
        String traceId = MDC.get(TRACE_ID);
        if (traceId != null && !traceId.isEmpty()) {
            request.getHeaders().add(X_TRACE_ID, traceId);
        }
        return execution.execute(request, body);
    }
}
