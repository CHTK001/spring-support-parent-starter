package com.chua.starter.common.support.mdc;

import com.chua.common.support.constant.Constants;
import com.chua.common.support.mdc.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * mdc处理程序拦截器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/22
 */
public class MdcHandlerInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //如果有上层调用就用上层的ID
        String traceId = request.getHeader(Constants.TRACE_ID);
        if (traceId == null) {
            traceId = TraceContextHolder.get();
        }

        response.setHeader("log-allow-mdc", traceId);
        TraceContextHolder.put(traceId);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView)
            throws Exception {
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        //调用结束后删除
        TraceContextHolder.clear();
    }
}
