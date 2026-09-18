package com.chua.common.support.data.materialized;

import javax.sql.DataSource;

/**
* 物化视图 SQL 路由器接口
*
* @author CH
* @since 4.0.0
 */
public interface MaterializedSqlRouter {

    /**
    * 根据请求确定路由
    *
    * @param request 物化 SQL 请求
    * @return 路由结果
    */
    MaterializedSqlRoute route(MaterializedSqlRequest request);

    /**
    * 解析数据源
    *
    * @param route 路由信息
    * @return 数据源
    */
    DataSource resolveDataSource(MaterializedSqlRoute route);

    /**
    * 写操作成功回调
    *
    * @param request 物化 SQL 请求
    */
    void onWriteSuccess(MaterializedSqlRequest request);
}
