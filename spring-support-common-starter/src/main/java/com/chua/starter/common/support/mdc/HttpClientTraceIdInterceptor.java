package com.chua.starter.common.support.mdc;

import com.chua.common.support.constant.Constants;
import com.chua.common.support.mdc.TraceContextHolder;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

/**
 * http客户端跟踪id拦截器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/22
 */
public class HttpClientTraceIdInterceptor implements HttpRequestInterceptor {
    @Override
    public void process(HttpRequest httpRequest, HttpContext httpContext) throws HttpException, IOException {
        String traceId = TraceContextHolder.get();
        //当前线程调用中有traceId，则将该traceId进行透传
        if (traceId != null) {
            //添加请求体
            httpRequest.addHeader(Constants.TRACE_ID, traceId);
        }
    }
}