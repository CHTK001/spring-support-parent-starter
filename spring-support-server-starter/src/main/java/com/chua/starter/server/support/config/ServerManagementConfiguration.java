package com.chua.starter.server.support.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@EnableConfigurationProperties(ServerManagementProperties.class)
@ConditionalOnProperty(prefix = ServerManagementProperties.PREFIX, name = "enable", havingValue = "true", matchIfMissing = true)
@MapperScan("com.chua.starter.server.support.mapper")
@ComponentScan("com.chua.starter.server.support")
public class ServerManagementConfiguration {
}
