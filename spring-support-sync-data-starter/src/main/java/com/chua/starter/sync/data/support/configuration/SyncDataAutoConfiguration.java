package com.chua.starter.sync.data.support.configuration;

import com.chua.starter.sync.data.support.properties.SyncDataProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 数据同步自动配置
 *
 * @author System
 * @since 2026/03/09
 */
@Configuration
@EnableConfigurationProperties(SyncDataProperties.class)
public class SyncDataAutoConfiguration {
}
