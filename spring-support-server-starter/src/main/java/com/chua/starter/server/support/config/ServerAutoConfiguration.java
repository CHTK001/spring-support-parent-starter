package com.chua.starter.server.support.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 服务器资产模块自动配置
 * <p>
 * 扫描资产/远控/批量执行全部组件与 MyBatis-Plus Mapper，
 * 开启事务管理与定时调度（cron 任务触发、终端会话超时清理）。
 * WebSocket 端点由 {@link TerminalWebSocketConfig} 单独注册；
 * 凭据加解密器与会话注册表以 @Component 形式随包扫描装配。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.chua.starter.server.support")
@MapperScan(basePackages = "com.chua.starter.server.support.mapper")
@EnableConfigurationProperties(ServerProperties.class)
@EnableScheduling
@EnableTransactionManagement
public class ServerAutoConfiguration {
}
