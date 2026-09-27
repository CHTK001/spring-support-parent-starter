package com.chua.starter.datasourcehub.support.config;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * 数据源中心自动配置。
 *
 * <p>扫描数据源台账 CRUD/探测/连接构建全部组件与 MyBatis-Plus 映射器。
 * 独立于监控模块，供监控、采集等系统依赖复用。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.chua.starter.datasourcehub.support")
@MapperScan(basePackages = "com.chua.starter.datasourcehub.support.mapper")
@EnableConfigurationProperties(DatasourceHubProperties.class)
public class DatasourceHubAutoConfiguration {

    /**
     * 数据源中心的服务信息贡献者。
     *
     * @param properties 配置属性
     * @return ServiceInfoContributor
     */
    @Bean
    public ServiceInfoContributor datasourceHubServiceInfoContributor(DatasourceHubProperties properties) {
        return registry -> registry.registerFlag("datasource", "数据源中心", properties.isEnabled(),
                "数据源台账与连接中心",
                "[连接空闲 TTL: " + properties.getConnectionTtlMinutes() + "min]");
    }
}