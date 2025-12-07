package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.configuration.resolver.VersionArgumentResolver;
import com.chua.starter.common.support.actuator.ActuatorAuthenticationFilter;
import com.chua.starter.common.support.log.ParameterLogFilter;
import com.chua.starter.common.support.actuator.ActuatorProperties;
import com.chua.starter.common.support.api.cors.CorsProperties;
import com.chua.starter.common.support.log.LogProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * FilterConfiguration 类用于管理过滤器的配置。
 * 该类作为一个配置容器，不包含具体的业务逻辑，而是作为配置过滤器规则的入口。
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/6/21
 */
@EnableConfigurationProperties({
        CorsProperties.class,
})
@Slf4j
@RequiredArgsConstructor
public class FilterConfiguration {
    private static final String X_HEADER_VERSION = "x-response-version";

    final CorsProperties corsProperties;
    /**
     * 跨域
     *
     * @return CorsFilter
     */
    @Bean("corsFilterFilterRegistrationBean")
    @ConditionalOnProperty(name = "plugin.cors.enable", matchIfMissing = false, havingValue = "true")
    public FilterRegistrationBean<CorsFilter> corsFilterFilterRegistrationBean() {
        //1. 添加 CORS配置信息
        org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();
        List<String> exposedHeaders = Arrays.asList(X_HEADER_VERSION, "x-oauth-token", "content-type", "X-Requested-With", "XMLHttpRequest");
        // 放行所有原始域
        config.addAllowedOriginPattern("*");
        // 是否发送Cookie
        config.setAllowCredentials(true);
        //放行哪些请求方式
        config.addAllowedMethod("*");
        config.setExposedHeaders(exposedHeaders);
        //放行哪些原始请求头部信息
        config.addAllowedHeader("*");
        //暴露哪些头部信息
        config.addExposedHeader("*");

        config.setAllowCredentials(true);
        //2. 添加映射路径
        UrlBasedCorsConfigurationSource corsConfigurationSource = new UrlBasedCorsConfigurationSource();
        if (corsProperties.getPattern().isEmpty()) {
            corsConfigurationSource.registerCorsConfiguration("/**", config);
        } else {
            for (String s : corsProperties.getPattern()) {
                corsConfigurationSource.registerCorsConfiguration(s, config);
            }
        }
        //3. 返回新的CorsFilter
        log.info(">>>>>>> 开启跨域处理");
        CorsFilter corsFilter = new CorsFilter(corsConfigurationSource);
        FilterRegistrationBean<CorsFilter> filterRegistrationBean = new FilterRegistrationBean<>(corsFilter);
        filterRegistrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return filterRegistrationBean;
    }
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

