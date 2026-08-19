package com.chua.starter.software.support;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * 软件管理配置
 *
 * @since 2026/7/25
 * @author CH
 */
@Slf4j
@AutoConfiguration
@MapperScan("com.chua.starter.software.support.mapper")
@ComponentScan("com.chua.starter.software.support")
public class SoftwareConfiguration {

    @Bean
    /** SoftwareServiceInfoContributor */
    public ServiceInfoContributor softwareServiceInfoContributor() {
        return registry -> {
            registry.registerFlag("software", "软件管理", true, "软件管理模块");
        };
    }
}