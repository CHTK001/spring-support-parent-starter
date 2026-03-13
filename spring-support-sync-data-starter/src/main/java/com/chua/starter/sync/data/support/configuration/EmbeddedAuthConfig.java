package com.chua.starter.sync.data.support.configuration;

import com.chua.starter.sync.data.support.filter.AuthenticationFilter;
import com.chua.starter.sync.data.support.properties.SyncDataProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 嵌入式认证配置
 *
 * @author System
 * @since 2026/03/09
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.sync.web-auth", name = "mode", havingValue = "embedded", matchIfMissing = true)
public class EmbeddedAuthConfig {

    private final SyncDataProperties properties;

    @Bean
    public FilterRegistrationBean<AuthenticationFilter> authFilter() {
        FilterRegistrationBean<AuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AuthenticationFilter());
        registration.addUrlPatterns("/v1/sync/*");
        registration.setOrder(1);
        return registration;
    }
}
