package com.chua.starter.proxy.support;

import com.chua.starter.proxy.support.properties.ProxyManagementProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ComponentScan;

/**
 * 代理管理自动配置
 *
 * @author CH
 * @since 2026/03/26
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProxyManagementProperties.class)
@ConditionalOnProperty(prefix = ProxyManagementProperties.PREFIX, name = "enable", havingValue = "true")
@MapperScan("com.chua.starter.proxy.support.mapper")
@ComponentScan("com.chua.starter.proxy.support")
public class ProxyManagementConfiguration {
}


