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
            
            // 设置用户ID
            String userId = RequestUtils.getUserId();
            if (userId != null && !userId.isEmpty()) {
                MDC.put(MdcConstant.USER_ID, userId);
            }
            
            // 设置请求IP
            String requestIp = RequestUtils.getIpAddress(httpRequest);
            if (requestIp != null && !requestIp.isEmpty()) {
                MDC.put(MdcConstant.REQUEST_IP, requestIp);
            }
            
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
