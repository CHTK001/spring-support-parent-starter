package com.chua.scheduler.support;

import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.config.Task;

import java.util.Set;

/**
 * @author CH
 * @since 2024/8/1
 */
public class ScheduleCornChangeHandler implements SchedulingConfigurer {
    private final ScheduledAnnotationBeanPostProcessor processor;
    private ScheduledTaskRegistrar registrar;

    public ScheduleCornChangeHandler(ScheduledAnnotationBeanPostProcessor processor) {
        this.processor = processor;
    }

    public String cornChanged(String bean, String method) {
        // 获取所有定时任务
        Set<ScheduledTask> scheduledTasks = processor.getScheduledTasks();
        for (ScheduledTask scheduledTask : scheduledTasks) {
            Task task = scheduledTask.getTask();
            if (task instanceof CronTask cronTask) {
                Runnable runnable = cronTask.getRunnable();
            }
        }

        return null;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        // 目的是拿到ScheduledTaskRegistrar对象来注册定时任务（注意：该对象无法直接通过自动注入获得）
        registrar = taskRegistrar;
    }
}
