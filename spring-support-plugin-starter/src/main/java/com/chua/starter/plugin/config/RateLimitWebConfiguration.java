package com.chua.starter.plugin.config;

import com.chua.starter.plugin.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 限流Web配置
 * 
 * @author CH
 * @since 2025/1/16
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitWebConfiguration implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/error",
                    "/actuator/**",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/webjars/**",
                    "/favicon.ico",
                    "/static/**",
                    "/public/**"
                );
    }
}
