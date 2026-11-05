// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.report.client.starter.job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 作业配置
 *
 * @since 2024/03/05
 * @author CH
 */
public class JobConfiguration implements SchedulingConfigurer {
    /**
     * 任务registrar
    */
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
    /**
     * configure任务
     * @param taskRegistrar 参数
     * @return 返回值
    */
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        this.taskRegistrar = taskRegistrar;
        ThreadPoolTaskScheduler threadPool = threadPoolTaskScheduler();
        taskRegistrar.setScheduler(threadPool);
    }
}