package com.chua.starter.datasource.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * ShardingSphere分库分表配置属性
 * <p>
 * 配置示例:
 * <pre>
 * spring:
 *   datasource:
 *     sharding:
 *       enabled: true
 *       show-sql: true
 *       tables:
 *         - logic-table: t_order
 *           sharding-column: create_time
 *           time-unit: MONTH
 *         - logic-table: t_user
 *           sharding-column: user_id
 *       databases:
 *         - db_order
 *         - db_user
 * </pre>
 *
 * @since 2024/12/21
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "spring.datasource.sharding")
public class ShardingDataSourceProperties {

    /**
     * 是否启用分片
     */
    private boolean enabled = false;

    /**
     * 是否显示SQL
     */
    private boolean showSql = false;

    /**
     * 分表配置列表
     */
    private List<ShardingTableConfig> tables;

    /**
     * 分库配置列表（逻辑库名）
     */
    private List<String> databases;

    /**
     * 分表配置
     */
    @Data
    public static class ShardingTableConfig {
        /**
         * 逻辑表名
         */
        private String logicTable;

        /**
         * 分片列（分片键）
         */
        private String shardingColumn;

        /**
         * 时间分片单位: YEAR, QUARTER, MONTH, WEEK, DAY, HOUR
         */
        private String timeUnit;

        /**
         * 分片策略: TABLE, DB
         */
        private String strategy = "TABLE";

        // Getter 方法（Lombok 在 Java 25 下可能不工作，手动添加）
        /** 获取LogicTable */
        public String getLogicTable() {
            return logicTable;
        }

        /** 获取ShardingColumn */
        public String getShardingColumn() {
            return shardingColumn;
        }

        /** 获取TimeUnit */
        public String getTimeUnit() {
            return timeUnit;
        }

        /** 获取Strategy */
        public String getStrategy() {
            return strategy;
        }
    }

    // Getter 方法（Lombok 在 Java 25 下可能不工作，手动添加）
    /** 是否ShowSql */
    public boolean isShowSql() {
        return showSql;
    }

    /** 获取Databases */
    public List<String> getDatabases() {
        return databases;
    }

    /** 获取Tables */
    public List<ShardingTableConfig> getTables() {
        return tables;
    }
}
