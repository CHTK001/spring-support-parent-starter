package com.chua.starter.common.support.scheduler;

import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 自定义定时任务
 * @author CH
 * @since 2024/12/9
 */
@Import(SchedulerEndpoint.class)
public class CustomSchedulingConfigurer implements SchedulingConfigurer {

    ScheduledTaskRegistrar taskRegistrar ;
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        this.taskRegistrar = taskRegistrar;
    }
}
