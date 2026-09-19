package com.chua.starter.common.support.log;

import com.chua.starter.common.support.application.ModuleEnvironmentRegistration;
import com.chua.starter.common.support.constant.MdcConstant;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * @since 2024/06/21
 * @author CH
 */
@EnableConfigurationProperties({LogProperties.class})
public class LogConfiguration {
    /**
     * 日志
    */
    private static final Logger log = LoggerFactory.getLogger(LogConfiguration.class);

    /**
     * 日志属性
    */
    private final LogProperties logProperties;

    /**
     * 构造函数
     *
     * @param logProperties 日志属性
     */
    public LogConfiguration(LogProperties logProperties) {
        this.logProperties = logProperties;
    }

    /**
     * 注册日志配置到全局环境
     */
    @PostConstruct
    public void registerEnvironment() {
        new ModuleEnvironmentRegistration("plugin.log", logProperties, logProperties.isEnable());
    }

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

    /**
     * 日志/MDC 模块的 服务信息 贡献者: 输出 MDC 链路追踪、参数日志过滤器等开关状态。
     *
     * <p>MDC 链路追踪由 {@link MdcHandlerFilter} 实现, 通过
     * {@link com.chua.starter.common.support.configuration.MessageConverterWebMvcConfigurer}
     * 注册为全局过滤器, 订单 为 {@link Integer#MIN_VALUE} (过滤链最前), 永久开启无需配置。</p>
     *
     * @return ServiceInfoContributor
     */
    @Bean
    public ServiceInfoContributor logServiceInfoContributor() {
        return registry -> {
            registry.registerFlag("mdc", "日志", true,
                    "MDC 链路追踪 (traceId)", "[位置: 过滤链最前] [Header: " + MdcConstant.X_TRACE_ID + "]");
            registry.registerFlag("mdc-async", "日志", true,
                    "MDC 异步线程传递 (MdcTaskDecorator)", "[位置: 异步线程池]");
            registry.registerFlag("param-log", "日志", logProperties.isEnable(),
                    "HTTP 参数日志过滤器", "[配置: plugin.log.enable]");
            registry.registerFlag("interface-log", "日志", logProperties.isOpenInterfaceLog(),
                    "接口日志事件", "[配置: plugin.log.open-interface-log]");
            registry.registerFlag("slow-request", "日志", true,
                    "慢请求 WARN 日志", "[阈值: " + logProperties.getSlowRequestThresholdMs() + "ms]");
        };
    }

}
