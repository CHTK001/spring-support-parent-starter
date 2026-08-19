package com.chua.common.support.data.materialized;

import java.util.ArrayList;
import java.util.List;

/**
 * 物化视图 SQL 请求类
 *
 * @author CH
 */
public class MaterializedSqlRequest {

    /** StatementID */
    private String statementId;
    /** 来源数据来源 */
    private String sourceDataSource;
    /** SQL */
    private String sql;
    /** 命令类型 */
    private MaterializedSqlCommandType commandType;
    /** Definition */
    private MaterializedRouteDefinition definition;
    /** Tables */
    private List<String> tables = new ArrayList<>();
    /** Parameters */
    private List<Object> parameters = new ArrayList<>();

    /**
     * 获取 SQL 语句
     *
     * @return SQL 语句
     */
    public String getSql() {
        return sql;
    }

    /**
     * 获取 SQL 语句 ID
     *
     * @return 语句 ID
     */
    public String getStatementId() {
        return statementId;
    }

    /**
     * 获取源数据源
     *
     * @return 源数据源
     */
    public String getSourceDataSource() {
        return sourceDataSource;
    }

    /**
     * 获取命令类型
     *
     * @return 命令类型
     */
    public MaterializedSqlCommandType getCommandType() {
        return commandType;
    }

    /**
     * 获取表列表
     *
     * @return 表列表
     */
    public List<String> getTables() {
        return tables;
    }

    /**
     * 获取参数列表
     *
     * @return 参数列表
     */
    public List<Object> getParameters() {
        return parameters;
    }

    /**
     * 创建构建器
     *
     * @return 构建器实例
     */
    public static MaterializedSqlRequestBuilder builder() {
        return new MaterializedSqlRequestBuilder();
    }

    /**
     * MaterializedSqlRequest 构建器
     */
    public static class MaterializedSqlRequestBuilder {
        /** 请求 */
        private final MaterializedSqlRequest request = new MaterializedSqlRequest();

        /**
         * 设置语句 ID
         *
         * @param statementId 语句 ID
         * @return 当前构建器
         */
        public MaterializedSqlRequestBuilder statementId(String statementId) {
            request.statementId = statementId;
            return this;
        }

        /**
         * 设置源数据源
         *
         * @param sourceDataSource 源数据源
         * @return 当前构建器
         */
        public MaterializedSqlRequestBuilder sourceDataSource(String sourceDataSource) {
            request.sourceDataSource = sourceDataSource;
            return this;
        }

        /**
         * 设置 SQL 语句
         *
         * @param sql SQL 语句
         * @return 当前构建器
         */
        public MaterializedSqlRequestBuilder sql(String sql) {
            request.sql = sql;
            return this;
        }

        /**
         * 设置命令类型
         *
         * @param commandType 命令类型
         * @return 当前构建器
         */
        public MaterializedSqlRequestBuilder commandType(MaterializedSqlCommandType commandType) {
            request.commandType = commandType;
            return this;
        }

        /**
         * 设置路由定义
         *
         * @param definition 路由定义
         * @return 当前构建器
         */
        public MaterializedSqlRequestBuilder definition(MaterializedRouteDefinition definition) {
            request.definition = definition;
            return this;
        }

        /**
         * 添加表名
         *
         * @param table 表名
         * @return 当前构建器
         */
        public MaterializedSqlRequestBuilder table(String table) {
            request.tables.add(table);
            return this;
        }

        /**
         * 添加参数
         *
         * @param parameter 参数
         * @return 当前构建器
         */
        public MaterializedSqlRequestBuilder parameter(Object parameter) {
            request.parameters.add(parameter);
            return this;
        }

        /**
         * 构建请求
         *
         * @return 请求实例
         */
        public MaterializedSqlRequest build() {
            return request;
        }
    }
}