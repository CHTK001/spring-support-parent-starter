package com.chua.starter.server.support.acme.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * ACME 证书生命周期管理的独立装配入口。
 *
 * <p>该配置由 server starter 注册，不依赖 monitor 的组件扫描；业务控制器、服务、持久层
 * 和续签任务随 server 模块一起装配。证书管理统一使用控制器原生路径 {@code /v1/cert}。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.chua.starter.server.support.acme")
@MapperScan(basePackages = "com.chua.starter.server.support.acme.mapper")
@EnableConfigurationProperties(AcmeProperties.class)
@EnableScheduling
@EnableTransactionManagement
@ConditionalOnProperty(prefix = "monitor.acme", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AcmeAutoConfiguration {
}
