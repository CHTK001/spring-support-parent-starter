package com.chua.starter.oauth.client.support.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import com.chua.starter.oauth.client.support.filter.AuthFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.io.IOException;

/**
 * 鉴权过滤器的"全放行"fallback 配置。
 *
 * <p>当 {@code plugin.oauth.enable=false} 时，{@link AuthClientConfiguration} 整体不激活，
 * 但 Spring Web 仍会尝试注入 {@link AuthFilter}（部分模块通过 {@code @Autowired}
 * 引用）。该配置提供一个直接放行的占位 {@link AuthFilter}，避免启动失败。</p>
 *
 * <p>仅在 {@link AuthFilter} bean 缺失且 {@code plugin.oauth.enable=false} 时激活。</p>
 *
 * @author opencode
 * @since 4.0.0.42
 */
@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        prefix = "plugin.oauth", name = "enable", havingValue = "false", matchIfMissing = false)
public class AuthFilterBypassConfiguration {

    /**
     * 提供一个不进行任何校验的 {@link AuthFilter} 占位实现，
     * 绕过真实的 OAuth 远程调用，让所有请求直接通过。
     */
    @Bean
    @ConditionalOnMissingBean(AuthFilter.class)
    public AuthFilter authFilterBypass() {
        return new AuthFilter(null, null) {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                chain.doFilter(request, response);
            }
        };
    }

    /**
     * 同时注册 FilterRegistrationBean 让 AuthFilter 拦截链生效（但内部 doFilter 直接放行）。
     */
    @Bean
    @ConditionalOnMissingBean(name = "authFilterFilterRegistrationBean")
    public FilterRegistrationBean<AuthFilter> authFilterBypassRegistration() {
        FilterRegistrationBean<AuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(authFilterBypass());
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        registration.setName("authFilterBypass");
        registration.setAsyncSupported(true);
        return registration;
    }
}
