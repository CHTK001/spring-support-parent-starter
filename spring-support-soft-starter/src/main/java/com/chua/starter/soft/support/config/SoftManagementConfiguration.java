package com.chua.starter.soft.support.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * 软件管理自动配置。
 *
 * @author CH
 * @since 2026/04/04
 */
@AutoConfiguration
@EnableConfigurationProperties(SoftManagementProperties.class)
@ConditionalOnProperty(prefix = SoftManagementProperties.PREFIX, name = "enable", havingValue = "true", matchIfMissing = true)
@MapperScan("com.chua.starter.soft.support.mapper")
@ComponentScan("com.chua.starter.soft.support")
public class SoftManagementConfiguration {
}
