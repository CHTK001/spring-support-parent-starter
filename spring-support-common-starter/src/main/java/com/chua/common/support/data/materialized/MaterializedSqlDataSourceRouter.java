package com.chua.common.support.data.materialized;

import javax.sql.DataSource;

/**
 * 物化视图 SQL 数据源路由器
 *
 * @author CH
 * @since 4.0.0
 */
public interface MaterializedSqlDataSourceRouter extends MaterializedSqlRouter {

    /**
     * 根据路由信息选择数据源
     *
     * @param route 路由信息
     * @return 数据源
     */
    DataSource selectDataSource(MaterializedSqlRoute route);

    /**
     * 获取所有可用数据源
     *
     * @return 数据源映射
     */
    java.util.Map<String, DataSource> getDataSources();

    /**
     * 根据请求确定路由
     *
     * @param request 物化 SQL 请求
     * @return 路由结果
     */
    @Override
    default MaterializedSqlRoute route(MaterializedSqlRequest request) {
        return null;
    }

    /**
     * 解析数据源
     *
     * @param route 路由信息
     * @return 数据源
     */
    @Override
    default DataSource resolveDataSource(MaterializedSqlRoute route) {
        return null;
    }

    /**
     * 写操作成功回调
     *
     * @param request 物化 SQL 请求
     */
    @Override
    default void onWriteSuccess(MaterializedSqlRequest request) {
    }
}
