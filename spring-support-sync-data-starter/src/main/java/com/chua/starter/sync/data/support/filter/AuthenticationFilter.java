package com.chua.starter.sync.data.support.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/**
 * 认证过滤器
 *
 * @author System
 * @since 2026/03/09
 */
public class AuthenticationFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        
        String uri = req.getRequestURI();
        
        // 登录接口和静态资源放行
        if (uri.endsWith("/login") || uri.contains("/static/") || uri.contains("/auth/")) {
            chain.doFilter(request, response);
            return;
        }
        
        // 检查Session
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("authenticated") != null) {
            chain.doFilter(request, response);
            return;
        }
        
        // 未认证,返回401
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\"code\":401,\"message\":\"未登录\"}");
    }
}
