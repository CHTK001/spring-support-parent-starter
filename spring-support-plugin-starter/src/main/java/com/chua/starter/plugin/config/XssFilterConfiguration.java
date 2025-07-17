package com.chua.starter.plugin.config;

import com.chua.starter.plugin.filter.XssFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * XSS过滤器配置
 * 
 * @author CH
 * @since 2025/1/16
 */
@Configuration
@RequiredArgsConstructor
public class XssFilterConfiguration {

    private final XssFilter xssFilter;

    /**
     * 注册XSS过滤器
     * 
     * @return 过滤器注册Bean
     */
    @Bean
    public FilterRegistrationBean<XssFilter> xssFilterRegistration() {
        FilterRegistrationBean<XssFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(xssFilter);
        registration.addUrlPatterns("/*"); // 拦截所有请求
        registration.setName("xssFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1); // 设置较高优先级
        return registration;
    }
}
