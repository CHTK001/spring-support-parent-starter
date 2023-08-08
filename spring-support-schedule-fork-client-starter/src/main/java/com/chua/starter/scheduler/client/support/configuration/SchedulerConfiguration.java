package com.chua.starter.scheduler.client.support.configuration;

import com.chua.starter.scheduler.client.support.properties.SchedulerProperties;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.annotation.Resource;

/**
 * freemarker
 *
 * @author CH
 */
@Slf4j
@EnableConfigurationProperties(SchedulerProperties.class)
public class SchedulerConfiguration {

    @Resource
    private SchedulerProperties schedulerProperties;
    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        log.info(">>>>>>>>>>> scheduler-job config init.");
        SchedulerProperties.ExecutorConfiguration executor = schedulerProperties.getExecutor();
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
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
