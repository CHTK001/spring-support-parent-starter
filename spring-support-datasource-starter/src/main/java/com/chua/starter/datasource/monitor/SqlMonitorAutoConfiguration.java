package com.chua.starter.datasource.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.List;

/**
 * SQL 监听自动配置
 *
 * <p>启用后创建 {@link DataSourceSqlMonitor} 并注册为静态实例，
 * 之后通过 {@link DataSourceContextSupport#addDatasource} 注册的数据源将自动被包装监控。
 * 配置前缀 {@code plugin.datasource.monitor}。</p>
 *
 * <p><b>启用策略</b>：显式配置 {@code enabled} 时以配置为准；未配置时根据 Spring 环境
 * 自动切换——匹配 {@code autoEnableEnvironments}（默认 dev/test/uat/local）任一激活
 * profile 则启用，否则关闭。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SqlMonitorProperties.class)
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnClass(DataSource.class)
public class SqlMonitorAutoConfiguration implements EnvironmentAware {

    /**
     * Spring 环境
     */
    private Environment environment;

    /**
     * 注入 Spring 环境
     *
     * @param environment Spring 环境
     */
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /**
     * 创建 SQL 监控门面
     *
     * @param eventPublisher Spring 事件发布器
     * @param listeners      自定义 SQL 监听器
     * @param properties     监控配置
     * @return SQL 监控门面
     */
    @Bean
    @ConditionalOnMissingBean
    public DataSourceSqlMonitor dataSourceSqlMonitor(ApplicationEventPublisher eventPublisher,
                                                     ObjectProvider<List<SqlExecutionListener>> listeners,
                                                     SqlMonitorProperties properties) {
        boolean enabled = properties.resolveEnabled(environment);
        DataSourceSqlMonitor monitor = new DataSourceSqlMonitor(
                eventPublisher, listeners.getIfAvailable(), properties);
        monitor.setEnabled(enabled);
        DataSourceSqlMonitor.initialize(monitor);
        // 补包装容器早期（BDRPP）已注册的数据源
        DataSourceSqlMonitor.wrapExistingDataSources();
        log.info("[SqlMonitor] SQL 监听{}, 慢SQL阈值={}ms, 环境={}",
                enabled ? "已启用" : "未启用",
                properties.getSlowSqlThresholdMs(),
                environment.getActiveProfiles());
        return monitor;
    }

    /**
     * 创建 SQL 监听 Actuator 端点
     * <p>依赖 actuator 环境，当类路径存在 {@link Endpoint} 时注册。</p>
     *
     * @param monitor 监控门面
     * @return SQL 监听端点
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
    public SqlMonitorEndpoint sqlMonitorEndpoint(DataSourceSqlMonitor monitor) {
        return new SqlMonitorEndpoint(monitor);
    }
}
