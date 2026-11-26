package com.chua.starter.common.support.log;

import com.chua.starter.logger.constant.MdcConstant;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.MDC;

import static com.chua.starter.logger.constant.MdcConstant.TRACE_ID;
import static com.chua.starter.logger.constant.MdcConstant.X_TRACE_ID;

/**
 * http客户端 追踪标识拦截器（兼容 http客户端 5.x）。
 * @author CH
 * @since 4.0.0
 */
public class HttpClientTraceIdInterceptor implements HttpRequestInterceptor {

    @Override
    /**
     * 处理
     * @param context 参数
     * @param entity 参数
     * @param request 参数
     * @return 返回值
     */
    public void process(HttpRequest request, org.apache.hc.core5.http.EntityDetails entity, HttpContext context) {
        String traceId = MDC.get(TRACE_ID);
        if (traceId != null && !traceId.isEmpty()) {
            request.setHeader(X_TRACE_ID, traceId);
        }
    }
}
