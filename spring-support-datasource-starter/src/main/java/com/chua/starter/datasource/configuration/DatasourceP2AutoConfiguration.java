package com.chua.starter.datasource.configuration;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.datasource.support.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * P2 功能自动配置
 * <p>
 * 注册以下功能 Bean：
 * <ul>
 *   <li><b>ENC 配置加密</b> — 默认 AES 密码解密器</li>
 *   <li><b>Actuator 健康检查</b> — 多数据源健康指示器</li>
 *   <li><b>优雅关闭</b> — 应用关闭时清理数据源连接池</li>
 * </ul>
 * </p>
 *
 * @since 2026/07/14
 * @author CH
 */
@AutoConfiguration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
public class DatasourceP2AutoConfiguration {

    // ==================== ENC 密码解密器 ====================

    /**
     * 注册默认 AES 密码解密器
     * @return 密码decryptor的结果
     */
    @Bean
    @ConditionalOnMissingBean(PasswordDecryptor.class)
    @ConditionalOnProperty(prefix = "plugin.datasource.enc", name = "enabled", havingValue = "true", matchIfMissing = false)
    public PasswordDecryptor passwordDecryptor() {
        /**
         * aes密码decryptor。
         * @return aes密码decryptor的结果
         */
        return new AesPasswordDecryptor();
    }

    // ==================== Actuator 健康检查 ====================

    /**
     * 数据源健康检查指示器
     * <p>
     * 仅在类路径存在 Spring Boot Actuator 及 {@code HealthIndicator} 时注册。
     * </p>
     * @return 数据源健康indicator的结果
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    @ConditionalOnProperty(prefix = "plugin.datasource.health", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DataSourceHealthIndicator dataSourceHealthIndicator() {
        /**
         * 数据源健康indicator。
         * @return 数据源健康indicator的结果
         */
        return new DataSourceHealthIndicator();
    }

    // ==================== 优雅关闭 ====================

    /**
     * 数据源优雅关闭监听器
     * <p>
     * 监听 Spring 容器的 {@code ContextClosedEvent}，在应用关闭时
     * 遍历所有已注册数据源并关闭其连接池。
     * </p>
     * @return graceful关闭监听器的结果
     */
    @Bean
    @ConditionalOnMissingBean(GracefulShutdownListener.class)
    @ConditionalOnProperty(prefix = "plugin.datasource.graceful-shutdown", name = "enabled", havingValue = "true", matchIfMissing = true)
    public GracefulShutdownListener gracefulShutdownListener() {
        /**
         * graceful关闭监听器。
         * @return graceful关闭监听器的结果
         */
        return new GracefulShutdownListener();
    }

    @Bean
    /**
     * datasource服务信息contributor
     *
     * @return datasource服务信息contributor的结果
     */
    public ServiceInfoContributor datasourceServiceInfoContributor() {
        return registry -> {
            registry.registerFlag("datasource", "多数据源", true, "多数据源与连接池管理模块");
        };
    }
}
