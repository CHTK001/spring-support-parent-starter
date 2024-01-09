package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.filter.ParameterLogFilter;
import com.chua.starter.common.support.limit.LimitAspect;
import com.chua.starter.common.support.logger.OperateLoggerPointcutAdvisor;
import com.chua.starter.common.support.properties.LimitProperties;
import com.chua.starter.common.support.properties.LogProperties;
import com.chua.starter.common.support.properties.ParameterProperties;
import com.chua.starter.common.support.result.ExceptionAdvice;
import com.chua.starter.common.support.result.UniformResponseBodyAdvice;
import com.chua.starter.common.support.watch.WatchPointcutAdvisor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestTemplate;

/**
 * 通用配置
 *
 * @author CH
 */
@EnableConfigurationProperties({
        LimitProperties.class, LogProperties.class, ParameterProperties.class
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
    @ConditionalOnProperty(name = "plugin.log.enable", havingValue = "true", matchIfMissing = true)
    public ParameterLogFilter paramLogFilter() {
        return new ParameterLogFilter();
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

}
