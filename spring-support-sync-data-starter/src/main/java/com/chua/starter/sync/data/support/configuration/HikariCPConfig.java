package com.chua.starter.sync.data.support.configuration;

import com.chua.starter.sync.data.support.properties.SyncProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * HikariCP连接池优化配置
 *
 * @author System
 * @since 2026/03/09
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = SyncProperties.PRE, name = "enabled", havingValue = "true", matchIfMissing = true)
public class HikariCPConfig {

    private final SyncProperties syncProperties;

    @PostConstruct
    public void init() {
        log.info("HikariCP连接池优化配置已加载");
        log.info("建议配置项:");
        log.info("  spring.datasource.hikari.maximum-pool-size=20");
        log.info("  spring.datasource.hikari.minimum-idle=5");
        log.info("  spring.datasource.hikari.connection-timeout=30000");
        log.info("  spring.datasource.hikari.idle-timeout=600000");
        log.info("  spring.datasource.hikari.max-lifetime=1800000");
        log.info("  spring.datasource.hikari.connection-test-query=SELECT 1");
        log.info("  spring.datasource.hikari.leak-detection-threshold=60000");
    }

    /**
     * 获取推荐的HikariCP配置
     */
    public static HikariConfig getRecommendedConfig() {
        HikariConfig config = new HikariConfig();
        
        // 连接池大小
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        
        // 超时配置
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        
        // 连接测试
        config.setConnectionTestQuery("SELECT 1");
        
        // 泄漏检测
        config.setLeakDetectionThreshold(60000);
        
        // 性能优化
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        return config;
    }
}
