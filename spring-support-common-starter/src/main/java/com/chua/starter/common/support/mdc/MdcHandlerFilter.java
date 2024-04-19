package com.chua.starter.common.support.mdc;

import com.chua.common.support.constant.Constants;
import com.chua.common.support.mdc.TraceContextHolder;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

/**
 * mdc处理程序拦截器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/22
 */
public class MdcHandlerFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            String traceId = null;
            if(!(request instanceof HttpServletRequest)) {
                //如果有上层调用就用上层的ID
                traceId = ((HttpServletRequest)request).getHeader(Constants.TRACE_ID);
            }

            if (null == traceId) {
                traceId = TraceContextHolder.get();
            }

            if(null == traceId) {
                traceId = UUID.randomUUID().toString();
            }

            if(response instanceof HttpServletResponse) {
                ((HttpServletResponse) response).setHeader("log-allow-mdc", traceId);
            }
            TraceContextHolder.put(traceId);
            chain.doFilter(request, response);
        } finally {
            TraceContextHolder.clear();
        }
    }
}
