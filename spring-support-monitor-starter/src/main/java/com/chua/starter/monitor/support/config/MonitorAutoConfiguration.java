package com.chua.starter.monitor.support.config;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.ApplicationDataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.autoconfigure.init.SqlInitializationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * 监控模块自动配置
 * <p>
 * 扫描所有 提供者、服务、调度器 组件，自动装配。
 * 开启事务管理和定时调度；注册时序存储配置属性
 * （influxdb 写入器按 {@code plugin.monitor.influx.enabled} 条件装配）。
 * </p>
 *
 * @since 2026/8/4
 * @author CH
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.chua.starter.monitor.support")
@EnableConfigurationProperties({MonitorInfluxProperties.class, MonitorTimeseriesProperties.class, SqlInitializationProperties.class})
@EnableScheduling
@EnableTransactionManagement
public class MonitorAutoConfiguration {

    /**
     * 建表脚本初始化器。
     * <p>
     * 框架的 datasource 插件会额外注册多个 DataSource Bean，Spring Boot 的
     * {@code DataSourceInitializationAutoConfiguration} 因
     * {@code @ConditionalOnSingleCandidate(DataSource.class)} 静默退避，
     * {@code spring.sql.init.schema-locations} 于是从未执行——全新库启动后一张表都没有。
     * 这里显式装配同一个初始化器，把配置里的脚本真正跑起来。
     * </p>
     *
     * @param dataSource 主数据源
     * @param properties spring.sql.init 配置
     * @return 数据源脚本初始化器
     */
    @Bean
    public ApplicationDataSourceScriptDatabaseInitializer monitorSchemaInitializer(DataSource dataSource,
                                                                                  SqlInitializationProperties properties) {
        return new ApplicationDataSourceScriptDatabaseInitializer(dataSource, properties);
    }

    /**
     * 监控模块的 服务信息 贡献者。
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
