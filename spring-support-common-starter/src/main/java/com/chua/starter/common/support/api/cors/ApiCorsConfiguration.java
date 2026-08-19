package com.chua.starter.common.support.api.cors;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * API跨域配置
 * <p>
 * 用于配置跨域请求过滤器，支持自定义跨域路径匹配规则。
 * </p>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>前后端分离项目的跨域请求处理</li>
 *   <li>微服务间的跨域访问</li>
 *   <li>开发环境的跨域调试</li>
 * </ul>
 *
 * <h3>配置示例</h3>
 * <pre>
 * plugin:
 *   api:
 *     cors:
 *       enable: true           # 开启跨域
 *       pattern:               # 跨域路径
 *         - /api/**
 * </pre>
 *
 * @version 1.0.0
 * @since 2024/12/07
 * @author CH
 */
@Configuration
@EnableConfigurationProperties(ApiCorsProperties.class)
@ConditionalOnProperty(name = "plugin.api.cors.enable", matchIfMissing = true, havingValue = "true")
public class ApiCorsConfiguration  {
    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(ApiCorsConfiguration.class);
        /** X_header_version */
        private static final String X_HEADER_VERSION = "x-response-version";

    /** Cors属性 */
    private final ApiCorsProperties corsProperties;

    /**
     * 创建 ApiCorsConfiguration 实例
     * @param corsProperties corsProperties
     */
    public ApiCorsConfiguration(ApiCorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    /**
     * 跨域过滤器
     *
     * @return CorsFilter 过滤器注册Bean
     */
    @Bean("apiCorsFilterRegistrationBean")
    public FilterRegistrationBean<CorsFilter> corsFilterFilterRegistrationBean() {
        // 1. 添加 CORS 配置信息
        CorsConfiguration config = new CorsConfiguration();
        List<String> exposedHeaders = Arrays.asList(
                X_HEADER_VERSION,
                "x-oauth-token",
                "content-type",
                "X-Requested-With",
                "XMLHttpRequest"
        );
        // 放行所有原始域
        config.addAllowedOriginPattern("*");
        // 是否发送Cookie
        config.setAllowCredentials(true);
        // 放行所有请求方式
        config.addAllowedMethod("*");
        config.setExposedHeaders(exposedHeaders);
        // 放行所有请求头部信息
        config.addAllowedHeader("*");
        // 暴露所有头部信息
        config.addExposedHeader("*");

        // 2. 添加映射路径
        UrlBasedCorsConfigurationSource corsConfigurationSource = new UrlBasedCorsConfigurationSource();
        if (corsProperties.getPattern().isEmpty()) {
            corsConfigurationSource.registerCorsConfiguration("/**", config);
        } else {
            for (String pattern : corsProperties.getPattern()) {
                corsConfigurationSource.registerCorsConfiguration(pattern, config);
            }
        }

        // 3. 返回新的 CorsFilter
        ServiceInfoRegistry.getInstance().registerFlag(
                "cors", "API控制", true, "跨域处理", null);
        CorsFilter corsFilter = new CorsFilter(corsConfigurationSource);
        FilterRegistrationBean<CorsFilter> filterRegistrationBean = new FilterRegistrationBean<>(corsFilter);
        filterRegistrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return filterRegistrationBean;
    }
}

