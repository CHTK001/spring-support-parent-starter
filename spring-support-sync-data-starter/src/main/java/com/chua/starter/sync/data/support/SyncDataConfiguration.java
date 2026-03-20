package com.chua.starter.sync.data.support;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import com.chua.starter.sync.data.support.properties.SyncJobIntegrationProperties;

/**
 * 同步数据配置
 * @author CH
 * @since 2024/12/19
 */
@ComponentScan("com.chua.starter.sync.data.support")
@MapperScan("com.chua.starter.sync.data.support.mapper")
@EnableConfigurationProperties(SyncJobIntegrationProperties.class)
public class SyncDataConfiguration {
}
