package com.chua.starter.job.support;

import com.chua.starter.job.support.log.JobFileAppender;
import com.chua.starter.job.support.log.JobLogBackupService;
import com.chua.starter.job.support.log.JobLogDetailService;
import com.chua.starter.job.support.mapper.SysJobLogBackupMapper;
import com.chua.starter.job.support.mapper.SysJobLogDetailMapper;
import com.chua.starter.job.support.mapper.SysJobLogMapper;
import com.chua.starter.job.support.scheduler.SchedulerTrigger;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.chua.starter.common.support.logger.ModuleLog.*;
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
 * Job调度模块自动配置类
 * <p>
 * 这是Spring Boot Auto-configuration配置类，负责初始化Job调度系统的所有组件。
 * 通过 {@code plugin.job.enable=true} 启用（默认启用）。
 * </p>
 * 
 * <h3>自动配置内容:</h3>
 * <ul>
 *     <li>{@link ThreadPoolTaskScheduler} - 任务调度线程池</li>
 *     <li>{@link JobAnnotationScanner} - @Job注解扫描器</li>
 *     <li>{@link SchedulerTrigger} - 调度系统启动器</li>
 *     <li>{@link JobLogDetailService} - 日志详情服务</li>
 *     <li>{@link JobLogBackupService} - 日志备份服务</li>
 * </ul>
 * 
 * <h3>配置属性:</h3>
 * <pre>
 * plugin.job.enable=true          # 启用Job模块
 * plugin.job.pool-size=10         # 线程池大小
 * plugin.job.log-path=/data/logs  # 日志路径
 * plugin.job.log-retention-days=30 # 日志保留天数
 * plugin.job.auto-backup-enabled=true # 自动备份
 * </pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/11
 * @see JobProperties
 * @see SchedulerTrigger
 */
@Slf4j
@Configuration
@EnableScheduling
@EnableConfigurationProperties(JobProperties.class)
@MapperScan("com.chua.starter.job.support.mapper")
@ComponentScan("com.chua.starter.job.support.log")
@ConditionalOnProperty(prefix = JobProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = false)
public class JobConfiguration {
    private static final Logger log = LoggerFactory.getLogger(JobConfiguration.class);

    /**
     * Job线程池任务调度器
     * <p>
     * 配置参数:
     * <ul>
     *     <li>poolSize: 线程池核心大小，默认10</li>
     *     <li>threadNamePrefix: 线程名前缀 "job-scheduler-"</li>
     *     <li>waitForTasksToComplete: 关闭时等待任务完成</li>
     *     <li>awaitTermination: 最大等待60秒</li>
     * </ul>
     * </p>
     *
     * @param jobProperties 配置属性
     * @return ThreadPoolTaskScheduler 任务调度器
     */
    @Bean(name = "jobThreadPoolTaskScheduler")
    @ConditionalOnMissingBean(name = "jobThreadPoolTaskScheduler")
    public ThreadPoolTaskScheduler jobThreadPoolTaskScheduler(JobProperties jobProperties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(jobProperties.getPoolSize());
        scheduler.setThreadNamePrefix("job-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);

        // 初始化日志文件存储路径
        JobFileAppender.initLogPath(jobProperties.getLogPath());

        log.info("[Job] 调度器初始化完成 [{}], 线程池: {}, 日志路径: {}", 
                enabled(), highlight(jobProperties.getPoolSize()), highlight(jobProperties.getLogPath()));
        return scheduler;
    }

    /**
     * Job注解扫描器
     * <p>
     * 扫描带有 {@link com.chua.starter.job.support.annotation.Job} 注解的方法，
     * 自动注册为JobHandler。
     * </p>
     *
     * @return JobAnnotationScanner 注解扫描器
     */
    @Bean
    @ConditionalOnMissingBean
    public JobAnnotationScanner jobAnnotationScanner() {
        return new JobAnnotationScanner();
    }

    /**
     * 定时任务触发器
     * <p>
     * 调度系统的核心启动器，负责:
     * <ul>
     *     <li>启动核心触发处理器</li>
     *     <li>启动时间环处理器</li>
     *     <li>启动触发线程池</li>
     * </ul>
     * </p>
     *
     * @return SchedulerTrigger 调度触发器
     */
    @Bean
    @ConditionalOnMissingBean
    public SchedulerTrigger schedulerTrigger() {
        return new SchedulerTrigger();
    }
}
