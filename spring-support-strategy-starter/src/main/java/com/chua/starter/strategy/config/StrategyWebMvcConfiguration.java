package com.chua.starter.strategy.config;

import com.chua.starter.strategy.interceptor.CircuitBreakerInterceptor;
import com.chua.starter.strategy.interceptor.IpAccessControlInterceptor;
import com.chua.starter.strategy.config.StrategyProperties;
import com.chua.starter.strategy.interceptor.PathTraversalInterceptor;
import com.chua.starter.strategy.interceptor.SysDebounceConfigurationInterceptor;
import com.chua.starter.strategy.interceptor.SysLimitConfigurationInterceptor;
import com.chua.starter.strategy.interceptor.XssProtectionInterceptor;
import com.chua.starter.strategy.interceptor.SqlInjectionProtectionInterceptor;
import com.chua.starter.strategy.interceptor.CsrfProtectionInterceptor;
import com.chua.starter.strategy.interceptor.RequestSizeLimitInterceptor;
import com.chua.starter.strategy.interceptor.HttpMethodRestrictionInterceptor;
import com.chua.starter.strategy.interceptor.RequestTimeoutInterceptor;
import com.chua.starter.strategy.interceptor.ParameterCountLimitInterceptor;
import com.chua.starter.strategy.interceptor.ContentSecurityPolicyInterceptor;
import com.chua.starter.strategy.interceptor.ClickjackingProtectionInterceptor;
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

    private final StrategyProperties strategyProperties;

    private final XssProtectionInterceptor xssProtectionInterceptor;

    private final SysLimitConfigurationInterceptor sysLimitConfigurationInterceptor;
    private final SysDebounceConfigurationInterceptor sysDebounceConfigurationInterceptor;

    @Nullable
    @Autowired(required = false)
    private CircuitBreakerInterceptor circuitBreakerInterceptor;

    @Nullable
    @Autowired(required = false)
    private IpAccessControlInterceptor ipAccessControlInterceptor;

    @Nullable
    @Autowired(required = false)
    private PathTraversalInterceptor pathTraversalInterceptor;

    @Nullable
    @Autowired(required = false)
    private SqlInjectionProtectionInterceptor sqlInjectionProtectionInterceptor;

    @Nullable
    @Autowired(required = false)
    private CsrfProtectionInterceptor csrfProtectionInterceptor;

    @Nullable
    @Autowired(required = false)
    private RequestSizeLimitInterceptor requestSizeLimitInterceptor;

    @Nullable
    @Autowired(required = false)
    private HttpMethodRestrictionInterceptor httpMethodRestrictionInterceptor;

    @Nullable
    @Autowired(required = false)
    private RequestTimeoutInterceptor requestTimeoutInterceptor;

    @Nullable
    @Autowired(required = false)
    private ParameterCountLimitInterceptor parameterCountLimitInterceptor;

    @Nullable
    @Autowired(required = false)
    private ContentSecurityPolicyInterceptor contentSecurityPolicyInterceptor;

    @Nullable
    @Autowired(required = false)
    private ClickjackingProtectionInterceptor clickjackingProtectionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 0. XSS 防护拦截器（最高优先级，按配置开关控制）
        if (strategyProperties.getXss().isEnabled()) {
            registry.addInterceptor(xssProtectionInterceptor)
                    .addPathPatterns("/**")
                    .order(0);
        }

        // 1. 路径穿透防护拦截器
        if (pathTraversalInterceptor != null && strategyProperties.getPathTraversal().isEnabled()) {
            registry.addInterceptor(pathTraversalInterceptor)
                    .addPathPatterns("/**")
                    .order(1);
        }

        // 2. IP访问控制拦截器
        if (ipAccessControlInterceptor != null) {
            registry.addInterceptor(ipAccessControlInterceptor)
                    .addPathPatterns("/**")
                    .order(2);
        }

        // 3. 限流拦截器
        registry.addInterceptor(sysLimitConfigurationInterceptor)
                .addPathPatterns("/**")
                .order(3);

        // 4. 防抖拦截器
        registry.addInterceptor(sysDebounceConfigurationInterceptor)
                .addPathPatterns("/**")
                .order(4);

        // 5. 熔断拦截器
        if (circuitBreakerInterceptor != null) {
            registry.addInterceptor(circuitBreakerInterceptor)
                    .addPathPatterns("/**")
                    .order(5);
        }

        // 6. SQL注入防护拦截器
        if (sqlInjectionProtectionInterceptor != null && strategyProperties.getSqlInjection().isEnabled()) {
            registry.addInterceptor(sqlInjectionProtectionInterceptor)
                    .addPathPatterns("/**")
                    .order(6);
        }

        // 7. CSRF防护拦截器
        if (csrfProtectionInterceptor != null && strategyProperties.getCsrf().isEnabled()) {
            registry.addInterceptor(csrfProtectionInterceptor)
                    .addPathPatterns("/**")
                    .order(7);
        }

        // 8. 请求大小限制拦截器
        if (requestSizeLimitInterceptor != null && strategyProperties.getRequestSizeLimit().isEnabled()) {
            registry.addInterceptor(requestSizeLimitInterceptor)
                    .addPathPatterns("/**")
                    .order(8);
        }

        // 9. HTTP方法限制拦截器
        if (httpMethodRestrictionInterceptor != null && strategyProperties.getHttpMethodRestriction().isEnabled()) {
            registry.addInterceptor(httpMethodRestrictionInterceptor)
                    .addPathPatterns("/**")
                    .order(9);
        }

        // 10. 参数数量限制拦截器
        if (parameterCountLimitInterceptor != null && strategyProperties.getParameterCountLimit().isEnabled()) {
            registry.addInterceptor(parameterCountLimitInterceptor)
                    .addPathPatterns("/**")
                    .order(10);
        }

        // 11. 请求超时控制拦截器
        if (requestTimeoutInterceptor != null && strategyProperties.getRequestTimeout().isEnabled()) {
            registry.addInterceptor(requestTimeoutInterceptor)
                    .addPathPatterns("/**")
                    .order(11);
        }

        // 12. 内容安全策略拦截器
        if (contentSecurityPolicyInterceptor != null && strategyProperties.getContentSecurityPolicy().isEnabled()) {
            registry.addInterceptor(contentSecurityPolicyInterceptor)
                    .addPathPatterns("/**")
                    .order(12);
        }

        // 13. 点击劫持防护拦截器
        if (clickjackingProtectionInterceptor != null && strategyProperties.getClickjackingProtection().isEnabled()) {
            registry.addInterceptor(clickjackingProtectionInterceptor)
                    .addPathPatterns("/**")
                    .order(13);
        }
    }
}
