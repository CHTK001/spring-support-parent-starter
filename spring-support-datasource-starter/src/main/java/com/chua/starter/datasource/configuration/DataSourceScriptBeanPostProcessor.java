package com.chua.starter.datasource.configuration;

import com.chua.starter.datasource.properties.DataSourceScriptProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 数据源脚本 Bean 后处理器。
 *
 * <p>在 {@link DataSource} 初始化完成后立即执行数据库脚本，确保脚本先于
 * 依赖数据库的 Bean（实体管理器工厂、SqlSessionFactory 等）就绪。</p>
 *
 * <p><b>为什么单独成类</b>：{@link DataSourceScriptConfiguration} 是
 * {@code @AutoConfiguration} 自动配置类，若它自己同时实现 {@link BeanPostProcessor}，
 * 容器会把「自动配置类实例」与「{@code @Bean} 暴露的实例」注册成两个 bean，
 * 脚本可能执行两次。拆开后职责单一：配置类负责装配，处理器负责触发。</p>
 *
 * @author CH
 */
@Slf4j
public class DataSourceScriptBeanPostProcessor implements BeanPostProcessor {

    /**
     * 数据源脚本配置属性
     */
    private final DataSourceScriptProperties dataSourceScriptProperties;

    /**
     * 已处理的数据源 Bean 名称集合，避免重复执行
     */
    private final Set<String> processedDataSources = Collections.synchronizedSet(new HashSet<>());

    /**
     * 已提示过禁用状态的数据源 Bean 名称集合，避免重复打印日志
     */
    private final Set<String> skippedDataSources = Collections.synchronizedSet(new HashSet<>());

    /**
     * 创建数据源脚本处理器
     *
     * @param dataSourceScriptProperties 数据源脚本配置属性
     */
    public DataSourceScriptBeanPostProcessor(DataSourceScriptProperties dataSourceScriptProperties) {
        this.dataSourceScriptProperties = dataSourceScriptProperties;
    }

    @Override
    /**
     * Post处理之后initialization
     */
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource) {
            if (!dataSourceScriptProperties.isEnable()) {
                if (skippedDataSources.add(beanName)) {
                    log.info("检测到 DataSource [{}] 初始化完成，但 [{}]=false，已跳过数据库脚本执行",
                            beanName, DataSourceScriptProperties.PRE + ".enable");
                }
                return bean;
            }

            DataSource ds = (DataSource) bean;
            // 避免重复处理同一个数据源
            if (processedDataSources.add(beanName)) {
                try {
                    log.info("检测到 DataSource [{}] 初始化完成，开始执行数据库脚本...", beanName);
                    // 优先通过 SPI 获取 Flyway 高优先级实现（utils-support-flyway-starter 的 数据源Flyway）；
                    // 获取成功则委托其迁移，否则回退到内置 flywaylikepopulator。
                    DataSourceScriptConfiguration delegate = new DataSourceScriptConfiguration(dataSourceScriptProperties);
                    if (delegate.tryMigrateWithSpiFlyway(ds, beanName)) {
                        log.info("DataSource [{}] 数据库脚本执行完成（SPI Flyway）", beanName);
                    } else {
                        DataSourceScriptConfiguration.FlywayLikePopulator populator =
                                new DataSourceScriptConfiguration.FlywayLikePopulator(ds, dataSourceScriptProperties);
                        populator.populate();
                        log.info("DataSource [{}] 数据库脚本执行完成", beanName);
                    }
                } catch (Exception e) {
                    log.warn("执行数据库脚本失败: {}", beanName, e);
                    if (!dataSourceScriptProperties.isContinueOnError()) {
                        /**
                         * illegal状态异常。
                         *
                         * @param beanName Bean 名称
                         * @param e 异常
                         * @return illegal状态异常的结果
                         */
                        throw new IllegalStateException("执行数据库脚本失败: " + beanName, e);
                    }
                }
            }
        }
        return bean;
    }
}
