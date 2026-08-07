package com.chua.starter.job.support;

import com.chua.starter.job.support.log.JobFileAppender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Job 模块自动配置类 (占位骨架)
 * <p>
 * 当前仅提供:配置属性绑定、日志路径初始化、{@code @Job} 注解扫描器注册。
 * 调度入口由后续重构补齐。
 * </p>
 *
 * @author CH
 * @since 2024/03/11
 * @see JobProperties
 * @see JobAnnotationScanner
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(JobProperties.class)
@ConditionalOnProperty(prefix = JobProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = false)
public class JobConfiguration {

    /**
     * {@code @Job} 注解扫描器
     */
    @Bean
    @ConditionalOnMissingBean
    public JobAnnotationScanner jobAnnotationScanner() {
        return new JobAnnotationScanner();
    }

    /**
     * 初始化日志文件路径 (即使当前模块没有触发器,后续重构仍会复用)
     */
    @Bean
    @ConditionalOnMissingBean
    public JobFileAppenderInitializer jobFileAppenderInitializer(JobProperties jobProperties) {
        return new JobFileAppenderInitializer(jobProperties);
    }
}
