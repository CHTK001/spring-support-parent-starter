package com.chua.starter.rust.server.configuration;

import com.chua.starter.rust.server.properties.RustServerProperties;
import com.chua.starter.rust.server.server.RustServletWebServerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import jakarta.servlet.Servlet;

/**
 * Rust HTTP Server 自动配置
 * <p>
 * 当配置 plugin.rust-server.enabled=true 时，自动创建 Rust HTTP Server 作为 Servlet 容器，
 * 替代默认的 Tomcat/Undertow/Jetty。
 * </p>
 *
 * <h3>使用方式：</h3>
 * <pre>
 * # application.yml
 * plugin:
 *   rust-server:
 *     enabled: true
 * </pre>
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@AutoConfiguration(before = ServletWebServerFactoryAutoConfiguration.class)
@ConditionalOnClass(Servlet.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = RustServerProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties({RustServerProperties.class, ServerProperties.class})
public class RustServerAutoConfiguration {

    public RustServerAutoConfiguration() {
        log.info("[Rust Server] 自动配置已加载");
    }

    /**
     * 创建 Rust Servlet Web Server 工厂
     * <p>
     * 替代默认的 TomcatServletWebServerFactory
     * </p>
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(ServletWebServerFactory.class)
    public RustServletWebServerFactory rustServletWebServerFactory(
            RustServerProperties rustServerProperties,
            ServerProperties serverProperties) {
        log.info("[Rust Server] 创建 RustServletWebServerFactory");
        return new RustServletWebServerFactory(rustServerProperties, serverProperties);
    }
}
