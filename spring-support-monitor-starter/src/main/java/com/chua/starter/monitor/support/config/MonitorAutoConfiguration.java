package com.chua.starter.monitor.support.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 监控模块自动配置
 * <p>
 * 扫描所有 Provider、Service、Scheduler 组件，自动装配。
 * 开启事务管理和定时调度。
 * </p>
 *
 * @author CH
 * @since 2026/8/4
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.chua.starter.monitor.support")
@EnableScheduling
@EnableTransactionManagement
public class MonitorAutoConfiguration {
}