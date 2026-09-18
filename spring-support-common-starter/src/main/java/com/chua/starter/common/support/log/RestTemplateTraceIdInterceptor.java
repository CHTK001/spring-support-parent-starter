package com.chua.starter.common.support.log;

import com.chua.starter.common.support.constant.MdcConstant;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;

import java.io.IOException;

import static com.chua.starter.common.support.constant.MdcConstant.X_TRACE_ID;

/**
* RestTemplate 追踪标识拦截器
* <p>
* 在RestTemplate请求中添加追踪标识头。
* </p>
*
* @版本 1.0.0
* @since 2024/01/01
* @author CH
 */
public class RestTemplateTraceIdInterceptor implements ClientHttpRequestInterceptor {


    @Override
    @NonNull
    /**
    * Intercept
    * @param request 请求
    * @param body 主体
    * @param execution execution
    */
    public ClientHttpResponse intercept(@NonNull HttpRequest request, @NonNull byte[] body,
                                         @NonNull ClientHttpRequestExecution execution) throws IOException {
        String traceId = MDC.get(MdcConstant.TRACE_ID);
        if (traceId != null && !traceId.isEmpty()) {
            request.getHeaders().add(X_TRACE_ID, traceId);
        }
        return execution.execute(request, body);
    }
}
