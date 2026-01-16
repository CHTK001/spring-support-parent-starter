package com.chua.starter.job.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 自动装配 @Scheduled 注解扫描器
 */
@Configuration
public class ScheduledAutoConfiguration {

    @Bean
    public ScheduledAnnotationScanner scheduledAnnotationScanner() {
        return new ScheduledAnnotationScanner();
    }
}
