package com.chua.starter.server.support.acme.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * ACME 证书生命周期管理的独立装配入口。
 *
 * <p>该配置由 server starter 注册，不依赖 monitor 的组件扫描；业务控制器、服务、持久层
 * 和续签任务随 server 模块一起装配。证书页面兼容地址继续由路径别名过滤器映射到原控制器。</p>
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

    /**
     * 将 ACME 页面现有的外部地址前缀改写为模块内控制器路径。
     *
     * @return 路径别名过滤器注册
     */
    @Bean
    public FilterRegistrationBean<AcmePathAliasFilter> acmePathAliasFilterRegistration() {
        FilterRegistrationBean<AcmePathAliasFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AcmePathAliasFilter());
        registration.setName("acmePathAliasFilter");
        registration.addUrlPatterns(AcmePathAliasFilter.ALIAS_PREFIX + "/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
