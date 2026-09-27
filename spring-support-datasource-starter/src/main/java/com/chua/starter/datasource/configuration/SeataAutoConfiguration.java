package com.chua.starter.datasource.configuration;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.starter.datasource.support.DataSourceContextSupport;
import com.chua.starter.datasource.support.DynamicDataSource;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Seata 分布式事务自动配置
 * <p>
 * 当类路径下存在 Seata 的核心类时自动启用。
 * 为所有已注册的动态数据源自动包装 {@code DataSourceProxy}，
 * 以便 Seata AT 模式或 XA 模式能够拦截数据库操作。
 * </p>
 *
 * <p>
 * <b>前置条件：</b>用户需要在 pom.xml 中自行引入 Seata 依赖：
 * <pre>{@code
 * <dependency>
 *     <groupId>org.apache.seata</groupId>
 *     <artifactId>seata-spring-boot-starter</artifactId>
 *     <version>2.6.0</version>
 * </dependency>
 * }</pre>
 *
 * <p>
 * <b>配置：</b>
 * <pre>{@code
 * seata:
 *   enabled: true                    # 默认开启
 *   application-id: my-app
 *   tx-service-group: my_tx_group
 *   enable-auto-data-source-proxy: false  # 我们自行代理，避免冲突
 *   data-source-proxy-mode: AT       # AT 或 XA
 * }</pre>
 *
 * @since 2026/07/14
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnClass(name = {
        "io.seata.core.context.RootContext",
        "io.seata.rm.datasource.DataSourceProxy"
})
@ConditionalOnProperty(prefix = "seata", name = "enabled", havingValue = "true", matchIfMissing = true)
/**
 * seataauto配置类。
 *
 * @author CH
 * @since 4.0.0
 */
public class SeataAutoConfiguration {

    /**
     * 创建 Seata 数据源代理的初始化器
     * <p>
     * 用于在 {@link DynamicDataSource} 注册完成后，
     * 将其中每个实际的 {@link DataSource} 包装为 Seata 的
     * {@code DataSourceProxy}，从而支持 AT 模式的分布式事务。
     * </p>
     *
     * @return 数据源代理初始化器
     */
    @Bean
    @ConditionalOnMissingBean(name = "seataDataSourcePostProcessor")
    public SeataDataSourcePostProcessor seataDataSourcePostProcessor() {
        /**
         * Seata数据源post处理器。
         * @return seata数据源post处理器的结果
         */
        return new SeataDataSourcePostProcessor();
    }

    /**
     * Seata 数据源后处理器
     * <p>
     * 在 Spring 容器启动完成后，遍历所有已注册的动态数据源，
     * 将其包装为 Seata {@code DataSourceProxy}。
     * 使用反射创建以避免编译期对 Seata 的强制依赖。
     * </p>
     * @author CH
     * @since 4.0.0
     */
    @Slf4j
    public static class SeataDataSourcePostProcessor {

        /**
         * 初始化方法：包装所有动态数据源
         * <p>
         * 在 Spring 容器启动后自动执行，将每个已注册的动态数据源包装为
         * Seata {@code DataSourceProxy}，支持 AT/XA 分布式事务模式。
         * 使用反射避免编译期对 Seata 的强制依赖。
         * </p>
         */
        @PostConstruct
        public void wrapDataSources() {
            // 对当前已注册的数据源做快照，避免并发修改问题
            Map<String, DataSource> allDataSources = new java.util.HashMap<>(DataSourceContextSupport.getAllDataSources());
            if (allDataSources.isEmpty()) {
                log.warn("[Seata] 没有找到已注册的数据源，跳过代理包装");
                return;
            }

            int wrapped = 0;
            for (Map.Entry<String, DataSource> entry : allDataSources.entrySet()) {
                String dsName = entry.getKey();
                DataSource dataSource = entry.getValue();

                // 跳过已经代理过的数据源
                if (isAlreadyProxied(dataSource)) {
                    log.debug("[Seata] 数据源 '{}' 已经代理，跳过", dsName);
                    continue;
                }

                try {
 // 使用反射创建 数据源代理，避免编译期依赖
                    DataSource proxy = createDataSourceProxy(dataSource);
                    // 替换原数据源
                    DataSourceContextSupport.addDatasource(dsName, proxy);
                    log.info("[Seata] 数据源 '{}' 已包装为 Seata DataSourceProxy", dsName);
                    wrapped++;
                } catch (Exception e) {
                    log.warn("[Seata] 包装数据源 '{}' 失败: {}", dsName, e.getMessage());
                }
            }
            log.info("[Seata] Seata 数据源代理初始化完成，共包装 {} 个数据源", wrapped);
        }

        /**
         * 通过反射创建 Seata 数据源代理
         * @param dataSource 数据源
         * @return 创建数据源代理的结果
         */
        @SuppressWarnings("unchecked")
        private DataSource createDataSourceProxy(DataSource dataSource) throws Exception {
            Class<?> proxyClass = ReflectUtils.forName("io.seata.rm.datasource.DataSourceProxy");
            return (DataSource) ReflectUtils.instantiate(proxyClass, dataSource);
        }

        /**
         * 检查数据源是否已经被 Seata 代理
         * @param dataSource 数据源
         * @return 是否alreadyproxied的结果
         */
        private boolean isAlreadyProxied(DataSource dataSource) {
            Class<?> proxyClass = ReflectUtils.forName("io.seata.rm.datasource.DataSourceProxy");
            if (proxyClass == null) {
                return false;
            }
            return proxyClass.isInstance(dataSource);
        }
    }
}
