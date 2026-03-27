package com.chua.starter.strategy.config;

import com.chua.starter.strategy.filter.StrategyAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Strategy 轻控制台嵌入式认证配置
 * <p>
 * 只保护 `/v2/strategy/**` 下的管理接口，静态页面本身仍允许直接访问，
 * 页面会在拿到 401 后自行跳转到登录页。
 * </p>
 *
 * @author System
 * @since 2026/03/26
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.strategy.web-auth", name = "mode", havingValue = "embedded", matchIfMissing = true)
public class StrategyEmbeddedAuthConfig {

    @Bean
    public FilterRegistrationBean<StrategyAuthenticationFilter> strategyAuthFilter() {
        FilterRegistrationBean<StrategyAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new StrategyAuthenticationFilter());
        registration.addUrlPatterns("/v2/strategy/*");
        registration.setOrder(1);
        return registration;
    }
}
