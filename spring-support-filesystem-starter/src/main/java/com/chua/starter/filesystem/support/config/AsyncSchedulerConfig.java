package com.chua.starter.filesystem.support.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
* 异步调度配置，启用 Spring 定时任务支持。
*
* @author CH
* @since 4.0.0
 */
@Configuration
@EnableScheduling
public class AsyncSchedulerConfig {
}
