package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.configuration.resolver.VersionArgumentResolver;
import com.chua.starter.common.support.actuator.ActuatorAuthenticationFilter;
import com.chua.starter.common.support.log.ParameterLogFilter;
import com.chua.starter.common.support.actuator.ActuatorProperties;
import com.chua.starter.common.support.log.LogProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.util.Collections;

/**
 * FilterConfiguration 类用于管理过滤器的配置。
 * 该类作为一个配置容器，不包含具体的业务逻辑，而是作为配置过滤器规则的入口。
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/6/21
 */
@Slf4j
@RequiredArgsConstructor
public class FilterConfiguration {
    private static final String X_HEADER_VERSION = "x-response-version";
    /**
     * 参数日志
     *
     * @return ParameterLogFilter
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.log.enable", havingValue = "true", matchIfMissing = true)
    public ParameterLogFilter paramLogFilter(LogProperties loggerProperties, ApplicationContext applicationContext) {
        return new ParameterLogFilter(loggerProperties, applicationContext);
    }
    /**
     * 认证过滤器
     *
     * @return FilterRegistrationBean
     */
    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<ActuatorAuthenticationFilter> actuatorAuthenticationFilter(ActuatorProperties actuatorProperties) {
        FilterRegistrationBean<ActuatorAuthenticationFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        filterRegistrationBean.setFilter(new ActuatorAuthenticationFilter(actuatorProperties));
        filterRegistrationBean.addUrlPatterns("/actuator/*");
        filterRegistrationBean.setName("actuator-filter");
        filterRegistrationBean.setAsyncSupported(true);

        return filterRegistrationBean;
    }


    @Bean("versionFilterRegistrationBeanFilter")
    public FilterRegistrationBean<VersionFilter> versionFilterRegistrationBeanFilter(@Autowired(required = false) VersionArgumentResolver versionArgumentResolver) {
        FilterRegistrationBean<VersionFilter> filterFilterRegistrationBean = new FilterRegistrationBean<>();
        filterFilterRegistrationBean.setAsyncSupported(true);
        filterFilterRegistrationBean.setName("version filter");
        filterFilterRegistrationBean.setFilter(new VersionFilter(versionArgumentResolver));
        filterFilterRegistrationBean.setUrlPatterns(Collections.singletonList("/*"));
        filterFilterRegistrationBean.setOrder(Integer.MIN_VALUE);
        return filterFilterRegistrationBean;
    }

    public static class VersionFilter implements Filter {

        private final VersionArgumentResolver versionArgumentResolver;

        public VersionFilter(VersionArgumentResolver versionArgumentResolver) {
            this.versionArgumentResolver = versionArgumentResolver;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            if(null != versionArgumentResolver && response instanceof HttpServletResponse httpServletResponse) {
                httpServletResponse.setHeader(X_HEADER_VERSION, versionArgumentResolver.version());
            }
            chain.doFilter(request, response);
        }
    }
}

