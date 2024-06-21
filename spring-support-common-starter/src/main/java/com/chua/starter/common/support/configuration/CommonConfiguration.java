package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.debounce.DebounceAspect;
import com.chua.starter.common.support.external.ExternalController;
import com.chua.starter.common.support.filter.ActuatorAuthenticationFilter;
import com.chua.starter.common.support.filter.ParameterLogFilter;
import com.chua.starter.common.support.limit.LimitAspect;
import com.chua.starter.common.support.logger.OperateLoggerPointcutAdvisor;
import com.chua.starter.common.support.properties.*;
import com.chua.starter.common.support.provider.SettingProvider;
import com.chua.starter.common.support.result.ExceptionAdvice;
import com.chua.starter.common.support.result.UniformResponseBodyAdvice;
import com.chua.starter.common.support.setting.DefaultSettingService;
import com.chua.starter.common.support.setting.SettingService;
import com.chua.starter.common.support.watch.WatchPointcutAdvisor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestTemplate;

/**
 * 通用配置
 *
 * @author CH
 */
@EnableConfigurationProperties({
        LimitProperties.class,
        LogProperties.class,
        ParameterProperties.class,
        ActuatorProperties.class,
        ExternalInterfaceProperties.class
})
public class CommonConfiguration {
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.limit.enable", havingValue = "true", matchIfMissing = true)
    public LimitAspect limitAspect(LimitProperties limitProperties) {
        return new LimitAspect(limitProperties);
    }
    @Bean
    @ConditionalOnMissingBean
    public DebounceAspect debounceAspect() {
        return new DebounceAspect();
    }
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.external.enable", havingValue = "true", matchIfMissing = true)
    public ExternalController externalController(ExternalInterfaceProperties externalInterfaceProperties) {
        return new ExternalController(externalInterfaceProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.log.enable", havingValue = "true", matchIfMissing = true)
    public ParameterLogFilter paramLogFilter() {
        return new ParameterLogFilter();
    }
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

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.parameter.enable", havingValue = "true", matchIfMissing = false)
    public UniformResponseBodyAdvice uniformResponseBodyAdvice() {
        return new UniformResponseBodyAdvice();
    }

    /**
     * 异常建议
     *
     * @return {@link ExceptionAdvice}
     */
    @Bean
    @ConditionalOnMissingBean
    public ExceptionAdvice exceptionAdvice() {
        return new ExceptionAdvice();
    }

    @Bean
    @ConditionalOnMissingBean
    @Lazy
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    @Bean
    @ConditionalOnMissingBean
    @Lazy
    public OperateLoggerPointcutAdvisor operateLoggerPointcutAdvisor() {
        return new OperateLoggerPointcutAdvisor();
    }

    @Bean
    @ConditionalOnMissingBean
    @Lazy
    public WatchPointcutAdvisor watchPointcutAdvisor() {
        return new WatchPointcutAdvisor();
    }
    @Bean
    @ConditionalOnMissingBean
    @Lazy
    public SettingProvider settingProvider(SettingService settingService) {
        return new SettingProvider(settingService);
    }
    @Bean
    @ConditionalOnMissingBean
    @Lazy
    public SettingService settingService() {
        return new DefaultSettingService();
    }

}
