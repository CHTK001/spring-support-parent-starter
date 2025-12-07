package com.chua.starter.common.support.api.configuration;

import com.chua.starter.common.support.configuration.resolver.VersionArgumentResolver;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.util.Collections;

/**
 * API 过滤器配置类
 * <p>
 * 统一管理 API 相关的过滤器，包括：
 * <ul>
 *     <li>版本过滤器 - 在响应头中添加 API 版本信息</li>
 * </ul>
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/06/21
 */
@Slf4j
public class ApiFilterConfiguration {

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
        
        log.info(">>>>>>> 注册 API 版本过滤器");
        
        FilterRegistrationBean<ApiVersionFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ApiVersionFilter(versionArgumentResolver));
        registration.setUrlPatterns(Collections.singletonList("/*"));
        registration.setName("apiVersionFilter");
        registration.setOrder(Integer.MIN_VALUE);
        registration.setAsyncSupported(true);
        
        if (versionArgumentResolver != null) {
            log.debug("API 版本过滤器已配置版本解析器: {}", versionArgumentResolver.getClass().getSimpleName());
        } else {
            log.debug("API 版本过滤器未配置版本解析器，将不添加版本响应头");
        }
        
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
}
