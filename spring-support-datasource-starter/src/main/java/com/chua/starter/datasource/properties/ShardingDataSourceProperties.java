package com.chua.starter.datasource.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 分库分表sphere分库分表配置属性
 * <p>
 * 配置示例:
 * <pre>
 * plugin:
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
@ConfigurationProperties(prefix = "plugin.datasource.sharding")
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
     * @author CH
     * @since 4.0.0
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
        /**
         * 获取逻辑table
         *
         * @return 获取逻辑table的结果
         */
        public String getLogicTable() {
            return logicTable;
        }

        /**
         * 获取分库分表column
         *
         * @return 获取分库分表column的结果
         */
        public String getShardingColumn() {
            return shardingColumn;
        }

        /**
         * 获取时间unit
         *
         * @return 获取时间unit的结果
         */
        public String getTimeUnit() {
            return timeUnit;
        }

        /**
         * 获取Strategy
         *
         * @return 获取strategy的结果
         */
        public String getStrategy() {
            return strategy;
        }
    }

    // Getter 方法（Lombok 在 Java 25 下可能不工作，手动添加）
    /**
     * 是否showsql
     *
     * @return 是否showsql的结果
     */
    public boolean isShowSql() {
        return showSql;
    }

    /**
     * 获取Databases
     *
     * @return 获取databases的结果
     */
    public List<String> getDatabases() {
        return databases;
    }

    /**
     * 获取Tables
     *
     * @return 获取tables的结果
     */
    public List<ShardingTableConfig> getTables() {
        return tables;
    }
}
