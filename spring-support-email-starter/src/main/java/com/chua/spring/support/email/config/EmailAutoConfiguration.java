package com.chua.spring.support.email.config;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * 邮箱管理自动配置
 * 
 * @author CH
 */
@AutoConfiguration
@ComponentScan("com.chua.spring.support.email")
public class EmailAutoConfiguration {

    @Bean
    public ServiceInfoContributor emailServiceInfoContributor() {
        return registry -> {
            registry.registerEndpoint("邮件", "邮件管理", "/email/*", "邮件管理");
            registry.registerFlag("email", "邮件", true, "邮件模块");
        };
    }
}
