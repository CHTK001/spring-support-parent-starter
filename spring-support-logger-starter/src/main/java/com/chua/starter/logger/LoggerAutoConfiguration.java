// [P3C A20 豁免] A20/A30/A31: 含日志/地址字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.logger;

import com.chua.common.support.network.discovery.ServiceDiscovery;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.logger.appender.LoggerAppenderLifecycle;
import com.chua.starter.logger.client.LoggerEventBatcher;
import com.chua.starter.logger.client.LoggerEventCollector;
import com.chua.starter.logger.client.TcpLoggerTransport;
import com.chua.starter.logger.properties.LoggerProperties;
import com.chua.starter.logger.server.LoggerController;
import com.chua.starter.logger.server.LoggerSseBroker;
import com.chua.starter.logger.server.LoggerTcpServer;
import com.chua.starter.logger.server.LoggerTraceRegistry;
import com.chua.starter.logger.store.LoggerEventBus;
import com.chua.starter.logger.store.LoggerEventStore;
import com.chua.starter.logger.store.LoggerStores;
import com.chua.starter.logger.store.LoggerTraceStateStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;

/**
 * 日志采集模块自动装配：按 {@code plugin.logger.type} 装配客户端（Appender 采集 + 批量推送）、
 * 服务端（TCP 接收 + 存储 + REST/SSE）或两者（both，单进程闭环，寻址走 127.0.0.1 回环）。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(LoggerProperties.class)
public class LoggerAutoConfiguration {

    /**
     * 客户端角色判定表达式（client 或 both）
     */
    static final String CLIENT_CONDITION =
            "'${plugin.logger.type:client}'.equalsIgnoreCase('client') || '${plugin.logger.type:client}'.equalsIgnoreCase('both')";

    /**
     * 服务端角色判定表达式（server 或 both）
     */
    static final String SERVER_CONDITION =
            "'${plugin.logger.type:client}'.equalsIgnoreCase('server') || '${plugin.logger.type:client}'.equalsIgnoreCase('both')";

    /**
     * 解析应用名：plugin.logger.app-name → spring.application.name → unknown。
     *
     * @param properties  模块配置
     * @param environment 容器环境
     * @return 应用名
     */
    public static String resolveAppName(LoggerProperties properties, Environment environment) {
        if (StringUtils.isNotBlank(properties.getAppName())) {
            return properties.getAppName().trim();
        }
        String appName = environment.getProperty("spring.application.name");
        return StringUtils.isBlank(appName) ? "unknown" : appName.trim();
    }

    /**
     * 实例标识：pid@host（JVM 维度）。
     *
     * @return 实例标识
     */
    public static String instanceId() {
        try {
            return ManagementFactory.getRuntimeMXBean().getName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 客户端角色装配
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnExpression(CLIENT_CONDITION)
    static class LoggerClientConfiguration {

        /**
         * 采集队列：Appender 唯一出口
         */
        @Bean
        public LoggerEventCollector loggerEventCollector(LoggerProperties properties) {
            return new LoggerEventCollector(properties.getClient().getQueueCapacity());
        }

        /**
         * 传输通道：发现优先，server-url / both 回环兜底
         */
        @Bean(destroyMethod = "close")
        public TcpLoggerTransport loggerEventTransport(LoggerProperties properties, Environment environment,
                                                       ObjectProvider<ServiceDiscovery> discoveryProvider) {
            String appName = resolveAppName(properties, environment);
            ServiceDiscovery discovery = discoveryProvider.getIfAvailable();
            String fallback = properties.getClient().getServerUrl();
            String type = properties.getType() == null ? "client" : properties.getType().trim().toLowerCase();
            if ("both".equals(type) && StringUtils.isBlank(fallback)) {
                // 同进程闭环：直连本进程接收端
                fallback = "tcp://127.0.0.1:" + properties.getServer().getPort();
            }
            String scatterId = properties.getClient().getScatterId();
            String effectiveScatterId = StringUtils.isBlank(scatterId)
                    ? properties.getClient().getServiceName() : scatterId;
            return new TcpLoggerTransport(appName + ":" + instanceId(), discovery,
                    properties.getClient().getServiceName(), effectiveScatterId, fallback);
        }

        /**
         * 批量推送线程
         */
        @Bean
        public LoggerEventBatcher loggerEventBatcher(LoggerProperties properties, Environment environment,
                                                     LoggerEventCollector collector,
                                                     TcpLoggerTransport loggerEventTransport) {
            return new LoggerEventBatcher(resolveAppName(properties, environment), instanceId(),
                    collector, loggerEventTransport,
                    properties.getClient().getBatchSize(), properties.getClient().getFlushIntervalMs());
        }

        /**
         * Appender 安装编排（SmartLifecycle）
         */
        @Bean
        public LoggerAppenderLifecycle loggerAppenderLifecycle(LoggerProperties properties,
                                                               LoggerEventCollector collector) {
            return new LoggerAppenderLifecycle(properties, collector);
        }
    }

    /**
     * 服务端角色装配
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnExpression(SERVER_CONDITION)
    static class LoggerServerConfiguration {

        /**
         * 事件总线
         */
        @Bean
        public LoggerEventBus loggerEventBus() {
            return new LoggerEventBus();
        }

        /**
         * 日志存储（datasource-bean | memory | redis-bean）
         */
        @Bean
        public LoggerEventStore loggerEventStore(LoggerProperties properties,
                                                 ObjectProvider<DataSource> dataSourceProvider) {
            DataSource dataSource = dataSourceProvider.getIfAvailable();
            return LoggerStores.create(properties.getStorage().getEngine(),
                    properties.getStorage().getMemoryCapacity(),
                    properties.getStorage().getRetentionHours(), dataSource);
        }

        /**
         * SSE 推送：订阅总线，每条事件实时转发给订阅者
         */
        @Bean
        public LoggerSseBroker loggerSseBroker(LoggerEventBus loggerEventBus, LoggerEventStore loggerEventStore) {
            LoggerSseBroker broker = new LoggerSseBroker(15L, loggerEventStore);
            loggerEventBus.subscribe(broker::broadcast);
            return broker;
        }

        /** SDK trace 生命周期登记与频控。 */
        @Bean
        public LoggerTraceRegistry loggerTraceRegistry(LoggerEventStore loggerEventStore,
                                                       LoggerEventBus loggerEventBus,
                                                       LoggerTraceStateStore loggerTraceStateStore,
                                                       LoggerProperties properties) {
            return new LoggerTraceRegistry(loggerEventStore, loggerEventBus, loggerTraceStateStore, properties);
        }

        /** SDK trace 生命周期状态：优先 JDBC/Redis，缺少共享 KV 时落本地磁盘。 */
        @Bean
        public LoggerTraceStateStore loggerTraceStateStore(LoggerProperties properties,
                                                           ObjectProvider<DataSource> dataSourceProvider) {
            return LoggerStores.createTraceStateStore(properties.getStorage().getEngine(),
                    properties.getStorage().getTraceStatePath(), dataSourceProvider.getIfAvailable());
        }

        /**
         * TCP 接收端（SmartLifecycle）
         */
        @Bean
        public LoggerTcpServer loggerTcpServer(LoggerProperties properties, LoggerEventBus loggerEventBus,
                                               LoggerEventStore loggerEventStore,
                                               ObjectProvider<ServiceDiscovery> discoveryProvider) {
            return new LoggerTcpServer(properties, loggerEventBus, loggerEventStore,
                    discoveryProvider.getIfAvailable());
        }

        /**
         * 检索 API（仅 Servlet Web 环境）
         */
        @Bean
        @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
        public LoggerController loggerController(LoggerEventStore loggerEventStore,
                                                  LoggerSseBroker loggerSseBroker,
                                                  LoggerEventBus loggerEventBus,
                                                  LoggerTraceRegistry loggerTraceRegistry,
                                                  LoggerProperties properties,
                                                  Environment environment) {
            return new LoggerController(loggerEventStore, loggerSseBroker, loggerEventBus,
                    loggerTraceRegistry, properties, environment);
        }
    }
}
