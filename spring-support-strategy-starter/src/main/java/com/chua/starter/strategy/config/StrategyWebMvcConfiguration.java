package com.chua.starter.strategy.config;

import com.chua.starter.strategy.interceptor.CircuitBreakerInterceptor;
import com.chua.starter.strategy.interceptor.IpAccessControlInterceptor;
import com.chua.starter.strategy.interceptor.SysDebounceConfigurationInterceptor;
import com.chua.starter.strategy.interceptor.SysLimitConfigurationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 策略模块 WebMvc 配置
 * <p>
 * 注册限流、防抖、熔断、IP访问控制等拦截器
 * </p>
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

    @Nullable
    @Autowired(required = false)
    private CircuitBreakerInterceptor circuitBreakerInterceptor;

    @Nullable
    @Autowired(required = false)
    private IpAccessControlInterceptor ipAccessControlInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. IP访问控制拦截器（最高优先级）
        if (ipAccessControlInterceptor != null) {
            registry.addInterceptor(ipAccessControlInterceptor)
                    .addPathPatterns("/**")
                    .order(0);
        }

        // 2. 限流拦截器
        registry.addInterceptor(sysLimitConfigurationInterceptor)
                .addPathPatterns("/**")
                .order(1);

        // 3. 防抖拦截器
        registry.addInterceptor(sysDebounceConfigurationInterceptor)
                .addPathPatterns("/**")
                .order(2);

        // 4. 熔断拦截器
        if (circuitBreakerInterceptor != null) {
            registry.addInterceptor(circuitBreakerInterceptor)
                    .addPathPatterns("/**")
                    .order(3);
        }
    }
}
