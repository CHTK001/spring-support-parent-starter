package com.chua.scheduler.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/**
 * 定时任务配置
 * @author CH
 * @since 2024/8/1
 */
public class SchedulingConfigurerConfiguration {


    @Bean
    @ConditionalOnMissingBean
    public ScheduleCornChangeHandler scheduleCornChangeHandler(ScheduledAnnotationBeanPostProcessor processor) {
        return new ScheduleCornChangeHandler(processor);
    }


    @Bean
    @ConditionalOnMissingBean
    public SchedulerEndpoint scheduleEndpoint(ScheduleCornChangeHandler scheduleCornChangeHandler) {
        return new SchedulerEndpoint(scheduleCornChangeHandler);
    }
}
