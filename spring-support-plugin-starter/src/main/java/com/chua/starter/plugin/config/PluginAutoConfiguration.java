package com.chua.starter.plugin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 插件模块自动配置
 * 
 * @author CH
 * @since 2025/1/16
 */
@Slf4j
@Configuration
@ComponentScan(basePackages = "com.chua.starter.plugin")
@EnableConfigurationProperties({
    RateLimitConfiguration.class,
    SqliteConfiguration.SqliteProperties.class
})
@Import({
    SqliteConfiguration.class,
    RateLimitConfiguration.class,
    RateLimitWebConfiguration.class
})
@ConditionalOnProperty(prefix = "plugin", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PluginAutoConfiguration {

    public PluginAutoConfiguration() {
        log.info("Plugin module auto configuration initialized");
        log.info("Features enabled:");
        log.info("  - SQLite database support");
        log.info("  - Rate limiting with annotation support");
        log.info("  - Memory cache management");
        log.info("  - Real-time configuration updates");
    }
}
