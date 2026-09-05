package com.chua.starter.monitor.support.config;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 监控模块自动配置
 * <p>
 * 扫描所有 Provider、Service、Scheduler 组件，自动装配。
 * 开启事务管理和定时调度；注册时序存储配置属性
 * （InfluxDB 写入器按 {@code monitor.influx.enabled} 条件装配）。
 * </p>
 *
 * @since 2026/8/4
 * @author CH
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.chua.starter.monitor.support")
@EnableConfigurationProperties(MonitorInfluxProperties.class)
@EnableScheduling
@EnableTransactionManagement
public class MonitorAutoConfiguration {

    /**
     * 监控模块的 ServiceInfo 贡献者。
     *
     * @param properties Influx 配置
     * @return ServiceInfoContributor
     */
    @Bean
    public ServiceInfoContributor monitorServiceInfoContributor(MonitorInfluxProperties properties) {
        return registry -> registry.registerFlag("monitor", "监控", true,
                "监控模块",
                "[Influx: " + (properties.isEnabled() ? "开" : "关") + "]");
    }
}