package com.chua.starter.datasource.configuration;

import com.chua.shardingv5.support.conversion.ShardingV5Conversion;
import com.chua.datasource.support.engine.DataSourceConversion;
import com.chua.datasource.support.engine.DataSourceEnvironment;
import com.chua.starter.datasource.properties.ShardingDataSourceProperties;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
* 分库分表sphere 分库分表自动配置。
* <p>
* 当 {@code spring.datasource.sharding.enabled=true} 时，
* 自动基于配置创建 分库分表sphere 分片数据源。
* </p>
*
* <p>
* 用户可自行注册 {@link ShardingV5Conversion} 或
* {@link DataSourceConversion} 类型的 Bean 来覆盖默认行为。
* </p>
*
* @since 2026/07/14
* @author CH
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(ShardingV5Conversion.class)
@ConditionalOnProperty(prefix = "spring.datasource.sharding", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ShardingDataSourceProperties.class)
public class ShardingAutoConfiguration {

    /**
    * 注册 分库分表v5 数据源转换器 Bean
    * @return 分库分表v5转换的结果
    */
    @Bean
    @ConditionalOnMissingBean(name = "shardingV5Conversion")
    public DataSourceConversion shardingV5Conversion() {
        /**
        * 分库分表v5转换。
        * @return 分库分表v5转换的结果
        */
        return new ShardingV5Conversion();
    }

    /**
    * 创建 分库分表sphere 分片数据源 Bean
    */
    @Bean
    @ConditionalOnMissingBean(name = "shardingDataSource")
    public DataSource shardingDataSource(
            DataSourceConversion shardingV5Conversion,
            ShardingDataSourceProperties properties) {

        List<DataSource> dataSources = collectDataSources();
        if (dataSources.isEmpty()) {
            log.warn("[Sharding] 没有找到已注册的数据源，跳过分片配置");
            return null;
        }

        try {
            ShardingV5Conversion conversion = configureSharding(shardingV5Conversion, properties);
            DataSourceEnvironment environment = new DataSourceEnvironment();
            environment.setDatabaseType("ShardingSphere");

            DataSource shardingDataSource = conversion.convert(dataSources, environment);
            log.info("[Sharding] 分片数据源创建成功，已配置 {} 个数据源", dataSources.size());
            return shardingDataSource;
        } catch (Exception e) {
            log.error("[Sharding] 创建分片数据源失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
    * 根据配置构建 分库分表v5转换
    * @param conversion 转换
    * @param properties 属性
    * @return configure分库分表的结果
    */
    private ShardingV5Conversion configureSharding(DataSourceConversion conversion, ShardingDataSourceProperties properties) {
        if (!(conversion instanceof ShardingV5Conversion)) {
            log.warn("[Sharding] 转换器不是 ShardingV5Conversion 类型，使用默认配置");
            /**
            * 分库分表v5转换。
            * @return 分库分表v5转换的结果
            */
            return new ShardingV5Conversion();
        }

        ShardingV5Conversion shardingV5 = (ShardingV5Conversion) conversion;
        shardingV5.autoDiscover(true);

        // 配置分表规则
        if (properties.getTables() != null) {
            for (ShardingDataSourceProperties.ShardingTableConfig tableConfig : properties.getTables()) {
                String logicTable = tableConfig.getLogicTable();
                String shardingColumn = tableConfig.getShardingColumn();
                String timeUnit = tableConfig.getTimeUnit();
                String strategy = tableConfig.getStrategy();

                if (timeUnit != null && !timeUnit.isEmpty()) {
 // 时间分片：使用 间隔 算法
                    String algoName = "interval_" + logicTable;
                    Map<String, String> algoProps = new HashMap<>();
                    algoProps.put("datetime-pattern", "yyyy-MM-dd");
                    algoProps.put("datetime-lower", "2000-01-01");
                    algoProps.put("datetime-upper", "2099-12-31");
                    algoProps.put("datetime-partition-pattern", getTimePartitionPattern(timeUnit));

                    shardingV5.algo(algoName, "INTERVAL", algoProps);
                    // 时间分片使用 12 个分片（覆盖一年）
                    shardingV5.table(logicTable, shardingColumn, 12, algoName);
                    log.info("[Sharding] 配置时间分片表: {} (列: {}, 单位: {})", logicTable, shardingColumn, timeUnit);
                } else {
                    // 普通分片：使用 MOD 算法
                    String algoName = "mod_" + logicTable;
                    shardingV5.algo(algoName, "MOD");
                    shardingV5.table(logicTable, shardingColumn, 16, algoName);
                    log.info("[Sharding] 配置普通分片表: {} (列: {})", logicTable, shardingColumn);
                }
            }
        }

 // 配置分库规则：分库与分表同前缀/同分片列对齐（原固定前缀 "db"/"用户_id" 与表前缀不匹配，
 // 导致 finddb配置 永远找不到分库配置、分库+分表分支从不激活——修正为按表逐表注册）
        boolean dbShardingEnabled = properties.getDatabases() != null && properties.getDatabases().size() > 1;
        if (dbShardingEnabled) {
            String algoName = "db_mod";
            shardingV5.algo(algoName, "MOD");
            if (properties.getTables() != null) {
                for (ShardingDataSourceProperties.ShardingTableConfig tableConfig : properties.getTables()) {
                    // 分库使用与分表相同的分片列，按库数 MOD 路由
                    shardingV5.db(tableConfig.getLogicTable(), tableConfig.getShardingColumn(),
                            properties.getDatabases().size(), algoName);
                }
            }
            log.info("[Sharding] 配置分库: {} 个数据库", properties.getDatabases().size());
        }

        return shardingV5;
    }

    /**
    * 根据时间单位返回分片模式
    * @param timeUnit 时间unit
    * @return 获取时间分区模式的结果
    */
    private String getTimePartitionPattern(String timeUnit) {
        switch (timeUnit.toUpperCase()) {
            case "YEAR":
                return "yyyy";
            case "QUARTER":
                return "yyyyQ";
            case "MONTH":
                return "yyyyMM";
            case "WEEK":
                return "yyyyww";
            case "DAY":
                return "yyyyMMdd";
            case "HOUR":
                return "yyyyMMddHH";
            default:
                return "yyyyMM";
        }
    }

    /**
    * 收集所有已注册的数据源（过滤动态路由数据源和分片数据源）
    * @return collect数据源的结果
    */
    private List<DataSource> collectDataSources() {
        Map<String, DataSource> allDataSources = DataSourceContextSupport.getAllDataSources();
        List<DataSource> result = new ArrayList<>();

        for (Map.Entry<String, DataSource> entry : allDataSources.entrySet()) {
            String name = entry.getKey();
            DataSource ds = entry.getValue();

            // 跳过分片数据源自身
            if ("shardingDataSource".equals(name)) {
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
