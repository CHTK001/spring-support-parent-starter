package com.chua.starter.common.support.log;

import com.chua.starter.common.support.constant.MdcConstant;
import com.chua.starter.common.support.utils.RequestUtils;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

/**
 * MDC处理过滤器
 * <p>
 * 在请求进入时设置traceId，请求结束时清理MDC上下文。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/01
 */
public class MdcHandlerFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            
            // 设置traceId
            String traceId = httpRequest.getHeader(MdcConstant.X_TRACE_ID);
            if (traceId == null || traceId.isEmpty()) {
                traceId = UUID.randomUUID().toString().replace("-", "");
            }
            MDC.put(MdcConstant.TRACE_ID, traceId);
            
            // 设置用户ID，如果没有则设置为N/A
            String userId = RequestUtils.getUserId();
            MDC.put(MdcConstant.USER_ID, (userId != null && !userId.isEmpty()) ? userId : "N/A");
            
            // 设置请求IP，如果没有则设置为N/A
            String requestIp = RequestUtils.getIpAddress(httpRequest);
            MDC.put(MdcConstant.REQUEST_IP, (requestIp != null && !requestIp.isEmpty()) ? requestIp : "N/A");
            
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
