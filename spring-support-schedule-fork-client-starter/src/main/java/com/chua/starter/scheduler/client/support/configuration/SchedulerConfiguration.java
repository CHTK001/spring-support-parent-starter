package com.chua.starter.scheduler.client.support.configuration;

import com.chua.starter.scheduler.client.support.JobLogService;
import com.chua.starter.scheduler.client.support.executor.JobSpringExecutor;
import com.chua.starter.scheduler.client.support.properties.SchedulerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * freemarker
 *
 * @author CH
 */
@Slf4j
@Import(JobLogService.class)
@EnableConfigurationProperties(SchedulerProperties.class)
public class SchedulerConfiguration {

    @Autowired
    private SchedulerProperties schedulerProperties;
    @Bean
    @ConditionalOnProperty(name = "plugin.scheduler.open", matchIfMissing = true, havingValue = "true")
    public JobSpringExecutor xxlJobExecutor() {
        log.info(">>>>>>>>>>> scheduler-job config init.");
        SchedulerProperties.ExecutorConfiguration executor = schedulerProperties.getExecutor();
        JobSpringExecutor xxlJobSpringExecutor = new JobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(schedulerProperties.getAddress());
        xxlJobSpringExecutor.setAppname(executor.getAppName());
        xxlJobSpringExecutor.setIp(executor.getIp());
        xxlJobSpringExecutor.setPort(executor.getPort());
        xxlJobSpringExecutor.setAccessToken(schedulerProperties.getAccessToken());
        xxlJobSpringExecutor.setLogPath(executor.getLogPath());
        xxlJobSpringExecutor.setLogRetentionDays(executor.getLogRetentionDays());
        return xxlJobSpringExecutor;
    }
}
