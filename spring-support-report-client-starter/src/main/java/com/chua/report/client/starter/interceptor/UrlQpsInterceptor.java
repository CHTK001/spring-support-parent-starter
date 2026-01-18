package com.chua.report.client.starter.interceptor;

import com.chua.report.client.starter.report.UrlQpsReporter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * URL QPS 统计拦截器
 * <p>
 * 拦截所有请求并记录统计数据
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025/01/17
 */
@Slf4j
public class UrlQpsInterceptor implements HandlerInterceptor {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UrlQpsInterceptor.class);

    private static final String ATTR_START_TIME = "urlQps_startTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 记录请求开始时间
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                 Object handler, Exception ex) {
        try {
            // 计算请求耗时
            Long startTime = (Long) request.getAttribute(ATTR_START_TIME);
            if (startTime == null) {
                return;
            }

            long duration = System.currentTimeMillis() - startTime;
            String url = getRequestUrl(request);
            String method = request.getMethod();
            int statusCode = response.getStatus();
            boolean success = statusCode >= 200 && statusCode < 400;

            // 记录统计
            UrlQpsReporter.getInstance().recordRequest(url, method, duration, success, statusCode);

            log.trace("[UrlQpsInterceptor] 记录请求: {} {} -> {} ({}ms)", 
                    method, url, statusCode, duration);

        } catch (Exception e) {
            log.warn("[UrlQpsInterceptor] 记录请求统计失败: {}", e.getMessage());
        }
    }

    /**
     * 获取请求 URL（去除查询参数，处理路径变量）
     *
     * @param request HTTP 请求
     * @return 处理后的 URL
     */
    private String getRequestUrl(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();

        // 移除 context path
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }

        // 简化路径变量（将数字替换为 {id}）
        uri = uri.replaceAll("/\\d+", "/{id}");
        // 处理 UUID 格式
        uri = uri.replaceAll("/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}", "/{uuid}");

        return uri;
    }
}
