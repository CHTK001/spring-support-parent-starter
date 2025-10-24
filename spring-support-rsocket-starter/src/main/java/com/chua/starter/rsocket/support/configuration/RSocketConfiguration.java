package com.chua.starter.rsocket.support.configuration;

import com.chua.starter.rsocket.support.auth.DefaultRSocketAuthFactory;
import com.chua.starter.rsocket.support.auth.RSocketAuthFactory;
import com.chua.starter.rsocket.support.properties.RSocketProperties;
import com.chua.starter.rsocket.support.resolver.DefaultRSocketSessionResolver;
import com.chua.starter.rsocket.support.resolver.RSocketSessionResolver;
import com.chua.starter.rsocket.support.server.DelegateRSocketServer;
import com.chua.starter.rsocket.support.session.DefaultRSocketSessionTemplate;
import com.chua.starter.rsocket.support.session.RSocketSessionTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RSocket自动配置类
 * <p>
 * 提供RSocket的Spring Boot自动配置
 * 
 * @author CH
 * @version 4.0.0.34
 * @since 2024/10/24
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(RSocketProperties.class)
@ConditionalOnProperty(prefix = RSocketProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = false)
public class RSocketConfiguration {

    /**
     * 创建RSocket会话模板Bean
     * 
     * @return RSocket会话模板实例
     */
    @Bean
    @ConditionalOnMissingBean
    public RSocketSessionTemplate rsocketSessionTemplate() {
        log.info("初始化RSocketSessionTemplate");
        return new DefaultRSocketSessionTemplate();
    }

    /**
     * 创建RSocket会话解析器Bean
     * 
     * @return RSocket会话解析器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public RSocketSessionResolver rsocketSessionResolver() {
        log.info("初始化RSocketSessionResolver");
        return new DefaultRSocketSessionResolver();
    }

    /**
     * 创建RSocket认证工厂Bean
     * 
     * @return RSocket认证工厂实例
     */
    @Bean
    @ConditionalOnMissingBean
    public RSocketAuthFactory rsocketAuthFactory() {
        log.info("初始化DefaultRSocketAuthFactory");
        return new DefaultRSocketAuthFactory();
    }

    /**
     * 创建RSocket服务器Bean
     * 
     * @param properties        RSocket配置属性
     * @param sessionTemplate   会话模板
     * @param sessionResolver   会话解析器
     * @param authFactory       认证工厂
     * @return RSocket服务器实例
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    public DelegateRSocketServer delegateRSocketServer(
            RSocketProperties properties,
            RSocketSessionTemplate sessionTemplate,
            RSocketSessionResolver sessionResolver,
            RSocketAuthFactory authFactory) {
        
        log.info("初始化DelegateRSocketServer: host={}, port={}", 
                properties.getHost(), properties.getPort());
        
        return new DelegateRSocketServer(properties, sessionTemplate, sessionResolver, authFactory);
    }
}

