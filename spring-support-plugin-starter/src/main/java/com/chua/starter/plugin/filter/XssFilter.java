package com.chua.starter.plugin.filter;

import com.chua.starter.plugin.service.XssProtectionService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * XSS防护过滤器
 * 
 * @author CH
 * @since 2025/1/16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XssFilter implements Filter {

    private final XssProtectionService xssProtectionService;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("XSS Filter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 检查XSS防护是否启用
        if (!xssProtectionService.isXssProtectionEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        // 检查是否需要对此URL进行XSS防护
        if (!xssProtectionService.shouldProtectUrl(httpRequest.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        try {
            // 创建XSS防护包装器
            XssHttpServletRequestWrapper xssRequest = new XssHttpServletRequestWrapper(
                httpRequest, xssProtectionService);
            
            // 继续过滤链
            chain.doFilter(xssRequest, response);
            
        } catch (XssAttackException e) {
            // 处理XSS攻击
            handleXssAttack(httpRequest, httpResponse, e);
        } catch (Exception e) {
            log.error("XSS filter error", e);
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
        log.info("XSS Filter destroyed");
    }

    /**
     * 处理XSS攻击
     * 
     * @param request 请求
     * @param response 响应
     * @param e XSS攻击异常
     */
    private void handleXssAttack(HttpServletRequest request, HttpServletResponse response, 
                                XssAttackException e) throws IOException {
        
        // 根据配置的处理策略响应
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json;charset=UTF-8");
        
        String errorMessage = String.format(
            "{\"error\":\"XSS attack detected\",\"message\":\"%s\",\"timestamp\":%d}",
            e.getMessage(), System.currentTimeMillis());
        
        response.getWriter().write(errorMessage);
        response.getWriter().flush();
    }

    /**
     * XSS攻击异常
     */
    public static class XssAttackException extends RuntimeException {
        private final String parameterName;
        private final String originalContent;
        private final String attackType;

        public XssAttackException(String message, String parameterName, String originalContent, String attackType) {
            super(message);
            this.parameterName = parameterName;
            this.originalContent = originalContent;
            this.attackType = attackType;
        }

        public String getParameterName() {
            return parameterName;
        }

        public String getOriginalContent() {
            return originalContent;
        }

        public String getAttackType() {
            return attackType;
        }
    }
}
