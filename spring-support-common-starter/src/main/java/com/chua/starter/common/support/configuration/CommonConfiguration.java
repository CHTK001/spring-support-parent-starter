package com.chua.starter.common.support.configuration;

import com.chua.common.support.objects.ConfigureObjectContext;
import com.chua.common.support.objects.DefaultConfigureObjectContext;
import com.chua.common.support.objects.ObjectContextSetting;
import com.chua.starter.common.support.debounce.DebounceAspect;
import com.chua.starter.common.support.logger.SysLoggerPointcutAdvisor;
import com.chua.starter.common.support.logger.UserLoggerPointcutAdvisor;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.properties.*;
import com.chua.starter.common.support.result.ExceptionAdvice;
import com.chua.starter.common.support.result.UniformResponseBodyAdvice;
import com.chua.starter.common.support.service.IptablesService;
import com.chua.starter.common.support.service.impl.IptablesServiceImpl;
import com.chua.starter.common.support.watch.WatchPointcutAdvisor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 通用配置
 *
 * @author CH
 * @since 2023-07-01
 */
@EnableConfigurationProperties({

        LogProperties.class,
        ParameterProperties.class,
        ActuatorProperties.class,
        SpiProperties.class,
        JacksonProperties.class,
        CacheProperties.class
})
public class CommonConfiguration {


    /**
     * 创建默认的认证服务实例。
     * 当容器中没有提供AuthService实例时，使用此默认实现。
     *
     * @return {@link AuthService} 默认认证服务实例
     * @example <pre>
     * // 使用示例
     * &#64;Autowired
     * private AuthService authService;
     * </pre>
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthService authService() {
        return new AuthService.DefaultAuthService();
    }

    /**
     * 创建IP服务实例。
     * 当容器中没有提供IptablesService实例时，使用此实现。
     *
     * @param ipProperties IP属性配置对象，包含IP相关配置信息
     * @return {@link IptablesService} IP服务实例
     * @example <pre>
     * // 使用示例
     * &#64;Autowired
     * private IptablesService iptablesService;
     * </pre>
     */
    @Bean
    @ConditionalOnMissingBean
    public IptablesService authService(IpProperties ipProperties) {
        return new IptablesServiceImpl(ipProperties);
    }

    /**
     * 创建防抖切面实例。
     * 当容器中没有提供DebounceAspect实例时，使用此实现。
     *
     * @return {@link DebounceAspect} 防抖切面实例
     * @example <pre>
     * // 使用示例
     * &#64;Autowired
     * private DebounceAspect debounceAspect;
     * </pre>
     */
    @Bean
    @ConditionalOnMissingBean
    public DebounceAspect debounceAspect() {
        return new DebounceAspect();
    }

    /**
     * 创建对象上下文配置实例。
     * 当容器中没有提供ConfigureObjectContext实例时，使用此实现。
     *
     * @return {@link ConfigureObjectContext} 对象上下文配置实例
     * @example <pre>
     * // 使用示例
     * &#64;Autowired
     * private ConfigureObjectContext configureObjectContext;
     * </pre>
     */
    @Bean
    @ConditionalOnMissingBean
    public ConfigureObjectContext configureObjectContext() {
        return new DefaultConfigureObjectContext(ObjectContextSetting.builder().build());
    }

    /**
     * 创建统一响应体建议实例。
     * 当容器中没有提供UniformResponseBodyAdvice实例且plugin.parameter.enable为true时，使用此实现。
     *
     * @return {@link UniformResponseBodyAdvice} 统一响应体建议实例
     * @example
     * <pre>
     * // 使用示例
     * &#64;Autowired
     * private UniformResponseBodyAdvice uniformResponseBodyAdvice;
     * </pre>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.parameter.enable", havingValue = "true", matchIfMissing = false)
    public UniformResponseBodyAdvice uniformResponseBodyAdvice() {
        return new UniformResponseBodyAdvice();
    }


    /**
     * 创建执行器服务实例。
     * 用于处理异步任务的线程池。
     *
     * @return {@link ExecutorService} 执行器服务实例
     * @example
     * <pre>
     * // 使用示例
     * &#64;Autowired
     * &#64;Qualifier("uniform")
     * private ExecutorService executorService;
     * </pre>
     */
    @Bean("uniform")
    public ExecutorService executor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("uniform-pool").factory());
    }

    /**
     * 异常建议
     *
     * @return {@link ExceptionAdvice} 异常处理建议实例
     * @example
     * <pre>
     * // 使用示例
     * &#64;Autowired
     * private ExceptionAdvice exceptionAdvice;
     * </pre>
     */
    @Bean
    @ConditionalOnMissingBean
    public ExceptionAdvice exceptionAdvice() {
        return new ExceptionAdvice();
    }

    /**
     * 创建RestTemplate实例。
     * 当容器中没有提供RestTemplate实例时，使用此实现。
     *
     * @return {@link RestTemplate} RestTemplate实例
     * @example
     * <pre>
     * // 使用示例
     * &#64;Autowired
     * private RestTemplate restTemplate;
     * </pre>
     */
    @Bean
    @ConditionalOnMissingBean
    @Lazy
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * 创建用户日志切入点顾问实例。
     * 当容器中没有提供UserLoggerPointcutAdvisor实例时，使用此实现。
     *
     * @return {@link UserLoggerPointcutAdvisor} 用户日志切入点顾问实例
     * @example
     * <pre>
     * // 使用示例
     * &#64;Autowired
     * private UserLoggerPointcutAdvisor userLoggerPointcutAdvisor;
     * </pre>
     */
    @Bean
    @ConditionalOnMissingBean
    @Lazy
    public UserLoggerPointcutAdvisor userLoggerPointcutAdvisor() {
        return new UserLoggerPointcutAdvisor();
    }

    /**
     * 创建系统日志切入点顾问实例。
     * 当容器中没有提供SysLoggerPointcutAdvisor实例时，使用此实现。
     *
     * @return {@link SysLoggerPointcutAdvisor} 系统日志切入点顾问实例
     * @example
     * <pre>
     * // 使用示例
     * &#64;Autowired
     * private SysLoggerPointcutAdvisor sysLoggerPointcutAdvisor;
     * </pre>
     */
    @Bean
    @ConditionalOnMissingBean
    @Lazy
    public SysLoggerPointcutAdvisor sysLoggerPointcutAdvisor() {
        return new SysLoggerPointcutAdvisor();
    }

    /**
     * 创建监控切入点顾问实例。
     * 当容器中没有提供WatchPointcutAdvisor实例时，使用此实现。
     *
     * @return {@link WatchPointcutAdvisor} 监控切入点顾问实例
     * @example
     * <pre>
     * // 使用示例
     * &#64;Autowired
     * private WatchPointcutAdvisor watchPointcutAdvisor;
     * </pre>
     */
    @Bean
    @ConditionalOnMissingBean
    @Lazy
    public WatchPointcutAdvisor watchPointcutAdvisor() {
        return new WatchPointcutAdvisor();
    }

}
