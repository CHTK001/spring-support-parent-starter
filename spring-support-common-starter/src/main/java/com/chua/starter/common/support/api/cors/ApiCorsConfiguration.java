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
 * @since 2024/12/07
 * @author CH
 */
@Configuration
@EnableConfigurationProperties({ApiCorsProperties.class, ApiSecurityHeaderFilter.ApiSecurityHeaderProperties.class})
@ConditionalOnProperty(name = "plugin.api.cors.enable", matchIfMissing = true, havingValue = "true")
public class ApiCorsConfiguration  {
    /**
     * 日志
    */
    private static final Logger log = LoggerFactory.getLogger(ApiCorsConfiguration.class);
        /**
         * X_头部_版本
        */
        private static final String X_HEADER_VERSION = "x-response-version";

        /**
         * 响应加密相关头（与前端 packages/core/src/httpCrypto.ts 的常量一一对应）
         */
        private static final String ENCRYPT_HEADER = "x-ec";

        /**
         * 响应密钥头
         */
        private static final String KEY_HEADER = "x-ck";

        /**
         * 噪声等级头
         */
        private static final String NOISE_LEVEL_HEADER = "x-ot";

    /**
     * 跨域资源共享属性
    */
    private final ApiCorsProperties corsProperties;

    /**
     * 创建 apicors配置 实例
     * @param corsProperties 跨域资源共享属性
     */
    public ApiCorsConfiguration(ApiCorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    /**
     * 安全响应头过滤器注册
     *
     * <p>与 CORS 过滤器同一生命周期开关：关掉跨域同时也关掉安全头
     * （两者都由 {@code plugin.api.cors.enable} 控制，避免出现
     * 「CORS 关了但安全头还在」这种难以排查的半开状态）。</p>
     *
     * <p>独立配置项 {@code plugin.api.security.header.enable} 可单独关闭过滤器，
     * 但它只在 cors.enable 为 true 时才被创建。</p>
     *
     * @param properties 安全响应头配置
     * @return 过滤器注册Bean
     */
    @Bean("apiSecurityHeaderFilterRegistrationBean")
    public FilterRegistrationBean<ApiSecurityHeaderFilter> apiSecurityHeaderFilterRegistrationBean(
            ApiSecurityHeaderFilter.ApiSecurityHeaderProperties properties) {
        FilterRegistrationBean<ApiSecurityHeaderFilter> registrationBean =
                new FilterRegistrationBean<>(new ApiSecurityHeaderFilter(properties));
        // 比 CORS 稍后：让 CORS 先写完 Access-Control-* 头，本过滤器只补安全头
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }

    /**
     * 跨域过滤器
     *
     * @return CorsFilter 过滤器注册Bean
     */
    @Bean("apiCorsFilterRegistrationBean")
    public FilterRegistrationBean<CorsFilter> corsFilterFilterRegistrationBean() {
 // 1. 添加 跨域资源共享 配置信息
        CorsConfiguration config = new CorsConfiguration();
        List<String> exposedHeaders = Arrays.asList(
                X_HEADER_VERSION,
                "x-oauth-token",
                "content-type",
                "X-Requested-With",
                "XMLHttpRequest",
                // 前端 packages/core/src/httpCrypto.ts 会读这四个响应头：
                // 缺任何一个都会让响应加密/密钥协商/噪声等级静默失效。
                // 移除了原来的 addExposedHeader("*")，必须在此显式列出。
                ENCRYPT_HEADER,
                KEY_HEADER,
                NOISE_LEVEL_HEADER
        );
        // 允许的来源：按白名单放行，白名单为空则不放行任何跨域来源（同源访问不受影响）。
        //
        // 原实现是 addAllowedOriginPattern("*") + setAllowCredentials(true)，
        // 实测（2026-10-01，198）任意 Origin（https://evil.example.com 与 null）
        // 都会被原样反射回 Access-Control-Allow-Origin，并带
        // Access-Control-Allow-Credentials: true —— 任意网站可携带用户凭证读接口响应。
        // 改为显式白名单；确需放开时用端口通配如 http://192.168.50.*:8096，而不是 "*"。
        if (corsProperties.getAllowedOrigins().isEmpty()) {
            log.warn("[cors] 未配置 plugin.api.cors.allowed-origins，已禁用跨域来源放行。"
                    + "前端与服务端不同源时必须在配置中显式列出白名单。");
        } else {
            for (String origin : corsProperties.getAllowedOrigins()) {
                config.addAllowedOriginPattern(origin);
            }
            log.info("[cors] 跨域来源白名单: {}", corsProperties.getAllowedOrigins());
        }
        // 是否发送Cookie：默认关闭；打开前必须确认白名单已配好
        config.setAllowCredentials(corsProperties.isAllowCredentials());
        // 放行所有请求方式
        config.addAllowedMethod("*");
        config.setExposedHeaders(exposedHeaders);
        // 放行所有请求头部信息
        config.addAllowedHeader("*");

        // 2. 添加映射路径
        UrlBasedCorsConfigurationSource corsConfigurationSource = new UrlBasedCorsConfigurationSource();
        // WS 升级握手：Origin 校验交由 WebSocket 层（allowedOriginPatterns）负责，
        // CORS 层全放行。否则同源页面经 dev 代理（保留 Origin）转发到后端时，
        // 后端把请求误判为跨源，白名单为空即 403 —— VNC/SSH 直连 WS 全部连不上。
        // 注意：UrlBasedCorsConfigurationSource 取「第一个匹配」的配置，具体路径
        // 必须先注册，否则 /ws/** 会被后注册的 /** 抢先命中而永不生效。
        CorsConfiguration wsConfig = new CorsConfiguration();
        wsConfig.addAllowedOriginPattern("*");
        wsConfig.setAllowCredentials(false);
        wsConfig.addAllowedMethod("*");
        wsConfig.addAllowedHeader("*");
        corsConfigurationSource.registerCorsConfiguration("/ws/**", wsConfig);
        if (corsProperties.getPattern().isEmpty()) {
            corsConfigurationSource.registerCorsConfiguration("/**", config);
        } else {
            for (String pattern : corsProperties.getPattern()) {
                corsConfigurationSource.registerCorsConfiguration(pattern, config);
            }
        }

 // 3. 返回新的 跨域资源共享过滤器
        ServiceInfoRegistry.getInstance().registerFlag(
                "cors", "API控制", true, "跨域处理", null);
        CorsFilter corsFilter = new CorsFilter(corsConfigurationSource);
        FilterRegistrationBean<CorsFilter> filterRegistrationBean = new FilterRegistrationBean<>(corsFilter);
        filterRegistrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return filterRegistrationBean;
    }
}

