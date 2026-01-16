package com.chua.report.client.starter.job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * JOB配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/05
 */
public class JobConfiguration implements SchedulingConfigurer {
    private ScheduledTaskRegistrar taskRegistrar;

    /**
     * 用线程池执行任务
     * @return
     */
    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolTaskScheduler threadPoolTaskScheduler(){
        ThreadPoolTaskScheduler threadPool = new ThreadPoolTaskScheduler();
        threadPool.setPoolSize(200);
        threadPool.setThreadNamePrefix("job");
        return threadPool;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        this.taskRegistrar = taskRegistrar;
        ThreadPoolTaskScheduler threadPool = threadPoolTaskScheduler();
        taskRegistrar.setScheduler(threadPool);
    }
}
