package com.chua.starter.test.config;

import com.chua.starter.oauth.client.support.interceptor.PermissionInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 测试应用权限拦截链配置。
 *
 * <p>因 {@code AuthClientConfiguration} 被排除（其 {@code AuthFilter} 依赖真实 OAuth 服务），
 * 此处以与生产一致的方式注册真实的 {@link PermissionInterceptor}（Advisor Bean，
 * Spring AOP 自动代理 @Permission 标注的 Bean 方法）。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
@Configuration
public class PermissionInterceptorConfiguration {

    @Bean
    public PermissionInterceptor permissionInterceptor() {
        // Spring 容器自动调用 InitializingBean#afterPropertiesSet 装配拦截 Advice
        return new PermissionInterceptor();
    }
}
