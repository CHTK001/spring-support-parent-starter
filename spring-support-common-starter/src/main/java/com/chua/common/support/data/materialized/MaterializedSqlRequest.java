package com.chua.common.support.data.materialized;

import java.util.ArrayList;
import java.util.List;

/**
 * 物化视图 SQL 请求类
 *
 * @param statementId      对账单标识
 * @param sourceDataSource 来源数据来源
 * @param sql              SQL
 * @param commandType      命令类型
 * @param definition       路由定义
 * @param tables           表名列表
 * @param parameters       参数列表
 * @author CH
 * @since 4.0.0
 */
public record MaterializedSqlRequest(
        String statementId,
        String sourceDataSource,
        String sql,
        MaterializedSqlCommandType commandType,
        MaterializedRouteDefinition definition,
        List<String> tables,
        List<Object> parameters) {

    /**
     * 创建构建器
     *
     * @return 构建器实例
     */
    public static MaterializedSqlRequestBuilder builder() {
        return new MaterializedSqlRequestBuilder();
    }

    /**
     * 物化SQL请求 构建器
     *
     * @author CH
     * @since 4.0.0
     */
    public static class MaterializedSqlRequestBuilder {
        /**
         * 对账单标识
         */
        private String statementId;
        /**
         * 来源数据来源
         */
        private String sourceDataSource;
        /**
         * SQL
         */
        private String sql;
        /**
         * 命令类型
         */
        private MaterializedSqlCommandType commandType;
        /**
         * 路由定义
         */
        private MaterializedRouteDefinition definition;
        /**
         * 表名列表
         */
        private final List<String> tables = new ArrayList<>();
        /**
         * 参数列表
         */
        private final List<Object> parameters = new ArrayList<>();

        /**
         * 设置语句 标识
         *
         * @param statementId 语句 标识
         * @return 当前构建器
         */
        public MaterializedSqlRequestBuilder statementId(String statementId) {
            this.statementId = statementId;
            return this;
        }

        /**
         * 设置源数据源
         *
         * @param sourceDataSource 源数据源
         * @return 当前构建器
         */
        public MaterializedSqlRequestBuilder sourceDataSource(String sourceDataSource) {
            this.sourceDataSource = sourceDataSource;
            return this;
        }

        /**
         * 设置 SQL 语句
         *
         * @param sql SQL 语句
         * @return 当前构建器
         */
        public MaterializedSqlRequestBuilder sql(String sql) {
            this.sql = sql;
            return this;
        }

        /**
         * 设置命令类型
         *
         * @param commandType 命令类型
         * @return 当前构建器
         */
        public MaterializedSqlRequestBuilder commandType(MaterializedSqlCommandType commandType) {
            this.commandType = commandType;
            return this;
        }

        /**
         * 设置路由定义
         *
         * @param definition 路由定义
         * @return 当前构建器
         */
        public MaterializedSqlRequestBuilder definition(MaterializedRouteDefinition definition) {
            this.definition = definition;
            return this;
        }

        /**
         * 添加表名
         *
         * @param table 表名
         * @return 当前构建器
         */
        public MaterializedSqlRequestBuilder table(String table) {
            this.tables.add(table);
            return this;
        }

        /**
         * 添加参数
         *
         * @param parameter 参数
         * @return 当前构建器
         */
        public MaterializedSqlRequestBuilder parameter(Object parameter) {
            this.parameters.add(parameter);
            return this;
        }

        /**
         * 构建请求
         *
         * @return 请求实例
         */
        public MaterializedSqlRequest build() {
            return new MaterializedSqlRequest(statementId, sourceDataSource, sql, commandType,
                    definition, tables, parameters);
        }
    }
}
