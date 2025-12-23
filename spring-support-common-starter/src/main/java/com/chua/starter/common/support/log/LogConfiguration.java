package com.chua.starter.common.support.log;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * 日志配置类
 * <p>
 * 统一管理日志相关的过滤器和配置，包括：
 * <ul>
 *     <li>参数日志过滤器 - 记录请求参数、响应时间等</li>
 *     <li>MDC 过滤器 - 链路追踪</li>
 * </ul>
 * </p>
 *
 * <h3>配置示例：</h3>
 * <pre>
 * plugin:
 *   log:
 *     enable: true              # 是否开启日志过滤器
 *     open-interface-log: true  # 是否开启接口日志事件
 * </pre>
 * <p>MDC 链路追踪默认永久开启，无需配置</p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/06/21
 */
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties({LogProperties.class})
public class LogConfiguration {

    private final LogProperties logProperties;

    /**
     * 参数日志过滤器
     * <p>
     * 记录 HTTP 请求的详细信息，包括：
     * <ul>
     *     <li>请求方法和 URL</li>
     *     <li>请求参数（Query/Body）</li>
     *     <li>请求头（以 x- 开头的自定义头）</li>
     *     <li>客户端 IP 地址</li>
     *     <li>请求耗时</li>
     * </ul>
     * </p>
     *
     * @param applicationContext Spring 应用上下文
     * @return FilterRegistrationBean 过滤器注册 Bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "parameterLogFilterRegistration")
    @ConditionalOnProperty(name = "plugin.log.enable", havingValue = "true", matchIfMissing = false)
    public FilterRegistrationBean<ParameterLogFilter> parameterLogFilterRegistration(ApplicationContext applicationContext) {
        log.info(">>>>>>> 注册参数日志过滤器 [plugin.log.enable=true]");
        
        FilterRegistrationBean<ParameterLogFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ParameterLogFilter(logProperties, applicationContext));
        registration.addUrlPatterns("/*");
        registration.setName("parameterLogFilter");
        registration.setOrder(Integer.MAX_VALUE - 100);
        registration.setAsyncSupported(true);
        
        log.debug("参数日志过滤器配置: enable={}, openInterfaceLog={}", 
                logProperties.isEnable(), logProperties.isOpenInterfaceLog());
        
        return registration;
    }

}
