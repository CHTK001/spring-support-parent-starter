package com.chua.starter.job.support;

import com.chua.starter.job.support.log.JobFileAppender;
import com.chua.starter.job.support.log.JobLogBackupService;
import com.chua.starter.job.support.log.JobLogDetailService;
import com.chua.starter.job.support.mapper.MonitorJobLogBackupMapper;
import com.chua.starter.job.support.mapper.MonitorJobLogDetailMapper;
import com.chua.starter.job.support.mapper.MonitorJobLogMapper;
import com.chua.starter.job.support.scheduler.SchedulerTrigger;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Job配置类
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 */
@Slf4j
@Configuration
@EnableScheduling
@EnableConfigurationProperties(JobProperties.class)
@MapperScan("com.chua.starter.job.support.mapper")
@ComponentScan("com.chua.starter.job.support.log")
@ConditionalOnProperty(prefix = JobProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
public class JobConfiguration {

    /**
     * Job线程池任务调度器
     *
     * @param jobProperties 配置属性
     * @return ThreadPoolTaskScheduler
     */
    @Bean(name = "jobThreadPoolTaskScheduler")
    @ConditionalOnMissingBean(name = "jobThreadPoolTaskScheduler")
    public ThreadPoolTaskScheduler jobThreadPoolTaskScheduler(JobProperties jobProperties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(jobProperties.getPoolSize());
        scheduler.setThreadNamePrefix("job-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);

        // 初始化日志路径
        JobFileAppender.initLogPath(jobProperties.getLogPath());

        log.info(">>>>>>>>>>> job scheduler init success, poolSize:{}", jobProperties.getPoolSize());
        return scheduler;
    }

    /**
     * Job注解扫描器
     *
     * @return JobAnnotationScanner
     */
    @Bean
    @ConditionalOnMissingBean
    public JobAnnotationScanner jobAnnotationScanner() {
        return new JobAnnotationScanner();
    }

    /**
     * 定时任务触发器
     *
     * @return 定时任务触发器
     */
    @Bean
    @ConditionalOnMissingBean
    public SchedulerTrigger schedulerTrigger() {
        return new SchedulerTrigger();
    }
}
