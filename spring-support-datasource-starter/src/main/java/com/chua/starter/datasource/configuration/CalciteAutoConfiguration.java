package com.chua.starter.datasource.configuration;

import com.chua.common.support.table.schema.DataSourceSchema;
import com.chua.datasource.support.table.CalciteConnectorFactory;
import com.chua.starter.datasource.properties.CalciteDataSourceProperties;
import com.chua.starter.datasource.support.DataSourceContextSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Map;

import static com.chua.starter.common.support.logger.ModuleLog.highlight;

/**
 * Calcite数据源合并自动配置
 * <p>
 * 将多个数据源合并为一个虚拟数据源，支持跨库查询
 * 当classpath中存在CalciteConnectorFactory时自动启用
 * </p>
 *
 * @author CH
 * @since 2024/12/21
 */
@Slf4j
@Configuration
@ConditionalOnClass(CalciteConnectorFactory.class)
@ConditionalOnProperty(prefix = "spring.datasource.calcite", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CalciteDataSourceProperties.class)
public class CalciteAutoConfiguration {

    @Autowired
    private CalciteDataSourceProperties properties;

    /**
     * 创建Calcite合并数据源
     *
     * @return 合并后的数据源
     */
    @Bean("calciteDataSource")
    public DataSource calciteDataSource() {
        log.info("[Datasource] 开始创建Calcite合并数据源...");
        
        CalciteConnectorFactory factory = new CalciteConnectorFactory();
        
        // 获取所有已注册的数据源
        Map<String, DataSource> dataSources = DataSourceContextSupport.getAllDataSources();
        if (dataSources.isEmpty()) {
            throw new IllegalStateException("没有可用的数据源，请先配置数据源");
        }
        
        // 添加数据源到Calcite Schema
        for (Map.Entry<String, DataSource> entry : dataSources.entrySet()) {
            String schemaName = entry.getKey();
            DataSource dataSource = entry.getValue();
            
            // 如果配置了schema映射，使用映射的名称
            if (properties.getSchemaMapping() != null && properties.getSchemaMapping().containsKey(schemaName)) {
                schemaName = properties.getSchemaMapping().get(schemaName);
            }
            
            factory.addSchema(new DataSourceSchema(schemaName, dataSource));
            log.debug("添加数据源Schema: {}", schemaName);
        }
        
        DataSource dataSource = factory.getDataSource(properties.getMaxPoolSize());
        log.info("[Datasource] Calcite合并数据源创建完成, 共合并 {} 个数据源", highlight(dataSources.size()));
        
        return dataSource;
    }
}
