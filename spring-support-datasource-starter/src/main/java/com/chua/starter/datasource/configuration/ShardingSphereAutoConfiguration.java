package com.chua.starter.datasource.configuration;

import com.chua.sharding.v5.support.builder.ShardingDataSourceBuilder;
import com.chua.starter.datasource.properties.ShardingDataSourceProperties;
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

/**
 * ShardingSphere 5.x 分库分表自动配置
 * <p>
 * 当classpath中存在ShardingDataSourceBuilder时自动启用
 * </p>
 *
 * @author CH
 * @since 2024/12/21
 */
@Slf4j
@Configuration
@ConditionalOnClass(ShardingDataSourceBuilder.class)
@ConditionalOnProperty(prefix = "spring.datasource.sharding", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ShardingDataSourceProperties.class)
public class ShardingSphereAutoConfiguration {

    @Autowired
    private ShardingDataSourceProperties properties;

    /**
     * 创建分片数据源
     *
     * @return 分片数据源
     */
    @Bean("shardingDataSource")
    public DataSource shardingDataSource() {
        log.info("开始创建ShardingSphere分片数据源...");
        
        ShardingDataSourceBuilder builder = ShardingDataSourceBuilder.newBuilder();
        
        // 添加数据源
        Map<String, DataSource> dataSources = DataSourceContextSupport.getAllDataSources();
        if (dataSources.isEmpty()) {
            throw new IllegalStateException("没有可用的数据源，请先配置数据源");
        }
        builder.dataSources(dataSources);
        
        // 配置分表规则
        if (properties.getTables() != null) {
            for (ShardingDataSourceProperties.ShardingTableConfig tableConfig : properties.getTables()) {
                ShardingDataSourceBuilder.TableShardingBuilder tableBuilder = builder.shardingTable(tableConfig.getLogicTable());
                tableBuilder.shardingColumn(tableConfig.getShardingColumn());
                
                // 配置时间分片
                if (tableConfig.getTimeUnit() != null) {
                    switch (tableConfig.getTimeUnit().toUpperCase()) {
                        case "YEAR" -> tableBuilder.shardByYear();
                        case "QUARTER" -> tableBuilder.shardByQuarter();
                        case "MONTH" -> tableBuilder.shardByMonth();
                        case "WEEK" -> tableBuilder.shardByWeek();
                        case "DAY" -> tableBuilder.shardByDay();
                        case "HOUR" -> tableBuilder.shardByHour();
                    }
                }
                
                tableBuilder.done();
            }
        }
        
        // 配置分库规则
        if (properties.getDatabases() != null) {
            for (String database : properties.getDatabases()) {
                builder.shardingDatabase(database);
            }
        }
        
        // 其他配置
        builder.showSql(properties.isShowSql());
        
        DataSource dataSource = builder.build();
        log.info("ShardingSphere分片数据源创建完成");
        
        return dataSource;
    }
}
