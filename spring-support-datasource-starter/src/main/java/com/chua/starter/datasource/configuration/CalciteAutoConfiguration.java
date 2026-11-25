package com.chua.starter.datasource.configuration;

import com.chua.calcite.support.conversion.CalciteDataSourceConversion;
import com.chua.datasource.support.engine.DataSourceConversion;
import com.chua.datasource.support.engine.DataSourceEnvironment;
import com.chua.starter.datasource.properties.CalciteDataSourceProperties;
import com.chua.starter.datasource.support.DataSourceContextSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calcite 聚合数据源自动配置。
 * <p>
 * 当 {@code plugin.datasource.calcite.enabled=true} 时，
 * 自动将所有已注册的动态数据源聚合为统一的 Calcite 数据源，
 * 支持跨库联邦查询。
 * </p>
 *
 * <p>
 * 用户可自行注册 {@link CalciteDataSourceConversion} 或
 * {@link DataSourceConversion} 类型的 Bean 来覆盖默认行为；
 * {@code plugin.datasource.calcite.bean-name} 可为聚合数据源追加注入别名，
 * {@code plugin.datasource.calcite.data-sources} 可限定参与聚合的数据源名单。
 * </p>
 *
 * @since 2026/07/14
 * @author CH
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(CalciteDataSourceConversion.class)
@ConditionalOnProperty(prefix = "plugin.datasource.calcite", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CalciteDataSourceProperties.class)
public class CalciteAutoConfiguration {

    /**
     * 聚合数据源的规范 Bean 名。运行期联邦管理器（CalciteFederationManager.BEAN_NAME）、
     * 热替换注册表与 {@code @ConditionalOnMissingBean} 覆盖语义都依赖该名称，不可更改；
     * 自定义 {@code bean-name} 只能以别名形式注册。
     */
    private static final String CANONICAL_BEAN_NAME = "calciteDataSource";

    /**
     * 注册 Calcite 数据源转换器 Bean
     * @return calcite数据源转换的结果
     */
    @Bean
    @ConditionalOnMissingBean(name = "calciteDataSourceConversion")
    public DataSourceConversion calciteDataSourceConversion() {
        /**
         * calcite数据源转换。
         * @return calcite数据源转换的结果
         */
        return new CalciteDataSourceConversion();
    }

    /**
     * 把配置的 {@code plugin.datasource.calcite.bean-name} 注册为规范 Bean 的别名。
     *
     * <p>静态 {@code @Bean} + {@link BeanFactoryPostProcessor}：别名注册发生在
     * Bean 定义加载之后、实例化之前，不会提前实例化本配置类。</p>
     *
     * @param properties Calcite 数据源配置
     * @return 别名注册处理器
     */
    @Bean
    public static BeanFactoryPostProcessor calciteBeanNameAliasPostProcessor(CalciteDataSourceProperties properties) {
        return beanFactory -> {
            String customName = properties.getBeanName();
            if (customName == null || customName.isBlank()
                    || !(beanFactory instanceof ConfigurableListableBeanFactory registry)) {
                return;
            }
            String alias = customName.trim();
            if (CANONICAL_BEAN_NAME.equals(alias) || registry.containsBeanDefinition(alias)) {
                return;
            }
            try {
                registry.registerAlias(CANONICAL_BEAN_NAME, alias);
                log.info("[Calcite] 聚合数据源 Bean 别名注册成功: {} -> {}", alias, CANONICAL_BEAN_NAME);
            } catch (IllegalStateException e) {
                log.warn("[Calcite] 聚合数据源 Bean 别名 {} 注册失败: {}", alias, e.getMessage());
            }
        };
    }

    /**
     * 创建 Calcite 聚合数据源 Bean。
     *
     * @param calciteDataSourceConversion Calcite 数据源转换器
     * @param properties                  Calcite 数据源配置
     * @return 聚合数据源；未注册数据源或创建失败返回 {@code null}
     */
    @Bean
    @ConditionalOnMissingBean(name = "calciteDataSource")
    public DataSource calciteDataSource(
            DataSourceConversion calciteDataSourceConversion,
            CalciteDataSourceProperties properties) {

        List<DataSource> dataSources = collectDataSources(properties);
        if (dataSources.isEmpty()) {
            log.warn("[Calcite] 没有找到可聚合的数据源，跳过聚合");
            return null;
        }

        DataSourceEnvironment environment = new DataSourceEnvironment();
        environment.setDatabaseType("CALCITE");

        try {
            DataSource calciteDataSource = calciteDataSourceConversion.convert(dataSources, environment);
            log.info("[Calcite] 聚合数据源创建成功，已聚合 {} 个数据源", dataSources.size());
            return calciteDataSource;
        } catch (Exception e) {
            log.error("[Calcite] 创建聚合数据源失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 收集参与聚合的数据源。
     *
     * <p>未配置 {@code data-sources} 时聚合全部已注册数据源（过滤动态路由数据源与
     * 聚合数据源自身）；配置后只聚合名单内的数据源，名单顺序不改变 schema 编号
     * （schema 顺序仍按注册表迭代序），名单中未注册的名称告警跳过。</p>
     *
     * @param properties Calcite 数据源配置
     * @return collect数据源的结果
     */
    private List<DataSource> collectDataSources(CalciteDataSourceProperties properties) {
        Map<String, DataSource> allDataSources = DataSourceContextSupport.getAllDataSources();
        List<DataSource> result = new ArrayList<>();
        String customBeanName = properties.getBeanName() == null ? null : properties.getBeanName().trim();

        List<String> includeNames = properties.getDataSources();
        if (includeNames == null || includeNames.isEmpty()) {
            for (Map.Entry<String, DataSource> entry : allDataSources.entrySet()) {
                String name = entry.getKey();
                DataSource ds = entry.getValue();

                // 跳过聚合数据源自身（规范名与配置的自定义名）
                if (CANONICAL_BEAN_NAME.equals(name) || name.equals(customBeanName)) {
                    continue;
                }

                // 跳过动态路由数据源
                if (ds instanceof AbstractRoutingDataSource) {
                    continue;
                }

                result.add(ds);
            }
            return result;
        }

        Set<String> wanted = new LinkedHashSet<>();
        for (String item : includeNames) {
            if (item != null && !item.isBlank()) {
                wanted.add(item.trim());
            }
        }
        if (wanted.isEmpty()) {
            log.warn("[Calcite] data-sources 配置为空名单，跳过聚合");
            return result;
        }
        for (Map.Entry<String, DataSource> entry : allDataSources.entrySet()) {
            String name = entry.getKey();
            DataSource ds = entry.getValue();
            if (!wanted.contains(name)) {
                continue;
            }
            if (CANONICAL_BEAN_NAME.equals(name) || name.equals(customBeanName)) {
                log.warn("[Calcite] data-sources 包含聚合数据源自身: {}，已跳过", name);
                continue;
            }
            if (ds instanceof AbstractRoutingDataSource) {
                log.warn("[Calcite] data-sources 包含动态路由数据源: {}，已跳过", name);
                continue;
            }
            result.add(ds);
        }
        for (String name : wanted) {
            if (!allDataSources.containsKey(name)) {
                log.warn("[Calcite] data-sources 中的数据源未注册: {}", name);
            }
        }
        if (result.isEmpty()) {
            log.warn("[Calcite] data-sources 名单均未命中已注册数据源，跳过聚合");
        }
        return result;
    }
}
