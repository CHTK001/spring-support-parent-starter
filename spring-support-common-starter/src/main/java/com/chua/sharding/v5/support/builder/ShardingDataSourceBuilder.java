package com.chua.sharding.v5.support.builder;

import javax.sql.DataSource;
import java.util.Map;

/**
* 分库分表sphere 5.x 分片数据源构建器
* <p>
* 提供构建分片数据源的链式 API，支持分库、分表、显示 SQL 等配置。
* 当 类路径 中不存在 分库分表sphere 依赖时，相关配置不会生效。
* </p>
*
* @author CH
* @since 4.0.0
 */
public class ShardingDataSourceBuilder {

    private Map<String, DataSource> dataSources; // 数据源
    /** showsql */
    private boolean showSql;

    /**
    * 创建构建器实例
    *
    * @return 构建器实例
     */
    public static ShardingDataSourceBuilder newBuilder() {
        /**
        * 分库分表数据源构建器。
         */
        return new ShardingDataSourceBuilder();
    }

    /**
    * 设置数据源映射
    *
    * @param dataSources 数据源名称到实例的映射
    * @return 当前构建器实例
     */
    public ShardingDataSourceBuilder dataSources(Map<String, DataSource> dataSources) {
        this.dataSources = dataSources;
        return this;
    }

    /**
    * 设置分库规则
    *
    * @param database 数据库名称
    * @return 当前构建器实例
     */
    public ShardingDataSourceBuilder shardingDatabase(String database) {
        return this;
    }

    /**
    * 设置是否显示 SQL
    *
    * @param showSql 是否显示 SQL
    * @return 当前构建器实例
     */
    public ShardingDataSourceBuilder showSql(boolean showSql) {
        this.showSql = showSql;
        return this;
    }

    /**
    * 开始配置分表规则
    *
    * @param logicTable 逻辑表名
    * @return 分表规则构建器
     */
    public TableShardingBuilder shardingTable(String logicTable) {
        /**
        * table分库分表构建器。
        * @return table分库分表构建器的结果
         */
        return new TableShardingBuilder();
    }

    /**
    * 构建分片数据源
    *
    * @return 数据源实例
     */
    public DataSource build() {
        return null;
    }

    /**
    * 分表规则构建器
    * @author CH
    * @since 4.0.0
     */
    public static class TableShardingBuilder {

        /**
        * 设置分片列
        *
        * @param shardingColumn 分片列名
        * @return 当前构建器实例
         */
        public TableShardingBuilder shardingColumn(String shardingColumn) {
            return this;
        }

        /**
        * 按年分片
        *
        * @return 当前构建器实例
         */
        public TableShardingBuilder shardByYear() {
            return this;
        }

        /**
        * 按季度分片
        *
        * @return 当前构建器实例
         */
        public TableShardingBuilder shardByQuarter() {
            return this;
        }

        /**
        * 按月分片
        *
        * @return 当前构建器实例
         */
        public TableShardingBuilder shardByMonth() {
            return this;
        }

        /**
        * 按周分片
        *
        * @return 当前构建器实例
         */
        public TableShardingBuilder shardByWeek() {
            return this;
        }

        /**
        * 按天分片
        *
        * @return 当前构建器实例
         */
        public TableShardingBuilder shardByDay() {
            return this;
        }

        /**
        * 按小时分片
        *
        * @return 当前构建器实例
         */
        public TableShardingBuilder shardByHour() {
            return this;
        }

        /**
        * 完成分表规则配置
         */
        public void done() {
        }
    }
}
