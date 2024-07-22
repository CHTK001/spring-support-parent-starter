package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.filter.ActuatorAuthenticationFilter;
import com.chua.starter.common.support.filter.ParameterLogFilter;
import com.chua.starter.common.support.properties.ActuatorProperties;
import com.chua.starter.common.support.properties.CorsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * FilterConfiguration 类用于管理过滤器的配置。
 * 该类作为一个配置容器，不包含具体的业务逻辑，而是作为配置过滤器规则的入口。
 * @author CH
 * @since 2024/6/21
 */
@EnableConfigurationProperties({
        CorsProperties.class,
})
@Slf4j
@RequiredArgsConstructor
public class FilterConfiguration {

    final CorsProperties corsProperties;
    /**
     * 跨域
     *
     * @return CorsFilter
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.cors.enable", matchIfMissing = false, havingValue = "true")
    public CorsFilter corsFilter() {
        //1. 添加 CORS配置信息
        org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();
        //放行哪些原始域
        config.addAllowedOriginPattern("*");
        //是否发送 Cookie
//        config.setAllowCredentials(true);
        //放行哪些请求方式
        config.addAllowedMethod("*");
        //放行哪些原始请求头部信息
        config.addAllowedHeader("*");
        //暴露哪些头部信息
        config.addExposedHeader("*");
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
        return new CorsFilter(corsConfigurationSource);
    }
    /**
     * 参数日志
     *
     * @return ParameterLogFilter
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.log.enable", havingValue = "true", matchIfMissing = true)
    public ParameterLogFilter paramLogFilter() {
        return new ParameterLogFilter();
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
}
