package com.chua.starter.common.support.configuration;

import com.chua.common.support.objects.ConfigureObjectContext;
import com.chua.common.support.objects.DefaultConfigureObjectContext;
import com.chua.starter.common.support.debounce.DebounceAspect;

import com.chua.starter.common.support.logger.SysLoggerPointcutAdvisor;
import com.chua.starter.common.support.logger.UserLoggerPointcutAdvisor;
import com.chua.starter.common.support.properties.*;
import com.chua.starter.common.support.result.ExceptionAdvice;
import com.chua.starter.common.support.result.UniformResponseBodyAdvice;
import com.chua.starter.common.support.watch.WatchPointcutAdvisor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 通用配置
 *
 * @author CH
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

    @Bean
    @ConditionalOnMissingBean
    public DebounceAspect debounceAspect() {
        return new DebounceAspect();
    }

    @Bean
    @ConditionalOnMissingBean
    public ConfigureObjectContext configureObjectContext() {
        return new DefaultConfigureObjectContext();
    }
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.parameter.enable", havingValue = "true", matchIfMissing = false)
    public UniformResponseBodyAdvice uniformResponseBodyAdvice() {
        return new UniformResponseBodyAdvice();
    }


    @Bean("uniform")
    public ExecutorService executor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("uniform-pool").factory());
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
    public UserLoggerPointcutAdvisor userLoggerPointcutAdvisor() {
        return new UserLoggerPointcutAdvisor();
    }
    @Bean
    @ConditionalOnMissingBean
    @Lazy
    public SysLoggerPointcutAdvisor sysLoggerPointcutAdvisor() {
        return new SysLoggerPointcutAdvisor();
    }

    @Bean
    @ConditionalOnMissingBean
    @Lazy
    public WatchPointcutAdvisor watchPointcutAdvisor() {
        return new WatchPointcutAdvisor();
    }

}
