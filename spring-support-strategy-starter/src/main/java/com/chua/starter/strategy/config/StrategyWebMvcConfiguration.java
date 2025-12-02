package com.chua.starter.strategy.config;

import com.chua.starter.strategy.interceptor.SysDebounceConfigurationInterceptor;
import com.chua.starter.strategy.interceptor.SysLimitConfigurationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 策略模块 WebMvc 配置
 * 
 * 注册限流和防抖拦截器
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Configuration
@RequiredArgsConstructor
public class StrategyWebMvcConfiguration implements WebMvcConfigurer {

    private final SysLimitConfigurationInterceptor sysLimitConfigurationInterceptor;
    private final SysDebounceConfigurationInterceptor sysDebounceConfigurationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册限流拦截器（优先级高）
        registry.addInterceptor(sysLimitConfigurationInterceptor)
                .addPathPatterns("/**")
                .order(1);
        
        // 注册防抖拦截器
        registry.addInterceptor(sysDebounceConfigurationInterceptor)
                .addPathPatterns("/**")
                .order(2);
    }
}
