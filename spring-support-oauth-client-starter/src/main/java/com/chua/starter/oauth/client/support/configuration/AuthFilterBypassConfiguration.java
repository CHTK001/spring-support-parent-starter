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
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;

import java.io.IOException;

/**
 * 开发与测试环境认证过滤器降级配置。
 *
 * <p>仅当 Spring Profile 为 {@code dev} 或 {@code test}，且显式设置
 * {@code plugin.oauth.enable=false} 时提供无远程认证的兼容过滤器。
 * 其他环境不会创建该 Bean，应用按缺少认证依赖失败关闭。</p>
 *
 * @author opencode
 * @since 4.0.0.42
 */
@Configuration
@Profile({"dev", "test"})
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        prefix = "plugin.oauth", name = "enable", havingValue = "false", matchIfMissing = false)
public class AuthFilterBypassConfiguration {

    /**
     * 创建开发测试环境认证过滤器。
     *
     * @return 认证过滤器实例
     */
    @Bean
    @ConditionalOnMissingBean(AuthFilter.class)
    public AuthFilter authFilterBypass() {
        return new AuthFilter(null, null) {
            /**
             * 执行请求过滤器链。
             *
             * @param request Servlet 请求
             * @param response Servlet 响应
             * @param chain 过滤器链
             * @throws IOException 请求处理失败
             * @throws ServletException 过滤器链处理失败
             */
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                chain.doFilter(request, response);
            }
        };
    }

    /**
     * 注册开发测试环境认证过滤器。
     *
     * @return 过滤器注册对象
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
