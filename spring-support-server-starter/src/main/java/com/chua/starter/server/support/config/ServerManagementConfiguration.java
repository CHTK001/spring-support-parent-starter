package com.chua.starter.server.support.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ServerManagementProperties.class)
public class ServerManagementConfiguration {
}
