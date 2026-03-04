package com.chua.starter.common.support.api.configuration;

import com.chua.starter.common.support.configuration.resolver.VersionArgumentResolver;
import com.chua.starter.common.support.utils.NonceUtils;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import lombok.extern.slf4j.Slf4j;

/**
 * API 过滤器配置类
 * <p>
 * 统一管理 API 相关的过滤器，包括：
 * <ul>
 *     <li>版本过滤器 - 在响应头中添加 API 版本信息</li>
 *     <li>XHR Nonce 防重放过滤器 - 对携带 x-nonce 的请求做时间戳、重放、签名校验</li>
 * </ul>
 * </p>
 *
 * @author CH
 * @since 2024/06/21
 */
@Slf4j
public class ApiFilterConfiguration {

    private static final int SC_NONCE_REJECT = 400;
        /**
     * 响应头中的版本字段名
     */
    private static final String X_HEADER_VERSION = "x-response-version";

    /**
     * API 版本过滤器
     * <p>
     * 在每个响应头中添加当前 API 版本信息，便于客户端识别服务版本。
     * </p>
     *
     * @param versionArgumentResolver 版本参数解析器（可选）
     * @return FilterRegistrationBean 过滤器注册 Bean
     */
    @Bean("apiVersionFilterRegistration")
    @ConditionalOnMissingBean(name = "apiVersionFilterRegistration")
    public FilterRegistrationBean<ApiVersionFilter> apiVersionFilterRegistration(
            @Autowired(required = false) VersionArgumentResolver versionArgumentResolver) {
        FilterRegistrationBean<ApiVersionFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ApiVersionFilter(versionArgumentResolver));
        registration.setUrlPatterns(Collections.singletonList("/*"));
        registration.setName("apiVersionFilter");
        registration.setOrder(Integer.MIN_VALUE);
        registration.setAsyncSupported(true);
        return registration;
    }

    /**
     * XHR Nonce 防重放过滤器
     * <p>
     * 仅对携带 x-nonce、x-sign、x-timestamp、x-req-fingerprint 的请求做校验，
     * 校验失败返回 403。
     * </p>
     */
    @Bean("nonceXhrFilterRegistration")
    @ConditionalOnMissingBean(name = "nonceXhrFilterRegistration")
    public FilterRegistrationBean<NonceXhrFilter> nonceXhrFilterRegistration() {
        var registration = new FilterRegistrationBean<NonceXhrFilter>();
        registration.setFilter(new NonceXhrFilter());
        registration.setUrlPatterns(Collections.singletonList("/*"));
        registration.setName("nonceXhrFilter");
        registration.setOrder(Integer.MIN_VALUE + 1);
        registration.setAsyncSupported(true);
        return registration;
    }

    /**
     * API 版本过滤器
     * <p>
     * 在响应头中添加版本信息。
     * </p>
     */
    public static class ApiVersionFilter implements Filter {

        private final VersionArgumentResolver versionArgumentResolver;

        public ApiVersionFilter(VersionArgumentResolver versionArgumentResolver) {
            this.versionArgumentResolver = versionArgumentResolver;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            if (versionArgumentResolver != null && response instanceof HttpServletResponse httpServletResponse) {
                String version = versionArgumentResolver.version();
                if (version != null && !version.isEmpty()) {
                    httpServletResponse.setHeader(X_HEADER_VERSION, version);
                }
            }
            chain.doFilter(request, response);
        }
    }

    /**
     * XHR Nonce 防重放过滤器
     * 对携带完整签名头的请求做 x-nonce 重放校验及签名校验
     */
    public static class NonceXhrFilter implements Filter {

        private static final String REJECT_MSG = "{\"code\":400,\"msg\":\"请求校验失败，请勿重放请求\"}";

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            if (!(request instanceof HttpServletRequest req) || !(response instanceof HttpServletResponse resp)) {
                chain.doFilter(request, response);
                return;
            }
            if (!NonceUtils.hasSignHeaders(req)) {
                chain.doFilter(request, response);
                return;
            }
            if (!NonceUtils.validateXhrRequest(req)) {
                log.warn("[Nonce][XHR] 请求校验失败 uri={}", req.getRequestURI());
                resp.setStatus(SC_NONCE_REJECT);
                resp.setContentType("application/json;charset=UTF-8");
                resp.getOutputStream().write(REJECT_MSG.getBytes(StandardCharsets.UTF_8));
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
