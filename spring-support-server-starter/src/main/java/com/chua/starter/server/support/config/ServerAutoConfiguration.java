package com.chua.starter.server.support.config;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
* 服务器资产模块自动配置
* <p>
* 扫描资产/远控/批量执行全部组件与 MyBatis-Plus 映射器，
* 开启事务管理。周期任务由 {@code ServerTaskScheduler} 基于
* common-starter 哈希时间轮统一调度（在离线检测/定时采集/计划任务/会话清理）。
* web套接字 端点由 {@link TerminalWebSocketConfig} 单独注册；
* 凭据加解密器与会话注册表以 @组件 形式随包扫描装配。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.chua.starter.server.support")
@MapperScan(basePackages = "com.chua.starter.server.support.mapper")
@EnableConfigurationProperties(ServerProperties.class)
@EnableTransactionManagement
@EnableScheduling
public class ServerAutoConfiguration {

    /**
    * 服务器资产模块的 服务信息 贡献者。
    *
    * @param properties 配置属性
    * @return ServiceInfoContributor
     */
    @Bean
    public ServiceInfoContributor serverServiceInfoContributor(ServerProperties properties) {
        return registry -> registry.registerFlag("server", "服务器资产", properties.isEnabled(),
                "服务器资产模块",
                "[并发: " + properties.getExecConcurrency() + " 会话超时: "
                        + properties.getSessionIdleMinutes() + "min]");
    }
}
