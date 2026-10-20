package com.chua.starter.datasource.configuration;

import com.chua.calcite.support.conversion.CalciteDataSourceConversion;
import com.chua.datasource.support.engine.DataSourceConversion;
import com.chua.datasource.support.engine.DataSourceEnvironment;
import com.chua.starter.datasource.properties.CalciteDataSourceProperties;
import com.chua.starter.datasource.support.DataSourceContextSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
 * {@link DataSourceConversion} 类型的 Bean 来覆盖默认行为。
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
     * 创建 Calcite 聚合数据源 Bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "calciteDataSource")
    public DataSource calciteDataSource(
            DataSourceConversion calciteDataSourceConversion,
            CalciteDataSourceProperties properties) {

        List<DataSource> dataSources = collectDataSources();
        if (dataSources.isEmpty()) {
            log.warn("[Calcite] 没有找到已注册的数据源，跳过聚合");
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
     * 收集所有已注册的数据源（过滤动态路由数据源和监控包装数据源）
     * @return collect数据源的结果
     */
    private List<DataSource> collectDataSources() {
        Map<String, DataSource> allDataSources = DataSourceContextSupport.getAllDataSources();
        List<DataSource> result = new ArrayList<>();

        for (Map.Entry<String, DataSource> entry : allDataSources.entrySet()) {
            String name = entry.getKey();
            DataSource ds = entry.getValue();

            // 跳过聚合数据源自身
            if ("calciteDataSource".equals(name)) {
                continue;
            }

            // 跳过动态路由数据源
            if (ds instanceof org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource) {
                continue;
            }

            result.add(ds);
        }

        return result;
    }
}
